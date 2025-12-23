-- Migration: Create institution table
-- Version: V1_2
-- Description: Create financial_data.institution table for BACEN institution registry with change detection
-- 
-- This migration creates the institution table that stores the current-state BACEN registry.
-- The table supports hash-based change detection and Kafka event publishing for NEW/CHANGED institutions.
-- 
-- Key features:
-- - Natural primary key: bacen_cod_inst (TEXT to preserve leading zeros)
-- - Change detection: source_hash (CHAR(64)) for detecting NEW/CHANGED/UNCHANGED institutions
-- - Audit trail: created_at, updated_at timestamps
-- - Indexes for common query patterns

SET search_path TO financial_data, public;

-- Create institution table
CREATE TABLE IF NOT EXISTS financial_data.institution (
    -- Primary key: BACEN institution code (TEXT to preserve leading zeros)
    bacen_cod_inst TEXT NOT NULL PRIMARY KEY,
    
    -- Reference quarter: last snapshot quarter that updated this row (format: YYYYMM)
    ano_mes_ref CHAR(6) NOT NULL,
    
    -- Institution name fields
    nome_instituicao TEXT,
    nome_reduzido TEXT,
    
    -- Institution status and classification
    situacao CHAR(1),
    segmento_tb TEXT,
    atividade TEXT,
    tipo_controle TEXT,
    
    -- Location fields
    uf CHAR(2),
    municipio TEXT,
    
    -- Conglomerate and leadership fields
    cod_conglomerado_financeiro TEXT,
    cod_conglomerado_prudencial TEXT,
    cnpj_instituicao_lider TEXT,
    
    -- Change detection: SHA-256 hex hash of event-relevant fields
    source_hash CHAR(64) NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Create indexes for common query patterns

-- Index on ano_mes_ref for querying institutions by quarter
CREATE INDEX IF NOT EXISTS idx_institution_ano_mes_ref 
    ON financial_data.institution(ano_mes_ref);

-- Index on situacao for filtering active institutions (situacao='A')
CREATE INDEX IF NOT EXISTS idx_institution_situacao 
    ON financial_data.institution(situacao);

-- Index on source_hash for change detection lookups
CREATE INDEX IF NOT EXISTS idx_institution_source_hash 
    ON financial_data.institution(source_hash);
