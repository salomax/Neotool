SET search_path TO assistant, public;

ALTER TABLE assistant.tools
    ADD COLUMN IF NOT EXISTS mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mcp_required_scope TEXT;

CREATE INDEX IF NOT EXISTS idx_tools_mcp_exposed_status
    ON assistant.tools(mcp_exposed, status);
