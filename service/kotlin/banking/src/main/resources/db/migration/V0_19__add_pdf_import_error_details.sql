-- Store structured failure context for PDF import jobs.
ALTER TABLE banking.pdf_import_jobs
    ADD COLUMN IF NOT EXISTS error_details JSONB;
