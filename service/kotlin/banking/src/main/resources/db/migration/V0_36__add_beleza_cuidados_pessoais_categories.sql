-- Add system expense category for beauty and personal care.
SET search_path TO banking, public;

INSERT INTO banking.transaction_categories
    (name, applicable_to, icon, color, is_system, sort_order)
VALUES
    ('Beleza e Cuidados Pessoais', 'EXPENSE', 'face_retouching_natural', '#EC407A', true, 245);

-- EXPENSE > Beleza e Cuidados Pessoais
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, icon, color, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', sub.icon, '#EC407A', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Perfumaria',           'local_florist',            10),
             ('Cabeleireiro',         'content_cut',              20),
             ('Depilação',            'spa',                      30),
             ('Maquiagem',            'brush',                    40),
             ('Barbearia',            'content_cut',              50),
             ('Manicure e Pedicure',  'color_lens',              60),
             ('Estética Facial',      'face_retouching_natural', 70),
             ('Massagem',             'self_improvement',        80)) AS sub(name, icon, ord)
WHERE banking.transaction_categories.name = 'Beleza e Cuidados Pessoais'
  AND is_system = true AND account_id IS NULL;
