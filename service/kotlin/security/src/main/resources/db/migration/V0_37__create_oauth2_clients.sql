CREATE TABLE IF NOT EXISTS security.oauth2_clients (
    client_id VARCHAR(255) PRIMARY KEY,
    client_name TEXT,
    redirect_uris TEXT NOT NULL,
    scopes TEXT NOT NULL,
    resources TEXT NOT NULL,
    grant_types TEXT NOT NULL,
    response_types TEXT NOT NULL,
    token_endpoint_auth_method VARCHAR(64) NOT NULL,
    logo_uri TEXT,
    client_uri TEXT,
    client_id_issued_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_from_ip VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_oauth2_clients_status
    ON security.oauth2_clients(status);

CREATE INDEX IF NOT EXISTS idx_oauth2_clients_unused
    ON security.oauth2_clients(client_id_issued_at)
    WHERE last_used_at IS NULL AND status = 'ACTIVE';

COMMENT ON TABLE security.oauth2_clients IS
    'Dynamically registered OAuth 2.1 public clients for MCP and other authorization-code integrations.';
