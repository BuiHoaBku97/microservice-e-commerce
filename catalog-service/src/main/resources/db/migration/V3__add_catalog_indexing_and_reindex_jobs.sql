ALTER TABLE products ADD COLUMN search_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE catalog_outbox_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    product_version BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX idx_catalog_outbox_pending ON catalog_outbox_events(status, next_attempt_at, created_at);

CREATE TABLE catalog_reindex_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    requested_by BIGINT NOT NULL,
    target_index VARCHAR(150) NOT NULL,
    alias_name VARCHAR(100) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    checkpoint BIGINT NOT NULL DEFAULT 0,
    error_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_catalog_reindex_active ON catalog_reindex_jobs(alias_name)
    WHERE status IN ('PENDING', 'RUNNING');
