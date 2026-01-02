-- Migration: Create bacen_ifdata_extract_task table
-- Version: V1_3
-- Description: Create financial_data.bacen_ifdata_extract_task table for BACEN IFData extraction task control
-- 
-- This migration creates the ifdata_extract_task table that controls every external request
-- made to BACEN IFData APIs. It serves as a queue and audit log for HTTP calls, enabling
-- parallelism, retries, and idempotency at the request level.
-- 
-- Key features:
-- - Composite primary key: (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip)
-- - Status lifecycle: PENDING -> RUNNING -> SUCCESS | EMPTY | ERROR
-- - EMPTY is terminal state (end of pagination), not an error
-- - Retry tracking: attempt_count, last_error, last_http_status
-- - Audit trail: started_at, finished_at, created_at, updated_at timestamps
-- - Indexes for common query patterns (status, load control queries)

SET search_path TO financial_data, public;

-- Create bacen_ifdata_extract_task table
CREATE TABLE IF NOT EXISTS financial_data.bacen_ifdata_extract_task (
    -- Natural key dimensions (composite primary key)
    ano_mes CHAR(6) NOT NULL,
    tipo_instituicao INTEGER NOT NULL,
    nome_relatorio TEXT NOT NULL,
    numero_relatorio TEXT NOT NULL,
    page_skip INTEGER NOT NULL,

    -- Pagination control
    page_top INTEGER NOT NULL DEFAULT 5000,

    -- Status: PENDING -> RUNNING -> SUCCESS | EMPTY | ERROR
    -- EMPTY is terminal (end of pagination), not an error
    status TEXT NOT NULL DEFAULT 'PENDING',

    -- Retry tracking
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_http_status INTEGER,

    -- Execution timestamps
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Composite primary key on natural key
    PRIMARY KEY (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip),

    -- CHECK constraint for valid status values
    CONSTRAINT chk_bacen_ifdata_extract_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'EMPTY', 'ERROR'))
);

-- Create indexes for common query patterns

-- Index on status for finding PENDING tasks (worker queue queries)
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_extract_task_status 
    ON financial_data.bacen_ifdata_extract_task(status);

-- Index on (ano_mes, nome_relatorio) for load control queries
-- Used to check if all tasks for a quarter+report are terminal
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_extract_task_ano_mes_nome_relatorio 
    ON financial_data.bacen_ifdata_extract_task(ano_mes, nome_relatorio);

-- Index on created_at for audit queries and task ordering
CREATE INDEX IF NOT EXISTS idx_bacen_ifdata_extract_task_created_at 
    ON financial_data.bacen_ifdata_extract_task(created_at);

