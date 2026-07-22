CREATE TABLE IF NOT EXISTS security.oauth2_consents (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES security.users(id),
    client_id VARCHAR(255) NOT NULL,
    account_id UUID NOT NULL REFERENCES security.accounts(id),
    granted_scopes TEXT NOT NULL,
    first_granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_oauth2_consents_active_grant
    ON security.oauth2_consents(user_id, client_id, account_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_oauth2_consents_user
    ON security.oauth2_consents(user_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_consents_client
    ON security.oauth2_consents(client_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_consents_account
    ON security.oauth2_consents(account_id);

ALTER TABLE security.oauth2_authorization_requests
    ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES security.accounts(id);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_requests_account
    ON security.oauth2_authorization_requests(account_id);

COMMENT ON TABLE security.oauth2_consents IS
    'Persisted OAuth consent grants per user, client, and account. Revoked rows remain auditable.';
