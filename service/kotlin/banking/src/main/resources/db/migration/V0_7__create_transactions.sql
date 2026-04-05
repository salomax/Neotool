-- Final transactions ledger shape after attribution, import, category, and merchant changes.

CREATE TABLE banking.transactions (
    id                           UUID PRIMARY KEY DEFAULT uuidv7(),

    financial_account_id         UUID NOT NULL
                                     REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id                   UUID NOT NULL,
    created_by_user_id           UUID NOT NULL,

    credit_debit_type            TEXT NOT NULL
                                     CHECK (credit_debit_type IN ('CREDIT', 'DEBIT')),
    type                         TEXT NOT NULL
                                     CHECK (type IN (
                                         'PIX', 'TED', 'DOC', 'BOLETO', 'TRANSFER',
                                         'SALARY', 'DEPOSIT', 'CASHBACK',
                                         'INVESTMENT_RETURN', 'OTHER_INCOME',
                                         'CARD', 'WITHDRAWAL', 'FEE', 'INTEREST',
                                         'LOAN_PAYMENT', 'TAX', 'OTHER_EXPENSE',
                                         'BALANCE_ADJUSTMENT'
                                     )),
    status                       TEXT NOT NULL DEFAULT 'POSTED'
                                     CHECK (status IN ('POSTED', 'PENDING', 'FUTURE')),
    source                       TEXT NOT NULL DEFAULT 'MANUAL',
    source_data                  JSONB,
    applied_rule_ids             JSONB,

    amount                       NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    currency_code                CHAR(3) NOT NULL DEFAULT 'BRL',
    amount_brl                   NUMERIC(18,4),

    date                         DATE NOT NULL,
    transaction_datetime         TIMESTAMPTZ,

    description                  TEXT NOT NULL,
    description_raw              TEXT,
    notes                        TEXT,

    category_id                  UUID REFERENCES banking.transaction_categories(id),
    category_source              TEXT,

    counterparty_name            TEXT,
    counterparty_document        TEXT,
    counterparty_bank_code       CHAR(3),
    counterparty_account_number  TEXT,
    payment_reason               TEXT,

    transfer_peer_transaction_id UUID,

    investment_transaction_id    UUID,

    bill_id                      UUID REFERENCES banking.credit_card_bills(id),
    installment_number           SMALLINT CHECK (installment_number >= 1),
    total_installments           SMALLINT CHECK (total_installments >= 1),
    payee_mcc                    INT,
    card_identifier              TEXT,
    bill_post_date               DATE,

    provider_transaction_id      TEXT,
    provider_account_id          TEXT,
    import_fingerprint           TEXT,
    import_job_id                UUID REFERENCES banking.document_import_jobs(id),
    merchant_id                  UUID REFERENCES banking.merchants(id) ON DELETE SET NULL,

    belongs_to_user_id           UUID,
    belongs_to_name              TEXT,

    deleted_at                   TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_transactions_provider
        UNIQUE (financial_account_id, provider_transaction_id),

    CONSTRAINT chk_transactions_source
        CHECK (source IN (
            'MANUAL',
            'OPEN_FINANCE',
            'OFX',
            'CSV',
            'PDF',
            'NFC_E',
            'RECEIPT_OCR'
        )),

    CONSTRAINT chk_transactions_belongs_to_exactly_one
        CHECK (
            (belongs_to_user_id IS NOT NULL AND belongs_to_name IS NULL) OR
            (belongs_to_user_id IS NULL AND belongs_to_name IS NOT NULL)
        ),

    CONSTRAINT chk_transactions_belongs_to_name_not_blank
        CHECK (belongs_to_name IS NULL OR length(btrim(belongs_to_name)) > 0),

    CONSTRAINT transactions_category_source_check
        CHECK (category_source IS NULL OR category_source IN ('MANUAL', 'RULE', 'IMPORTED', 'AI'))
);

CREATE INDEX idx_transactions_account_date
    ON banking.transactions(financial_account_id, date DESC, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_tenant_date
    ON banking.transactions(account_id, date DESC, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_balance
    ON banking.transactions(financial_account_id, credit_debit_type, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_bill_id
    ON banking.transactions(bill_id)
    WHERE bill_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_transactions_transfer_peer
    ON banking.transactions(transfer_peer_transaction_id)
    WHERE transfer_peer_transaction_id IS NOT NULL;

CREATE INDEX idx_transactions_investment_transaction
    ON banking.transactions(investment_transaction_id)
    WHERE investment_transaction_id IS NOT NULL;

CREATE INDEX idx_transactions_provider
    ON banking.transactions(financial_account_id, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE INDEX idx_transactions_category
    ON banking.transactions(category_id)
    WHERE category_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_transactions_belongs_to_user
    ON banking.transactions(account_id, belongs_to_user_id)
    WHERE deleted_at IS NULL AND belongs_to_user_id IS NOT NULL;

CREATE INDEX idx_transactions_belongs_to_name
    ON banking.transactions(account_id, belongs_to_name)
    WHERE deleted_at IS NULL AND belongs_to_name IS NOT NULL;

CREATE INDEX idx_transactions_import_fingerprint
    ON banking.transactions(financial_account_id, import_fingerprint)
    WHERE import_fingerprint IS NOT NULL;

CREATE INDEX idx_transactions_provider_tx_id
    ON banking.transactions(financial_account_id, provider_transaction_id, source)
    WHERE provider_transaction_id IS NOT NULL;

CREATE INDEX idx_transactions_import_fingerprint_active
    ON banking.transactions(financial_account_id, import_fingerprint)
    WHERE import_fingerprint IS NOT NULL
      AND deleted_at IS NULL;

CREATE INDEX idx_transactions_merchant_id
    ON banking.transactions(merchant_id)
    WHERE merchant_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_transactions_description_fts
    ON banking.transactions
    USING GIN (to_tsvector('simple', description))
    WHERE deleted_at IS NULL;

-- Tags for manual categorization and filtering of transactions.
-- A transaction can have many tags, and tags are account-scoped for listing/filtering.

CREATE TABLE banking.transaction_tags (
    transaction_id UUID NOT NULL
        REFERENCES banking.transactions(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    tag TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transaction_tags PRIMARY KEY (transaction_id, tag),
    CONSTRAINT chk_transaction_tags_tag_not_blank CHECK (length(btrim(tag)) > 0)
);

CREATE INDEX idx_transaction_tags_account_tag
    ON banking.transaction_tags(account_id, tag);

CREATE INDEX idx_transaction_tags_transaction
    ON banking.transaction_tags(transaction_id);

-- ─── Monthly Balance Snapshots ────────────────────────────────────────────────
-- Read-cache for the monthly summary header (entradas, saídas, saldo total).
-- Source of truth is always banking.transactions — this table is a derived view
-- materialised per (financial_account, year, month) and updated synchronously
-- on every transaction write.
--
-- Design rules:
--   • opening_balance = prior month's closing_balance, or 0 at account genesis.
--   • closing_balance = opening_balance + SUM(CREDIT) - SUM(DEBIT) for the month.
--   • total_income    = SUM(CREDIT) WHERE type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT').
--   • total_expenses  = SUM(DEBIT)  WHERE type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT').
--   • FUTURE-status transactions are excluded from all balance fields.
--   • is_dirty = true signals a failed mid-chain recompute; read path heals on demand.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.monthly_balance_snapshots (
    financial_account_id UUID        NOT NULL
                             REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    account_id           UUID        NOT NULL,
    year                 INT         NOT NULL,
    month                INT         NOT NULL,

    opening_balance      NUMERIC(18,4) NOT NULL,
    closing_balance      NUMERIC(18,4) NOT NULL,
    total_income         NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_expenses       NUMERIC(18,4) NOT NULL DEFAULT 0,

    -- Safety net: set when a synchronous recompute fails partway through.
    -- The read path (monthSummary query) recomputes dirty rows on demand.
    is_dirty             BOOLEAN     NOT NULL DEFAULT false,
    computed_at          TIMESTAMPTZ,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_monthly_balance_snapshots
        PRIMARY KEY (financial_account_id, year, month),

    CONSTRAINT chk_month_range
        CHECK (month BETWEEN 1 AND 12)
);

-- Fast lookup for the sweeper job (heals dirty rows)
CREATE INDEX idx_monthly_balance_snapshots_dirty
    ON banking.monthly_balance_snapshots (financial_account_id, year, month)
    WHERE is_dirty = true;
