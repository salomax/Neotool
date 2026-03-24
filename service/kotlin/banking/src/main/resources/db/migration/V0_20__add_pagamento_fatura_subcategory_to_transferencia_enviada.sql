-- Adds Pagamento de fatura under TRANSFER > Transferência enviada.
-- Uses idempotent insert to avoid duplicate child category.
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT parent.id, 'Pagamento de fatura', 'TRANSFER', true, 40
FROM banking.transaction_categories parent
WHERE parent.name = 'Transferência enviada'
  AND parent.is_system = true
  AND parent.account_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM banking.transaction_categories child
      WHERE child.parent_id = parent.id
        AND child.name = 'Pagamento de fatura'
        AND child.is_system = true
        AND child.account_id IS NULL
  );
