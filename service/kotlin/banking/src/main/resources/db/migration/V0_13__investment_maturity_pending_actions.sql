-- Pending account actions (maturity confirmation, future event types)
CREATE TABLE IF NOT EXISTS banking.account_pending_actions (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id              UUID NOT NULL,
    type                    TEXT NOT NULL,
    severity                TEXT NOT NULL,
    status                  TEXT NOT NULL DEFAULT 'PENDING',
    related_holding_id      UUID REFERENCES banking.investment_holdings(id),
    related_transaction_id  UUID,
    payload                 JSONB NOT NULL DEFAULT '{}',
    dismissed_at            TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pending_actions_account_status
    ON banking.account_pending_actions(account_id, status);

DO $idx$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'banking'
          AND table_name = 'account_pending_actions'
          AND column_name = 'related_holding_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'banking'
          AND indexname = 'idx_pending_actions_holding'
    ) THEN
        EXECUTE
            'CREATE INDEX idx_pending_actions_holding ON banking.account_pending_actions(related_holding_id)';
    END IF;
END $idx$;

ALTER TABLE banking.investment_holdings
    ADD COLUMN IF NOT EXISTS closed_at DATE,
    ADD COLUMN IF NOT EXISTS close_reason TEXT;
