-- Corporate events sourced from B3's public API.
-- cashDividends  → asset_cash_dividend   (DIVIDENDO, JCP)
-- stockDividends → asset_stock_event     (SPLIT, REVERSE_SPLIT, BONUS_SHARES)
-- subscriptions  → asset_stock_event     (SUBSCRIPTION)
--
-- last_date_prior = "data COM": last day a holder is entitled to the event.
-- ex_date         = next business day after last_date_prior (not stored — computed by consumers).

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: asset_cash_dividend
-- ============================================================================
CREATE TABLE IF NOT EXISTS financial_data.asset_cash_dividend (
    id              UUID        NOT NULL DEFAULT uuidv7(),
    asset_id        UUID        REFERENCES financial_data.investment_asset(id),
    isin_code       TEXT        NOT NULL,
    dividend_type   TEXT        NOT NULL,        -- DIVIDEND | JCP
    rate            NUMERIC(18, 8) NOT NULL,     -- amount per share in BRL
    last_date_prior DATE        NOT NULL,        -- data COM
    payment_date    DATE        NOT NULL,
    approved_on     DATE        NOT NULL,
    related_to      TEXT,                        -- e.g. "Anual/2025"
    remarks         TEXT,
    source          TEXT        NOT NULL DEFAULT 'B3',
    content_hash    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_asset_cash_dividend PRIMARY KEY (id),
    CONSTRAINT uq_asset_cash_dividend
        UNIQUE (isin_code, dividend_type, last_date_prior, payment_date, approved_on),
    CONSTRAINT ck_asset_cash_dividend_type
        CHECK (dividend_type IN ('DIVIDEND', 'JCP'))
);

CREATE INDEX IF NOT EXISTS idx_asset_cash_dividend_asset_id
    ON financial_data.asset_cash_dividend (asset_id);

CREATE INDEX IF NOT EXISTS idx_asset_cash_dividend_last_date_prior
    ON financial_data.asset_cash_dividend (last_date_prior);

CREATE INDEX IF NOT EXISTS idx_asset_cash_dividend_payment_date
    ON financial_data.asset_cash_dividend (payment_date);

COMMENT ON TABLE financial_data.asset_cash_dividend IS
    'Cash dividends and JCP (Juros sobre Capital Próprio) sourced from B3 public API.';

COMMENT ON COLUMN financial_data.asset_cash_dividend.last_date_prior IS
    'Data COM: last day a shareholder must hold the asset to receive the dividend. Ex-date is the next business day.';

-- ============================================================================
-- Table: asset_stock_event
-- ============================================================================
CREATE TABLE IF NOT EXISTS financial_data.asset_stock_event (
    id                UUID        NOT NULL DEFAULT uuidv7(),
    asset_id          UUID        REFERENCES financial_data.investment_asset(id),
    isin_code         TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,      -- SPLIT | REVERSE_SPLIT | BONUS_SHARES | SUBSCRIPTION
    factor            NUMERIC(22, 8),            -- split ratio / bonus % / subscription %
    price_unit        NUMERIC(18, 8),            -- subscription price per share
    trading_start     DATE,                      -- subscription: rights trading period start
    trading_end       DATE,                      -- subscription: rights trading period end
    subscription_date DATE,                      -- subscription: deadline to exercise right
    last_date_prior   DATE        NOT NULL,      -- data COM
    approved_on       DATE        NOT NULL,
    remarks           TEXT,
    source            TEXT        NOT NULL DEFAULT 'B3',
    content_hash      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_asset_stock_event PRIMARY KEY (id),
    CONSTRAINT uq_asset_stock_event
        UNIQUE (isin_code, event_type, last_date_prior, approved_on),
    CONSTRAINT ck_asset_stock_event_type
        CHECK (event_type IN ('SPLIT', 'REVERSE_SPLIT', 'BONUS_SHARES', 'SUBSCRIPTION'))
);

CREATE INDEX IF NOT EXISTS idx_asset_stock_event_asset_id
    ON financial_data.asset_stock_event (asset_id);

CREATE INDEX IF NOT EXISTS idx_asset_stock_event_last_date_prior
    ON financial_data.asset_stock_event (last_date_prior);

COMMENT ON TABLE financial_data.asset_stock_event IS
    'Non-cash corporate events (splits, reverse splits, bonus shares, subscriptions) sourced from B3 public API.';

COMMENT ON COLUMN financial_data.asset_stock_event.factor IS
    'SPLIT: new shares per old share (e.g. 100 = 1:100). REVERSE_SPLIT: fraction remaining (e.g. 0.01 = 100:1). BONUS_SHARES: bonus ratio (e.g. 33.33 = 33.33% extra). SUBSCRIPTION: rights percentage.';

COMMENT ON COLUMN financial_data.asset_stock_event.last_date_prior IS
    'Data COM: last day a shareholder must hold the asset to be entitled to the event.';
