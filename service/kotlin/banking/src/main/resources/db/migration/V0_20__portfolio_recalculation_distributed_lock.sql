-- Distributed-safe recalculation: node identity + heartbeat for zombie detection.
--
-- processing_node_id: pod/instance that claimed the job (set atomically via tryClaimJob).
-- last_heartbeat_at:  updated every HEARTBEAT_INTERVAL_DAYS days during the loop.
--                     Jobs stuck in PROCESSING with stale heartbeat are zombies and can
--                     be reclaimed by ZombieJobRecoveryScheduler.

ALTER TABLE banking.investment_portfolio_recalculation_job
    ADD COLUMN IF NOT EXISTS processing_node_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_heartbeat_at  TIMESTAMPTZ;

-- Index to make zombie scan efficient (filters on status + heartbeat).
CREATE INDEX IF NOT EXISTS idx_recalculation_job_zombie
    ON banking.investment_portfolio_recalculation_job (status, last_heartbeat_at)
    WHERE status = 'PROCESSING';
