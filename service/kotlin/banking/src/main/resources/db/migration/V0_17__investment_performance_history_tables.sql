-- Historical pricing and portfolio performance infrastructure.
-- Supports TWR calculation, benchmark comparison and recalculation workflows.

CREATE TABLE banking.investment_asset_daily_price (
    asset_id        UUID NOT NULL
                        REFERENCES banking.investment_assets(id) ON DELETE RESTRICT,
    price_date      DATE NOT NULL,
    close_price     NUMERIC(18, 8) NOT NULL,
    currency_code   CHAR(3) NOT NULL DEFAULT 'BRL',
    source          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (asset_id, price_date)
);

CREATE INDEX idx_asset_daily_price_date
    ON banking.investment_asset_daily_price(price_date DESC);

CREATE TABLE banking.investment_portfolio_quota (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    financial_account_id    UUID NOT NULL
                                REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    dimension_type          VARCHAR(20) NOT NULL
                                CHECK (dimension_type IN ('PORTFOLIO', 'ASSET_GROUP')),
    -- Empty string is the normalized storage form for PORTFOLIO rows.
    dimension_key           VARCHAR(50) NOT NULL DEFAULT '',
    quota_date              DATE NOT NULL,
    quota_value             NUMERIC(18, 8) NOT NULL,
    total_quotas            NUMERIC(18, 8) NOT NULL,
    total_portfolio_value   NUMERIC(18, 2) NOT NULL,
    has_estimated_values    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_investment_portfolio_quota_dimension
        UNIQUE (financial_account_id, dimension_type, dimension_key, quota_date)
);

CREATE INDEX idx_portfolio_quota_account_date
    ON banking.investment_portfolio_quota(financial_account_id, quota_date DESC);

CREATE INDEX idx_portfolio_quota_dimension
    ON banking.investment_portfolio_quota(
        financial_account_id,
        dimension_type,
        dimension_key,
        quota_date DESC
    );

CREATE TABLE banking.investment_portfolio_flow_event (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    financial_account_id    UUID NOT NULL
                                REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id              UUID NOT NULL,
    event_date              DATE NOT NULL,
    event_type              VARCHAR(30) NOT NULL
                                CHECK (
                                    event_type IN (
                                        'DEPOSIT',
                                        'WITHDRAWAL',
                                        'DIVIDEND',
                                        'INCOME_TAX',
                                        'CORPORATE_ACTION'
                                    )
                                ),
    gross_amount            NUMERIC(18, 2) NOT NULL,
    portfolio_value_before  NUMERIC(18, 2) NOT NULL,
    quota_value_at_event    NUMERIC(18, 8) NOT NULL,
    quotas_transacted       NUMERIC(18, 8) NOT NULL,
    related_holding_id      UUID
                                REFERENCES banking.investment_holdings(id) ON DELETE SET NULL,
    source_reference_id     UUID,
    source_reference_type   VARCHAR(50),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portfolio_flow_account_date
    ON banking.investment_portfolio_flow_event(financial_account_id, event_date DESC);

CREATE TABLE banking.investment_portfolio_recalculation_job (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    financial_account_id    UUID
                                REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id              UUID NOT NULL,
    status                  VARCHAR(20) NOT NULL
                                CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    recalculate_from        DATE NOT NULL,
    triggered_by            VARCHAR(100) NOT NULL,
    days_processed          INT,
    error_details           TEXT,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recalculation_job_account_status
    ON banking.investment_portfolio_recalculation_job(account_id, status, created_at DESC);

CREATE TABLE banking.market_benchmark_daily (
    benchmark_code  VARCHAR(10) NOT NULL
                        CHECK (benchmark_code IN ('CDI', 'IBOV', 'IPCA', 'IFIX')),
    price_date      DATE NOT NULL,
    daily_factor    NUMERIC(18, 10) NOT NULL,
    cumulative_from NUMERIC(18, 8),
    source          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (benchmark_code, price_date)
);
