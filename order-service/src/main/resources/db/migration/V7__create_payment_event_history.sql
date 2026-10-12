CREATE TABLE payment_event_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    result VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_event_history_order_id ON payment_event_history (order_id);
