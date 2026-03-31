-- Pending account actions.
CREATE TABLE IF NOT EXISTS banking.account_pending_actions (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id              UUID NOT NULL,
    type                    TEXT NOT NULL,
    severity                TEXT NOT NULL,
    status                  TEXT NOT NULL DEFAULT 'PENDING',
    entity_type             TEXT,
    entity_id               UUID,
    related_transaction_id  UUID,
    payload                 JSONB NOT NULL DEFAULT '{}',
    visible_from            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at             TIMESTAMPTZ,
    dismissed_at            TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pending_actions_account_status
    ON banking.account_pending_actions(account_id, status);

CREATE INDEX IF NOT EXISTS idx_pending_actions_entity
    ON banking.account_pending_actions(entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_pending_actions_visible_from
    ON banking.account_pending_actions(visible_from)
    WHERE status = 'PENDING';

ALTER TABLE banking.investment_holdings
    ADD COLUMN IF NOT EXISTS closed_at DATE,
    ADD COLUMN IF NOT EXISTS close_reason TEXT;
