SET search_path TO mcp, public;

CREATE TABLE IF NOT EXISTS mcp.tool_call_audit_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    occurred_at TIMESTAMPTZ NOT NULL,
    user_id UUID,
    client_id VARCHAR(255),
    token_id VARCHAR(255),
    tool_name VARCHAR(255),
    arguments JSONB NOT NULL DEFAULT '{}'::jsonb,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'ERROR', 'REJECTED')),
    error_code VARCHAR(255),
    error_message TEXT,
    latency_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mcp_tool_call_audit_log_occurred_at
    ON mcp.tool_call_audit_log (occurred_at);

REVOKE UPDATE, DELETE ON mcp.tool_call_audit_log FROM PUBLIC;

CREATE OR REPLACE FUNCTION mcp.prevent_tool_call_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'mcp.tool_call_audit_log is immutable';
END;
$$;

DROP TRIGGER IF EXISTS tr_tool_call_audit_log_immutable ON mcp.tool_call_audit_log;

CREATE TRIGGER tr_tool_call_audit_log_immutable
    BEFORE UPDATE OR DELETE
    ON mcp.tool_call_audit_log
    FOR EACH ROW
EXECUTE FUNCTION mcp.prevent_tool_call_audit_log_mutation();
