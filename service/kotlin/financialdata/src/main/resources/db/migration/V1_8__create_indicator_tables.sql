SET search_path TO financial_data, public;

CREATE TABLE IF NOT EXISTS financial_data.indicator (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    country_code CHAR(2) NOT NULL,
    label TEXT NOT NULL,
    issuer TEXT NOT NULL,
    description TEXT,
    frequency TEXT NOT NULL,
    unit TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_indicator_country_label UNIQUE (country_code, label)
);

CREATE TABLE IF NOT EXISTS financial_data.indicator_variation (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    indicator_id UUID NOT NULL REFERENCES financial_data.indicator(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    unit TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    incremental_months INT,
    incremental_years INT,
    incremental_days INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_indicator_variation UNIQUE (indicator_id, label)
);

CREATE TABLE IF NOT EXISTS financial_data.indicator_value (
    indicator_variation_id UUID NOT NULL REFERENCES financial_data.indicator_variation(id) ON DELETE CASCADE,
    reference_date DATE NOT NULL,
    publication_date DATE,
    value NUMERIC(18,6) NOT NULL,
    source_meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_indicator_value PRIMARY KEY (indicator_variation_id, reference_date)
);

CREATE INDEX IF NOT EXISTS idx_indicator_value_variation_date
    ON financial_data.indicator_value (indicator_variation_id, reference_date DESC);

CREATE TABLE IF NOT EXISTS financial_data.indicator_load_control (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    indicator_variation_id UUID NOT NULL REFERENCES financial_data.indicator_variation(id) ON DELETE CASCADE,
    run_type TEXT NOT NULL,
    status TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_reference_date DATE,
    last_publication_date DATE,
    cursor JSONB,
    error_message TEXT,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_indicator_load_control_variation
    ON financial_data.indicator_load_control (indicator_variation_id);

CREATE INDEX IF NOT EXISTS idx_indicator_load_control_status
    ON financial_data.indicator_load_control (status);
