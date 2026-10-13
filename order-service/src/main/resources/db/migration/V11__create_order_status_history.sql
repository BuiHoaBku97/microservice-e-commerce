CREATE TABLE order_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    status VARCHAR(20) NOT NULL,
    source VARCHAR(50) NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_status_history_order_created
    ON order_status_history(order_id, created_at);
