-- Seed canonical bank report accounts from existing raw accounts.
-- Useful for new environments after loading bacen_bank_report_account.

WITH source_accounts AS (
    SELECT COALESCE(nome_coluna, conta) AS raw_name
    FROM financial_data.bacen_bank_report_account
    WHERE COALESCE(nome_coluna, conta) IS NOT NULL
),
canonical_seed AS (
    SELECT DISTINCT
        financial_data.normalize_report_account_name(raw_name) AS normalized_name,
        financial_data.clean_report_account_display_name(raw_name) AS display_name
    FROM source_accounts
    WHERE financial_data.normalize_report_account_name(raw_name) IS NOT NULL
)
INSERT INTO financial_data.bacen_bank_report_account_canonical (normalized_name, display_name)
SELECT normalized_name, display_name
FROM canonical_seed
ON CONFLICT (normalized_name) DO NOTHING;
