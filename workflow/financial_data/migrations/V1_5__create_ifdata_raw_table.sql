-- Migration: Create ifdata_raw table
-- Version: V1_5
-- Description: Create financial_data.ifdata_raw staging table for BACEN IFData raw CSV row storage
-- 
-- This migration creates the ifdata_raw table that stores BACEN payload as-is (CSV rows),
-- preserving original values for auditing, debugging, and full reprocessing. This is a
-- tall/raw staging table that enables safe reprocessing and promotion to hot tables.
-- 
-- Key features:
-- - UUID v7 primary key (no natural unique key exists for raw CSV rows)
-- - Dimension columns: ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, cod_inst
-- - Raw data column: raw_data (TEXT) stores entire CSV row as-is
-- - Audit trail: created_at, updated_at timestamps
-- - Indexes for promotion queries, institution filtering, and purge operations

SET search_path TO financial_data, public;

-- Create ifdata_raw table
CREATE TABLE IF NOT EXISTS financial_data.ifdata_raw (
    -- Primary key: UUID v7 for uniqueness (no natural unique key exists for raw CSV rows)
    -- Using uuidv7() which is built-in in PostgreSQL 18+ (no extension needed)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Dimension columns (for scoping and querying)
    ano_mes CHAR(6) NOT NULL,
    tipo_instituicao INTEGER NOT NULL,
    nome_relatorio TEXT NOT NULL,
    numero_relatorio TEXT NOT NULL,
    cod_inst TEXT,  -- Nullable, as some CSV rows may not have institution code
    
    -- Raw data: Store entire CSV row as-is to preserve original values
    raw_data TEXT NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Create indexes for common query patterns

-- Composite index on scope dimensions for promotion queries and purge operations
-- Used to efficiently query all raw rows for a specific scope:
-- (ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
CREATE INDEX IF NOT EXISTS idx_ifdata_raw_scope 
    ON financial_data.ifdata_raw(ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao);

-- Partial index on cod_inst for institution filtering during promotion
-- Only indexes non-null values for efficiency
CREATE INDEX IF NOT EXISTS idx_ifdata_raw_cod_inst 
    ON financial_data.ifdata_raw(cod_inst) 
    WHERE cod_inst IS NOT NULL;

-- Index on created_at for audit queries and ordering
CREATE INDEX IF NOT EXISTS idx_ifdata_raw_created_at 
    ON financial_data.ifdata_raw(created_at);


