CREATE TABLE IF NOT EXISTS banking.mcp_write_idempotency (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mcp_write_idempotency_account_key
        UNIQUE (account_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_mcp_write_idempotency_created_at
    ON banking.mcp_write_idempotency (created_at);
