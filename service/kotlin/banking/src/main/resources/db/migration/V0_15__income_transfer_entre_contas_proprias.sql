-- Subcategory for manual investment deposits (spec: Transferência recebida > Entre contas próprias).
-- TRANSFER > Transferência enviada already has this child; INCOME > Transferência recebida had only PIX/TED/Outros.
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT p.id, 'Entre contas próprias', 'INCOME', true, 25
FROM banking.transaction_categories p
WHERE p.name = 'Transferência recebida'
  AND p.is_system = TRUE
  AND p.account_id IS NULL
  AND p.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM banking.transaction_categories c
      WHERE c.parent_id = p.id
        AND c.name = 'Entre contas próprias'
        AND c.deleted_at IS NULL
  );
