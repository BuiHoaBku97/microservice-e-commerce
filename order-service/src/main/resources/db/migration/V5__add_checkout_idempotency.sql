ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN idempotency_hash VARCHAR(64);

CREATE UNIQUE INDEX uk_orders_user_idempotency_key
    ON orders (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_orders_idempotency_hash
    ON orders (user_id, idempotency_key, idempotency_hash);
