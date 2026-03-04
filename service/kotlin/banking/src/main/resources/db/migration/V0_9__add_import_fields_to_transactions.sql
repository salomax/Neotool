-- Add import-related columns and extend source enum for file-based and OCR imports.
-- import_job_id FK is added in V0_10 after banking.transaction_imports exists.

ALTER TABLE banking.transactions
    ADD COLUMN import_fingerprint TEXT NULL,
    ADD COLUMN import_job_id      UUID NULL;

-- Extend source CHECK to include: OFX, CSV_*, NFC_E, RECEIPT_OCR
ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS transactions_source_check;

ALTER TABLE banking.transactions
    ADD CONSTRAINT chk_transactions_source
    CHECK (source IN (
        'MANUAL', 'OPEN_FINANCE', 'PLUGGY',
        'OFX', 'CSV_NUBANK', 'CSV_ITAU', 'CSV_BRADESCO', 'NFC_E', 'RECEIPT_OCR'
    ));

-- Deduplication: same fingerprint per account (Level 2 / cross-source)
CREATE INDEX idx_transactions_import_fingerprint
    ON banking.transactions(financial_account_id, import_fingerprint)
    WHERE import_fingerprint IS NOT NULL;

-- Provider + source lookup (OFB/Pluggy and file-based idempotency)
CREATE INDEX idx_transactions_provider_tx_id
    ON banking.transactions(financial_account_id, provider_transaction_id, source)
    WHERE provider_transaction_id IS NOT NULL;
