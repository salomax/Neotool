package database

import (
	"context"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	findLastDataDateSQL = `
		SELECT last_data_date
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

// FindLastDataDate returns the most recent successfully imported data date for a job,
// or nil when the job has never run (triggering a backfill).
func (r *JobControlRepository) FindLastDataDate(ctx context.Context, jobName string) (*time.Time, error) {
	var lastDate *time.Time
	err := r.pool.QueryRow(ctx, findLastDataDateSQL, jobName).Scan(&lastDate)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil // first run
		}
		return nil, err
	}
	return lastDate, nil
}

// MarkSuccess records a successful import up to lastDataDate.
func (r *JobControlRepository) MarkSuccess(ctx context.Context, jobName string, lastDataDate time.Time) error {
	_, err := r.pool.Exec(ctx, upsertJobControlSQL, jobName, lastDataDate)
	return err
}

// MarkFailed records a failed run with an error message.
func (r *JobControlRepository) MarkFailed(ctx context.Context, jobName string, errMsg string) error {
	_, err := r.pool.Exec(ctx, markFailedSQL, jobName, errMsg)
	return err
}
