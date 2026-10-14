ALTER TABLE outbox_events ADD COLUMN claimed_until TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE outbox_events ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE processed_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_processed_events_consumer_event UNIQUE (consumer_name, event_id)
);
