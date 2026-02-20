-- Set search path to security schema
SET search_path TO security, public;

-- Accounts table (account-based resource ownership)
-- Uses account_name, account_type, account_status to avoid reserved words "name", "type", "status"
CREATE TABLE IF NOT EXISTS security.accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_name TEXT NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('PERSONAL', 'FAMILY', 'BUSINESS')),
    account_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    owner_user_id UUID REFERENCES security.users(id),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_owner ON security.accounts(owner_user_id) WHERE owner_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_accounts_status ON security.accounts(account_status) WHERE account_status = 'ACTIVE';

COMMENT ON TABLE security.accounts IS 'Accounts are the primary ownership boundary for resources; users access resources via account membership';

-- Account memberships table (user-account relationship with roles)
-- Uses account_role and membership_status to avoid reserved words "role" and "status"
CREATE TABLE IF NOT EXISTS security.account_memberships (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL REFERENCES security.accounts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    account_role TEXT NOT NULL CHECK (account_role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    membership_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (membership_status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    joined_at TIMESTAMPTZ,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    invited_by UUID REFERENCES security.users(id),
    invited_at TIMESTAMPTZ,
    invitation_token TEXT,
    invitation_expires_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    removed_by UUID REFERENCES security.users(id),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_account_user UNIQUE (account_id, user_id),
    CONSTRAINT chk_default_membership_active
        CHECK (NOT is_default OR membership_status = 'ACTIVE')
);

CREATE INDEX IF NOT EXISTS idx_memberships_user ON security.account_memberships(user_id) WHERE membership_status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_memberships_account ON security.account_memberships(account_id) WHERE membership_status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_memberships_token ON security.account_memberships(invitation_token) WHERE invitation_token IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_memberships_default_per_user
    ON security.account_memberships(user_id)
    WHERE membership_status = 'ACTIVE' AND is_default = TRUE;

COMMENT ON TABLE security.account_memberships IS 'Links users to accounts with roles; at most one ACTIVE default membership per user';
