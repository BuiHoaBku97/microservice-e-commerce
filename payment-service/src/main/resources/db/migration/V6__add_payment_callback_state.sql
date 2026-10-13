ALTER TABLE payments ADD COLUMN funds_status VARCHAR(20) NOT NULL DEFAULT 'NOT_RECEIVED';
ALTER TABLE payments ADD COLUMN order_acceptance VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE payments ADD COLUMN provider_transaction_id VARCHAR(120);
ALTER TABLE payments ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN review_reason VARCHAR(100);
UPDATE payments SET status='SUCCESS', funds_status='RECEIVED' WHERE status='COMPLETED';

CREATE TABLE payment_callbacks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_event_id VARCHAR(120) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    received_at TIMESTAMPTZ NOT NULL
);
