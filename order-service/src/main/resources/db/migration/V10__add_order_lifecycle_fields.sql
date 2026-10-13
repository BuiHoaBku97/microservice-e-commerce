ALTER TABLE orders ADD COLUMN accepted_payment_id BIGINT;
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(50);
ALTER TABLE orders ADD COLUMN shipping_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE orders SET cancellation_reason = 'PAYMENT_FAILED', status = 'CANCELLED'
WHERE status = 'PAYMENT_FAILED';
UPDATE orders SET cancellation_reason = 'PAYMENT_EXPIRED', status = 'CANCELLED'
WHERE status = 'PAYMENT_REJECTED';
