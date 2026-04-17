CREATE INDEX IF NOT EXISTS idx_transactions_import_job_active
    ON banking.transactions(import_job_id)
    WHERE import_job_id IS NOT NULL
      AND deleted_at IS NULL;

-- Speeds up the installment branch of period queries: filters to only installment rows
-- (installment_number > 1) without scanning all transactions for a financial account.
CREATE INDEX IF NOT EXISTS idx_transactions_installments_active
    ON banking.transactions (financial_account_id, status)
    WHERE installment_number > 1
      AND deleted_at IS NULL;

-- Speeds up the installment-with-bill branch: allows range scans on due_date
-- after joining credit_card_bills to installment transactions.
CREATE INDEX IF NOT EXISTS idx_credit_card_bills_due_date_active
    ON banking.credit_card_bills (financial_account_id, due_date)
    WHERE deleted_at IS NULL;
