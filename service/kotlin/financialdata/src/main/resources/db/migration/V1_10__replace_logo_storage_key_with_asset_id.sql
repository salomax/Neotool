-- Migration: Replace logo_storage_key with asset_id
-- Version: V1_10
-- Description: Replace logo_storage_key (TEXT) column with asset_id (UUID) to reference Asset entities via GraphQL federation
-- 
-- This migration:
-- 1. Adds asset_id UUID column (nullable, no FK constraint due to different schemas)
-- 2. Sets existing rows to NULL (clean slate - existing storage keys are not migrated)
-- 3. Drops logo_storage_key column
-- 4. Updates table comments

SET search_path TO financial_data, public;

-- Add asset_id column
ALTER TABLE financial_data.financial_institution
    ADD COLUMN asset_id UUID;

-- Set existing rows to NULL (clean slate migration)
-- Note: We're not migrating existing storage_key values to asset IDs
UPDATE financial_data.financial_institution
SET asset_id = NULL
WHERE asset_id IS NULL;

-- Drop the old logo_storage_key column
ALTER TABLE financial_data.financial_institution
    DROP COLUMN IF EXISTS logo_storage_key;

-- Update column comment
COMMENT ON COLUMN financial_data.financial_institution.asset_id IS 
    'Asset ID (UUID) referencing the Asset entity via GraphQL federation. Used to resolve logoAsset field in GraphQL queries. No foreign key constraint due to different schemas (financial_data vs assets).';
