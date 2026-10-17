ALTER TABLE catalog_outbox_events ADD COLUMN aggregate_type VARCHAR(80) NOT NULL DEFAULT 'PRODUCT';
ALTER TABLE catalog_outbox_events ADD COLUMN aggregate_id BIGINT;
ALTER TABLE catalog_outbox_events ADD COLUMN aggregate_version BIGINT;
ALTER TABLE catalog_outbox_events ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE catalog_outbox_events ADD COLUMN topic VARCHAR(200) NOT NULL DEFAULT 'catalog.search.index';
ALTER TABLE catalog_outbox_events ADD COLUMN partition_key VARCHAR(128);
ALTER TABLE catalog_outbox_events ADD COLUMN payload TEXT;
ALTER TABLE catalog_outbox_events ADD COLUMN occurred_at TIMESTAMPTZ;
ALTER TABLE catalog_outbox_events ADD COLUMN claimed_by VARCHAR(128);
ALTER TABLE catalog_outbox_events ADD COLUMN lease_until TIMESTAMPTZ;

UPDATE catalog_outbox_events
SET aggregate_id = product_id,
    aggregate_version = product_version,
    partition_key = product_id::text,
    payload = json_build_object('productId', product_id, 'productVersion', product_version)::text,
    occurred_at = created_at
WHERE aggregate_id IS NULL;

ALTER TABLE catalog_outbox_events ALTER COLUMN aggregate_id SET NOT NULL;
ALTER TABLE catalog_outbox_events ALTER COLUMN aggregate_version SET NOT NULL;
ALTER TABLE catalog_outbox_events ALTER COLUMN partition_key SET NOT NULL;
ALTER TABLE catalog_outbox_events ALTER COLUMN payload SET NOT NULL;
ALTER TABLE catalog_outbox_events ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE catalog_outbox_events ADD CONSTRAINT ck_catalog_outbox_schema_version CHECK (schema_version > 0);
CREATE INDEX idx_catalog_outbox_lease ON catalog_outbox_events(lease_until)
    WHERE status = 'PROCESSING';
