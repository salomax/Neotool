SET search_path TO mcp, public;

CREATE TABLE IF NOT EXISTS mcp.rate_limit_counters (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    scope TEXT NOT NULL CHECK (scope IN ('TOKEN', 'USER')),
    scope_id UUID NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_size_ms BIGINT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_mcp_rate_limit_counters_scope_scope_id_window
        UNIQUE (scope, scope_id, window_start, window_size_ms)
);

CREATE INDEX IF NOT EXISTS idx_mcp_rate_limit_counters_scope_scope_id_window_start
    ON mcp.rate_limit_counters(scope, scope_id, window_start);
