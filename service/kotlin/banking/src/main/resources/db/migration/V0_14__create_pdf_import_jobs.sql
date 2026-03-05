-- Create PDF import jobs table for async PDF bill import pipeline.
-- Also extends transactions.source check with PDF_BILL.

CREATE TABLE banking.pdf_import_jobs (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    financial_account_id  UUID REFERENCES banking.financial_accounts(id),
    account_id            UUID NOT NULL,
    created_by_user_id    UUID NOT NULL,
    status                TEXT NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN (
                                  'PENDING',
                                  'EXTRACTING',
                                  'EXTRACTED',
                                  'LLM_PROCESSING',
                                  'AWAITING_REVIEW',
                                  'IMPORTING',
                                  'DONE',
                                  'FAILED'
                              )),
    asset_id              UUID NOT NULL,
    file_name             TEXT,
    file_hash             TEXT NOT NULL,
    extracted_text        TEXT,
    extraction_metadata   JSONB,
    review_payload        JSONB,
    error_message         TEXT,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pdf_import_jobs_account_hash
        UNIQUE (account_id, file_hash)
);

CREATE INDEX idx_pdf_import_jobs_account_status
    ON banking.pdf_import_jobs (account_id, status);

-- Extend source CHECK to include PDF_BILL.
ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS transactions_source_check;

ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_source;

ALTER TABLE banking.transactions
    ADD CONSTRAINT chk_transactions_source
    CHECK (source IN (
        'MANUAL', 'OPEN_FINANCE', 'PLUGGY',
        'OFX', 'CSV_NUBANK', 'CSV_ITAU', 'CSV_BRADESCO', 'NFC_E', 'RECEIPT_OCR',
        'PDF_BILL'
    ));
