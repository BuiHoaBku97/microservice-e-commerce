# C4 — Event flow integration tests

## Run

Use JDK 21 and a running Docker engine:

```powershell
.\gradlew test
.\gradlew :event-flow-tests:integrationTest
```

The integration task starts disposable PostgreSQL 16.4, Kafka 3.9.1 and Redis 7.4.1
containers. It runs separate Spring contexts for Order, Payment and Notification,
each with its own database schema and consumer group. It uses production services,
repositories, consumers, publishers and Flyway migrations. Scheduling is disabled;
tests explicitly trigger outbox publication. Checkout HTTP clients are mocked
because checkout pricing/address lookup is outside this event-flow test.

No application Docker Compose stack or local database credentials are needed.
The integration task fails when Docker is unavailable; it does not silently skip.
The normal `test` task excludes the `integration` tag.
Reports: `event-flow-tests/build/reports/tests/integrationTest/index.html`.

## Coverage

- OrderCreated → PaymentSucceeded → OrderConfirmed → persisted notifications.
- Restart the Order application context with the same consumer group, then replay
  the original payment event. Wait for the committed offset of the replay before
  asserting one stock deduction, one payment-history row and one confirmation.
- Two concurrent payment callbacks in independent database transactions using real
  PostgreSQL row locks and Redis inventory locks.
- Wrong amount and expired payment: review, reservation release, unchanged on-hand.
- Callback after the expiry job released the reservation: no second release or confirmation.
- PaymentFailed replay: release reservation once and deduplicate notification.
- Rollback after payment handling, followed by retry: stock/history/outbox remain atomic.
- Invalid envelope sent through Kafka: original key/payload/partition arrive on the
  group-specific DLT and source offset is committed after recovery.
- Upgrade Order V7 and Payment V3 databases containing old outbox data; preserve
  existing IDs, backfill missing IDs into both column and payload, enforce unique
  and not-null constraints, and verify a second migration run is a no-op.

The backfill adds only missing event IDs. It does not synthesize other envelope
fields for records predating C1; those records remain subject to consumer validation.

Publisher unit tests exercise an actual failed future followed by success and assert
unchanged key/payload/eventId, PENDING after failure and PUBLISHED after acknowledgement.
They also simulate a failed database save after Kafka acknowledged publication and
verify that reloading the durable pending row republishes the original identity.
Existing KafkaErrorHandler tests verify retries and refusal to commit when DLT
publication fails or has not yet completed. These failure-injection tests use mocks;
they do not simulate a broker process outage.

## Observability

All three consumer factories log `eventId`, `orderId`, `paymentId`, topic,
partition, offset and handler outcome. Identifiers are bounded and line breaks
removed; complete business payloads are not logged. A handled event can be a
duplicate or business rejection; it does not mean the order was confirmed.

Publishers log event ID, aggregate ID, type and failed-attempt count.
Micrometer `outbox.publish` counters have bounded `result` tags:
`acknowledged` and `failed`. Acknowledged counts Kafka acknowledgements, including
retries; it is not a count of unique business events or successful DB saves.
The consumer factories explicitly enable the standard `spring.kafka.listener`
timers; KafkaTemplate provides `spring.kafka.template` timers.
IDs appear in logs, not metric labels, to avoid unbounded cardinality.
This change does not expose new actuator endpoints or change their authorization.

## M2 review

Catalog and Identity restrict `/internal/**` to `SCOPE_INTERNAL_SERVICE`.
Filter tests cover valid, wrong, missing and empty service tokens, empty server
configuration, preserving the authenticated user, and public API paths.
User JWT authentication remains applicable to public service APIs.

## Limits

These tests cover the event pipeline and persistence, not browser/API checkout,
real payment providers, external notification delivery or multi-broker failover.
Matching Kafka keys preserves per-partition ordering within each topic; it does
not establish total ordering across the Order and Payment topics.
