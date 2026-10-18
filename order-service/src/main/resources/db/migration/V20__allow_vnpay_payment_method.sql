ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_payment_method;
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method
    CHECK (payment_method IN ('MOCK_ONLINE', 'VNPAY'));
