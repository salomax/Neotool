-- Stores the original values received from import/provider at creation time.
-- Immutable after insert: only populated for non-MANUAL transactions.
-- Used to detect user edits (description, amount) and allow revert.

ALTER TABLE banking.transactions
    ADD COLUMN IF NOT EXISTS source_data JSONB;
