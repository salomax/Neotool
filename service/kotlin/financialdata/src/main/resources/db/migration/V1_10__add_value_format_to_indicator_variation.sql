SET search_path TO financial_data, public;

-- Add value_format column to economic_indicator_variation table
-- Possible values:
--   PERCENTAGE_0_100: Source provides percentage as 0-100 (e.g., 50 means 50%), needs /100 transformation
--   PERCENTAGE_0_1: Already normalized (e.g., 0.5 means 50%), no transformation needed
--   ABSOLUTE: Non-percentage value, no transformation needed
ALTER TABLE financial_data.economic_indicator_variation
ADD COLUMN IF NOT EXISTS value_format TEXT NOT NULL DEFAULT 'ABSOLUTE';

-- Update percentage indicators that store values as 0-100 scale
-- These are the indicators where the source provides values like 6.8 to mean 6.8%
UPDATE financial_data.economic_indicator_variation v
SET value_format = 'PERCENTAGE_0_100'
FROM financial_data.economic_indicator i
WHERE v.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN (
    'IPCA', 'IPCA_15', 'INPC', 'IGP_M', 'IGP_DI',
    'SELIC', 'UNEMPLOYMENT', 'PUBLIC_DEBT_GDP',
    'CDI', 'DI_OVER'
  );
