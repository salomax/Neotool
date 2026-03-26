-- Full-text search index for transaction description filtering.
CREATE INDEX idx_transactions_description_fts
    ON banking.transactions
    USING GIN (to_tsvector('simple', description))
    WHERE deleted_at IS NULL;
