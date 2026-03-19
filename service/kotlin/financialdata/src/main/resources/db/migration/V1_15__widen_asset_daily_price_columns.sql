-- B3 COTAHIST price fields are 13-char integers with 2 implicit decimal places,
-- giving a maximum representable value of 99,999,999,999.99 (11 integer digits).
-- NUMERIC(18, 8) only allows 10 integer digits (18 − 8 = 10), so any raw B3 value
-- ≥ 10,000,000,000 overflowed the column (SQLSTATE 22003).
--
-- Widening to NUMERIC(22, 8) provides 14 integer digits — safe for any B3 price
-- including historical anomalies and high-denomination instruments.
ALTER TABLE financial_data.asset_daily_price
    ALTER COLUMN open_price  TYPE NUMERIC(22, 8),
    ALTER COLUMN high_price  TYPE NUMERIC(22, 8),
    ALTER COLUMN low_price   TYPE NUMERIC(22, 8),
    ALTER COLUMN close_price TYPE NUMERIC(22, 8),
    ALTER COLUMN avg_price   TYPE NUMERIC(22, 8);
