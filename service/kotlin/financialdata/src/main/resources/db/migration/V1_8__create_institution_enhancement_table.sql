-- Migration: Create financial_institution table
-- Version: V1_8
-- Description: Create financial_data.financial_institution table for storing enhanced institution data
--              from CNPJ data provider, including domain, logo storage key, and receita data
-- 
-- This migration creates the financial_institution table that stores enhanced data for financial institutions.
-- Enhancement is per parent CNPJ: if multiple institutions share the same parent (cnpj_instituicao_lider),
-- they share the same enhancement. If an institution has no parent, enhancement is for that institution itself.
-- 
-- Key features:
-- - Primary key: parent_cnpj (root CNPJ - either cnpj_instituicao_lider or constructed from bacen_cod_inst)
-- - Domain extraction from CNPJ data provider emails
-- - Website URL (if available from CNPJ data provider or website resolution)
-- - Legal name and trade name extracted from CNPJ data provider
-- - Social network links discovered from website scraping (JSONB)
-- - Logo storage key (TODO: asset management service integration)
-- - Receita data (JSONB) storing full CNPJ data provider response
-- - Audit trail: created_at, updated_at timestamps
-- - No additional indexes (only primary key) - indexes can be added later if query patterns emerge

SET search_path TO financial_data, public;

-- Create financial_institution table
CREATE TABLE IF NOT EXISTS financial_data.financial_institution (
    -- Primary key: Root CNPJ (either cnpj_instituicao_lider from bacen_institution table,
    -- or constructed CNPJ from bacen_cod_inst for institutions with no parent)
    parent_cnpj TEXT NOT NULL PRIMARY KEY,
    
    -- Legal name extracted from CNPJ data provider (company.name field)
    legal_name TEXT,
    
    -- Trade name extracted from CNPJ data provider (alias field)
    trade_name TEXT,

    -- Domain extracted from CNPJ data provider emails
    email_domain TEXT,
    
    -- Website URL (if available from CNPJ data provider or website resolution)
    website TEXT,
    
    -- Logo storage key from asset management service (TODO: service integration)
    logo_storage_key TEXT,
    
    -- Social network links discovered from website scraping
    -- JSON array/object with keys: linkedin, instagram, facebook, twitter, x
    social_network JSONB,
    
    -- Full CNPJ data provider response stored as JSONB
    -- This data comes from Receita.gov.br via CNPJ data provider
    receita_data JSONB,
        
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add comments for documentation
COMMENT ON TABLE financial_data.financial_institution IS 
    'Stores enhanced institution data from CNPJ data provider, including domain, website, legal name, trade name, social networks, logo, and receita data. One enhancement per parent CNPJ (shared by institutions with the same parent).';

COMMENT ON COLUMN financial_data.financial_institution.parent_cnpj IS 
    'Root CNPJ (primary key). Either cnpj_instituicao_lider from bacen_institution table (for institutions with a parent), or constructed CNPJ from bacen_cod_inst (for institutions with no parent). Multiple institutions can share the same parent_cnpj.';

COMMENT ON COLUMN financial_data.financial_institution.email_domain IS 
    'Email domain extracted from first email in CNPJ data provider response (excluding blacklisted email provider domains).';

COMMENT ON COLUMN financial_data.financial_institution.website IS 
    'Website URL if available from CNPJ data provider or website resolution. Used for logo download and social network discovery.';

COMMENT ON COLUMN financial_data.financial_institution.logo_storage_key IS 
    'Storage key for institution logo from asset management service. TODO: Service integration pending.';

COMMENT ON COLUMN financial_data.financial_institution.social_network IS 
    'Social network links discovered from website scraping. JSON object with keys: linkedin, instagram, facebook, twitter, x.';

COMMENT ON COLUMN financial_data.financial_institution.receita_data IS 
    'Full CNPJ data provider response stored as JSONB. Contains data from Receita.gov.br.';

COMMENT ON COLUMN financial_data.financial_institution.legal_name IS 
    'Company legal name extracted from CNPJ data provider response (company.name field).';

COMMENT ON COLUMN financial_data.financial_institution.trade_name IS 
    'Company trade name extracted from CNPJ data provider response (alias field).';

