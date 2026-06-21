-- Add system expense categories for gift and celebration spending.
SET search_path TO banking, public;

INSERT INTO banking.transaction_categories
    (name, applicable_to, icon, color, is_system, sort_order)
VALUES
    ('Presentes',              'EXPENSE', 'card_giftcard', '#AB47BC', true, 235),
    ('Celebrações & Festas',   'EXPENSE', 'celebration',   '#FF5722', true, 240);
