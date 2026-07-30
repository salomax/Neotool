CREATE TABLE banking.investment_tax_recalculation_job (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    holding_id           UUID NOT NULL REFERENCES banking.investment_holdings(id) ON DELETE CASCADE,
    financial_account_id UUID NOT NULL REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id           UUID NOT NULL,
    status               VARCHAR(20) NOT NULL
                             CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    recalculate_from     DATE NOT NULL,
    triggered_by         VARCHAR(100) NOT NULL,
    days_processed       INTEGER,
    error_code           VARCHAR(80),
    error_details        TEXT,
    processing_node_id   VARCHAR(255),
    last_heartbeat_at    TIMESTAMPTZ,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investment_tax_recalculation_job_pending_created_at
    ON banking.investment_tax_recalculation_job (status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_investment_tax_recalculation_job_holding_status_created_at
    ON banking.investment_tax_recalculation_job (holding_id, status, created_at DESC);

CREATE INDEX idx_investment_tax_recalculation_job_zombie
    ON banking.investment_tax_recalculation_job (status, last_heartbeat_at)
    WHERE status = 'PROCESSING';
