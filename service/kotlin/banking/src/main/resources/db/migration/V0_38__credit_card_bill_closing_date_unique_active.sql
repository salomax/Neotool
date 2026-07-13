ALTER TABLE banking.credit_card_bills
    DROP CONSTRAINT uq_credit_card_bills_account_closing;

CREATE UNIQUE INDEX uq_credit_card_bills_account_closing_active
    ON banking.credit_card_bills (financial_account_id, closing_date)
    WHERE deleted_at IS NULL;
