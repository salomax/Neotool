-- ─── Investment Transactions ──────────────────────────────────────────────────
-- Each row is an event that changes (or explains) an investment holding.
-- The source of truth for quantity and average_cost in banking.investment_holdings.
--
-- Design principles:
--   • trade_date is the legal event date (fato gerador for IR, not settlement).
--   • amount is always positive; direction is INFLOW or OUTFLOW.
--   • holding_id is the FK to the position affected. Denormalized fields
--     (financial_account_id, account_id) allow fast tenant-level queries
--     without joins — same pattern as banking.transactions.
--   • provider_transaction_id + financial_account_id is the dedup key for sync.
--   • broker_notes is a 1:1 extension table for renda variável only.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.investment_transactions (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership
    holding_id           UUID NOT NULL
                             REFERENCES banking.investment_holdings(id) ON DELETE RESTRICT,
    financial_account_id UUID NOT NULL
                             REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id           UUID NOT NULL,
    created_by_user_id   UUID NOT NULL,

    -- ── Classification ────────────────────────────────────────────────────────
    -- direction: INFLOW increases holding quantity or brings income; OUTFLOW reduces it.
    direction            TEXT NOT NULL CHECK (direction IN ('INFLOW', 'OUTFLOW')),

    -- type: the specific nature of the event (OFB/Pluggy/Belvo canonical).
    type                 TEXT NOT NULL CHECK (type IN (
                             -- Compra / Venda
                             'PURCHASE',             -- Compra / Aplicação
                             'SALE',                 -- Venda / Resgate
                             'CANCELLATION',         -- Cancelamento
                             -- Renda / Proventos
                             'DIVIDEND',             -- Dividendos
                             'INTEREST_ON_EQUITY',   -- JCP (exclusivo BR)
                             'INTEREST_PAYMENT',     -- Pagamento de juros / cupom
                             'AMORTIZATION',         -- Amortização
                             'PRIZE',                -- Prêmio (CRI/CRA)
                             -- Vencimento
                             'MATURITY',             -- Vencimento do título
                             -- Eventos corporativos (lacuna do OFB — caem em OUTROS lá)
                             'STOCK_SPLIT',          -- Desdobramento
                             'REVERSE_SPLIT',        -- Grupamento
                             'BONUS_SHARES',         -- Bonificação em ações
                             'SUBSCRIPTION',         -- Subscrição
                             -- Transferências
                             'CUSTODY_TRANSFER',     -- Transferência de custódia
                             'OWNERSHIP_TRANSFER',   -- Transferência de titularidade
                             'QUOTA_TRANSFER',       -- Transferência de cotas (fundos)
                             -- Aluguel (BTC B3)
                             'STOCK_LENDING',        -- Aluguel de ações
                             -- Impostos
                             'WITHHOLDING_TAX',      -- Come-cotas / IR antecipado
                             -- Penalidades RF crédito
                             'FINE',                 -- Multa
                             'LATE_PAYMENT_INTEREST',-- Mora
                             -- Fallback
                             'OTHER'
                         )),

    -- type_additional_info: mandatory when type = 'OTHER' (OFB transactionTypeAdditionalInfo).
    type_additional_info TEXT,

    -- ── Amounts ───────────────────────────────────────────────────────────────
    -- quantity: units transacted. Null for pure monetary events (DIVIDEND, INTEREST_PAYMENT).
    quantity             NUMERIC(18,8),

    -- unit_price: price per unit at time of transaction.
    -- For RF: unit price of the bond (may include accrued interest).
    -- For equities: execution price per share.
    unit_price           NUMERIC(18,8),

    -- gross_value: total value before taxes and fees. Always required.
    gross_value          NUMERIC(18,4) NOT NULL CHECK (gross_value >= 0),

    -- net_value: gross_value minus income_tax and iof. Null when not provided.
    net_value            NUMERIC(18,4),

    -- ── Brazilian taxes (retained at source) ──────────────────────────────────
    -- income_tax: IR retido na fonte nesta operação.
    income_tax           NUMERIC(18,4),

    -- iof: IOF retido. Applies only to RF redemptions within 30 days of purchase.
    iof                  NUMERIC(18,4),

    -- ── Fixed income context ──────────────────────────────────────────────────
    -- indexer_percentage: the rate actually applied in this transaction (may differ
    -- from the holding's contracted rate — e.g. early redemption penalties).
    indexer_percentage   NUMERIC(8,6),

    -- agreed_rate: the pre-fixed rate applied (Pluggy: agreedRate, OFB: remunerationTransactionRate).
    agreed_rate          NUMERIC(10,8),

    -- ── Equity context ────────────────────────────────────────────────────────
    -- price_factor: B3 multiplier for this specific transaction (from broker note).
    price_factor         NUMERIC(10,6),

    -- ── Dates ─────────────────────────────────────────────────────────────────
    -- trade_date: the business date of the event. FATO GERADOR for IR calculation.
    -- This is the legally relevant date — not settlement, not creation.
    trade_date           DATE NOT NULL,

    -- settlement_date: when cash and securities actually move. D+2 for equities,
    -- D+1 for fixed income. Null for events with no settlement leg (DIVIDEND cash).
    settlement_date      DATE,

    -- ── Provenance ────────────────────────────────────────────────────────────
    source               TEXT NOT NULL DEFAULT 'MANUAL'
                             CHECK (source IN ('MANUAL', 'OPEN_FINANCE', 'FILE_IMPORT')),

    -- provider_transaction_id: OFB transactionId / Pluggy id / Belvo id.
    provider_transaction_id TEXT,

    import_job_id        UUID,          -- FK to import job (no FK constraint — cross-domain)

    -- ── Soft delete + audit ───────────────────────────────────────────────────
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Dedup: one provider transaction per custodian account
    CONSTRAINT uq_investment_transactions_provider
        UNIQUE (financial_account_id, provider_transaction_id)
);

-- Primary query: transactions for a holding (position history)
CREATE INDEX idx_investment_transactions_holding
    ON banking.investment_transactions(holding_id, trade_date DESC)
    WHERE deleted_at IS NULL;

-- Tenant-level: all investment transactions for IR calculation (cross-account)
CREATE INDEX idx_investment_transactions_tenant_date
    ON banking.investment_transactions(account_id, trade_date DESC)
    WHERE deleted_at IS NULL;

-- Custodian-level: all transactions for a specific custodian account
CREATE INDEX idx_investment_transactions_account_date
    ON banking.investment_transactions(financial_account_id, trade_date DESC)
    WHERE deleted_at IS NULL;

-- IR engine: filter by type and date range for tax calculations
CREATE INDEX idx_investment_transactions_type_date
    ON banking.investment_transactions(account_id, type, trade_date)
    WHERE deleted_at IS NULL;


-- ─── Broker Notes ─────────────────────────────────────────────────────────────
-- 1:1 extension of investment_transactions for renda variável (STOCK, FII, ETF, BDR).
-- Stores the complete fee breakdown from the broker note (nota de corretagem).
-- Populated when source = OPEN_FINANCE (from OFB/Pluggy broker note data) or
-- when the user manually enters the note details.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.broker_notes (
    -- 1:1 with investment_transaction: same PK
    investment_transaction_id UUID PRIMARY KEY
                                  REFERENCES banking.investment_transactions(id) ON DELETE CASCADE,
    account_id                UUID NOT NULL,

    -- broker_note_number: número da nota de corretagem (OFB: brokerNoteNumber).
    broker_note_number        TEXT,

    -- All fee fields default to 0 — present but zero when not charged.
    gross_value                          NUMERIC(18,4) NOT NULL,
    brokerage_fee                        NUMERIC(18,4) NOT NULL DEFAULT 0,
    clearing_settlement_fee              NUMERIC(18,4) NOT NULL DEFAULT 0,
    clearing_registration_fee            NUMERIC(18,4) NOT NULL DEFAULT 0,
    stock_exchange_asset_trade_notice_fee NUMERIC(18,4) NOT NULL DEFAULT 0,
    stock_exchange_fee                   NUMERIC(18,4) NOT NULL DEFAULT 0,
    clearing_custody_fee                 NUMERIC(18,4) NOT NULL DEFAULT 0,
    income_tax                           NUMERIC(18,4) NOT NULL DEFAULT 0,
    taxes                                NUMERIC(18,4) NOT NULL DEFAULT 0,  -- ISS, emolumentos, etc.
    net_value                            NUMERIC(18,4) NOT NULL,

    created_at                           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
