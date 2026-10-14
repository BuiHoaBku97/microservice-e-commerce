CREATE TABLE reservations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED')),
    expires_at TIMESTAMPTZ NOT NULL,
    committed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    release_reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_reservations_status_expiry ON reservations(status, expires_at);

CREATE TABLE reservation_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_reservation_items_sku UNIQUE (reservation_id, sku_id)
);

CREATE TABLE stock_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN
        ('INITIALIZE', 'RECEIPT', 'ADJUSTMENT', 'RESERVE', 'COMMIT', 'RELEASE')),
    on_hand_delta INTEGER NOT NULL,
    reserved_delta INTEGER NOT NULL,
    on_hand_after INTEGER NOT NULL CHECK (on_hand_after >= 0),
    reserved_after INTEGER NOT NULL CHECK (reserved_after >= 0 AND reserved_after <= on_hand_after),
    order_id BIGINT,
    reservation_id BIGINT REFERENCES reservations(id),
    actor_user_id BIGINT,
    source_event_id VARCHAR(100),
    operation_id UUID NOT NULL,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_stock_movement_operation UNIQUE (operation_id, sku_id, movement_type)
);

CREATE INDEX idx_stock_movements_sku_created ON stock_movements(sku_id, created_at DESC);

CREATE TABLE inventory_idempotency_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    sku_id BIGINT NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_idempotency_expiry ON inventory_idempotency_records(expires_at);

-- Existing pending orders already contributed to inventory.reserved. Persist their reservation
-- representation without changing stock. The statement is idempotent through order_id uniqueness.
INSERT INTO reservations(order_id, status, expires_at, created_at, updated_at)
SELECT o.id, 'ACTIVE', o.payment_due_at, o.created_at, now()
FROM orders o
WHERE o.status = 'PENDING_PAYMENT'
ON CONFLICT (order_id) DO NOTHING;

INSERT INTO reservation_items(reservation_id, sku_id, quantity)
SELECT r.id, oi.sku_id, SUM(oi.quantity)::INTEGER
FROM reservations r
JOIN order_items oi ON oi.order_id = r.order_id
GROUP BY r.id, oi.sku_id
ON CONFLICT (reservation_id, sku_id) DO NOTHING;
