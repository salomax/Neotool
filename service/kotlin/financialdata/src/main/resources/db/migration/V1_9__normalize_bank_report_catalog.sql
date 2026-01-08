-- Migration: Normalize bacen_bank_report_catalog
-- Version: V1_9
-- Description: Extract nome_relatorio to new table bacen_bank_report_catalog and normalize bacen_bank_report
-- 
-- This migration normalizes the database by moving report names to a catalog table.
-- 
-- Key changes:
-- 1. Create bacen_bank_report_catalog table
-- 2. Migrate existing data from bacen_bank_report
-- 3. Remove nome_relatorio from bacen_bank_report
-- 4. Update indexes and constraints

SET search_path TO financial_data, public;

-- 1. Create bacen_bank_report_catalog table
CREATE TABLE IF NOT EXISTS financial_data.bacen_bank_report_catalog (
    -- Primary key: Report number
    numero_relatorio TEXT NOT NULL PRIMARY KEY,
    
    -- Report name
    nome_relatorio TEXT NOT NULL,
    
    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Migrate existing data
INSERT INTO financial_data.bacen_bank_report_catalog (numero_relatorio, nome_relatorio)
SELECT DISTINCT numero_relatorio, nome_relatorio
FROM financial_data.bacen_bank_report
ON CONFLICT (numero_relatorio) DO NOTHING;

-- 3. Update bacen_bank_report table

-- Drop index that uses nome_relatorio
DROP INDEX IF EXISTS financial_data.idx_bacen_bank_report_scope;

-- Drop unique constraint that uses nome_relatorio
ALTER TABLE financial_data.bacen_bank_report DROP CONSTRAINT IF EXISTS bacen_bank_report_natural_key_unique;

-- Drop the column
ALTER TABLE financial_data.bacen_bank_report DROP COLUMN IF EXISTS nome_relatorio;

-- Recreate unique constraint without nome_relatorio
ALTER TABLE financial_data.bacen_bank_report ADD CONSTRAINT bacen_bank_report_natural_key_unique 
    UNIQUE (cod_inst, ano_mes, numero_relatorio, tipo_instituicao);

-- Recreate index without nome_relatorio
CREATE INDEX IF NOT EXISTS idx_bacen_bank_report_scope 
    ON financial_data.bacen_bank_report(ano_mes, numero_relatorio, tipo_instituicao);

-- Add foreign key constraint
ALTER TABLE financial_data.bacen_bank_report 
    ADD CONSTRAINT fk_bacen_bank_report_catalog 
    FOREIGN KEY (numero_relatorio) 
    REFERENCES financial_data.bacen_bank_report_catalog (numero_relatorio);
