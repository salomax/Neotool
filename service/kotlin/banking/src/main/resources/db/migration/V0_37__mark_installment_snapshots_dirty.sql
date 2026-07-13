-- Mark snapshots dirty for accounts with installment transactions tied to bills so
-- month summaries are recomputed with the same month-resolution logic as the list query.
UPDATE banking.monthly_balance_snapshots s
SET is_dirty = TRUE
WHERE EXISTS (
    SELECT 1
    FROM banking.transactions t
    WHERE t.financial_account_id = s.financial_account_id
      AND t.installment_number > 1
      AND t.bill_id IS NOT NULL
      AND t.deleted_at IS NULL
);
