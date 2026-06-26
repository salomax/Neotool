-- Mark snapshots dirty for months that contain excluded transaction types so they heal
-- after historical rule reclassification changes a transaction's type.
UPDATE banking.monthly_balance_snapshots AS snapshot
SET is_dirty = TRUE,
    updated_at = NOW()
WHERE snapshot.is_dirty = FALSE
  AND EXISTS (
    SELECT 1
    FROM banking.transactions AS transaction
    WHERE transaction.financial_account_id = snapshot.financial_account_id
      AND transaction.date >= make_date(snapshot.year, snapshot.month, 1)
      AND transaction.date < (make_date(snapshot.year, snapshot.month, 1) + INTERVAL '1 month')
      AND transaction.deleted_at IS NULL
      AND transaction.type IN ('TRANSFER', 'BALANCE_ADJUSTMENT', 'BILL_PAYMENT')
  );
