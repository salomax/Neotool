-- Migration: Create bank_report table
-- Version: V1_6
-- Description: Create financial_data.bank_report hot table for BACEN IFData aggregated report data
-- 
-- This migration creates the bank_report table that stores aggregated/normalized report data
-- as JSONB documents. This is the product-facing layer optimized for reads, analytics, and
-- downstream services. One row per (CodInst, AnoMes, NomeRelatorio, NumeroRelatorio, TipoInstituicao).
-- 
-- Key features:
-- - Composite primary key: (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
-- - Includes tipo_instituicao to preserve different consolidation perspectives (1-4)
-- - JSONB report_data column for flexible schema and efficient querying
-- - Audit trail: created_at, updated_at timestamps
-- - Indexes for common query patterns (institution lookups, quarter queries, promotion operations)
-- - UPSERT operations are idempotent (can be re-run safely)

SET search_path TO financial_data, public;

-- Create bank_report table
CREATE TABLE IF NOT EXISTS financial_data.bank_report (
    -- Natural key (composite primary key)
    cod_inst TEXT NOT NULL,
    ano_mes CHAR(6) NOT NULL,
    nome_relatorio TEXT NOT NULL,
    numero_relatorio TEXT NOT NULL,
    tipo_instituicao INTEGER NOT NULL,
    
    -- Aggregated/normalized report data stored as JSONB document
    report_data JSONB NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Composite primary key on natural key (includes tipo_instituicao to preserve consolidation perspectives)
    PRIMARY KEY (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
);

-- Create indexes for common query patterns

-- Index on cod_inst for querying by institution
CREATE INDEX IF NOT EXISTS idx_bank_report_cod_inst 
    ON financial_data.bank_report(cod_inst);

-- Index on ano_mes for querying by quarter
CREATE INDEX IF NOT EXISTS idx_bank_report_ano_mes 
    ON financial_data.bank_report(ano_mes);

-- Composite index on scope dimensions for promotion queries and scope-based operations
-- Used to efficiently query all reports for a specific scope:
-- (ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
-- Matches the scope pattern from ifdata_raw table
CREATE INDEX IF NOT EXISTS idx_bank_report_scope 
    ON financial_data.bank_report(ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao);

-- Index on created_at for audit queries and ordering
CREATE INDEX IF NOT EXISTS idx_bank_report_created_at 
    ON financial_data.bank_report(created_at);
