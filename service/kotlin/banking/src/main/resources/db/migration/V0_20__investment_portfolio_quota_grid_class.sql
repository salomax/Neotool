ALTER TABLE banking.investment_portfolio_quota
    DROP CONSTRAINT IF EXISTS investment_portfolio_quota_dimension_type_check;

ALTER TABLE banking.investment_portfolio_quota
    ADD CONSTRAINT investment_portfolio_quota_dimension_type_check
        CHECK (dimension_type IN ('PORTFOLIO', 'ASSET_GROUP', 'GRID_CLASS'));
