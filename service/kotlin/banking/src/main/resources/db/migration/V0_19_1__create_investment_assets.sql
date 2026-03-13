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
