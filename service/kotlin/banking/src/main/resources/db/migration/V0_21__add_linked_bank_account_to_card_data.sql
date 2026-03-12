-- Adds optional FK from a card account to a bank account within the same tenant.
-- Used to auto-create the debit leg of a bill-payment transfer.
ALTER TABLE banking.financial_account_card_data
    ADD COLUMN linked_bank_account_id UUID NULL
        REFERENCES banking.financial_accounts (id) ON DELETE SET NULL;
