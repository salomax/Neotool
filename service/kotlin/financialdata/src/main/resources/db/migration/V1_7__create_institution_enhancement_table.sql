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
-- - Primary key: id (UUID v7) - consistent with other tables
-- - Unique constraint: parent_cnpj (root CNPJ - either cnpj_instituicao_lider or constructed from bacen_cod_inst)
-- - Domain extraction from CNPJ data provider emails
-- - Website URL (if available from CNPJ data provider or website resolution)
-- - Legal name and trade name extracted from CNPJ data provider
-- - Social network links discovered from website scraping (JSONB)
-- - Logo storage key (TODO: asset management service integration)
-- - Receita data (JSONB) storing full CNPJ data provider response
-- - Audit trail: created_at, updated_at timestamps
-- - Relation table for many-to-many relationship with bacen_institution
-- - Foreign key column in bacen_institution for direct relationship

SET search_path TO financial_data, public;

-- Create financial_institution table
CREATE TABLE IF NOT EXISTS financial_data.financial_institution (
    -- Primary key: UUID v7 (PostgreSQL 18+ has uuidv7() built-in)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Root CNPJ (unique constraint for Go consumer's ON CONFLICT clause)
    -- Either cnpj_instituicao_lider from bacen_institution table,
    -- or constructed CNPJ from bacen_cod_inst for institutions with no parent
    parent_cnpj TEXT NOT NULL UNIQUE,
    
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

COMMENT ON COLUMN financial_data.financial_institution.id IS 
    'Primary key: UUID v7 for uniqueness and temporal ordering.';
    
COMMENT ON COLUMN financial_data.financial_institution.parent_cnpj IS 
    'Root CNPJ (unique constraint). Either cnpj_instituicao_lider from bacen_institution table (for institutions with a parent), or constructed CNPJ from bacen_cod_inst (for institutions with no parent). Multiple institutions can share the same parent_cnpj.';

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

-- Create relation table for many-to-many relationship between financial_institution and bacen_institution
-- This allows multiple bacen_institutions to be linked to the same financial_institution
CREATE TABLE IF NOT EXISTS financial_data.financial_institution_bacen_institution (
    -- Foreign key to financial_institution
    financial_institution_id UUID NOT NULL,
    
    -- Foreign key to bacen_institution
    bacen_cod_inst TEXT NOT NULL,
    
    -- Composite primary key
    PRIMARY KEY (financial_institution_id, bacen_cod_inst),
    
    -- Foreign key constraint to financial_institution with CASCADE delete
    CONSTRAINT fk_financial_institution_bacen_institution_financial_institution 
        FOREIGN KEY (financial_institution_id) 
        REFERENCES financial_data.financial_institution(id) 
        ON DELETE CASCADE,
    
    -- Foreign key constraint to bacen_institution with CASCADE delete
    CONSTRAINT fk_financial_institution_bacen_institution_bacen_institution 
        FOREIGN KEY (bacen_cod_inst) 
        REFERENCES financial_data.bacen_institution(bacen_cod_inst) 
        ON DELETE CASCADE
);

-- Add unique constraint to ensure one-to-one relationship from bacen_institution side
ALTER TABLE financial_data.financial_institution_bacen_institution 
ADD CONSTRAINT uk_financial_institution_bacen_institution_bacen_cod_inst 
UNIQUE (bacen_cod_inst);

-- Create indexes for efficient joins
CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_financial_institution_id 
    ON financial_data.financial_institution_bacen_institution(financial_institution_id);

CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_bacen_cod_inst 
    ON financial_data.financial_institution_bacen_institution(bacen_cod_inst);

-- Add comments for relation table
COMMENT ON TABLE financial_data.financial_institution_bacen_institution IS 
    'Junction table for many-to-many relationship between financial_institution and bacen_institution. Links enhanced institution data to BACEN institutions.';
