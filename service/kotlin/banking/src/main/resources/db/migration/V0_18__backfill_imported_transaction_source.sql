-- Backfill: fix transactions that were incorrectly stored with source = 'MANUAL' due to a bug
-- in ImportProcessor.toTransactionSource() where the else branch mapped all unrecognised
-- ImportSource values to TransactionSource.MANUAL. This affected OFX, CSV_* (Nubank, Itaú,
-- Bradesco, Santander, CSV_AUTO), NFC_E, and RECEIPT_OCR imports.
-- We fix them by joining with document_import_jobs to recover the actual import source.
UPDATE banking.transactions t
SET source = CASE j.source
    WHEN 'OFX'           THEN 'OFX'
    WHEN 'CSV_AUTO'      THEN 'CSV'
    WHEN 'CSV_NUBANK'    THEN 'CSV'
    WHEN 'CSV_ITAU'      THEN 'CSV'
    WHEN 'CSV_BRADESCO'  THEN 'CSV'
    WHEN 'CSV_SANTANDER' THEN 'CSV'
    WHEN 'NFC_E'         THEN 'NFC_E'
    WHEN 'RECEIPT_OCR'   THEN 'RECEIPT_OCR'
    WHEN 'OPEN_FINANCE'  THEN 'OPEN_FINANCE'
    WHEN 'PLUGGY'        THEN 'OPEN_FINANCE'
    ELSE t.source
END
FROM banking.document_import_jobs j
WHERE t.import_job_id = j.id
  AND t.source = 'MANUAL'
  AND j.source IS NOT NULL
  AND j.source <> 'AUTO';
