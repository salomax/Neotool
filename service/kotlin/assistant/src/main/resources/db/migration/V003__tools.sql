-- ============================================================================
-- Assistant Service - Tool Registry Schema
-- Version: V003__tools.sql
-- Purpose:
--   - Define tool metadata table and execution audit table
-- ============================================================================

SET search_path TO assistant, public;

-- ============================================================================
-- Table: tools
-- Purpose: Catalog of tool metadata available for LLM tool-calling
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.tools (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL,
    input_schema JSONB NOT NULL DEFAULT '{}',
    output_schema JSONB NOT NULL DEFAULT '{}',
    executor_service TEXT NOT NULL,
    executor_endpoint TEXT NOT NULL,
    timeout_ms INTEGER NOT NULL,
    retry_policy JSONB,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tools_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_tools_status ON assistant.tools(status);

-- ============================================================================
-- Table: tool_executions
-- Purpose: Audit trail of tool execution requests/responses
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.tool_executions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    completion_request_id UUID NOT NULL,
    tool_id UUID NOT NULL,
    input JSONB NOT NULL DEFAULT '{}',
    output JSONB,
    status TEXT NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    error TEXT,
    latency_ms INTEGER,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tool_executions_completion_request
        FOREIGN KEY (completion_request_id)
        REFERENCES assistant.completion_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_tool_executions_tool
        FOREIGN KEY (tool_id)
        REFERENCES assistant.tools(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tool_executions_completion_request_id
    ON assistant.tool_executions(completion_request_id);
