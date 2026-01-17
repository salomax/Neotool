-- Migration: Create IFData extraction and loading tables
-- Version: V1_3
-- Description: Create tables for BACEN IFData extraction, load control, and raw data storage
-- 
-- This migration creates three tables that work together to manage the BACEN IFData ingestion process:
-- 1. bacen_ifdata_extract_task: Controls every external request to BACEN IFData APIs
-- 2. bacen_ifdata_load_control: Tracks extraction and promotion success at coarse level
-- 3. bacen_ifdata_raw: Stores raw CSV rows for staging and reprocessing
-- 
-- Key features:
-- - Task queue and audit log for HTTP calls
-- - Coarse-grained load tracking for safe reprocessing
-- - Raw data preservation for debugging and full reprocessing
-- - Indexes for common query patterns
--
-- Note: This migration consolidates V1_3, V1_4, and V1_5 into a single cohesive migration.

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: bacen_ifdata_extract_task
-- Purpose: Controls every external request made to BACEN IFData APIs
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_ifdata_extract_task (
    -- Natural key dimensions (composite primary key)
    ano_mes CHAR(6) NOT NULL,
    tipo_instituicao INTEGER NOT NULL,
    nome_relatorio TEXT NOT NULL,
    numero_relatorio TEXT NOT NULL,
    page_skip INTEGER NOT NULL,

    -- Pagination control
    page_top INTEGER NOT NULL DEFAULT 5000,

    -- Status: PENDING -> RUNNING -> SUCCESS | EMPTY | ERROR
    -- EMPTY is terminal (end of pagination), not an error
    status TEXT NOT NULL DEFAULT 'PENDING',

    -- Total rows processed
    rows_processed INTEGER NOT NULL DEFAULT 0,

    -- Retry tracking
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_http_status INTEGER,

    -- Execution timestamps
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Composite primary key on natural key
    PRIMARY KEY (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip),

    -- CHECK constraint for valid status values
    CONSTRAINT chk_bacen_ifdata_extract_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'EMPTY', 'ERROR'))
);

-- Indexes for bacen_ifdata_extract_task

-- Index on status for finding PENDING tasks (worker queue queries)
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_extract_task_status 
    ON financial_data.bacen_ifdata_extract_task(status);

-- Index on (ano_mes, nome_relatorio) for load control queries
-- Used to check if all tasks for a quarter+report are terminal
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_extract_task_ano_mes_nome_relatorio 
    ON financial_data.bacen_ifdata_extract_task(ano_mes, nome_relatorio);

-- Index on created_at for audit queries and task ordering
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_extract_task_created_at 
    ON financial_data.bacen_ifdata_extract_task(created_at);

-- ============================================================================
-- Table: bacen_ifdata_load_control
-- Purpose: Tracks extraction and promotion success at coarse level (AnoMes, NomeRelatorio)
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_ifdata_load_control (
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

-- Indexes for bacen_ifdata_load_control

-- Index on ano_mes for querying by quarter
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_load_control_ano_mes 
    ON financial_data.bacen_ifdata_load_control(ano_mes);

-- Index on extracted flag for finding non-extracted records
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_load_control_extracted 
    ON financial_data.bacen_ifdata_load_control(extracted);

-- Index on promoted flag for finding non-promoted records
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_load_control_promoted 
    ON financial_data.bacen_ifdata_load_control(promoted);

-- Index on completed flag for finding non-completed records
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_load_control_completed 
    ON financial_data.bacen_ifdata_load_control(completed);

-- Index on created_at for audit queries and ordering
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_load_control_created_at 
    ON financial_data.bacen_ifdata_load_control(created_at);

-- ============================================================================
-- Table: bacen_ifdata_raw
-- Purpose: Staging table for BACEN IFData raw CSV row storage
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.bacen_ifdata_raw (
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

-- Indexes for bacen_ifdata_raw

-- Composite index on scope dimensions for promotion queries and purge operations
-- Used to efficiently query all raw rows for a specific scope:
-- (ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_raw_scope 
    ON financial_data.bacen_ifdata_raw(ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao);

-- Partial index on cod_inst for institution filtering during promotion
-- Only indexes non-null values for efficiency
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_raw_cod_inst 
    ON financial_data.bacen_ifdata_raw(cod_inst) 
    WHERE cod_inst IS NOT NULL;

-- Index on created_at for audit queries and ordering
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_raw_created_at 
    ON financial_data.bacen_ifdata_raw(created_at);
