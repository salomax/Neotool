CREATE TABLE IF NOT EXISTS security.oauth2_authorization_codes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    authorization_request_id UUID NOT NULL REFERENCES security.oauth2_authorization_requests(id) ON DELETE CASCADE,
    issued_access_token_jti VARCHAR(64),
    issued_refresh_token_family_id UUID,
    consumed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_codes_request
    ON security.oauth2_authorization_codes(authorization_request_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_codes_expires
    ON security.oauth2_authorization_codes(expires_at);

COMMENT ON TABLE security.oauth2_authorization_codes IS
    'Hashed, single-use OAuth 2.1 authorization codes with replay revocation metadata.';
