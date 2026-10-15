UPDATE orders
SET status = 'CANCELLED',
    cancellation_reason = 'PAYMENT_MISMATCH'
WHERE status = 'PAYMENT_REVIEW';

UPDATE order_status_history
SET status = 'CANCELLED',
    reason = COALESCE(reason, 'PAYMENT_MISMATCH')
WHERE status = 'PAYMENT_REVIEW';
