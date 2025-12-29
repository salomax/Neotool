-- Migration: Create bank_report table
-- Version: V1_6
-- Description: Create financial_data.bank_report hot table for BACEN IFData aggregated report data
-- 
-- This migration creates the bank_report table that stores aggregated/normalized report data
-- in a normalized relational structure. This is the product-facing layer optimized for reads, 
-- analytics, and downstream services. One row per (CodInst, AnoMes, NomeRelatorio, NumeroRelatorio, TipoInstituicao).
-- 
-- Key features:
-- - UUID v7 primary key (uuidv7() built-in in PostgreSQL 18+)
-- - Unique constraint on natural key: (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
-- - Includes tipo_instituicao to preserve different consolidation perspectives (1-4)
-- - Related data stored in bank_report_data table (1:N relationship)
-- - Audit trail: created_at, updated_at timestamps
-- - Indexes for common query patterns (institution lookups, quarter queries, promotion operations)
-- - UPSERT operations are idempotent (can be re-run safely)

SET search_path TO financial_data, public;

-- Create bank_report table
CREATE TABLE IF NOT EXISTS financial_data.bank_report (
    -- UUID v7 primary key (PostgreSQL 18+ has uuidv7() built-in)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Natural key (unique constraint)
    cod_inst TEXT NOT NULL,
    ano_mes CHAR(6) NOT NULL,
    nome_relatorio TEXT NOT NULL,
    numero_relatorio TEXT NOT NULL,
    tipo_instituicao INTEGER NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Unique constraint on natural key (includes tipo_instituicao to preserve consolidation perspectives)
    CONSTRAINT bank_report_natural_key_unique 
        UNIQUE (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
);

-- Create indexes for common query patterns

-- Index on id for foreign key lookups
CREATE INDEX IF NOT EXISTS idx_bank_report_id 
    ON financial_data.bank_report(id);

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

-- Create bank_report_data table for normalized report data rows
-- This table stores individual data rows from CSV reports in a normalized structure
CREATE TABLE IF NOT EXISTS financial_data.bank_report_data (
    -- UUID v7 primary key (PostgreSQL 18+ has uuidv7() built-in)
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Foreign key to bank_report
    bank_report_id UUID NOT NULL,
    
    -- CSV row fields (mapped from field_5+)
    grupo TEXT,
    conta TEXT,
    nome_coluna TEXT,
    descricao_coluna TEXT,
    saldo NUMERIC,  -- Nullable, can be currency or percentage
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Foreign key constraint with CASCADE delete
    CONSTRAINT fk_bank_report_data_bank_report 
        FOREIGN KEY (bank_report_id) 
        REFERENCES financial_data.bank_report(id) 
        ON DELETE CASCADE
);

-- Create indexes for bank_report_data table

-- Index on foreign key for efficient joins and deletes
CREATE INDEX IF NOT EXISTS idx_bank_report_data_bank_report_id 
    ON financial_data.bank_report_data(bank_report_id);

-- Composite index for common query patterns
CREATE INDEX IF NOT EXISTS idx_bank_report_data_lookup 
    ON financial_data.bank_report_data(bank_report_id, conta, nome_coluna);

