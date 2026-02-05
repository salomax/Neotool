SET search_path TO financial_data, public;

INSERT INTO financial_data.economic_indicator
    (country_code, label, issuer, description, frequency, unit)
VALUES
    ('BR', 'TR', 'BCB', 'TAXA REFERENCIAL', 'monthly', '%')
ON CONFLICT (country_code, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'TR'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'TR'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'TR'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Incremental windows
UPDATE financial_data.economic_indicator_variation iv
SET incremental_months = 6,
    incremental_days = NULL
FROM financial_data.economic_indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label = 'TR';

-- Update percentage indicators that store values as 0-100 scale
UPDATE financial_data.economic_indicator_variation v
SET value_format = 'PERCENTAGE_0_100'
FROM financial_data.economic_indicator i
WHERE v.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label = 'TR';
