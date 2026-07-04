-- Allow "Pagamento de fatura" to be selected for BILL_PAYMENT transactions in the UI.
UPDATE banking.transaction_categories c
SET applicable_to = 'ANY'
FROM banking.transaction_categories p
WHERE c.parent_id = p.id
  AND p.name = 'Transferência enviada'
  AND c.name = 'Pagamento de fatura'
  AND c.is_system = true
  AND c.account_id IS NULL
  AND p.is_system = true
  AND p.account_id IS NULL
  AND c.deleted_at IS NULL
  AND p.deleted_at IS NULL;
