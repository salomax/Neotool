-- Adds Faxina under EXPENSE > Moradia with a broom-like icon.
-- Uses idempotent insert to avoid duplicate child category.
INSERT INTO banking.transaction_categories (
    parent_id,
    name,
    applicable_to,
    icon,
    color,
    is_system,
    sort_order
)
SELECT
    parent.id,
    'Faxina',
    'EXPENSE',
    'cleaning_services',
    parent.color,
    true,
    70
FROM banking.transaction_categories parent
WHERE parent.name = 'Moradia'
  AND parent.is_system = true
  AND parent.account_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM banking.transaction_categories child
      WHERE child.parent_id = parent.id
        AND child.name = 'Faxina'
        AND child.is_system = true
        AND child.account_id IS NULL
  );
