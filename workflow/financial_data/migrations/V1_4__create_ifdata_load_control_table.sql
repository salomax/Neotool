-- Migration: Create ifdata_load_control table
-- Version: V1_4
-- Description: Create financial_data.ifdata_load_control table for BACEN IFData coarse-grained load tracking
-- 
-- This migration creates the ifdata_load_control table that tracks extraction and promotion
-- success at a coarse level (AnoMes, NomeRelatorio). It enables safe reprocessing of an entire
-- quarter+report and decouples "did we finish all requests?" from "did we promote/hydrate hot tables?".
-- 
-- Key features:
-- - Composite primary key: (ano_mes, nome_relatorio)
-- - Status tracking: extracted, promoted, completed (boolean flags + timestamps)
-- - Extract completion is determined by checking if all ifdata_extract_task rows for the
--   quarter+report are terminal (SUCCESS or EMPTY)
-- - Audit trail: created_at, updated_at timestamps
-- - Indexes for common query patterns (quarter queries, status queries, audit queries)

SET search_path TO financial_data, public;

-- Create ifdata_load_control table
CREATE TABLE IF NOT EXISTS financial_data.ifdata_load_control (
    -- Natural key (composite primary key)
    ano_mes CHAR(6) NOT NULL,
    nome_relatorio TEXT NOT NULL,
    
    -- Status flags: track completion of each stage
    extracted BOOLEAN NOT NULL DEFAULT FALSE,
    promoted BOOLEAN NOT NULL DEFAULT FALSE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Status timestamps: track when each stage completed (nullable, set when stage completes)
    extracted_at TIMESTAMPTZ,
    promoted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Composite primary key on natural key
    PRIMARY KEY (ano_mes, nome_relatorio)
);

-- Create indexes for common query patterns

-- Index on ano_mes for querying by quarter
CREATE INDEX IF NOT EXISTS idx_ifdata_load_control_ano_mes 
    ON financial_data.ifdata_load_control(ano_mes);

-- Index on extracted flag for finding non-extracted records
CREATE INDEX IF NOT EXISTS idx_ifdata_load_control_extracted 
    ON financial_data.ifdata_load_control(extracted);

-- Index on promoted flag for finding non-promoted records
CREATE INDEX IF NOT EXISTS idx_ifdata_load_control_promoted 
    ON financial_data.ifdata_load_control(promoted);

-- Index on completed flag for finding non-completed records
CREATE INDEX IF NOT EXISTS idx_ifdata_load_control_completed 
    ON financial_data.ifdata_load_control(completed);

-- Index on created_at for audit queries and ordering
CREATE INDEX IF NOT EXISTS idx_ifdata_load_control_created_at 
    ON financial_data.ifdata_load_control(created_at);

