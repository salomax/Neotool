ALTER TABLE banking.financial_accounts
    ADD COLUMN IF NOT EXISTS include_cash_in_allocation BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN banking.financial_accounts.include_cash_in_allocation IS
    'When true, idle cash on this investment/benefit custodian account counts toward allocation breakdown in the investments UI.';
