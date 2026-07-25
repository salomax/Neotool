ALTER TABLE banking.transactions
    ADD COLUMN origin_channel VARCHAR(10) NOT NULL DEFAULT 'UI',
    ADD COLUMN origin_client_id VARCHAR(255),
    ADD COLUMN origin_tool_call_id UUID;

ALTER TABLE banking.transactions
    ADD CONSTRAINT ck_transactions_origin_channel
        CHECK (origin_channel IN ('UI', 'MCP'));

ALTER TABLE banking.transaction_rules
    ADD COLUMN origin_channel VARCHAR(10) NOT NULL DEFAULT 'UI',
    ADD COLUMN origin_client_id VARCHAR(255),
    ADD COLUMN origin_tool_call_id UUID;

ALTER TABLE banking.transaction_rules
    ADD CONSTRAINT ck_transaction_rules_origin_channel
        CHECK (origin_channel IN ('UI', 'MCP'));
