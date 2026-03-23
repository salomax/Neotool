ALTER TABLE banking.investment_holdings
    ADD COLUMN IF NOT EXISTS pricing_mode TEXT
        CHECK (pricing_mode IN ('MANUAL', 'MARKET_DATA')),
    ADD COLUMN IF NOT EXISTS manual_gross_value NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS manual_valued_at DATE;

UPDATE banking.investment_holdings h
SET pricing_mode = CASE
    WHEN ia.asset_type IN ('MUTUAL_FUND', 'PGBL', 'VGBL') AND h.source = 'MANUAL' THEN 'MANUAL'
    ELSE NULL
END
FROM banking.investment_assets ia
WHERE h.asset_id = ia.id
  AND h.pricing_mode IS NULL;
