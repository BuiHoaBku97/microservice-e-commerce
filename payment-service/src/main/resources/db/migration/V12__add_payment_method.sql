ALTER TABLE payments ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'MOCK_ONLINE';
ALTER TABLE payments ADD CONSTRAINT ck_payments_payment_method
    CHECK (payment_method IN ('MOCK_ONLINE', 'VNPAY'));
