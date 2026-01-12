-- Migration: Add FK from bacen_bank_report to bacen_institution composite key
-- Version: V1_13
-- Description: Add foreign key constraint from bacen_bank_report to bacen_institution
--              using composite key (bacen_cod_inst, ano_mes_ref) for referential integrity
-- 
-- This migration adds a foreign key constraint to ensure every bank report has a matching
-- institution snapshot for the same quarter. This improves data integrity and aligns with
-- the quarter-scoped nature of both tables.
-- 
-- IMPORTANT: This migration will fail if there are any bacen_bank_report rows that don't
-- have a matching bacen_institution row for the same (cod_inst, ano_mes). You may need to:
-- 1. Ensure all reports have matching institution snapshots
-- 2. Clean up orphaned reports if any exist
-- 3. Or temporarily disable this FK if data integrity cannot be guaranteed
-- 
-- This FK is optional - the system works without it, but it provides better referential integrity.

SET search_path TO financial_data, public;

-- Step 1: Check for orphaned reports (reports without matching institution)
-- This query will show any reports that would cause the FK constraint to fail
-- Run this manually before applying the migration to identify issues:
-- SELECT DISTINCT br.cod_inst, br.ano_mes
-- FROM financial_data.bacen_bank_report br
-- LEFT JOIN financial_data.bacen_institution bi
--   ON bi.bacen_cod_inst = br.cod_inst
--   AND bi.ano_mes_ref = br.ano_mes
-- WHERE bi.bacen_cod_inst IS NULL;

-- Step 2: Add foreign key constraint
-- This will fail if there are orphaned reports (reports without matching institutions)
ALTER TABLE financial_data.bacen_bank_report
ADD CONSTRAINT fk_bacen_bank_report_bacen_institution
    FOREIGN KEY (cod_inst, ano_mes)
    REFERENCES financial_data.bacen_institution(bacen_cod_inst, ano_mes_ref)
    ON DELETE RESTRICT;

-- Note: ON DELETE RESTRICT ensures that institution snapshots cannot be deleted
-- if they have associated bank reports, maintaining data integrity.

-- Add comment for documentation
COMMENT ON CONSTRAINT fk_bacen_bank_report_bacen_institution 
    ON financial_data.bacen_bank_report IS 
    'Foreign key to bacen_institution composite key (bacen_cod_inst, ano_mes_ref). Ensures every bank report has a matching institution snapshot for the same quarter.';
