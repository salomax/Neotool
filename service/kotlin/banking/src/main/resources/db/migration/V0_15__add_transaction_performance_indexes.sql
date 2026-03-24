-- Composite index for high-traffic queries that filter by account + date range.
-- Used by: monthly totals, snapshot recompute, month summary, and bill transaction lookups.
-- Partial index (deleted_at IS NULL) keeps the index small and skips soft-deleted rows.
CREATE INDEX IF NOT EXISTS idx_transactions_account_date
    ON banking.transactions (financial_account_id, date)
    WHERE deleted_at IS NULL;

-- Index for bill transaction aggregation (recomputeTotal / computeCurrentTotal).
CREATE INDEX IF NOT EXISTS idx_transactions_bill_id
    ON banking.transactions (bill_id)
    WHERE bill_id IS NOT NULL
      AND deleted_at IS NULL;
