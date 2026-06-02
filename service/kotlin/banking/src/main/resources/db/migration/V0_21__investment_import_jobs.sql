ALTER TABLE banking.document_import_jobs
    DROP CONSTRAINT IF EXISTS document_import_jobs_job_kind_check;

ALTER TABLE banking.document_import_jobs
    ADD CONSTRAINT document_import_jobs_job_kind_check
    CHECK (job_kind IN ('TRANSACTION_IMPORT', 'BILL_IMPORT', 'INVESTMENT_IMPORT'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_import_jobs_investment_account_hash
    ON banking.document_import_jobs(account_id, file_hash)
    WHERE job_kind = 'INVESTMENT_IMPORT';
