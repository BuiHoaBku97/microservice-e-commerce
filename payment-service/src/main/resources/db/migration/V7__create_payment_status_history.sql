CREATE TABLE payment_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    status VARCHAR(20) NOT NULL,
    funds_status VARCHAR(20) NOT NULL,
    order_acceptance VARCHAR(20) NOT NULL,
    source VARCHAR(50) NOT NULL,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payment_status_history_payment_created
    ON payment_status_history(payment_id, created_at DESC);
