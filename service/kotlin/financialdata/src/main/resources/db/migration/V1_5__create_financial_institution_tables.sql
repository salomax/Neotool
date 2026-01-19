-- Migration: Create financial institution tables
-- Version: V1_5
-- Description: Create tables for enhanced financial institution data and relationships
-- 
-- This migration creates:
-- 1. financial_institution: Enhanced institution data from CNPJ data provider
-- 2. financial_institution_bacen_institution: Junction table for quarter-specific relationships
-- 
-- Key features:
-- - Enhanced data per parent CNPJ (shared by institutions with same parent)
-- - Asset ID reference for logo (via GraphQL federation)
-- - Quarter-specific relationships to bacen_institution snapshots
-- - Full-text search indexes on trade_name and legal_name
-- - JSONB storage for social networks and receita data
--
-- Note: This migration consolidates V1_7, V1_10, and V1_12 into a single cohesive migration.
-- Tables are created in their final state with all columns, constraints, and indexes.

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: financial_institution
-- Purpose: Enhanced institution data from CNPJ data provider
-- ============================================================================

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
    
    -- Asset ID (UUID) referencing the Asset entity via GraphQL federation
    -- Used to resolve logoAsset field in GraphQL queries
    -- No foreign key constraint due to different schemas (financial_data vs assets)
    asset_id UUID,
    
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

-- Indexes for financial_institution

-- Index for trade_name searches using trigram similarity
CREATE INDEX IF NOT EXISTS idx_financial_institution_trade_name_trgm 
    ON financial_data.financial_institution 
    USING GIN (COALESCE(trade_name, '') gin_trgm_ops);

-- Index for legal_name searches using trigram similarity
CREATE INDEX IF NOT EXISTS idx_financial_institution_legal_name_trgm 
    ON financial_data.financial_institution 
    USING GIN (COALESCE(legal_name, '') gin_trgm_ops);

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

COMMENT ON COLUMN financial_data.financial_institution.asset_id IS 
    'Asset ID (UUID) referencing the Asset entity via GraphQL federation. Used to resolve logoAsset field in GraphQL queries. No foreign key constraint due to different schemas (financial_data vs assets).';

COMMENT ON COLUMN financial_data.financial_institution.social_network IS 
    'Social network links discovered from website scraping. JSON object with keys: linkedin, instagram, facebook, twitter, x.';

COMMENT ON COLUMN financial_data.financial_institution.receita_data IS 
    'Full CNPJ data provider response stored as JSONB. Contains data from Receita.gov.br.';

COMMENT ON COLUMN financial_data.financial_institution.legal_name IS 
    'Company legal name extracted from CNPJ data provider response (company.name field).';

COMMENT ON COLUMN financial_data.financial_institution.trade_name IS 
    'Company trade name extracted from CNPJ data provider response (alias field).';

-- ============================================================================
-- Table: financial_institution_bacen_institution
-- Purpose: Junction table for quarter-specific relationships between financial_institution and bacen_institution
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.financial_institution_bacen_institution (
    -- Foreign key to financial_institution
    financial_institution_id UUID NOT NULL,
    
    -- Foreign key to bacen_institution (composite key)
    bacen_cod_inst TEXT NOT NULL,
    ano_mes_ref CHAR(6) NOT NULL,
    
    -- Composite primary key
    PRIMARY KEY (financial_institution_id, bacen_cod_inst, ano_mes_ref),
    
    -- Foreign key constraint to financial_institution with CASCADE delete
    CONSTRAINT fk_financial_institution_bacen_institution_financial_institution 
        FOREIGN KEY (financial_institution_id) 
        REFERENCES financial_data.financial_institution(id) 
        ON DELETE CASCADE,
    
    -- Foreign key constraint to bacen_institution composite key with CASCADE delete
    -- Relationships are quarter-specific (ano_mes_ref)
    CONSTRAINT fk_financial_institution_bacen_institution_bacen_institution 
        FOREIGN KEY (bacen_cod_inst, ano_mes_ref) 
        REFERENCES financial_data.bacen_institution(bacen_cod_inst, ano_mes_ref) 
        ON DELETE CASCADE
);

-- Indexes for financial_institution_bacen_institution

-- Index on financial_institution_id for efficient joins
CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_financial_institution_id 
    ON financial_data.financial_institution_bacen_institution(financial_institution_id);

-- Composite index for bacen_institution lookups
CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_bacen_cod_inst_ano_mes 
    ON financial_data.financial_institution_bacen_institution(bacen_cod_inst, ano_mes_ref);

-- Index on ano_mes_ref for quarter filtering
CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_ano_mes_ref 
    ON financial_data.financial_institution_bacen_institution(ano_mes_ref);

-- Add comments for relation table
COMMENT ON TABLE financial_data.financial_institution_bacen_institution IS 
    'Junction table for many-to-many relationship between financial_institution and bacen_institution. Links enhanced institution data to BACEN institution snapshots. Relationships are quarter-specific (ano_mes_ref).';
