SET search_path TO financial_data, public;

ALTER TABLE financial_data.investment_asset
    ADD COLUMN IF NOT EXISTS fund_search_text TEXT,
    ADD COLUMN IF NOT EXISTS fund_search_tokens TEXT[];

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_investment_asset_fund_cnpj'
          AND conrelid = 'financial_data.investment_asset'::regclass
    ) THEN
        ALTER TABLE financial_data.investment_asset
            ADD CONSTRAINT uq_investment_asset_fund_cnpj UNIQUE (fund_cnpj);
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION financial_data.normalize_fund_search_text(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN input IS NULL THEN ''
        ELSE TRIM(
            regexp_replace(
                regexp_replace(
                    financial_data.unaccent(UPPER(input)),
                    '[^A-Z0-9]+',
                    ' ',
                    'g'
                ),
                '\s+',
                ' ',
                'g'
            )
        )
    END;
$$;

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
                WHEN value LIKE '%FUNDO DE INVESTIMENTO FINANCEIRO%'
                THEN 'FIF'
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

CREATE OR REPLACE FUNCTION financial_data.build_fund_search_text(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    WITH normalized AS (
        SELECT financial_data.normalize_fund_search_text(input) AS value
    ),
    aliases AS (
        SELECT financial_data.fund_search_alias_text(input) AS value
    )
    SELECT TRIM(concat_ws(' ', normalized.value, aliases.value))
    FROM normalized
    CROSS JOIN aliases;
$$;

CREATE OR REPLACE FUNCTION financial_data.build_fund_search_tokens(input TEXT)
RETURNS TEXT[]
LANGUAGE SQL
IMMUTABLE
AS $$
    WITH search_text AS (
        SELECT financial_data.build_fund_search_text(input) AS value
    ),
    split_tokens AS (
        SELECT regexp_split_to_table(value, '\s+') AS token
        FROM search_text
    )
    SELECT ARRAY(
        SELECT DISTINCT token
        FROM split_tokens
        WHERE token <> ''
          AND token NOT IN ('A', 'AS', 'DA', 'DAS', 'DE', 'DO', 'DOS', 'E', 'EM')
        ORDER BY token
    );
$$;

UPDATE financial_data.investment_asset
SET fund_search_text = financial_data.build_fund_search_text(name),
    fund_search_tokens = financial_data.build_fund_search_tokens(name)
WHERE source = 'CVM'
  AND fund_cnpj IS NOT NULL
  AND (
      fund_search_text IS NULL
      OR fund_search_tokens IS NULL
  );

CREATE INDEX IF NOT EXISTS idx_investment_asset_fund_search_text_trgm
    ON financial_data.investment_asset USING gin (fund_search_text financial_data.gin_trgm_ops)
    WHERE source = 'CVM' AND fund_cnpj IS NOT NULL;
