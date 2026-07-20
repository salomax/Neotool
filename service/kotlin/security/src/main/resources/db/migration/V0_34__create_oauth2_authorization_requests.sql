CREATE TABLE IF NOT EXISTS security.oauth2_authorization_requests (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    client_id VARCHAR(255) NOT NULL,
    redirect_uri TEXT NOT NULL,
    requested_scopes TEXT NOT NULL,
    granted_scopes TEXT,
    code_challenge VARCHAR(128) NOT NULL,
    resource TEXT NOT NULL,
    state TEXT,
    user_id UUID REFERENCES security.users(id),
    csrf_token_hash VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_requests_client
    ON security.oauth2_authorization_requests(client_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_requests_user
    ON security.oauth2_authorization_requests(user_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_requests_expires
    ON security.oauth2_authorization_requests(expires_at);

COMMENT ON TABLE security.oauth2_authorization_requests IS
    'Server-side OAuth 2.1 authorization request records. Browser carries only the opaque id.';
