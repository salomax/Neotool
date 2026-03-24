ALTER TABLE banking.transaction_rules
    ADD COLUMN IF NOT EXISTS description_override VARCHAR(500),
    ADD COLUMN IF NOT EXISTS tags_override_json TEXT;
