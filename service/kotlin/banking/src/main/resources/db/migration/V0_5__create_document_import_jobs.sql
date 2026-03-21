CREATE TABLE banking.document_import_jobs (
    id                             UUID PRIMARY KEY DEFAULT uuidv7(),
    job_kind                       TEXT NOT NULL
                                       CHECK (job_kind IN ('TRANSACTION_IMPORT', 'BILL_IMPORT')),
    document_type                  TEXT,
    file_type                      TEXT GENERATED ALWAYS AS (
                                       CASE
                                           WHEN lower(coalesce(file_name, '')) LIKE '%.pdf' THEN 'PDF'
                                           WHEN lower(coalesce(file_name, '')) LIKE '%.xlsx' THEN 'XLSX'
                                           WHEN lower(coalesce(file_name, '')) LIKE '%.xls' THEN 'XLS'
                                           WHEN lower(coalesce(file_name, '')) LIKE '%.csv' THEN 'CSV'
                                           WHEN lower(coalesce(file_name, '')) LIKE '%.ofx' THEN 'OFX'
                                           ELSE NULL
                                       END
                                   ) STORED,
    financial_account_id           UUID REFERENCES banking.financial_accounts(id),
    account_id                     UUID NOT NULL,
    created_by_user_id             UUID NOT NULL,
    source                         TEXT,
    status                         TEXT NOT NULL
                                       CHECK (status IN (
                                           'PENDING',
                                           'PROCESSING',
                                           'COMPLETED',
                                           'FAILED',
                                           'REVIEW_REQUIRED',
                                           'REVIEW_CANCELLED',
                                           'EXTRACTING',
                                           'EXTRACTED',
                                           'LLM_PROCESSING',
                                           'AWAITING_REVIEW',
                                           'IMPORTING',
                                           'DONE'
                                       )),
    asset_id                       UUID,
    file_name                      TEXT,
    file_hash                      TEXT NOT NULL,
    extracted_text                 TEXT,
    extraction_metadata            JSONB,
    assistant_response             JSONB,
    total_rows                     INT,
    imported_count                 INT,
    skipped_count                  INT,
    duplicate_review_count         INT,
    error_count                    INT,
    error_message                  TEXT,
    error_details                  JSONB,
    review_payload                 JSONB,
    assistant_request_published_at TIMESTAMPTZ,
    date_from                      DATE,
    date_to                        DATE,
    completed_at                   TIMESTAMPTZ,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_document_import_jobs_transaction_account_hash
    ON banking.document_import_jobs(financial_account_id, file_hash)
    WHERE job_kind = 'TRANSACTION_IMPORT';

CREATE UNIQUE INDEX uq_document_import_jobs_bill_account_hash
    ON banking.document_import_jobs(account_id, file_hash)
    WHERE job_kind = 'BILL_IMPORT';

CREATE INDEX idx_document_import_jobs_account_created
    ON banking.document_import_jobs(account_id, created_at DESC);

CREATE INDEX idx_document_import_jobs_account_status
    ON banking.document_import_jobs(account_id, status);

CREATE INDEX idx_document_import_jobs_financial_account_status
    ON banking.document_import_jobs(financial_account_id, status);
