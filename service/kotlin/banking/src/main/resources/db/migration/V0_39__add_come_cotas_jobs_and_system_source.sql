ALTER TABLE banking.investment_holdings
    ADD COLUMN come_cotas_enabled BOOLEAN NULL;

ALTER TABLE banking.investment_holdings
    DROP CONSTRAINT IF EXISTS investment_holdings_source_check;

ALTER TABLE banking.investment_holdings
    ADD CONSTRAINT investment_holdings_source_check
        CHECK (source IN ('MANUAL', 'OPEN_FINANCE', 'FILE_IMPORT', 'SYSTEM'));

ALTER TABLE banking.investment_transactions
    DROP CONSTRAINT IF EXISTS investment_transactions_source_check;

ALTER TABLE banking.investment_transactions
    ADD CONSTRAINT investment_transactions_source_check
        CHECK (source IN ('MANUAL', 'OPEN_FINANCE', 'FILE_IMPORT', 'SYSTEM'));

CREATE TABLE banking.come_cotas_checkpoint_job (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    holding_id           UUID NOT NULL REFERENCES banking.investment_holdings(id) ON DELETE CASCADE,
    financial_account_id UUID NOT NULL REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id           UUID NOT NULL,
    checkpoint_date      DATE NOT NULL,
    status               VARCHAR(20) NOT NULL
                             CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    processing_node_id   VARCHAR(255),
    last_heartbeat_at    TIMESTAMPTZ,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    error_code           VARCHAR(80),
    error_details        TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_come_cotas_checkpoint_job_holding_date UNIQUE (holding_id, checkpoint_date)
);

CREATE INDEX idx_come_cotas_checkpoint_job_pending_created_at
    ON banking.come_cotas_checkpoint_job (status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_come_cotas_checkpoint_job_account_status
    ON banking.come_cotas_checkpoint_job (account_id, status, created_at DESC);

CREATE INDEX idx_come_cotas_checkpoint_job_zombie
    ON banking.come_cotas_checkpoint_job (status, last_heartbeat_at)
    WHERE status = 'PROCESSING';
