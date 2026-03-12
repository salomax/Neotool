-- Idempotency marker for assistant request publishing in transaction imports.
ALTER TABLE banking.transaction_imports
    ADD COLUMN IF NOT EXISTS assistant_request_published_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_transaction_imports_assistant_request_published_at
    ON banking.transaction_imports(assistant_request_published_at);
