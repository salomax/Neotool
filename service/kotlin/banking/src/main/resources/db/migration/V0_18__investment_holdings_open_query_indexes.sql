-- Hot path indexes for read-heavy holdings queries.
-- The investmentHoldings GraphQL query always serves open positions only.

CREATE INDEX IF NOT EXISTS idx_holdings_open_account
    ON banking.investment_holdings(financial_account_id, asset_id)
    WHERE deleted_at IS NULL
      AND quantity > 0;

CREATE INDEX IF NOT EXISTS idx_holdings_open_tenant
    ON banking.investment_holdings(account_id, asset_id)
    WHERE deleted_at IS NULL
      AND quantity > 0;
