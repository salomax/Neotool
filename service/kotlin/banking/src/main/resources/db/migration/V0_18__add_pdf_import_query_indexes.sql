-- Sprint 7 (PDF-19): query-path indexes for PDF import history and confirm dedup checks.

-- Import history query:
--   SELECT * FROM banking.pdf_import_jobs WHERE account_id = ? ORDER BY created_at DESC;
CREATE INDEX IF NOT EXISTS idx_pdf_import_jobs_account_created
    ON banking.pdf_import_jobs (account_id, created_at DESC);

-- Confirm dedup query:
--   SELECT id FROM banking.transactions
--    WHERE financial_account_id = ? AND import_fingerprint = ? AND deleted_at IS NULL;
-- Existing idx_transactions_import_fingerprint covers the key columns, but this partial
-- index matches the deleted_at filter used by confirm-time dedup.
CREATE INDEX IF NOT EXISTS idx_transactions_import_fingerprint_active
    ON banking.transactions (financial_account_id, import_fingerprint)
    WHERE import_fingerprint IS NOT NULL
      AND deleted_at IS NULL;
