-- flyway:nonTransactional
-- Migration: Covering index on bacen_inst_report_data for popular institutions query
-- Version: V1_20
-- Description: The popularInstitutions query joins bacen_inst_report_data filtering by
--              account_id (3 canonical account types). The existing index (inst_report_id, account_id)
--              forces a heap fetch for saldo/saldo_ajustado on every matching row.
--              This covering index on (account_id, inst_report_id) with INCLUDE allows
--              index-only scans, eliminating the disk I/O (AioIoCompletion waits) observed
--              in production when the table is not cached in shared_buffers.
--
--              Built CONCURRENTLY to avoid locking the table in production.

SET search_path TO financial_data, public;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_bacen_inst_report_data_account_covering
    ON financial_data.bacen_inst_report_data (account_id, inst_report_id)
    INCLUDE (saldo, saldo_ajustado);
