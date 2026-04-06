ALTER TABLE banking.investment_portfolio_recalculation_job
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(80);
