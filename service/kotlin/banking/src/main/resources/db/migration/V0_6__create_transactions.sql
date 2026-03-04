-- ─── Transactions ─────────────────────────────────────────────────────────────
-- The core ledger table. Manual-first, OFB-ready.
--
-- Design principles:
--   • amount is always positive; direction is carried by credit_debit_type.
--   • Account balance is ALWAYS derived (SUM queries); never stored as a field.
--   • All OFB sync fields (provider_*) are nullable — zero cost until Phase 5.
--   • Transfers are two rows linked by transfer_peer_transaction_id (no FK
--     constraint to avoid circular dependency on insert; integrity via service).
--   • Parcelamento: N rows per purchase, one per installment month.
--   • BALANCE_ADJUSTMENT type is neutral (not income/expense/transfer) and is
--     used for opening balance and account reconciliation.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.transactions (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership
    financial_account_id UUID NOT NULL
                             REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id           UUID NOT NULL,
    created_by_user_id   UUID NOT NULL,

    -- ── Classification ────────────────────────────────────────────────────────
    credit_debit_type    TEXT NOT NULL
                             CHECK (credit_debit_type IN ('CREDIT', 'DEBIT')),

    type                 TEXT NOT NULL
                             CHECK (type IN (
                                 -- Payment types (OFB-aligned)
                                 'PIX', 'TED', 'DOC', 'BOLETO', 'TRANSFER',
                                 -- Income types
                                 'SALARY', 'DEPOSIT', 'CASHBACK',
                                 'INVESTMENT_RETURN', 'OTHER_INCOME',
                                 -- Expense types
                                 'CARD', 'WITHDRAWAL', 'FEE', 'INTEREST',
                                 'LOAN_PAYMENT', 'TAX', 'OTHER_EXPENSE',
                                 -- Neutral
                                 'BALANCE_ADJUSTMENT'
                             )),

    status               TEXT NOT NULL DEFAULT 'POSTED'
                             CHECK (status IN ('POSTED', 'PENDING', 'FUTURE')),

    source               TEXT NOT NULL DEFAULT 'MANUAL'
                             CHECK (source IN ('MANUAL', 'OPEN_FINANCE', 'PLUGGY')),

    -- ── Amount ────────────────────────────────────────────────────────────────
    -- amount is always positive; direction is carried by credit_debit_type.
    amount               NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    currency_code        CHAR(3)       NOT NULL DEFAULT 'BRL',
    -- BRL equivalent for FX transactions; maps to OFB brazilianAmount
    amount_brl           NUMERIC(18,4),

    -- ── Dates ─────────────────────────────────────────────────────────────────
    -- date: effective posting date shown to the user (used for grouping/sorting)
    -- transaction_datetime: full UTC timestamp from institution (OFB transactionDateTime)
    date                 DATE        NOT NULL,
    transaction_datetime TIMESTAMPTZ,

    -- ── Description ───────────────────────────────────────────────────────────
    description          TEXT NOT NULL,
    -- description_raw: original institution text, immutable after OFB sync
    description_raw      TEXT,
    notes                TEXT,

    -- ── Category ──────────────────────────────────────────────────────────────
    category_id          UUID REFERENCES banking.transaction_categories(id),

    -- ── Counterparty (bank account transactions) ──────────────────────────────
    -- Maps to OFB partieCnpjCpf, partieCompeCode, partieNumber; Pluggy paymentData
    counterparty_name        TEXT,
    counterparty_document    TEXT,           -- CPF (11 digits) or CNPJ (14 digits)
    counterparty_bank_code   CHAR(3),        -- COMPE code (OFB: partieCompeCode)
    counterparty_account_number TEXT,        -- OFB: partieNumber
    payment_reason           TEXT,           -- PIX reason / Pluggy reason

    -- ── Transfer between own accounts ─────────────────────────────────────────
    -- UUID of the peer transaction (the other leg). No FK — both rows are
    -- inserted in the same DB transaction; service guarantees consistency.
    transfer_peer_transaction_id UUID,

    -- ── Credit card ───────────────────────────────────────────────────────────
    bill_id              UUID REFERENCES banking.credit_card_bills(id),
    installment_number   SMALLINT CHECK (installment_number >= 1),
    total_installments   SMALLINT CHECK (total_installments >= 1),
    payee_mcc            INT,                -- Merchant Category Code (OFB: payeeMCC)
    card_identifier      TEXT,              -- last 4 digits (OFB: identificationNumber)
    -- date transaction was posted to the credit card statement (OFB: billPostDate)
    bill_post_date       DATE,

    -- ── Open Finance sync (Phase 5) ───────────────────────────────────────────
    -- Used for deduplication on Pluggy/OFB sync.
    -- UNIQUE per account+provider ensures re-sync is idempotent.
    provider_transaction_id TEXT,           -- OFB transactionId / Pluggy id
    provider_account_id     TEXT,           -- OFB account ID

    -- ── Soft delete ───────────────────────────────────────────────────────────
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Deduplication: one provider transaction per financial account
    CONSTRAINT uq_transactions_provider
        UNIQUE (financial_account_id, provider_transaction_id)
        -- Partial unique: only enforced when provider_transaction_id is present
        -- Note: PostgreSQL UNIQUE treats NULLs as distinct, so this is safe.
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Primary query: transactions per account, newest first
CREATE INDEX idx_transactions_account_date
    ON banking.transactions(financial_account_id, date DESC, created_at DESC)
    WHERE deleted_at IS NULL;

-- Tenant-level query: all transactions for an account
CREATE INDEX idx_transactions_tenant_date
    ON banking.transactions(account_id, date DESC, created_at DESC)
    WHERE deleted_at IS NULL;

-- Balance computation: credit/debit sums per account
CREATE INDEX idx_transactions_balance
    ON banking.transactions(financial_account_id, credit_debit_type, status)
    WHERE deleted_at IS NULL;

-- Credit card: transactions per bill (fatura)
CREATE INDEX idx_transactions_bill_id
    ON banking.transactions(bill_id)
    WHERE bill_id IS NOT NULL AND deleted_at IS NULL;

-- Transfer peer lookup
CREATE INDEX idx_transactions_transfer_peer
    ON banking.transactions(transfer_peer_transaction_id)
    WHERE transfer_peer_transaction_id IS NOT NULL;

-- OFB/Pluggy sync deduplication
CREATE INDEX idx_transactions_provider
    ON banking.transactions(financial_account_id, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

-- Category filter
CREATE INDEX idx_transactions_category
    ON banking.transactions(category_id)
    WHERE category_id IS NOT NULL AND deleted_at IS NULL;
