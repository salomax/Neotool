ALTER TABLE security.refresh_tokens
    ADD COLUMN IF NOT EXISTS client_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS granted_scopes TEXT,
    ADD COLUMN IF NOT EXISTS resource TEXT;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_client_family
    ON security.refresh_tokens(client_id, family_id)
    WHERE client_id IS NOT NULL;

COMMENT ON COLUMN security.refresh_tokens.client_id IS
    'OAuth client id for OAuth-issued refresh tokens; null for first-party app refresh tokens.';

COMMENT ON COLUMN security.refresh_tokens.granted_scopes IS
    'Space-delimited OAuth scopes originally granted to this refresh token family.';

COMMENT ON COLUMN security.refresh_tokens.resource IS
    'OAuth resource originally granted to this refresh token family.';
