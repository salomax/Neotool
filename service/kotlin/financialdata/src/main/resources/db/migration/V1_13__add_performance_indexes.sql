-- Migration: Add performance indexes for institution ranking queries
-- Version: V1_13
-- Description: Covering index on financial_institution_bacen_institution to eliminate
--              Bitmap Heap Scan (578 blocks) when fetching all institutions for a period.
--              Enables index-only scans for queries that filter by ano_mes_ref and join
--              on bacen_cod_inst + financial_institution_id.

SET search_path TO financial_data, public;

-- Covering index: ano_mes_ref as leading column for period filter,
-- bacen_cod_inst for hash join condition, financial_institution_id included
-- to avoid heap fetch in queries like findTopByMetric and findTopMetricsForInstitutions.
--
-- Replaces Bitmap Heap Scan (578 blocks, ~19ms) with an Index Only Scan.
CREATE INDEX IF NOT EXISTS idx_fibi_ano_mes_ref_covering
    ON financial_data.financial_institution_bacen_institution (ano_mes_ref, bacen_cod_inst)
    INCLUDE (financial_institution_id);

-- Update statistics on the most impacted tables to help the planner
-- choose the best join order with the new LATERAL queries.
ANALYZE financial_data.financial_institution_bacen_institution;
ANALYZE financial_data.bacen_inst_report;
ANALYZE financial_data.bacen_inst_report_data;
ANALYZE financial_data.financial_institution;
