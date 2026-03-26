-- ─── Investment Asset Catalog ─────────────────────────────────────────────────
-- Stores the identity of each investable instrument.
-- Scope: global (no account_id). Equities are identified by ticker; fixed
-- income instruments are identified by ISIN or created per-position.
--
-- Design principles:
--   • Equities/FIIs/ETFs/BDRs: one row per ticker (global, account-agnostic).
--   • Fixed income (CDB, LCI, etc.): one row per distinct product/issuer.
--     Position-specific terms (rate, maturity) live on banking.investment_holdings.
--   • Funds: one row per fund CNPJ.
--   • Tesouro Direto: one row per benchmark title (TESOURO_SELIC, TESOURO_IPCA, etc.).
--   • Market prices are NOT stored here — they are fetched from financial-data.
--   • ticker uniqueness is partial (only enforced when ticker IS NOT NULL).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.investment_assets (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Identity
    -- ticker: B3 ticker for equities/FIIs/ETFs (PETR4, MXRF11, BOVA11).
    -- Null for fixed income and funds (identified by ISIN or fund_cnpj).
    ticker        TEXT,
    -- ISIN: ISO 6166 12-char code. Null when not applicable or unknown.
    isin_code     TEXT,
    name          TEXT NOT NULL,

    -- Classification
    -- asset_type drives which fields are relevant and how pricing works.
    asset_type    TEXT NOT NULL CHECK (asset_type IN (
                      -- Renda Variável (B3)
                      'STOCK',               -- Ações (ON, PN, UNIT)
                      'STOCK_RIGHTS',        -- Bônus de subscrição, direitos, recibos
                      'BDR',                 -- Brazilian Depositary Receipt
                      'REAL_ESTATE_FUND',    -- FII
                      'AGRIBUSINESS_FUND',   -- FIAGRO
                      'ETF',                 -- ETF de renda variável
                      'ETF_FIXED_INCOME',    -- ETF de renda fixa
                      'ETF_INTERNATIONAL',   -- ETF de ativos internacionais
                      -- Renda Fixa Bancária
                      'CDB', 'RDB', 'LCI', 'LCA', 'LC', 'LF', 'LIG',
                      -- Renda Fixa Crédito
                      'DEBENTURES', 'CRI', 'CRA',
                      -- Tesouro Direto
                      'TREASURY',
                      -- Fundos de Investimento
                      'MUTUAL_FUND',
                      -- Previdência
                      'PGBL', 'VGBL',
                      -- Estruturado
                      'COE',
                      -- Outros
                      'CRYPTO', 'OPTION', 'DERIVATIVES', 'OTHER'
                  )),

    exchange      TEXT,               -- B3, NYSE, NASDAQ, etc. Null for non-exchange assets
    currency_code CHAR(3) NOT NULL DEFAULT 'BRL',

    -- ── Fixed income / Fund issuer ────────────────────────────────────────────
    -- For RF bancária/crédito and funds: the entity that issued the asset.
    issuer_cnpj   TEXT,               -- CNPJ do emissor (banco, fundo, etc.)
    issuer_name   TEXT,               -- Nome do emissor

    -- ── Fund-specific (asset_type = MUTUAL_FUND, PGBL, VGBL) ─────────────────
    -- ANBIMA classification required for correct tax treatment (come-cotas).
    fund_cnpj         TEXT,           -- CNPJ do fundo (primary identifier for funds)
    anbima_category   TEXT CHECK (anbima_category IN (
                          'RENDA_FIXA', 'ACOES', 'MULTIMERCADO', 'CAMBIAL'
                      )),
    anbima_class      TEXT,           -- classe ANBIMA (e.g. "Simples", "Soberano")
    anbima_subclass   TEXT,           -- subclasse ANBIMA

    -- ── Equity-specific ───────────────────────────────────────────────────────
    -- price_factor: B3 pricing multiplier. Most assets = 1. Some assets = 0.01
    -- (e.g. certain debentures quoted per 1000). Required for correct gross_value.
    price_factor  NUMERIC(10,6) NOT NULL DEFAULT 1,

    -- ── External references ───────────────────────────────────────────────────
    -- Populated when the asset was discovered via Open Finance or file import.
    provider_id   TEXT,               -- OFB investmentId / Pluggy id / Belvo id

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique ticker (partial: only when ticker is present — equities, FIIs, ETFs, BDRs)
CREATE UNIQUE INDEX uq_assets_ticker
    ON banking.investment_assets(ticker)
    WHERE ticker IS NOT NULL;

-- Unique ISIN (partial: only when ISIN is present)
CREATE UNIQUE INDEX uq_assets_isin
    ON banking.investment_assets(isin_code)
    WHERE isin_code IS NOT NULL;

-- Unique fund CNPJ (partial: only for funds)
CREATE UNIQUE INDEX uq_assets_fund_cnpj
    ON banking.investment_assets(fund_cnpj)
    WHERE fund_cnpj IS NOT NULL;

-- Search by type (e.g. "show all FIIs in the catalog")
CREATE INDEX idx_assets_type
    ON banking.investment_assets(asset_type);

-- ─── Investment Holdings ──────────────────────────────────────────────────────
-- A holding is the user's current position in an asset within a custodian account.
--
-- Design principles:
--   • One holding per (financial_account, asset). Quantity aggregates all lots.
--   • average_cost is maintained by the transaction processing engine (not a user input).
--     For manual entry, it is derived from the first PURCHASE transaction.
--   • Current market value is NOT stored — it is computed at query time by
--     multiplying quantity × latest price from financial-data service.
--   • gross_value / net_value / income_tax / iof are provider-cached values
--     (populated on Open Finance sync). They may be stale between syncs.
--   • Fixed income position-specific terms (rate, indexer, maturity) live here
--     because two holdings of the same asset_type can have different terms.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.investment_holdings (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership
    -- financial_account_id: the custodian account (type = INVESTMENT/BROKERAGE)
    financial_account_id UUID NOT NULL
                             REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id           UUID NOT NULL,

    -- Asset reference
    asset_id             UUID NOT NULL
                             REFERENCES banking.investment_assets(id) ON DELETE RESTRICT,

    -- ── Position ──────────────────────────────────────────────────────────────
    -- quantity: current number of units/shares/cotas held. Always >= 0.
    -- When quantity reaches 0 the position is closed (not deleted — history is preserved).
    quantity             NUMERIC(18,8) NOT NULL CHECK (quantity >= 0),

    -- average_cost: weighted average purchase price per unit (custo médio ponderado).
    -- Maintained by InvestmentTransactionService on every PURCHASE/SALE event.
    -- Precision 18,8 to handle assets priced in fractions of a cent (e.g. crypto).
    average_cost         NUMERIC(18,8) NOT NULL DEFAULT 0,

    -- ── Fixed income position-specific terms ──────────────────────────────────
    -- Null for equities, FIIs, ETFs, BDRs (price is from market data).
    -- Required for RF to compute accrued interest correctly.
    indexer              TEXT CHECK (indexer IN (
                             'CDI', 'DI', 'TR', 'IPCA', 'IGP_M', 'IGP_DI',
                             'INPC', 'BCP', 'TLC', 'SELIC', 'PRE_FIXADO', 'OUTROS'
                         )),
    -- indexer_percentage: fraction of the indexer (1.15 = 115% CDI).
    indexer_percentage   NUMERIC(8,6),

    -- pre_fixed_rate: annual rate for pre-fixed or hybrid instruments (0.12 = 12% a.a.).
    pre_fixed_rate       NUMERIC(10,8),

    issue_date           DATE,
    maturity_date        DATE,
    grace_period_date    DATE,         -- carência: earliest redemption date

    -- is_tax_exempt: true for LCI/LCA/CRI/CRA for individuals (isentos de IR).
    is_tax_exempt        BOOLEAN NOT NULL DEFAULT false,

    -- ── Provider-cached valuations ────────────────────────────────────────────
    -- Populated on Open Finance sync. Null for manual-entry holdings until synced.
    -- gross_value  = quantity × current gross unit price (before taxes).
    -- net_value    = gross_value − income_tax − iof (spendable amount today).
    -- income_tax   = IR provisionado (may be deducted on redemption).
    -- iof          = IOF provisionado (applies to redemptions within 30 days of purchase).
    gross_value          NUMERIC(18,4),
    net_value            NUMERIC(18,4),
    income_tax           NUMERIC(18,4),
    iof                  NUMERIC(18,4),
    pricing_mode         TEXT
                             CHECK (pricing_mode IN ('MANUAL', 'MARKET_DATA')),
    manual_gross_value   NUMERIC(18,4),
    manual_valued_at     DATE,

    -- last_valued_at: when gross_value/net_value were last updated from the provider.
    last_valued_at       TIMESTAMPTZ,

    -- ── Provenance ────────────────────────────────────────────────────────────
    source               TEXT NOT NULL DEFAULT 'MANUAL'
                             CHECK (source IN ('MANUAL', 'OPEN_FINANCE', 'FILE_IMPORT')),
    -- provider_investment_id: OFB investmentId / Pluggy id / Belvo id.
    -- Used for idempotent sync: if the provider sends the same position again,
    -- we update the existing holding instead of creating a duplicate.
    provider_investment_id TEXT,

    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Uniqueness is enforced with partial unique indexes (active rows only).
);

-- Primary query: all holdings for an account
CREATE INDEX idx_holdings_account
    ON banking.investment_holdings(financial_account_id)
    WHERE deleted_at IS NULL;

-- Tenant-level: all holdings across all custodian accounts for net worth
CREATE INDEX idx_holdings_tenant
    ON banking.investment_holdings(account_id)
    WHERE deleted_at IS NULL;

-- Asset lookup: find all users holding a specific asset (future advisory use)
CREATE INDEX idx_holdings_asset
    ON banking.investment_holdings(asset_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_holdings_account_asset
    ON banking.investment_holdings(financial_account_id, asset_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_holdings_provider
    ON banking.investment_holdings(financial_account_id, provider_investment_id)
    WHERE provider_investment_id IS NOT NULL
      AND deleted_at IS NULL;

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
