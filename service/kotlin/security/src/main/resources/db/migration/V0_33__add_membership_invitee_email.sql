SET search_path TO security, public;

-- Invite-before-signup: PENDING memberships may exist without a user yet.
ALTER TABLE security.account_memberships
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE security.account_memberships
    ADD COLUMN IF NOT EXISTS invitee_email TEXT;

UPDATE security.account_memberships m
SET invitee_email = lower(u.email)
FROM security.users u
WHERE m.user_id = u.id
  AND m.invitee_email IS NULL;

ALTER TABLE security.account_memberships
    DROP CONSTRAINT IF EXISTS chk_membership_user_or_invitee_email;

ALTER TABLE security.account_memberships
    ADD CONSTRAINT chk_membership_user_or_invitee_email
    CHECK (
        (membership_status = 'PENDING' AND (user_id IS NOT NULL OR invitee_email IS NOT NULL))
        OR (membership_status <> 'PENDING' AND user_id IS NOT NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_memberships_pending_invitee_email
    ON security.account_memberships (account_id, lower(invitee_email))
    WHERE membership_status = 'PENDING' AND invitee_email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_memberships_invitee_email
    ON security.account_memberships (lower(invitee_email))
    WHERE invitee_email IS NOT NULL;
