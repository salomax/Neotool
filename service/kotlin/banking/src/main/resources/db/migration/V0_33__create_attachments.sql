-- ============================================================================
-- Attachments for Transactions and Investment Holdings
--
-- Files are stored in the assets service (private namespace).
-- These tables hold the link between a banking record and an asset id.
-- Max 3 attachments per record enforced at the service layer.
-- ============================================================================

CREATE TABLE banking.transaction_attachments
(
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id     UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    asset_id       UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transaction_attachment UNIQUE (transaction_id, asset_id)
);

CREATE INDEX idx_transaction_attachments_transaction_id
    ON banking.transaction_attachments (transaction_id);

CREATE INDEX idx_transaction_attachments_account_id
    ON banking.transaction_attachments (account_id);

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.holding_attachments
(
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id UUID        NOT NULL,
    holding_id UUID        NOT NULL,
    asset_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_holding_attachment UNIQUE (holding_id, asset_id)
);

CREATE INDEX idx_holding_attachments_holding_id
    ON banking.holding_attachments (holding_id);

CREATE INDEX idx_holding_attachments_account_id
    ON banking.holding_attachments (account_id);
