ALTER TABLE orders ADD COLUMN order_number VARCHAR(40);
ALTER TABLE orders ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION assign_order_number()
RETURNS trigger AS $$
BEGIN
    IF NEW.order_number IS NULL THEN
        NEW.order_number := 'ORD-' || lpad(NEW.id::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_assign_order_number
BEFORE INSERT ON orders
FOR EACH ROW EXECUTE FUNCTION assign_order_number();

UPDATE orders
SET order_number = 'ORD-' || lpad(id::text, 6, '0')
WHERE order_number IS NULL;

ALTER TABLE orders ALTER COLUMN order_number SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT uk_orders_order_number UNIQUE (order_number);
ALTER TABLE orders ADD CONSTRAINT ck_orders_lifecycle_timestamps CHECK (
    (status NOT IN ('CONFIRMED', 'SHIPPING', 'COMPLETED') OR confirmed_at IS NOT NULL)
    AND (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
    AND (status NOT IN ('SHIPPING', 'COMPLETED') OR shipping_at IS NOT NULL)
    AND (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(128) NOT NULL;
