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

    -- One position per asset per custodian account
    CONSTRAINT uq_holdings_account_asset
        UNIQUE (financial_account_id, asset_id),

    -- Idempotent sync per custodian + provider
    CONSTRAINT uq_holdings_provider
        UNIQUE (financial_account_id, provider_investment_id)
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
