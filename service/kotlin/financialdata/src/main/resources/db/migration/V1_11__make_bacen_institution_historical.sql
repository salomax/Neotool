-- Migration: Make bacen_institution historical per quarter
-- Version: V1_11
-- Description: Transform bacen_institution from current-state table to historical quarterly snapshots
--              by changing primary key to composite (bacen_cod_inst, ano_mes_ref)
-- 
-- This migration transforms the bacen_institution table to support historical tracking per quarter.
-- Each row now represents an institution snapshot for a specific quarter, allowing institutions
-- to exist in one quarter and not in the next (as BACEN models them).
-- 
-- Key changes:
-- - Primary key changed from bacen_cod_inst to composite (bacen_cod_inst, ano_mes_ref)
-- - Indexes updated to support composite key lookups
-- - Existing data preserved (all rows keep their current ano_mes_ref)

SET search_path TO financial_data, public;

-- Step 1: Drop foreign key constraint from financial_institution_bacen_institution
-- This FK depends on the primary key, so it must be dropped first
-- It will be recreated in V1_12 with the composite key
ALTER TABLE financial_data.financial_institution_bacen_institution 
DROP CONSTRAINT IF EXISTS fk_financial_institution_bacen_institution_bacen_institution;

-- Step 2: Drop existing primary key constraint
ALTER TABLE financial_data.bacen_institution 
DROP CONSTRAINT IF EXISTS bacen_institution_pkey;

-- Step 3: Add composite primary key
ALTER TABLE financial_data.bacen_institution 
ADD CONSTRAINT bacen_institution_pkey 
PRIMARY KEY (bacen_cod_inst, ano_mes_ref);

-- Step 4: Create composite index for efficient lookups by (bacen_cod_inst, ano_mes_ref)
CREATE INDEX IF NOT EXISTS idx_bacen_institution_cod_inst_ano_mes 
    ON financial_data.bacen_institution(bacen_cod_inst, ano_mes_ref);

-- Step 5: Update table comment to reflect historical nature
COMMENT ON TABLE financial_data.bacen_institution IS 
    'Historical quarterly snapshots of BACEN institution registry. Each row represents an institution snapshot for a specific quarter (ano_mes_ref). Primary key is composite (bacen_cod_inst, ano_mes_ref).';

-- Step 6: Update column comment for ano_mes_ref
COMMENT ON COLUMN financial_data.bacen_institution.ano_mes_ref IS 
    'Reference quarter (format: YYYYMM). Part of composite primary key. Represents the quarter this institution snapshot belongs to.';