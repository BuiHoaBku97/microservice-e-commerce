CREATE TABLE processed_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 consumer_name VARCHAR(100) NOT NULL,
 event_id VARCHAR(100) NOT NULL,
 event_type VARCHAR(80) NOT NULL,
 processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uk_notification_processed_event UNIQUE(consumer_name,event_id)
);
