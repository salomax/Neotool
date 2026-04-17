ALTER TABLE banking.financial_account_card_data
    ADD COLUMN is_multi_card BOOLEAN NOT NULL DEFAULT FALSE;
