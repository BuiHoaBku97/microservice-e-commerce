ALTER TABLE payments ADD COLUMN payment_due_at TIMESTAMPTZ;

UPDATE payments SET payment_due_at = created_at + INTERVAL '15 minutes';

ALTER TABLE payments ALTER COLUMN payment_due_at SET NOT NULL;
