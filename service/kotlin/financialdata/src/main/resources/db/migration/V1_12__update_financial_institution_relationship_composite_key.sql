-- Migration: Update financial_institution_bacen_institution to use composite FK
-- Version: V1_12
-- Description: Update junction table to reference composite key (bacen_cod_inst, ano_mes_ref)
--              from bacen_institution, making relationships quarter-specific
-- 
-- This migration updates the relationship table to support quarter-specific relationships.
-- Each relationship now links a financial_institution to a specific quarter's bacen_institution snapshot.
-- 
-- Key changes:
-- - Add ano_mes_ref column to junction table
-- - Drop existing FK constraint (single bacen_cod_inst)
-- - Add composite FK (bacen_cod_inst, ano_mes_ref)
-- - Remove unique constraint on bacen_cod_inst (allows multiple relationships per institution across quarters)
-- - Migrate existing data: assign latest quarter per institution to existing relationships

SET search_path TO financial_data, public;

-- Step 1: Add ano_mes_ref column to junction table
ALTER TABLE financial_data.financial_institution_bacen_institution 
ADD COLUMN IF NOT EXISTS ano_mes_ref CHAR(6);

-- Step 2: Populate ano_mes_ref for existing relationships
-- Use the latest quarter (max ano_mes_ref) for each bacen_cod_inst
UPDATE financial_data.financial_institution_bacen_institution fibi
SET ano_mes_ref = (
    SELECT MAX(bi.ano_mes_ref)
    FROM financial_data.bacen_institution bi
    WHERE bi.bacen_cod_inst = fibi.bacen_cod_inst
)
WHERE ano_mes_ref IS NULL;

-- Step 3: Make ano_mes_ref NOT NULL after populating
ALTER TABLE financial_data.financial_institution_bacen_institution 
ALTER COLUMN ano_mes_ref SET NOT NULL;

-- Step 4: Drop existing foreign key constraint
ALTER TABLE financial_data.financial_institution_bacen_institution 
DROP CONSTRAINT IF EXISTS fk_financial_institution_bacen_institution_bacen_institution;

-- Step 5: Drop unique constraint on bacen_cod_inst (allows multiple relationships per institution across quarters)
ALTER TABLE financial_data.financial_institution_bacen_institution 
DROP CONSTRAINT IF EXISTS uk_financial_institution_bacen_institution_bacen_cod_inst;

-- Step 6: Drop existing primary key (will recreate with ano_mes_ref)
ALTER TABLE financial_data.financial_institution_bacen_institution 
DROP CONSTRAINT IF EXISTS financial_institution_bacen_institution_pkey;

-- Step 7: Add composite primary key (financial_institution_id, bacen_cod_inst, ano_mes_ref)
ALTER TABLE financial_data.financial_institution_bacen_institution 
ADD CONSTRAINT financial_institution_bacen_institution_pkey 
PRIMARY KEY (financial_institution_id, bacen_cod_inst, ano_mes_ref);

-- Step 8: Add composite foreign key to bacen_institution
ALTER TABLE financial_data.financial_institution_bacen_institution 
ADD CONSTRAINT fk_financial_institution_bacen_institution_bacen_institution 
    FOREIGN KEY (bacen_cod_inst, ano_mes_ref) 
    REFERENCES financial_data.bacen_institution(bacen_cod_inst, ano_mes_ref) 
    ON DELETE CASCADE;

-- Step 9: Update indexes for efficient lookups
-- Drop old index on bacen_cod_inst (will recreate with composite)
DROP INDEX IF EXISTS financial_data.idx_financial_institution_bacen_institution_bacen_cod_inst;

-- Create composite index for bacen_institution lookups
CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_bacen_cod_inst_ano_mes 
    ON financial_data.financial_institution_bacen_institution(bacen_cod_inst, ano_mes_ref);

-- Index on ano_mes_ref for quarter filtering
CREATE INDEX IF NOT EXISTS idx_financial_institution_bacen_institution_ano_mes_ref 
    ON financial_data.financial_institution_bacen_institution(ano_mes_ref);

-- Step 10: Update table comment
COMMENT ON TABLE financial_data.financial_institution_bacen_institution IS 
    'Junction table for many-to-many relationship between financial_institution and bacen_institution. Links enhanced institution data to BACEN institution snapshots. Relationships are quarter-specific (ano_mes_ref).';
