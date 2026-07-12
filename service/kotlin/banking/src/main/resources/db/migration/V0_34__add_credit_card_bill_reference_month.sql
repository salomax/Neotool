-- Business reference month for credit card bills (first day of the due month).
-- Used for bill identity, import reconciliation, and user-facing month semantics.

ALTER TABLE banking.credit_card_bills
    ADD COLUMN reference_month DATE;

UPDATE banking.credit_card_bills
   SET reference_month = date_trunc('month', due_date)::date
 WHERE reference_month IS NULL;

ALTER TABLE banking.credit_card_bills
    ALTER COLUMN reference_month SET NOT NULL;

CREATE INDEX idx_credit_card_bills_account_reference_month_active
    ON banking.credit_card_bills (financial_account_id, reference_month)
    WHERE deleted_at IS NULL;
