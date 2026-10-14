# Chaos and recovery runbook

Run one failure at a time against disposable Compose data. Capture service metrics and verify database
invariants after each recovery.

1. Stop Kafka, create an order and update catalog data, restart Kafka, then verify pending outbox age
   returns to zero and event IDs are unchanged.
2. Stop Elasticsearch, update a product, restart it, then verify the catalog outbox is published and
   the new `productVersion` appears in search.
3. Stop Redis and attempt inventory writes. They must fail without changing inventory or movements.
4. Stop Identity and attempt checkout/admin writes. They must fail closed.
5. Kill an outbox or reindex worker during a batch. Restart it and verify lease/checkpoint recovery.
6. Make the notification provider fail until retry exhaustion, then verify delivery attempts and the
   terminal failed state.

Required final SQL invariants: `on_hand >= 0`, `reserved >= 0`, `reserved <= on_hand`, one ACTIVE
reservation per pending order, no duplicate processed-event key, and no order with two accepted payments.

Use `compose-chaos.ps1 -Dependency <name>` to stop and restart one dependency. Pass
`-DuringFailureProbe` and `-AfterRecoveryProbe` script blocks to create the workload and assert recovery;
the helper always restarts the selected dependency in `finally`.
