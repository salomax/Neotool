-- Replace holding-specific FK with generic entity reference.
-- Add visible_from (scheduling) and notified_at (comms integration).
--
-- Also creates banking.account_pending_actions when it does not exist yet (e.g. V0_13
-- was skipped or migrations were applied out of order), so this script is safe to run alone.

CREATE TABLE IF NOT EXISTS banking.account_pending_actions (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id              UUID NOT NULL,
    type                    TEXT NOT NULL,
    severity                TEXT NOT NULL,
    status                  TEXT NOT NULL DEFAULT 'PENDING',
    related_transaction_id  UUID,
    payload                 JSONB NOT NULL DEFAULT '{}',
    dismissed_at            TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    entity_type             TEXT,
    entity_id               UUID,
    visible_from            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE banking.investment_holdings
    ADD COLUMN IF NOT EXISTS closed_at DATE,
    ADD COLUMN IF NOT EXISTS close_reason TEXT;

-- Legacy path: table was created by V0_13 with related_holding_id.
DO $legacy$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'banking'
          AND table_name = 'account_pending_actions'
          AND column_name = 'related_holding_id'
    ) THEN
        ALTER TABLE banking.account_pending_actions
            ADD COLUMN IF NOT EXISTS entity_type  TEXT,
            ADD COLUMN IF NOT EXISTS entity_id    UUID,
            ADD COLUMN IF NOT EXISTS visible_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            ADD COLUMN IF NOT EXISTS notified_at  TIMESTAMPTZ;
        UPDATE banking.account_pending_actions
           SET entity_type = 'HOLDING',
               entity_id   = related_holding_id
         WHERE related_holding_id IS NOT NULL;
        ALTER TABLE banking.account_pending_actions
            DROP COLUMN IF EXISTS related_holding_id;
    END IF;
END $legacy$;

ALTER TABLE banking.account_pending_actions
    ADD COLUMN IF NOT EXISTS entity_type  TEXT,
    ADD COLUMN IF NOT EXISTS entity_id    UUID,
    ADD COLUMN IF NOT EXISTS visible_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS notified_at  TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_pending_actions_account_status
    ON banking.account_pending_actions(account_id, status);

CREATE INDEX IF NOT EXISTS idx_pending_actions_entity
    ON banking.account_pending_actions(entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_pending_actions_visible_from
    ON banking.account_pending_actions(visible_from)
    WHERE status = 'PENDING';
