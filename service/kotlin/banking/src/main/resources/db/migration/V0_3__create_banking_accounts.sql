-- Final financial account schema after issuer/card/import refactors.
-- Seed data is not required for these tables.

CREATE TABLE banking.financial_accounts (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),

    account_id           UUID NOT NULL,
    created_by_user_id   UUID NOT NULL,

    type                 TEXT NOT NULL CHECK (type IN (
                               'BANK', 'CREDIT', 'PAYMENT', 'CASH', 'INVESTMENT', 'LOAN', 'ASSET', 'BENEFIT'
                           )),
    subtype              TEXT NOT NULL CHECK (subtype IN (
                               'CONTA_CORRENTE', 'CONTA_POUPANCA', 'CONTA_SALARIO', 'CONTA_DIGITAL', 'CONTA_CONJUNTA',
                               'CREDIT_CARD', 'CHARGE_CARD',
                               'PREPAID_CARD', 'VOUCHER_CARD',
                               'WALLET',
                               'BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO',
                               'PERSONAL_LOAN', 'MORTGAGE', 'AUTO_LOAN',
                               'PROPERTY', 'VEHICLE', 'OTHER_ASSET',
                               'FGTS', 'INSS'
                           )),

    name                 TEXT NOT NULL,
    currency_code        CHAR(3) NOT NULL DEFAULT 'BRL',
    include_in_net_worth         BOOLEAN NOT NULL DEFAULT TRUE,
    include_in_dashboard         BOOLEAN NOT NULL DEFAULT TRUE,
    include_cash_in_allocation   BOOLEAN NOT NULL DEFAULT TRUE,
    theme_color          TEXT,
    logo_key             TEXT,

    issuer_id            UUID REFERENCES banking.issuers(id),
    issuer_name          TEXT,
    issuer_country       CHAR(2),
    issuer_compe_code    TEXT,
    issuer_ispb          TEXT,

    connection_status    TEXT CHECK (connection_status IN (
                               'CONNECTED', 'SYNCING', 'DISCONNECTED',
                               'ERROR', 'CONSENT_EXPIRING', 'CONSENT_EXPIRED'
                           )),
    consent_expires_at   TIMESTAMPTZ,
    last_sync_at         TIMESTAMPTZ,
    provider_item_id     TEXT,
    card_last_four_digits CHAR(4),

    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_banking_financial_accounts_account_id
    ON banking.financial_accounts(account_id);

CREATE INDEX idx_banking_financial_accounts_active
    ON banking.financial_accounts(account_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_banking_financial_accounts_last4_active
    ON banking.financial_accounts(account_id, card_last_four_digits)
    WHERE deleted_at IS NULL
      AND card_last_four_digits IS NOT NULL;

COMMENT ON COLUMN banking.financial_accounts.include_cash_in_allocation IS
    'When true, idle cash on this investment/benefit custodian account counts toward allocation breakdown in the investments UI.';

CREATE TABLE banking.financial_account_bank_data (
    financial_account_id UUID PRIMARY KEY
        REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    branch_code          TEXT,
    number               TEXT,
    check_digit          TEXT,
    masked_number        TEXT
);

CREATE TABLE banking.financial_account_card_data (
    financial_account_id    UUID PRIMARY KEY
        REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    masked_number           TEXT,
    bin                     TEXT,
    card_network            TEXT CHECK (card_network IN (
                                  'VISA', 'MASTERCARD', 'AMERICAN_EXPRESS',
                                  'DINERS_CLUB', 'HIPERCARD', 'ELO',
                                  'BANDEIRA_PROPRIA', 'OUTRAS'
                              )),
    card_level              TEXT CHECK (card_level IN (
                                  'STANDARD', 'GOLD', 'PLATINUM', 'INFINITE', 'BLACK'
                              )),
    credit_limit            NUMERIC(18,2),
    available_credit_limit  NUMERIC(18,2),
    min_payment_percentage  NUMERIC(5,2),
    closing_day             SMALLINT CHECK (closing_day BETWEEN 1 AND 31),
    due_day                 SMALLINT CHECK (due_day BETWEEN 1 AND 31),
    linked_bank_account_id  UUID NULL
        REFERENCES banking.financial_accounts(id) ON DELETE SET NULL
);

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
