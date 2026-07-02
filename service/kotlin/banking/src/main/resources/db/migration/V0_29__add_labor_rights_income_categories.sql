-- Add system income category for labor rights benefits (FGTS, unemployment insurance).
SET search_path TO banking, public;

INSERT INTO banking.transaction_categories
    (name, applicable_to, icon, color, is_system, sort_order)
VALUES
    ('Direitos Trabalhistas', 'INCOME', 'gavel', '#00695C', true, 60);

-- INCOME > Direitos Trabalhistas
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'INCOME', sub.icon, sub.color, true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('FGTS',               'savings', '#00695C', 10),
             ('Seguro Desemprego',  'shield',  '#00695C', 20)) AS sub(name, icon, color, ord)
WHERE banking.transaction_categories.name = 'Direitos Trabalhistas'
  AND is_system = true AND account_id IS NULL;
