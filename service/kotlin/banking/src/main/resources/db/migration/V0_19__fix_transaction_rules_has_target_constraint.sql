-- V0_13 added description_override and tags_override_json to transaction_rules, but the
-- chk_transaction_rules_has_target constraint was not updated to account for them.
-- A rule that only overrides the description or tags (no category/merchant) is valid:
-- it renames or re-tags future imported transactions with a matching description.
ALTER TABLE banking.transaction_rules
    DROP CONSTRAINT chk_transaction_rules_has_target,
    ADD CONSTRAINT chk_transaction_rules_has_target
        CHECK (
            category_id         IS NOT NULL OR
            merchant_id         IS NOT NULL OR
            description_override IS NOT NULL OR
            tags_override_json  IS NOT NULL
        );
