CREATE SCHEMA IF NOT EXISTS banking;

-- ─── Base table (fields common to all account types) ────────────────────────

CREATE TABLE banking.accounts (
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

CREATE INDEX idx_banking_accounts_account_id
    ON banking.accounts(account_id);

CREATE INDEX idx_banking_accounts_account_active
    ON banking.accounts(account_id)
    WHERE deleted_at IS NULL;

-- ─── Extension: bank data (type = 'BANK') ───────────────────────────────────

CREATE TABLE banking.account_bank_data (
    account_id    UUID PRIMARY KEY
                      REFERENCES banking.accounts(id) ON DELETE CASCADE,
    branch_code   TEXT,
    number        TEXT,
    check_digit   TEXT,
    masked_number TEXT
);

-- ─── Extension: credit data (type = 'CREDIT' or 'PAYMENT') ─────────────────

CREATE TABLE banking.account_credit_data (
    account_id             UUID PRIMARY KEY
                               REFERENCES banking.accounts(id) ON DELETE CASCADE,
    masked_number          TEXT,
    bin                    TEXT,
    credit_card_network    TEXT CHECK (credit_card_network IN (
                               'VISA', 'MASTERCARD', 'AMERICAN_EXPRESS',
                               'DINERS_CLUB', 'HIPERCARD', 'ELO',
                               'BANDEIRA_PROPRIA', 'OUTRAS'
                           )),
    credit_card_level      TEXT CHECK (credit_card_level IN (
                               'STANDARD', 'GOLD', 'PLATINUM', 'INFINITE', 'BLACK'
                           )),
    credit_limit           NUMERIC(18,2) NOT NULL,
    available_credit_limit NUMERIC(18,2),
    min_payment_percentage NUMERIC(5,2),
    closing_day            SMALLINT NOT NULL CHECK (closing_day BETWEEN 1 AND 28),
    due_day                SMALLINT NOT NULL CHECK (due_day BETWEEN 1 AND 28)
);
