CREATE TABLE IF NOT EXISTS banking.mcp_change_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tool_call_id UUID NOT NULL,
    client_id VARCHAR(255),
    token_id VARCHAR(255),
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    before JSONB NOT NULL,
    after JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mcp_change_log_client_created_at
    ON banking.mcp_change_log (client_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_mcp_change_log_token_created_at
    ON banking.mcp_change_log (token_id, created_at DESC);

ALTER TABLE banking.mcp_change_log
    ADD CONSTRAINT ck_mcp_change_log_resource_type
        CHECK (resource_type IN ('TRANSACTION'));

ALTER TABLE banking.mcp_change_log
    ADD CONSTRAINT ck_mcp_change_log_action
        CHECK (action IN ('UPDATE', 'REVERT'));

CREATE OR REPLACE FUNCTION banking.prevent_mcp_change_log_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'banking.mcp_change_log is immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_mcp_change_log_immutable ON banking.mcp_change_log;

CREATE TRIGGER tr_mcp_change_log_immutable
    BEFORE UPDATE OR DELETE ON banking.mcp_change_log
    FOR EACH ROW
EXECUTE FUNCTION banking.prevent_mcp_change_log_mutation();

REVOKE UPDATE, DELETE ON banking.mcp_change_log FROM PUBLIC;
