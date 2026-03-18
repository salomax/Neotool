ALTER TABLE banking.pdf_import_jobs
    ADD COLUMN IF NOT EXISTS assistant_response JSONB;

ALTER TABLE banking.pdf_import_jobs
    ADD COLUMN import_type TEXT NOT NULL DEFAULT 'CREDIT_CARD_BILL';

ALTER TABLE banking.pdf_import_jobs
    ADD CONSTRAINT chk_pdf_import_jobs_import_type
    CHECK (import_type IN ('CREDIT_CARD_BILL', 'BANK_STATEMENT'));
