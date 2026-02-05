-- Migration: Create institution data restriction table
-- Version: V1_11
-- Description: Stores restrictions on institution financial data exposure.
--   Institutions in this table (with is_active = true) will not have their
--   financial data exposed via API.
--
-- Key features:
--   - One row per institution (bacen_cod_inst UNIQUE)
--   - Reason code required (extensible via CHECK constraint)
--   - Soft delete via is_active
--   - Audit fields: created_at, updated_at, created_by

SET search_path TO financial_data, public;

CREATE TABLE IF NOT EXISTS financial_data.institution_data_restriction (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    bacen_cod_inst TEXT NOT NULL UNIQUE,
    reason TEXT NOT NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reason CHECK (reason IN ('REQUEST_BY_INSTITUTION'))
);

CREATE INDEX idx_institution_data_restriction_active
    ON financial_data.institution_data_restriction(bacen_cod_inst)
    WHERE is_active = TRUE;

COMMENT ON TABLE financial_data.institution_data_restriction IS
    'Stores restrictions on institution financial data exposure. Institutions in this table (with is_active=true) will not have their financial data exposed via API.';
COMMENT ON COLUMN financial_data.institution_data_restriction.bacen_cod_inst IS 'BACEN institution identifier (bacen_cod_inst).';
COMMENT ON COLUMN financial_data.institution_data_restriction.reason IS 'Reason code for restriction (e.g. INSTITUTION_REQUEST).';
COMMENT ON COLUMN financial_data.institution_data_restriction.notes IS 'Internal notes about the restriction.';
COMMENT ON COLUMN financial_data.institution_data_restriction.is_active IS 'When false, restriction is lifted and financial data is exposed again.';
