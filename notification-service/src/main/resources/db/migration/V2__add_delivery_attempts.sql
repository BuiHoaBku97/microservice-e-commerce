ALTER TABLE notifications ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN last_error TEXT;

CREATE TABLE delivery_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    error TEXT,
    attempted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_delivery_attempts_notification ON delivery_attempts(notification_id, attempted_at DESC);
