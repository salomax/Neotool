ALTER TABLE banking.wishlist_item
    ADD COLUMN category_id UUID REFERENCES banking.transaction_categories(id);

UPDATE banking.wishlist_item wi
SET category_id = tc.id
FROM banking.transaction_categories tc
WHERE wi.category_id IS NULL
  AND tc.account_id IS NULL
  AND tc.is_system = true
  AND tc.parent_id IS NULL
  AND (
    (wi.category = 'PRODUCT' AND tc.name = 'Compras') OR
    (wi.category = 'SERVICE' AND tc.name = 'Serviços Digitais') OR
    (wi.category = 'EXPERIENCE' AND tc.name = 'Lazer e Entretenimento')
  );

CREATE INDEX idx_wishlist_item_category_id
    ON banking.wishlist_item(category_id)
    WHERE deleted_at IS NULL;
