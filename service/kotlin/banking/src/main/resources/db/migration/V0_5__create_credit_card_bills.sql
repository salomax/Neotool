-- ─── Credit card bills (faturas) ─────────────────────────────────────────────
-- One row per monthly credit card statement. Transactions link to their bill
-- via banking.transactions.bill_id.
--
-- Bills are auto-created by the service layer on the first transaction within
-- a billing cycle. closing_date and due_date are derived from the card's
-- closing_day and due_day configured on financial_account_card_data.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.credit_card_bills (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership
    financial_account_id  UUID NOT NULL
                              REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    account_id            UUID NOT NULL,

    -- Bill cycle
    closing_date          DATE NOT NULL,
    due_date              DATE NOT NULL,

    -- Amounts (computed / materialized; may be NULL until bill closes)
    total_amount          NUMERIC(18,4),
    minimum_payment       NUMERIC(18,4),

    status                TEXT NOT NULL DEFAULT 'OPEN'
                              CHECK (status IN ('OPEN', 'CLOSED', 'PAID', 'OVERDUE')),

    -- Open Finance (Phase 5)
    provider_bill_id      TEXT,

    deleted_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Each card has at most one bill per closing date
    CONSTRAINT uq_credit_card_bills_account_closing
        UNIQUE (financial_account_id, closing_date)
);

CREATE INDEX idx_credit_card_bills_account_id
    ON banking.credit_card_bills(financial_account_id);

CREATE INDEX idx_credit_card_bills_account_status
    ON banking.credit_card_bills(account_id, status)
    WHERE deleted_at IS NULL;
