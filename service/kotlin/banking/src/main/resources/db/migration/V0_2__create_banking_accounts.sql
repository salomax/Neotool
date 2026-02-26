CREATE SCHEMA IF NOT EXISTS banking;

-- ─── Base table (fields common to all account types) ────────────────────────

CREATE TABLE banking.financial_accounts (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership: account (tenant) owns the resource; user is the creator (audit)
    account_id           UUID NOT NULL REFERENCES security.accounts(id),
    created_by_user_id   UUID NOT NULL REFERENCES security.users(id),

    type    TEXT NOT NULL CHECK (type IN (
                'BANK', 'CREDIT', 'PAYMENT', 'CASH', 'INVESTMENT', 'LOAN', 'ASSET'
            )),
    subtype TEXT NOT NULL CHECK (subtype IN (
                'CONTA_CORRENTE', 'CONTA_POUPANCA', 'CONTA_SALARIO', 'CONTA_DIGITAL', 'CONTA_CONJUNTA',
                'CREDIT_CARD', 'CHARGE_CARD',
                'PREPAID_CARD', 'VOUCHER_CARD',
                'WALLET',
                'BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO',
                'PERSONAL_LOAN', 'MORTGAGE', 'AUTO_LOAN',
                'PROPERTY', 'VEHICLE', 'OTHER_ASSET'
            )),

    name                 TEXT NOT NULL,
    currency_code        CHAR(3)      NOT NULL DEFAULT 'BRL',
    include_in_net_worth BOOLEAN      NOT NULL DEFAULT TRUE,
    include_in_dashboard BOOLEAN      NOT NULL DEFAULT TRUE,
    theme_color          TEXT,
    logo_key             TEXT,

    -- Issuer / institution (common to all types)
    issuer_name          TEXT,
    issuer_country       CHAR(2),
    issuer_compe_code    TEXT,
    issuer_ispb          TEXT,

    -- Open Finance readiness (nullable in MVP, set when linked via OFB/Pluggy)
    connection_status    TEXT CHECK (connection_status IN (
                             'CONNECTED', 'SYNCING', 'DISCONNECTED',
                             'ERROR', 'CONSENT_EXPIRING', 'CONSENT_EXPIRED'
                         )),
    consent_expires_at   TIMESTAMPTZ,
    last_sync_at         TIMESTAMPTZ,
    provider_item_id     TEXT,

    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_banking_financial_accounts_account_id
    ON banking.financial_accounts(account_id);

CREATE INDEX idx_banking_financial_accounts_active
    ON banking.financial_accounts(account_id)
    WHERE deleted_at IS NULL;

-- ─── Extension: bank data (type = 'BANK') ───────────────────────────────────

CREATE TABLE banking.financial_account_bank_data (
    account_id    UUID PRIMARY KEY
                      REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    branch_code   TEXT,
    number        TEXT,
    check_digit   TEXT,
    masked_number TEXT
);

-- ─── Extension: card data (type = 'CREDIT' or 'PAYMENT') ────────────────────
-- Populated for all card subtypes (CREDIT_CARD, PREPAID_CARD, VOUCHER_CARD).
-- Credit-only fields (credit_limit, closing_day, due_day) are nullable here;
-- NOT NULL enforcement is at the service layer when type = 'CREDIT'.

CREATE TABLE banking.financial_account_card_data (
    account_id             UUID PRIMARY KEY
                               REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    masked_number          TEXT,
    bin                    TEXT,
    card_network           TEXT CHECK (card_network IN (
                               'VISA', 'MASTERCARD', 'AMERICAN_EXPRESS',
                               'DINERS_CLUB', 'HIPERCARD', 'ELO',
                               'BANDEIRA_PROPRIA', 'OUTRAS'
                           )),
    card_level             TEXT CHECK (card_level IN (
                               'STANDARD', 'GOLD', 'PLATINUM', 'INFINITE', 'BLACK'
                           )),
    -- Credit-card-only fields (null for PREPAID_CARD and VOUCHER_CARD)
    credit_limit           NUMERIC(18,2),
    available_credit_limit NUMERIC(18,2),
    min_payment_percentage NUMERIC(5,2),
    closing_day            SMALLINT CHECK (closing_day BETWEEN 1 AND 28),
    due_day                SMALLINT CHECK (due_day BETWEEN 1 AND 28)
);
