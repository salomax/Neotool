-- transactions.import_job_id now stores ids from both banking.transaction_imports
-- and banking.pdf_import_jobs. A single FK cannot enforce both targets.
-- Keep the column as audit/reference only and rely on source-specific logic.
ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS fk_transactions_import_job;
