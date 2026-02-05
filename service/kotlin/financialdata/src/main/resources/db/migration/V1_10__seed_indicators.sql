SET search_path TO financial_data, public;

INSERT INTO financial_data.economic_indicator
    (country_code, label, issuer, description, frequency, unit)
VALUES
    ('BR', 'IPCA', 'IBGE', 'INDICE NACIONAL DE PRECOS AO CONSUMIDOR AMPLO', 'monthly', '%'),
    ('BR', 'IPCA_15', 'IBGE', 'PREVIA DO IPCA', 'monthly', '%'),
    ('BR', 'INPC', 'IBGE', 'INDICE NACIONAL DE PRECOS AO CONSUMIDOR', 'monthly', '%'),
    ('BR', 'IGP_M', 'IPEA/FGV', 'INDICE GERAL DE PRECOS - MERCADO (IGP-M)', 'monthly', '%'),
    ('BR', 'IGP_DI', 'IPEA/FGV', 'INDICE GERAL DE PRECOS - DISPONIBILIDADE INTERNA (IGP-DI)', 'monthly', '%'),
    ('BR', 'SELIC', 'BCB', 'TAXA SELIC - SISTEMA ESPECIAL DE LIQUIDACAO E DE CUSTODIA', 'monthly', '%'),
    ('BR', 'GDP', 'IPEA', 'PRODUTO INTERNO BRUTO - VALORES CORRENTES (R$ MILHOES)', 'quarterly', 'BRL_MILLION'),
    ('BR', 'UNEMPLOYMENT', 'IBGE', 'TAXA DE DESOCUPACAO (PNAD CONTINUA)', 'monthly', '%'),
    ('BR', 'AVERAGE_INCOME', 'IBGE', 'RENDA MEDIA DO TRABALHO (PNAD CONTINUA)', 'monthly', 'BRL'),
    ('BR', 'TRADE_BALANCE', 'MDIC', 'BALANCA COMERCIAL - SALDO (USD MILHOES)', 'monthly', 'USD_MILLION'),
    ('BR', 'CURRENT_ACCOUNT', 'BCB', 'TRANSACOES CORRENTES - SALDO (US$ MILHOES)', 'monthly', 'USD_MILLION'),
    ('BR', 'INTERNATIONAL_RESERVES', 'BCB', 'RESERVAS INTERNACIONAIS - CONCEITO CAIXA (US$ MILHOES)', 'daily', 'USD_MILLION'),
    ('BR', 'PUBLIC_DEBT_GDP', 'BCB', 'DIVIDA PUBLICA DO GOVERNO GERAL (% PIB)', 'monthly', '%'),
    ('BR', 'PRIMARY_RESULT', 'BCB', 'NFSP - RESULTADO PRIMARIO - TOTAL - SETOR PUBLICO CONSOLIDADO (R$ MILHOES)', 'monthly', 'BRL_MILLION')
ON CONFLICT (country_code, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IGP_M'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IGP_M'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IGP_M'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'ROLLING_12M', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IGP_DI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'YTD', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IGP_DI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'IGP_DI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'TARGET_RATE', '% a.a.', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'SELIC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'BRL', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'GDP'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'RATE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'UNEMPLOYMENT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'HABITUAL', 'BRL', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'AVERAGE_INCOME'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'EFFECTIVE', 'BRL'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'AVERAGE_INCOME'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'USD_MILLION', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'TRADE_BALANCE'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'USD_MILLION', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'CURRENT_ACCOUNT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'USD_MILLION', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'INTERNATIONAL_RESERVES'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'GROSS', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'PUBLIC_DEBT_GDP'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', 'BRL_MILLION', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'PRIMARY_RESULT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'NET', '%', false
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'PUBLIC_DEBT_GDP'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'YTD', 'BRL_MILLION', false
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'PRIMARY_RESULT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'ROLLING_12M', 'BRL_MILLION', false
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'PRIMARY_RESULT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator
    (country_code, label, issuer, description, frequency, unit)
VALUES
    ('BR', 'CDI', 'BCB', 'TAXA CDI - CERTIFICADO DE DEPOSITO INTERBANCARIO', 'monthly', '%')
ON CONFLICT (country_code, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'CDI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'YTD', '%', false
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'CDI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.economic_indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.economic_indicator WHERE country_code = 'BR' AND label = 'CDI'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Incremental windows
UPDATE financial_data.economic_indicator_variation iv
SET incremental_months = 6
FROM financial_data.economic_indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('IPCA', 'IPCA_15', 'INPC', 'IGP_M', 'IGP_DI', 'UNEMPLOYMENT', 'AVERAGE_INCOME', 'CURRENT_ACCOUNT', 'PUBLIC_DEBT_GDP', 'PRIMARY_RESULT', 'TRADE_BALANCE');

UPDATE financial_data.economic_indicator_variation iv
SET incremental_months = 6,
    incremental_years = NULL
FROM financial_data.economic_indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('GDP');

UPDATE financial_data.economic_indicator_variation iv
SET incremental_days = 30
FROM financial_data.economic_indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('INTERNATIONAL_RESERVES');

UPDATE financial_data.economic_indicator_variation iv
SET incremental_months = 6,
    incremental_days = NULL
FROM financial_data.economic_indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('CDI');

UPDATE financial_data.economic_indicator_variation iv
SET incremental_months = 2,
    incremental_days = NULL
FROM financial_data.economic_indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label = 'SELIC'
  AND iv.label = 'TARGET_RATE';

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