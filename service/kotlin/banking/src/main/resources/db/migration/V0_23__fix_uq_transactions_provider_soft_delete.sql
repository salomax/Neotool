-- The plain UNIQUE constraint includes soft-deleted rows, so re-importing a
-- previously deleted transaction causes a duplicate key violation even though
-- the application-level dedup check correctly skips deleted rows.
-- Replace with a partial unique index that only enforces uniqueness for live rows.
ALTER TABLE banking.transactions DROP CONSTRAINT uq_transactions_provider;

CREATE UNIQUE INDEX uq_transactions_provider
    ON banking.transactions(financial_account_id, provider_transaction_id)
    WHERE deleted_at IS NULL AND provider_transaction_id IS NOT NULL;
