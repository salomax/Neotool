-- Add new system expense subcategories requested in issue #380.
SET search_path TO banking, public;

-- EXPENSE > Alimentação
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', sub.icon, parent.color, true, sub.ord
FROM banking.transaction_categories parent,
     (VALUES ('Padaria', 'bakery_dining', 50)) AS sub(name, icon, ord)
WHERE parent.name = 'Alimentação'
  AND parent.is_system = true
  AND parent.account_id IS NULL;

-- EXPENSE > Moradia
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', sub.icon, parent.color, true, sub.ord
FROM banking.transaction_categories parent,
     (VALUES ('Lavanderia', 'local_laundry_service', 80)) AS sub(name, icon, ord)
WHERE parent.name = 'Moradia'
  AND parent.is_system = true
  AND parent.account_id IS NULL;

-- EXPENSE > Transporte
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', sub.icon, parent.color, true, sub.ord
FROM banking.transaction_categories parent,
     (VALUES ('Lava rápido', 'local_car_wash', 90)) AS sub(name, icon, ord)
WHERE parent.name = 'Transporte'
  AND parent.is_system = true
  AND parent.account_id IS NULL;

-- EXPENSE > Lazer e Entretenimento
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', sub.icon, parent.color, true, sub.ord
FROM banking.transaction_categories parent,
     (VALUES ('Hotel / Airbnb e similares', 'hotel', 45)) AS sub(name, icon, ord)
WHERE parent.name = 'Lazer e Entretenimento'
  AND parent.is_system = true
  AND parent.account_id IS NULL;

-- EXPENSE > Tarifas Bancárias
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', sub.icon, parent.color, true, sub.ord
FROM banking.transaction_categories parent,
     (VALUES ('Fatura Cartão de Crédito', 'credit_card', 40)) AS sub(name, icon, ord)
WHERE parent.name = 'Tarifas Bancárias'
  AND parent.is_system = true
  AND parent.account_id IS NULL;
