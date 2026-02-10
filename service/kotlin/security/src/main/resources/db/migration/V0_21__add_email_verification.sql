-- Email verification for signup flow (OWASP-aligned: 8h expiry, rate limit, hashed code/token)
-- See docs/03-features/security/authentication/signup/email-verification-design.md

SET search_path TO security, public;

-- New table: email_verifications
CREATE TABLE IF NOT EXISTS security.email_verifications (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    attempts INT DEFAULT 0,
    max_attempts INT DEFAULT 5,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resend_count INT DEFAULT 0,
    created_by_ip VARCHAR(45),
    verified_from_ip VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_email_verifications_user_id ON security.email_verifications(user_id);
CREATE INDEX IF NOT EXISTS idx_email_verifications_expires_at
ON security.email_verifications(expires_at)
WHERE verified_at IS NULL AND invalidated_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_email_verifications_active
ON security.email_verifications(user_id)
WHERE invalidated_at IS NULL;

-- Add email verification columns to users
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_email_verified
ON security.users(email_verified, created_at)
WHERE email_verified = FALSE;
