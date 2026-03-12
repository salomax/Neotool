-- Disambiguate CTI extension PK/FK columns:
-- account_id (tenant) exists in banking.financial_accounts, while extension tables
-- store the parent financial account id in their shared PK/FK.
ALTER TABLE banking.financial_account_bank_data
    RENAME COLUMN account_id TO financial_account_id;

ALTER TABLE banking.financial_account_card_data
    RENAME COLUMN account_id TO financial_account_id;
