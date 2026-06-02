package repository

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type ImportJobControlRepository struct {
	pool *pgxpool.Pool
}

func NewImportJobControlRepository(pool *pgxpool.Pool) *ImportJobControlRepository {
	return &ImportJobControlRepository{pool: pool}
}

func (r *ImportJobControlRepository) FindLastDataDate(ctx context.Context, jobName string) (*time.Time, error) {
	var lastDate *time.Time
	err := r.pool.QueryRow(ctx, `
		SELECT last_data_date
		FROM financial_data.import_job_control
		WHERE job_name = $1
	`, jobName).Scan(&lastDate)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	return lastDate, nil
}

func (r *ImportJobControlRepository) MarkSuccess(ctx context.Context, jobName string, lastDataDate time.Time) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO financial_data.import_job_control
			(job_name, last_data_date, last_run_at, last_success_at, status, updated_at)
		VALUES
			($1, $2, NOW(), NOW(), 'SUCCESS', NOW())
		ON CONFLICT (job_name)
		DO UPDATE SET
			last_data_date  = EXCLUDED.last_data_date,
			last_run_at     = NOW(),
			last_success_at = NOW(),
			status          = 'SUCCESS',
			error_message   = NULL,
			updated_at      = NOW()
	`, jobName, lastDataDate)
	return err
}

func (r *ImportJobControlRepository) MarkFailed(ctx context.Context, jobName string, errMsg string) error {
	_, err := r.pool.Exec(ctx, `
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
	`, jobName, errMsg)
	return err
}
