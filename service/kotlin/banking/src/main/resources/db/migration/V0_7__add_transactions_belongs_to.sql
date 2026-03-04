-- Add transaction attribution ("belongs to") decoupled from created_by_user_id (audit).
-- Exactly one must be set:
--   - belongs_to_user_id: references an account member user id (enforced at app layer)
--   - belongs_to_name: free-form name/tag

ALTER TABLE banking.transactions
    ADD COLUMN belongs_to_user_id UUID,
    ADD COLUMN belongs_to_name TEXT;

-- Backfill existing rows with creator as default attribution.
UPDATE banking.transactions
   SET belongs_to_user_id = created_by_user_id
 WHERE belongs_to_user_id IS NULL
   AND belongs_to_name IS NULL;

ALTER TABLE banking.transactions
    ADD CONSTRAINT chk_transactions_belongs_to_exactly_one
    CHECK (
        (belongs_to_user_id IS NOT NULL AND belongs_to_name IS NULL) OR
        (belongs_to_user_id IS NULL AND belongs_to_name IS NOT NULL)
    );

ALTER TABLE banking.transactions
    ADD CONSTRAINT chk_transactions_belongs_to_name_not_blank
    CHECK (belongs_to_name IS NULL OR length(btrim(belongs_to_name)) > 0);

CREATE INDEX idx_transactions_belongs_to_user
    ON banking.transactions(account_id, belongs_to_user_id)
    WHERE deleted_at IS NULL AND belongs_to_user_id IS NOT NULL;

CREATE INDEX idx_transactions_belongs_to_name
    ON banking.transactions(account_id, belongs_to_name)
    WHERE deleted_at IS NULL AND belongs_to_name IS NOT NULL;
