-- Expand transaction_imports source check to support CSV_AUTO and CSV_SANTANDER.
ALTER TABLE banking.transaction_imports
    DROP CONSTRAINT IF EXISTS transaction_imports_source_check;

ALTER TABLE banking.transaction_imports
    DROP CONSTRAINT IF EXISTS chk_transaction_imports_source;

ALTER TABLE banking.transaction_imports
    ADD CONSTRAINT chk_transaction_imports_source
    CHECK (source IN (
        'OFX',
        'CSV_AUTO',
        'CSV_NUBANK',
        'CSV_ITAU',
        'CSV_BRADESCO',
        'CSV_SANTANDER',
        'NFC_E',
        'RECEIPT_OCR',
        'OPEN_FINANCE',
        'PLUGGY'
    ));
