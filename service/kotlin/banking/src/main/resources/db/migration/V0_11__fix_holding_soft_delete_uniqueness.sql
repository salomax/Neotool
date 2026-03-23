-- Holdings are soft-deleted, so uniqueness must only apply to active rows.
-- Otherwise, recreating a holding after delete fails with uq_holdings_account_asset
-- or uq_holdings_provider even though the previous row is no longer visible.

ALTER TABLE banking.investment_holdings
    DROP CONSTRAINT IF EXISTS uq_holdings_account_asset;

ALTER TABLE banking.investment_holdings
    DROP CONSTRAINT IF EXISTS uq_holdings_provider;

CREATE UNIQUE INDEX IF NOT EXISTS uq_holdings_account_asset
    ON banking.investment_holdings(financial_account_id, asset_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_holdings_provider
    ON banking.investment_holdings(financial_account_id, provider_investment_id)
    WHERE provider_investment_id IS NOT NULL
      AND deleted_at IS NULL;
