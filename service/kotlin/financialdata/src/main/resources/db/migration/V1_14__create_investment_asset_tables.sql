-- Investment asset catalog shared by all import jobs (B3, CVM, Tesouro, CoinGecko, Manual)
-- Ensure pg_trgm is available (operator class used below may live in financial_data schema)
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA financial_data;

CREATE TABLE IF NOT EXISTS financial_data.investment_asset
(
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    ticker        TEXT,
    isin_code     TEXT,
    name          TEXT        NOT NULL,
    asset_type    TEXT        NOT NULL,
    exchange      TEXT,
    currency_code TEXT        NOT NULL DEFAULT 'BRL',
    lot_size      INT         NOT NULL DEFAULT 100,
    price_factor  INT         NOT NULL DEFAULT 1,
    issuer_name   TEXT,
    fund_cnpj     TEXT,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    source        TEXT        NOT NULL,
    external_id   TEXT,
    content_hash  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_investment_asset PRIMARY KEY (id),
    CONSTRAINT uq_investment_asset_ticker    UNIQUE (ticker)
);

CREATE INDEX IF NOT EXISTS idx_investment_asset_type
    ON financial_data.investment_asset (asset_type);

CREATE INDEX IF NOT EXISTS idx_investment_asset_source
    ON financial_data.investment_asset (source);

CREATE INDEX IF NOT EXISTS idx_investment_asset_is_active
    ON financial_data.investment_asset (is_active);

CREATE INDEX IF NOT EXISTS idx_investment_asset_name_trgm
    ON financial_data.investment_asset USING gin (name financial_data.gin_trgm_ops);

-- Daily OHLCV prices — one row per (asset, date), all sources
CREATE TABLE IF NOT EXISTS financial_data.asset_daily_price
(
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    asset_id     UUID        NOT NULL,
    price_date   DATE        NOT NULL,
    open_price   NUMERIC(18, 8),
    high_price   NUMERIC(18, 8),
    low_price    NUMERIC(18, 8),
    close_price  NUMERIC(18, 8) NOT NULL,
    avg_price    NUMERIC(18, 8),
    volume       NUMERIC(22, 2),
    trades       INT,
    source       TEXT        NOT NULL,
    content_hash TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_asset_daily_price PRIMARY KEY (id),
    CONSTRAINT uq_asset_daily_price UNIQUE (asset_id, price_date),
    CONSTRAINT fk_asset_daily_price_asset
        FOREIGN KEY (asset_id) REFERENCES financial_data.investment_asset (id)
);

CREATE INDEX IF NOT EXISTS idx_asset_daily_price_asset
    ON financial_data.asset_daily_price (asset_id);

CREATE INDEX IF NOT EXISTS idx_asset_daily_price_date
    ON financial_data.asset_daily_price (price_date DESC);

-- Per-job run control: tracks last successful data date and status for each import job
CREATE TABLE IF NOT EXISTS financial_data.import_job_control
(
    job_name        TEXT        NOT NULL,
    last_data_date  DATE,
    last_run_at     TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    status          TEXT        NOT NULL DEFAULT 'IDLE',
    error_message   TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_import_job_control PRIMARY KEY (job_name)
);
