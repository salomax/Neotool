SET search_path TO financial_data, public;

INSERT INTO financial_data.indicator
    (country_code, label, issuer, description, frequency, unit)
VALUES
    ('BR', 'IPCA', 'IBGE', 'INDICE NACIONAL DE PRECOS AO CONSUMIDOR AMPLO', 'monthly', '%'),
    ('BR', 'IPCA_15', 'IBGE', 'PREVIA DO IPCA', 'monthly', '%'),
    ('BR', 'INPC', 'IBGE', 'INDICE NACIONAL DE PRECOS AO CONSUMIDOR', 'monthly', '%'),
    ('BR', 'IGP_M', 'FGV', 'INDICE GERAL DE PRECOS - MERCADO (IGP-M)', 'monthly', '%'),
    ('BR', 'IGP_DI', 'FGV', 'INDICE GERAL DE PRECOS - DISPONIBILIDADE INTERNA (IGP-DI)', 'monthly', '%'),
    ('BR', 'IGP_10', 'FGV', 'INDICE GERAL DE PRECOS - 10 (IGP-10)', 'monthly', 'INDEX'),
    ('BR', 'SELIC', 'BCB', 'TAXA SELIC', 'daily', '%'),
    ('BR', 'GDP', 'IBGE', 'PRODUTO INTERNO BRUTO - VALORES CORRENTES (R$ MILHOES)', 'annual', 'BRL_MILLION'),
    ('BR', 'UNEMPLOYMENT', 'IBGE', 'TAXA DE DESOCUPACAO (PNAD CONTINUA)', 'monthly', '%'),
    ('BR', 'AVERAGE_INCOME', 'IBGE', 'RENDA MEDIA DO TRABALHO (PNAD CONTINUA)', 'monthly', 'BRL'),
    ('BR', 'TRADE_BALANCE', 'MDIC', 'BALANCA COMERCIAL - SALDO (USD MILHOES)', 'monthly', 'USD_MILLION'),
    ('BR', 'CURRENT_ACCOUNT', 'BCB', 'TRANSACOES CORRENTES - SALDO (US$ MILHOES)', 'monthly', 'USD_MILLION'),
    ('BR', 'INTERNATIONAL_RESERVES', 'BCB', 'RESERVAS INTERNACIONAIS - CONCEITO CAIXA (US$ MILHOES)', 'daily', 'USD_MILLION'),
    ('BR', 'PUBLIC_DEBT_GDP', 'BCB', 'DIVIDA BRUTA DO GOVERNO GERAL (% PIB)', 'monthly', '%'),
    ('BR', 'PRIMARY_RESULT', 'BCB', 'NFSP - RESULTADO PRIMARIO - TOTAL - SETOR PUBLICO CONSOLIDADO (R$ MILHOES)', 'monthly', 'BRL_MILLION')
ON CONFLICT (country_code, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IGP_M'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IGP_M'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IGP_M'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'ROLLING_12M', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IGP_DI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'INDEX', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IGP_10'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'TARGET_RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'SELIC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'EFFECTIVE_RATE', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'SELIC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'BRL', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'GDP'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'UNEMPLOYMENT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'HABITUAL', 'BRL', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'AVERAGE_INCOME'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'EFFECTIVE', 'BRL'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'AVERAGE_INCOME'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'USD_MILLION', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'TRADE_BALANCE'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'USD_MILLION', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'CURRENT_ACCOUNT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'USD_MILLION', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INTERNATIONAL_RESERVES'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'PUBLIC_DEBT_GDP'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'BRL_MILLION', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'PRIMARY_RESULT'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator
    (country_code, label, issuer, description, frequency, unit)
VALUES
    ('BR', 'CDI', 'BCB', 'TAXA CDI (DI OVER EXTRA-GRUPO)', 'daily', '%'),
    ('BR', 'DI_OVER', 'BCB', 'TAXA DI OVER (EXTRA-GRUPO)', 'daily', '%')
ON CONFLICT (country_code, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'CDI'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'DI_OVER'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Incremental windows
UPDATE financial_data.indicator_variation iv
SET incremental_months = 6
FROM financial_data.indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('IPCA', 'IPCA_15', 'INPC', 'IGP_M', 'IGP_DI', 'IGP_10', 'UNEMPLOYMENT', 'AVERAGE_INCOME', 'CURRENT_ACCOUNT', 'PUBLIC_DEBT_GDP', 'PRIMARY_RESULT', 'TRADE_BALANCE');

UPDATE financial_data.indicator_variation iv
SET incremental_years = 2
FROM financial_data.indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('GDP');

UPDATE financial_data.indicator_variation iv
SET incremental_days = 30
FROM financial_data.indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('SELIC', 'INTERNATIONAL_RESERVES');

UPDATE financial_data.indicator_variation iv
SET incremental_days = 30
FROM financial_data.indicator i
WHERE iv.indicator_id = i.id
  AND i.country_code = 'BR'
  AND i.label IN ('CDI', 'DI_OVER');