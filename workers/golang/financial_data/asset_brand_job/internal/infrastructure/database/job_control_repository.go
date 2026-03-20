package database

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	markRunningSQL = `
		INSERT INTO financial_data.import_job_control
			(job_name, status, last_run_at, updated_at)
		VALUES
			($1, 'RUNNING', NOW(), NOW())
		ON CONFLICT (job_name)
		DO UPDATE SET
			status = 'RUNNING',
			error_message = NULL,
			last_run_at = NOW(),
			updated_at = NOW()
	`

	markSuccessSQL = `
		INSERT INTO financial_data.import_job_control
			(job_name, status, last_run_at, last_success_at, updated_at)
		VALUES
			($1, 'SUCCESS', NOW(), NOW(), NOW())
		ON CONFLICT (job_name)
		DO UPDATE SET
			status = 'SUCCESS',
			error_message = NULL,
			last_run_at = NOW(),
			last_success_at = NOW(),
			updated_at = NOW()
	`

	markFailedSQL = `
		INSERT INTO financial_data.import_job_control
			(job_name, status, error_message, last_run_at, updated_at)
		VALUES
			($1, 'FAILED', $2, NOW(), NOW())
		ON CONFLICT (job_name)
		DO UPDATE SET
			status = 'FAILED',
			error_message = EXCLUDED.error_message,
			last_run_at = NOW(),
			updated_at = NOW()
	`
)

type JobControlRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewJobControlRepository(pool *pgxpool.Pool, logger *slog.Logger) *JobControlRepository {
	return &JobControlRepository{pool: pool, logger: logger}
}

func (r *JobControlRepository) MarkRunning(ctx context.Context, jobName string) error {
	_, err := r.pool.Exec(ctx, markRunningSQL, jobName)
	return err
}

func (r *JobControlRepository) MarkSuccess(ctx context.Context, jobName string) error {
	_, err := r.pool.Exec(ctx, markSuccessSQL, jobName)
	return err
}

func (r *JobControlRepository) MarkFailed(ctx context.Context, jobName string, errMsg string) error {
	_, err := r.pool.Exec(ctx, markFailedSQL, jobName, errMsg)
	return err
}
