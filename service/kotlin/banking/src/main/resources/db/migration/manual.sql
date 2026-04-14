-- Subcategory for manual investment deposits (spec: Transferência recebida > Entre contas próprias).
-- TRANSFER > Transferência enviada already has this child; INCOME > Transferência recebida had only PIX/TED/Outros.
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order, color, icon)
SELECT p.id, 'Entre contas próprias', 'INCOME', true, 25, p.color, 'multiple_stop'
FROM banking.transaction_categories p
WHERE p.name = 'Transferência recebida'
  AND p.is_system = TRUE
  AND p.account_id IS NULL
  AND p.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM banking.transaction_categories c
      WHERE c.parent_id = p.id
        AND c.name = 'Entre contas próprias'
        AND c.deleted_at IS NULL
  );



  ALTER TABLE banking.financial_accounts
    ADD COLUMN IF NOT EXISTS include_cash_in_allocation BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN banking.financial_accounts.include_cash_in_allocation IS
    'When true, idle cash on this investment/benefit custodian account counts toward allocation breakdown in the investments UI.';


ALTER TABLE banking.investment_portfolio_recalculation_job
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(80);


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

