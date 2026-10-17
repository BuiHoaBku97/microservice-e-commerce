ALTER TABLE processed_events ADD COLUMN payload_hash VARCHAR(64);
ALTER TABLE processed_events ADD COLUMN aggregate_id BIGINT;
UPDATE processed_events SET payload_hash = repeat('0', 64) WHERE payload_hash IS NULL;
ALTER TABLE processed_events ALTER COLUMN payload_hash SET NOT NULL;
CREATE INDEX idx_notification_processed_events_processed_at ON processed_events(processed_at);
