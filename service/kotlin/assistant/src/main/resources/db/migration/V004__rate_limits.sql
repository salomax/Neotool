-- ============================================================================
-- Assistant Service - Rate Limiting Counters
-- Version: V004__rate_limits.sql
-- Purpose:
--   - Create rate limit counters table for atomic check-and-increment
--   - Extend usage budget scope to support PROVIDER limits
-- ============================================================================

SET search_path TO assistant, public;

-- ============================================================================
-- Extend usage_budgets.scope check constraint with PROVIDER
-- ============================================================================

DO $$
DECLARE
    constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'assistant.usage_budgets'::regclass
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%scope%'
          AND pg_get_constraintdef(c.oid) ILIKE '%ACCOUNT%'
    LOOP
        EXECUTE format(
            'ALTER TABLE assistant.usage_budgets DROP CONSTRAINT %I',
            constraint_row.conname
        );
    END LOOP;
END $$;

ALTER TABLE assistant.usage_budgets
    ADD CONSTRAINT usage_budgets_scope_check
    CHECK (scope IN ('PROVIDER', 'ACCOUNT', 'SERVICE', 'FEATURE'));

-- ============================================================================
-- Table: rate_limit_counters
-- Purpose: Windowed counters used by atomic rate limiting checks
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.rate_limit_counters (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    scope TEXT NOT NULL CHECK (scope IN ('PROVIDER', 'ACCOUNT', 'SERVICE', 'FEATURE')),
    scope_id UUID NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_size_ms BIGINT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    token_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_rate_limit_counters_scope_scope_id_window
        UNIQUE (scope, scope_id, window_start, window_size_ms)
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_counters_scope_scope_id_window_start
    ON assistant.rate_limit_counters(scope, scope_id, window_start);
