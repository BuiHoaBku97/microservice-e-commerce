CREATE TABLE order_payment_contexts (
    order_id BIGINT PRIMARY KEY,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_reason VARCHAR(50),
    cancellation_event_id VARCHAR(100) UNIQUE,
    cancellation_occurred_at TIMESTAMPTZ,
    cancellation_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_payment_context_cancellation
        CHECK (
            (closed = FALSE
                AND cancellation_reason IS NULL
                AND cancellation_event_id IS NULL
                AND cancellation_occurred_at IS NULL
                AND cancellation_version IS NULL)
            OR
            (closed = TRUE
                AND cancellation_reason IN ('PAYMENT_FAILED', 'PAYMENT_EXPIRED', 'CUSTOMER_CANCELLED')
                AND cancellation_event_id IS NOT NULL
                AND cancellation_occurred_at IS NOT NULL
                AND cancellation_version IS NOT NULL)
        )
);
