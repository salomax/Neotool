CREATE SCHEMA IF NOT EXISTS mcp;

CREATE TABLE IF NOT EXISTS mcp.connection_revocations (
    user_id UUID NOT NULL,
    client_id TEXT NOT NULL,
    account_id UUID NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_until TIMESTAMPTZ,
    reason TEXT,
    PRIMARY KEY (user_id, client_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_mcp_connection_revocations_revoked_until
    ON mcp.connection_revocations (revoked_until)
    WHERE revoked_until IS NOT NULL;
