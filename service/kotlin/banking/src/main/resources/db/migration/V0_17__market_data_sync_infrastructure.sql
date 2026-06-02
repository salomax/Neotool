CREATE TABLE banking.market_data_sync_control (
    job_name                VARCHAR(50) PRIMARY KEY,
    source_last_data_date   DATE,
    source_last_run_at      TIMESTAMPTZ,
    last_synced_date        DATE,
    last_sync_started_at    TIMESTAMPTZ,
    last_sync_completed_at  TIMESTAMPTZ,
    status                  VARCHAR(20) NOT NULL
                                CHECK (status IN ('IDLE', 'SYNCING', 'SUCCESS', 'FAILED')),
    last_event_id           UUID,
    last_error              TEXT,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE banking.market_indicator_point (
    indicator_label     VARCHAR(50) NOT NULL,
    variation_label     VARCHAR(50) NOT NULL,
    reference_date      DATE NOT NULL,
    publication_date    DATE,
    value               NUMERIC(18, 8) NOT NULL,
    source              VARCHAR(50) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (indicator_label, variation_label, reference_date)
);

CREATE INDEX idx_market_indicator_point_reference
    ON banking.market_indicator_point(reference_date DESC);

CREATE TABLE banking.investment_asset_corporate_event (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    asset_id            UUID
                            REFERENCES banking.investment_assets(id) ON DELETE SET NULL,
    ticker              VARCHAR(20),
    isin_code           VARCHAR(20) NOT NULL,
    event_type          VARCHAR(30) NOT NULL,
    factor              NUMERIC(22, 8),
    price_unit          NUMERIC(18, 8),
    trading_start       DATE,
    trading_end         DATE,
    subscription_date   DATE,
    last_date_prior     DATE NOT NULL,
    approved_on         DATE NOT NULL,
    remarks             TEXT,
    source              VARCHAR(50) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_investment_asset_corporate_event
        UNIQUE (isin_code, event_type, last_date_prior, approved_on)
);

CREATE INDEX idx_investment_asset_corporate_event_date
    ON banking.investment_asset_corporate_event(last_date_prior DESC);
