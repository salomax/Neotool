SET search_path TO banking, public;

ALTER TABLE banking.transaction_rules
    ADD COLUMN type_override TEXT
        CHECK (type_override IN ('INCOME', 'EXPENSE', 'TRANSFER'));

ALTER TABLE banking.transaction_rules
    DROP CONSTRAINT chk_transaction_rules_has_target;

ALTER TABLE banking.transaction_rules
    ADD CONSTRAINT chk_transaction_rules_has_target
        CHECK (
            category_id IS NOT NULL OR
            merchant_id IS NOT NULL OR
            description_override IS NOT NULL OR
            tags_override_json IS NOT NULL OR
            type_override IS NOT NULL
        );
