SET search_path TO financial_data, public;

CREATE OR REPLACE FUNCTION financial_data.fund_search_alias_text(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    WITH normalized AS (
        SELECT financial_data.normalize_fund_search_text(input) AS value
    )
    SELECT TRIM(
        concat_ws(
            ' ',
            CASE
                WHEN value LIKE '%RESPONSABILIDADE LIMITADA%'
                  OR value LIKE '%RESP LIMITADA%'
                THEN 'RL'
            END,
            CASE
                WHEN value LIKE '%FUNDO DE INVESTIMENTO FINANCEIRO EM COTAS%'
                THEN 'FIF COTAS'
                WHEN value LIKE '%FUNDO DE INVESTIMENTO EM COTAS%'
                THEN 'FIC'
                WHEN value LIKE '%FUNDO DE INVESTIMENTO FINANCEIRO%'
                THEN 'FIF'
            END,
            CASE
                WHEN value LIKE '%FUNDOS DE INVESTIMENTO EM ACOES%'
                THEN 'FIA'
                WHEN value LIKE '%FUNDO DE INVESTIMENTO FINANCEIRO EM ACOES%'
                THEN 'FIF ACOES'
            END,
            CASE
                WHEN value LIKE '%RENDA FIXA%'
                THEN 'RF'
            END,
            CASE
                WHEN value LIKE '%CREDITO PRIVADO%'
                THEN 'CP'
            END,
            CASE
                WHEN value LIKE '%LONGO PRAZO%'
                THEN 'LP'
            END,
            CASE
                WHEN value LIKE '%CLASSE DE INVESTIMENTO%'
                THEN 'CIC'
            END,
            CASE
                WHEN value LIKE '%MULTIMERCADO%'
                THEN 'MULTI'
            END,
            CASE
                WHEN value LIKE '%FUNDO DE INVESTIMENTO MULTIMERCADO%'
                  OR value LIKE '%CLASSE DE INVESTIMENTO MULTIMERCADO%'
                THEN 'FIM'
            END
        )
    )
    FROM normalized;
$$;

UPDATE financial_data.investment_asset
SET fund_search_text = financial_data.build_fund_search_text(name),
    fund_search_tokens = financial_data.build_fund_search_tokens(name)
WHERE source = 'CVM'
  AND fund_cnpj IS NOT NULL;
