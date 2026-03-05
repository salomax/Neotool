-- Normalize banking.transactions.source values.
-- Removes provider-specific (PLUGGY) and bank-specific (CSV_*) values.
-- TransactionSource now tracks channel/format only; provider detail lives in ImportSource.
--
-- Mapping:
--   PLUGGY       → OPEN_FINANCE  (provider is an implementation detail)
--   CSV_NUBANK   → CSV           (bank already implicit in financial_account_id)
--   CSV_ITAU     → CSV
--   CSV_BRADESCO → CSV
--   PDF_BILL     → PDF           (document type is not part of the source)

UPDATE banking.transactions SET source = 'OPEN_FINANCE' WHERE source = 'PLUGGY';
UPDATE banking.transactions SET source = 'CSV'          WHERE source IN ('CSV_NUBANK', 'CSV_ITAU', 'CSV_BRADESCO');
UPDATE banking.transactions SET source = 'PDF'          WHERE source = 'PDF_BILL';

ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS transactions_source_check;

ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_source;

ALTER TABLE banking.transactions
    ADD CONSTRAINT chk_transactions_source
    CHECK (source IN (
        'MANUAL',
        'OPEN_FINANCE',
        'OFX',
        'CSV',
        'PDF',
        'NFC_E',
        'RECEIPT_OCR'
    ));
