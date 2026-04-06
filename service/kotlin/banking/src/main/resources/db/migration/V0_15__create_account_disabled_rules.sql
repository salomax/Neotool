CREATE TABLE IF NOT EXISTS banking.account_disabled_rules (
    account_id UUID NOT NULL,
    rule_id UUID NOT NULL REFERENCES banking.transaction_rules(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, rule_id)
);
