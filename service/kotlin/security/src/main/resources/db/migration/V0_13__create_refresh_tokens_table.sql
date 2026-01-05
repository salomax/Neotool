-- Set search path to security schema
SET search_path TO security, public;

-- Create refresh_tokens table
-- This table stores refresh tokens with rotation support, token families, and reuse detection
CREATE TABLE IF NOT EXISTS security.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES security.refresh_tokens(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON security.refresh_tokens(family_id);-- Optimized partial index for user token queries (finds active tokens by user)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked ON security.refresh_tokens(user_id, revoked_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE security.refresh_tokens IS 'Stores refresh tokens with rotation support, token families, and reuse detection';
COMMENT ON COLUMN security.refresh_tokens.token_hash IS 'SHA-256 hash of the refresh token (never store plaintext)';
COMMENT ON COLUMN security.refresh_tokens.family_id IS 'Groups tokens in a rotation chain - all tokens from same refresh share family_id';
COMMENT ON COLUMN security.refresh_tokens.replaced_by IS 'ID of the token that replaced this one (null if not yet used)';
COMMENT ON COLUMN security.refresh_tokens.revoked_at IS 'Timestamp when token was revoked (null if still active)';

