SET search_path TO financial_data, public;

ALTER TABLE financial_data.investment_asset
    ADD COLUMN IF NOT EXISTS manager TEXT,
    ADD COLUMN IF NOT EXISTS administrador TEXT,
    ADD COLUMN IF NOT EXISTS tributacao_longo_prazo TEXT,
    ADD COLUMN IF NOT EXISTS publico_alvo TEXT,
    ADD COLUMN IF NOT EXISTS patrimonio_liquido TEXT,
    ADD COLUMN IF NOT EXISTS data_patrimonio_liquido TEXT,
    ADD COLUMN IF NOT EXISTS cnpj_auditor TEXT,
    ADD COLUMN IF NOT EXISTS auditor TEXT,
    ADD COLUMN IF NOT EXISTS cnpj_custodiante TEXT,
    ADD COLUMN IF NOT EXISTS custodiante TEXT;
