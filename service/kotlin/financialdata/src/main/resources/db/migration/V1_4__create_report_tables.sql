-- Migration: Create report tables
-- Version: V1_4
-- Description: Create all tables for BACEN institution report data storage and normalization
-- 
-- This migration creates the complete report data structure:
-- 1. bacen_inst_report_catalog: Normalized report catalog (numero_relatorio -> nome_relatorio)
-- 2. bacen_inst_report: Main report table (one row per institution/quarter/report)
-- 3. bacen_inst_report_account: Normalized account metadata
-- 4. bacen_inst_report_account_canonical: Canonical account names for matching
-- 5. bacen_inst_report_data: Individual report data rows with financial values
-- 
-- Key features:
-- - Normalized structure to reduce duplication
-- - Foreign keys for referential integrity
-- - Indexes for common query patterns
-- - Helper functions for account name normalization
--
-- Note: This migration consolidates V1_6, V1_9, V1_13, V1_14, and V1_15 into a single
-- cohesive migration. Tables are created in their final state with all columns,
-- constraints, and indexes.

SET search_path TO financial_data, public;

-- ============================================================================
-- Helper Functions for Account Name Normalization
-- ============================================================================

-- Helper to normalize account names consistently
CREATE OR REPLACE FUNCTION financial_data.normalize_report_account_name(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN input IS NULL THEN NULL
        ELSE TRIM('_' FROM UPPER(
            regexp_replace(
                regexp_replace(
                    regexp_replace(
                        regexp_replace(financial_data.unaccent(input), '[\n\r].*', '', 'g'),
                        '\(([^)]+)\)', '\1', 'g'
                    ),
                    '\s+', '_', 'g'
                ),
                '[/-]_', '', 'g'
            )
        ))
    END;
$$;

-- Helper to clean display name while preserving human-readable form
CREATE OR REPLACE FUNCTION financial_data.clean_report_account_display_name(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN input IS NULL THEN NULL
        ELSE TRIM(regexp_replace(input, '[\n\r].*', '', 'g'))
    END;
$$;

-- ============================================================================
-- Table: bacen_inst_report_catalog
-- Purpose: Normalized catalog of report names (numero_relatorio -> nome_relatorio)
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_inst_report_catalog (
    -- Primary key: Report number
    numero_relatorio TEXT NOT NULL PRIMARY KEY,
    
    -- Report name
    nome_relatorio TEXT NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Table: bacen_inst_report
-- Purpose: Main report table (one row per institution/quarter/report/consolidation type)
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_inst_report (
    -- UUID v7 primary key (PostgreSQL 18+ has uuidv7() built-in)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Natural key (unique constraint)
    cod_inst TEXT NOT NULL,
    ano_mes CHAR(6) NOT NULL,
    numero_relatorio TEXT NOT NULL,
    tipo_instituicao INTEGER NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Unique constraint on natural key (includes tipo_instituicao to preserve consolidation perspectives)
    CONSTRAINT bacen_inst_report_natural_key_unique 
        UNIQUE (cod_inst, ano_mes, numero_relatorio, tipo_instituicao),
    
    -- Foreign key to report catalog
    CONSTRAINT fk_bacen_inst_report_catalog 
        FOREIGN KEY (numero_relatorio) 
        REFERENCES financial_data.bacen_inst_report_catalog (numero_relatorio),
    
    -- Foreign key to bacen_institution composite key (bacen_cod_inst, ano_mes_ref)
    -- Ensures every institution report has a matching institution snapshot for the same quarter
    CONSTRAINT fk_bacen_inst_report_bacen_institution
        FOREIGN KEY (cod_inst, ano_mes)
        REFERENCES financial_data.bacen_institution(bacen_cod_inst, ano_mes_ref)
        ON DELETE RESTRICT
);

-- Indexes for bacen_inst_report

-- Index on id for foreign key lookups
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_id 
    ON financial_data.bacen_inst_report(id);

-- Index on cod_inst for querying by institution
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_cod_inst 
    ON financial_data.bacen_inst_report(cod_inst);

-- Index on ano_mes for querying by quarter
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_ano_mes 
    ON financial_data.bacen_inst_report(ano_mes);

-- Index on cod_inst and ano_mes for querying by institution and quarter
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_cod_inst_ano_mes 
    ON financial_data.bacen_inst_report(cod_inst, ano_mes);

-- Composite index on scope dimensions for promotion queries and scope-based operations
-- Used to efficiently query all reports for a specific scope:
-- (ano_mes, numero_relatorio, tipo_instituicao)
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_scope 
    ON financial_data.bacen_inst_report(ano_mes, numero_relatorio, tipo_instituicao);

-- Index for bacen query latest trimester (DESC ordering)
CREATE INDEX IF NOT EXISTS bacen_inst_report_cod_inst_ano_mes 
    ON financial_data.bacen_inst_report USING btree (cod_inst, ano_mes DESC);

-- Index on created_at for audit queries and ordering
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_created_at 
    ON financial_data.bacen_inst_report(created_at);

-- Add comment for foreign key constraint
COMMENT ON CONSTRAINT fk_bacen_inst_report_bacen_institution 
    ON financial_data.bacen_inst_report IS 
    'Foreign key to bacen_institution composite key (bacen_cod_inst, ano_mes_ref). Ensures every institution report has a matching institution snapshot for the same quarter.';

-- ============================================================================
-- Table: bacen_inst_report_account_canonical
-- Purpose: Canonical account names for matching and normalization
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_inst_report_account_canonical (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    normalized_name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_account_canonical_normalized_name
    ON financial_data.bacen_inst_report_account_canonical(normalized_name);

-- ============================================================================
-- Table: bacen_inst_report_account
-- Purpose: Normalized account metadata (unique combinations of account fields)
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_inst_report_account (
    -- UUID v7 primary key (PostgreSQL 18+ has uuidv7() built-in)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Account metadata fields (all nullable)
    grupo TEXT,
    conta TEXT,
    nome_coluna TEXT,
    descricao_coluna TEXT,
    
    -- Foreign key to canonical account
    canonical_account_id UUID,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Unique constraint on account metadata combination
    CONSTRAINT bacen_inst_report_account_unique 
        UNIQUE (grupo, conta, nome_coluna, descricao_coluna),
    
    -- Foreign key constraint to canonical account
    CONSTRAINT fk_bacen_inst_report_account_canonical
        FOREIGN KEY (canonical_account_id)
        REFERENCES financial_data.bacen_inst_report_account_canonical(id)
        ON DELETE RESTRICT
);

-- Indexes for bacen_inst_report_account

-- Index on id for foreign key lookups
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_account_id 
    ON financial_data.bacen_inst_report_account(id);

-- Index on unique key fields for efficient lookups
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_account_key 
    ON financial_data.bacen_inst_report_account(grupo, conta, nome_coluna, descricao_coluna);

-- Index on conta fields for efficient lookups
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_account_conta
    ON financial_data.bacen_inst_report_account(conta);

-- Index on canonical_account_id for efficient joins
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_account_canonical_account_id
    ON financial_data.bacen_inst_report_account(canonical_account_id);

-- ============================================================================
-- Table: bacen_inst_report_data
-- Purpose: Individual report data rows with financial values
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_inst_report_data (
    -- UUID v7 primary key (PostgreSQL 18+ has uuidv7() built-in)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Foreign key to inst_report
    inst_report_id UUID NOT NULL,
    
    -- Foreign key to inst_report_account (normalized account metadata)
    account_id UUID NOT NULL,
    
    -- Financial values
    saldo NUMERIC,  -- Nullable, can be currency or percentage
    saldo_ajustado NUMERIC,  -- Nullable, quarter-adjusted balance for cumulative accounts
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Foreign key constraint to inst_report with CASCADE delete
    CONSTRAINT fk_bacen_inst_report_data_bacen_inst_report 
        FOREIGN KEY (inst_report_id) 
        REFERENCES financial_data.bacen_inst_report(id) 
        ON DELETE CASCADE,
    
    -- Foreign key constraint to inst_report_account
    CONSTRAINT fk_bacen_inst_report_data_bacen_inst_report_account 
        FOREIGN KEY (account_id) 
        REFERENCES financial_data.bacen_inst_report_account(id) 
        ON DELETE RESTRICT
);

-- Indexes for bacen_inst_report_data

-- Index on foreign key for efficient joins and deletes
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_data_inst_report_id 
    ON financial_data.bacen_inst_report_data(inst_report_id);

-- Index on account_id for efficient joins
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_data_account_id 
    ON financial_data.bacen_inst_report_data(account_id);

-- Composite index for common query patterns
CREATE INDEX IF NOT EXISTS idx_bacen_inst_report_data_lookup 
    ON financial_data.bacen_inst_report_data(inst_report_id, account_id);
