-- Add merchant_id to link a rule to a merchant (nullable — a rule may set only category, only merchant, or both)
ALTER TABLE banking.category_rules
    ADD COLUMN merchant_id UUID REFERENCES banking.merchants(id) ON DELETE SET NULL;

-- Make category_id nullable so a rule can set just a merchant with no category
ALTER TABLE banking.category_rules
    ALTER COLUMN category_id DROP NOT NULL;

-- Ensure every rule targets at least one of: category or merchant
ALTER TABLE banking.category_rules
    ADD CONSTRAINT chk_transaction_rules_has_target
        CHECK (category_id IS NOT NULL OR merchant_id IS NOT NULL);

-- Rename table to reflect the broader purpose (category + merchant assignment)
ALTER TABLE banking.category_rules RENAME TO transaction_rules;

-- Link existing system category rules to their matching system merchants
-- where the rule pattern exactly matches the normalized merchant name
UPDATE banking.transaction_rules tr
SET merchant_id = m.id
FROM banking.merchants m
WHERE tr.is_system = true
  AND tr.account_id IS NULL
  AND tr.pattern = m.name
  AND m.is_system = true
  AND tr.merchant_id IS NULL;
