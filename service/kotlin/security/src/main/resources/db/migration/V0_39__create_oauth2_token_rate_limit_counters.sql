SET search_path TO security, public;

CREATE TABLE IF NOT EXISTS security.oauth2_token_rate_limit_counters (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    scope_id VARCHAR(255) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_size_ms BIGINT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_oauth2_token_rate_limit_counters_scope_window
        UNIQUE (scope_id, window_start, window_size_ms)
);

CREATE INDEX IF NOT EXISTS idx_oauth2_token_rate_limit_counters_scope_window_start
    ON security.oauth2_token_rate_limit_counters(scope_id, window_start);
