-- Tracks every import job for audit, retry, and idempotency (Level 3: file_hash).
-- See phase-2-5-transaction-imports spec.

CREATE TABLE banking.transaction_imports (
    id                     UUID PRIMARY KEY DEFAULT uuidv7(),

    financial_account_id   UUID NOT NULL
                              REFERENCES banking.financial_accounts(id) ON DELETE RESTRICT,
    account_id             UUID NOT NULL,
    created_by_user_id     UUID NOT NULL,

    source                 TEXT NOT NULL
                              CHECK (source IN (
                                  'OFX', 'CSV_NUBANK', 'CSV_ITAU', 'CSV_BRADESCO',
                                  'NFC_E', 'RECEIPT_OCR', 'OPEN_FINANCE', 'PLUGGY'
                              )),
    file_name              VARCHAR(255),
    file_hash              VARCHAR(64) NOT NULL,

    status                 TEXT NOT NULL
                              CHECK (status IN (
                                  'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVIEW_REQUIRED'
                              )),

    total_rows             INT,
    imported_count         INT,
    skipped_count          INT,
    duplicate_review_count INT,
    error_count            INT,
    error_details          JSONB,
    review_payload         JSONB,

    date_from              DATE,
    date_to                DATE,
    completed_at           TIMESTAMPTZ,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_transaction_imports_account_file_hash
        UNIQUE (financial_account_id, file_hash)
);

CREATE INDEX idx_transaction_imports_account_created
    ON banking.transaction_imports(account_id, created_at DESC);

CREATE INDEX idx_transaction_imports_financial_account_status
    ON banking.transaction_imports(financial_account_id, status);

-- FK from transactions.import_job_id (column added in V0_9)
ALTER TABLE banking.transactions
    ADD CONSTRAINT fk_transactions_import_job
    FOREIGN KEY (import_job_id) REFERENCES banking.transaction_imports(id);
