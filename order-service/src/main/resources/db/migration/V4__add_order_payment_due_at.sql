ALTER TABLE orders ADD COLUMN payment_due_at TIMESTAMPTZ;

-- Giữ hạn ổn định cho dữ liệu cũ, tính từ lúc tạo thay vì lúc đọc.
UPDATE orders SET payment_due_at = created_at + INTERVAL '15 minutes';

ALTER TABLE orders ALTER COLUMN payment_due_at SET NOT NULL;

