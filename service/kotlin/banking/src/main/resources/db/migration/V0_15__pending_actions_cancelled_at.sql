-- Add cancelled_at to track producer-revoked pending actions.
ALTER TABLE banking.account_pending_actions
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
