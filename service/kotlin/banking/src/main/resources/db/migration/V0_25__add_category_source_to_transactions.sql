-- Add category_source to distinguish user-assigned vs rule/import-assigned categories.

SET search_path TO banking, public;

ALTER TABLE banking.transactions
  ADD COLUMN IF NOT EXISTS category_source TEXT;

ALTER TABLE banking.transactions
  DROP CONSTRAINT IF EXISTS transactions_category_source_check;

ALTER TABLE banking.transactions
  ADD CONSTRAINT transactions_category_source_check
  CHECK (category_source IS NULL OR category_source IN ('MANUAL', 'RULE', 'IMPORTED', 'AI'));

-- Backfill: existing transactions with a category are assumed MANUAL
UPDATE banking.transactions
SET category_source = 'MANUAL'
WHERE category_id IS NOT NULL AND deleted_at IS NULL AND (category_source IS NULL OR category_source = '');
