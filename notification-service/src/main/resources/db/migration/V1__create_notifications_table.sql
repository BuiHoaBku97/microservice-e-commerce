CREATE TABLE notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    reference_type VARCHAR(80) NOT NULL,
    reference_id BIGINT NOT NULL,
    deduplication_key VARCHAR(180) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);
