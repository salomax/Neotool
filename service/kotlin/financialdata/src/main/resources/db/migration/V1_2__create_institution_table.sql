-- Migration: Create bacen_institution table
-- Version: V1_2
-- Description: Create financial_data.bacen_institution table for BACEN institution registry with change detection
-- 
-- This migration creates the bacen_institution table that stores historical quarterly snapshots
-- of the BACEN institution registry. The table supports hash-based change detection and
-- Kafka event publishing for NEW/CHANGED institutions.
-- 
-- Key features:
-- - Composite primary key: (bacen_cod_inst, ano_mes_ref) for historical tracking
-- - Change detection: source_hash (CHAR(64)) for detecting NEW/CHANGED/UNCHANGED institutions
-- - Audit trail: created_at, updated_at timestamps
-- - Indexes for common query patterns
--
-- Note: This migration consolidates V1_11 (make_bacen_institution_historical) by creating
-- the table with composite primary key from the start.

SET search_path TO financial_data, public;

-- Create bacen_institution table
CREATE TABLE IF NOT EXISTS financial_data.bacen_institution (
    -- Composite primary key: BACEN institution code and reference quarter
    -- TEXT to preserve leading zeros in institution code
    bacen_cod_inst TEXT NOT NULL,
    ano_mes_ref CHAR(6) NOT NULL,
    
    -- Institution name fields
    nome_instituicao TEXT,
    
    -- Institution status and classification
    situacao CHAR(1),
    segmento_tb TEXT,
    atividade TEXT,
    tipo_controle TEXT,
    
    -- Consolidation type fields
    tipo_consolidado_bancario TEXT,  -- TCB: Banking consolidated type (b1, B2, B3S, B3C, b4, N1, n2, N4)
    tipo_consolidacao CHAR(1),       -- TD: Consolidation type (I=Independent, C=Conglomerate)
    
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
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Composite primary key constraint
    CONSTRAINT bacen_institution_pkey PRIMARY KEY (bacen_cod_inst, ano_mes_ref)
);

-- Create indexes for common query patterns

-- Composite index for efficient lookups by (bacen_cod_inst, ano_mes_ref)
CREATE INDEX IF NOT EXISTS idx_bacen_institution_cod_inst_ano_mes 
    ON financial_data.bacen_institution(bacen_cod_inst, ano_mes_ref);

-- Index on ano_mes_ref for querying institutions by quarter
CREATE INDEX IF NOT EXISTS idx_bacen_institution_ano_mes_ref 
    ON financial_data.bacen_institution(ano_mes_ref);

-- Index on situacao for filtering active institutions (situacao='A')
CREATE INDEX IF NOT EXISTS idx_bacen_institution_situacao 
    ON financial_data.bacen_institution(situacao);

-- Index on source_hash for change detection lookups
CREATE INDEX IF NOT EXISTS idx_bacen_institution_source_hash 
    ON financial_data.bacen_institution(source_hash);

-- Add comments for documentation
COMMENT ON TABLE financial_data.bacen_institution IS 
    'Historical quarterly snapshots of BACEN institution registry. Each row represents an institution snapshot for a specific quarter (ano_mes_ref). Primary key is composite (bacen_cod_inst, ano_mes_ref).';

COMMENT ON COLUMN financial_data.bacen_institution.ano_mes_ref IS 
    'Reference quarter (format: YYYYMM). Part of composite primary key. Represents the quarter this institution snapshot belongs to.';

COMMENT ON COLUMN financial_data.bacen_institution.tipo_consolidado_bancario IS 'Banking consolidated type (TCB): b1=Commercial bank/Caixas, B2=Multiple/Investment, B3S=Singular cooperative, B3C=Central cooperative, b4=Development, N1=Non-bank credit, n2=Non-bank capital markets, N4=Payment institutions';
COMMENT ON COLUMN financial_data.bacen_institution.tipo_consolidacao IS 'Consolidation type (TD): I=Independent institution, C=Conglomerate';
COMMENT ON COLUMN financial_data.bacen_institution.tipo_controle IS 'Control type (TC): 1=Public, 2=Private national, 3=Private foreign control';

