ALTER TABLE payment_event_history
    DROP CONSTRAINT IF EXISTS payment_event_history_payment_id_key;

CREATE INDEX IF NOT EXISTS idx_payment_event_history_payment_id
    ON payment_event_history (payment_id);
