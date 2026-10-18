CREATE TABLE payment_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    provider VARCHAR(30) NOT NULL,
    txn_ref VARCHAR(100) NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_transaction_id VARCHAR(120),
    response_code VARCHAR(10),
    transaction_status VARCHAR(10),
    bank_code VARCHAR(30),
    bank_transaction_no VARCHAR(120),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_transactions_payment ON payment_transactions(payment_id, created_at DESC);
CREATE INDEX idx_payment_transactions_provider_tx ON payment_transactions(provider, provider_transaction_id);
