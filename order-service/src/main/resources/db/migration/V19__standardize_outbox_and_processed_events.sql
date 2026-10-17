ALTER TABLE outbox_events RENAME COLUMN claimed_until TO lease_until;
ALTER TABLE outbox_events ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE outbox_events ADD COLUMN topic VARCHAR(200) NOT NULL DEFAULT 'ecommerce.order.events';
ALTER TABLE outbox_events ADD COLUMN partition_key VARCHAR(128);
ALTER TABLE outbox_events ADD COLUMN occurred_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN claimed_by VARCHAR(128);

UPDATE outbox_events
SET partition_key = aggregate_id::text,
    occurred_at = COALESCE(NULLIF(payload::jsonb ->> 'occurredAt', '')::timestamptz, created_at)
WHERE partition_key IS NULL OR occurred_at IS NULL;

ALTER TABLE outbox_events ALTER COLUMN partition_key SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT ck_order_outbox_schema_version CHECK (schema_version > 0);
CREATE INDEX idx_order_outbox_lease ON outbox_events(lease_until)
    WHERE status = 'PROCESSING';

ALTER TABLE processed_events ADD COLUMN payload_hash VARCHAR(64);
ALTER TABLE processed_events ADD COLUMN aggregate_id BIGINT;
UPDATE processed_events SET payload_hash = repeat('0', 64) WHERE payload_hash IS NULL;
ALTER TABLE processed_events ALTER COLUMN payload_hash SET NOT NULL;
CREATE INDEX idx_order_processed_events_processed_at ON processed_events(processed_at);
