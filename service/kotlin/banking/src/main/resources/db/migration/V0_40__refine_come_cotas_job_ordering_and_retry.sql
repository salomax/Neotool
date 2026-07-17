ALTER TABLE banking.come_cotas_checkpoint_job
    ADD COLUMN next_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS banking.idx_come_cotas_checkpoint_job_pending_created_at;

CREATE INDEX idx_come_cotas_checkpoint_job_pending_due_checkpoint_created_at
    ON banking.come_cotas_checkpoint_job (status, next_attempt_at, checkpoint_date, created_at)
    WHERE status = 'PENDING';

DROP INDEX IF EXISTS banking.idx_come_cotas_checkpoint_job_account_status;

CREATE INDEX idx_come_cotas_checkpoint_job_account_status_checkpoint_created_at
    ON banking.come_cotas_checkpoint_job (account_id, status, checkpoint_date, created_at);
