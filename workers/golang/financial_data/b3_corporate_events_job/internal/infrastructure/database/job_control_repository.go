package database

import (
	"context"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	findLastSuccessAtSQL = `
		SELECT last_success_at
		FROM financial_data.import_job_control
		WHERE job_name = $1
	`

	upsertJobControlSQL = `
		INSERT INTO financial_data.import_job_control
			(job_name, last_data_date, last_success_at, status, updated_at)
		VALUES
			($1, $2, NOW(), 'SUCCESS', NOW())
		ON CONFLICT (job_name)
		DO UPDATE SET
			last_data_date  = EXCLUDED.last_data_date,
			last_success_at = NOW(),
			status          = 'SUCCESS',
			error_message   = NULL,
			updated_at      = NOW()
	`

	markFailedSQL = `
		INSERT INTO financial_data.import_job_control
			(job_name, status, error_message, last_run_at, updated_at)
		VALUES
			($1, 'FAILED', $2, NOW(), NOW())
		ON CONFLICT (job_name)
		DO UPDATE SET
			status        = 'FAILED',
			error_message = EXCLUDED.error_message,
			last_run_at   = NOW(),
			updated_at    = NOW()
	`
)

// JobControlRepository tracks per-job last-run state.
type JobControlRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewJobControlRepository(pool *pgxpool.Pool, logger *slog.Logger) *JobControlRepository {
	return &JobControlRepository{pool: pool, logger: logger}
}

// IsFirstRun returns true when this job has never succeeded (no row or null last_success_at).
func (r *JobControlRepository) IsFirstRun(ctx context.Context, jobName string) (bool, error) {
	var lastSuccess *time.Time
	err := r.pool.QueryRow(ctx, findLastSuccessAtSQL, jobName).Scan(&lastSuccess)
	if err != nil {
		if err == pgx.ErrNoRows {
			return true, nil
		}
		return false, err
	}
	return lastSuccess == nil, nil
}

// MarkSuccess records a successful import with today as last_data_date.
func (r *JobControlRepository) MarkSuccess(ctx context.Context, jobName string) error {
	_, err := r.pool.Exec(ctx, upsertJobControlSQL, jobName, time.Now().UTC().Truncate(24*time.Hour))
	return err
}

// MarkFailed records a failed run with an error message.
func (r *JobControlRepository) MarkFailed(ctx context.Context, jobName string, errMsg string) error {
	_, err := r.pool.Exec(ctx, markFailedSQL, jobName, errMsg)
	return err
}
