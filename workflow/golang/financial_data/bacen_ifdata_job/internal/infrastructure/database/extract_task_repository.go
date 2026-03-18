package database

import (
	"context"
	"errors"
	"fmt"
	"log/slog"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ExtractTaskRepository handles extract task persistence
type ExtractTaskRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewExtractTaskRepository creates a new extract task repository
func NewExtractTaskRepository(pool *pgxpool.Pool, logger *slog.Logger) *ExtractTaskRepository {
	return &ExtractTaskRepository{
		pool:   pool,
		logger: logger,
	}
}

// Create creates a new extract task (idempotent)
func (r *ExtractTaskRepository) Create(ctx context.Context, task *domain.ExtractTask) error {
	query := `
		INSERT INTO financial_data.bacen_ifdata_extract_task (
			ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
			page_skip, page_top, status
		) VALUES ($1, $2, $3, $4, $5, $6, $7)
		ON CONFLICT (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip) DO NOTHING
	`

	result, err := r.pool.Exec(
		ctx, query,
		task.AnoMes, task.TipoInstituicao, task.NomeRelatorio, task.NumeroRelatorio,
		task.PageSkip, task.PageTop, task.Status,
	)

	if err != nil {
		return fmt.Errorf("failed to create extract task: %w", err)
	}

	rowsAffected := result.RowsAffected()
	if rowsAffected > 0 {
		r.logger.Debug("created extract task",
			"ano_mes", task.AnoMes,
			"tipo_instituicao", task.TipoInstituicao,
			"nome_relatorio", task.NomeRelatorio,
			"page_skip", task.PageSkip)
	} else {
		r.logger.Debug("extract task already exists",
			"ano_mes", task.AnoMes,
			"tipo_instituicao", task.TipoInstituicao,
			"nome_relatorio", task.NomeRelatorio,
			"page_skip", task.PageSkip)
	}

	return nil
}

// AcquireNextPending acquires the next pending task with row-level locking
func (r *ExtractTaskRepository) AcquireNextPending(ctx context.Context, maxRetries int) (*domain.ExtractTask, error) {
	query := `
		UPDATE financial_data.bacen_ifdata_extract_task
		SET
			status = $1,
			attempt_count = attempt_count + 1,
			started_at = NOW(),
			updated_at = NOW()
		WHERE (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip) = (
			SELECT ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip
			FROM financial_data.bacen_ifdata_extract_task
			WHERE status = $2 AND attempt_count < $3
			ORDER BY created_at ASC
			LIMIT 1
			FOR UPDATE SKIP LOCKED
		)
		RETURNING
			ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
			page_skip, page_top, status, attempt_count, COALESCE(rows_processed, 0), last_error, last_http_status,
			started_at, finished_at, created_at, updated_at
	`

	task := &domain.ExtractTask{}
	err := r.pool.QueryRow(
		ctx, query,
		domain.StatusRunning, domain.StatusPending, maxRetries,
	).Scan(
		&task.AnoMes, &task.TipoInstituicao, &task.NomeRelatorio, &task.NumeroRelatorio,
		&task.PageSkip, &task.PageTop, &task.Status, &task.AttemptCount, &task.RowsProcessed, &task.LastError, &task.LastHTTPStatus,
		&task.StartedAt, &task.FinishedAt, &task.CreatedAt, &task.UpdatedAt,
	)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, nil // No pending tasks available
		}
		return nil, fmt.Errorf("failed to acquire next pending task: %w", err)
	}

	r.logger.Info("acquired extract task",
		"ano_mes", task.AnoMes,
		"tipo_instituicao", task.TipoInstituicao,
		"nome_relatorio", task.NomeRelatorio,
		"page_skip", task.PageSkip,
		"attempt_count", task.AttemptCount)

	return task, nil
}

// UpdateStatus updates the status of an extract task using composite key
func (r *ExtractTaskRepository) UpdateStatus(
	ctx context.Context,
	task *domain.ExtractTask,
	status domain.ExtractTaskStatus,
	lastError *string,
	lastHTTPStatus *int,
) error {
	query := `
		UPDATE financial_data.bacen_ifdata_extract_task
		SET
			status = $1,
			last_error = $2,
			last_http_status = $3,
			finished_at = CASE WHEN $1 IN ('SUCCESS', 'EMPTY', 'ERROR') THEN NOW() ELSE finished_at END,
			updated_at = NOW()
		WHERE ano_mes = $4
		  AND tipo_instituicao = $5
		  AND nome_relatorio = $6
		  AND numero_relatorio = $7
		  AND page_skip = $8
	`

	_, err := r.pool.Exec(ctx, query, status, lastError, lastHTTPStatus,
		task.AnoMes, task.TipoInstituicao, task.NomeRelatorio, task.NumeroRelatorio, task.PageSkip)
	if err != nil {
		return fmt.Errorf("failed to update extract task status: %w", err)
	}

	r.logger.Debug("updated extract task status",
		"ano_mes", task.AnoMes,
		"tipo_instituicao", task.TipoInstituicao,
		"nome_relatorio", task.NomeRelatorio,
		"page_skip", task.PageSkip,
		"status", status)

	return nil
}

// RequeueForRetry requeues a task for retry if under max attempts
func (r *ExtractTaskRepository) RequeueForRetry(ctx context.Context, task *domain.ExtractTask, maxRetries int, lastError string, lastHTTPStatus *int) error {
	query := `
		UPDATE financial_data.bacen_ifdata_extract_task
		SET
			status = CASE
				WHEN attempt_count < $1 THEN 'PENDING'
				ELSE 'ERROR'
			END,
			last_error = $2,
			last_http_status = $3,
			finished_at = CASE WHEN attempt_count >= $1 THEN NOW() ELSE NULL END,
			updated_at = NOW()
		WHERE ano_mes = $4
		  AND tipo_instituicao = $5
		  AND nome_relatorio = $6
		  AND numero_relatorio = $7
		  AND page_skip = $8
	`

	_, err := r.pool.Exec(ctx, query, maxRetries, lastError, lastHTTPStatus,
		task.AnoMes, task.TipoInstituicao, task.NomeRelatorio, task.NumeroRelatorio, task.PageSkip)
	if err != nil {
		return fmt.Errorf("failed to requeue extract task: %w", err)
	}

	r.logger.Info("requeued extract task for retry",
		"ano_mes", task.AnoMes,
		"tipo_instituicao", task.TipoInstituicao,
		"nome_relatorio", task.NomeRelatorio,
		"page_skip", task.PageSkip)

	return nil
}

// IsExtractionComplete checks if all extract tasks for a report are complete
func (r *ExtractTaskRepository) IsExtractionComplete(ctx context.Context, anoMes, nomeRelatorio, numeroRelatorio string) (bool, error) {
	query := `
		SELECT COUNT(*) = 0
		FROM financial_data.bacen_ifdata_extract_task
		WHERE ano_mes = $1
		  AND nome_relatorio = $2
		  AND numero_relatorio = $3
		  AND status NOT IN ('SUCCESS', 'EMPTY', 'ERROR')
	`

	var isComplete bool
	err := r.pool.QueryRow(ctx, query, anoMes, nomeRelatorio, numeroRelatorio).Scan(&isComplete)
	if err != nil {
		return false, fmt.Errorf("failed to check extraction completion: %w", err)
	}

	return isComplete, nil
}

// CountByStatus counts tasks by status for a given quarter
func (r *ExtractTaskRepository) CountByStatus(ctx context.Context, anoMes string) (map[domain.ExtractTaskStatus]int, error) {
	query := `
		SELECT status, COUNT(*)
		FROM financial_data.bacen_ifdata_extract_task
		WHERE ano_mes = $1
		GROUP BY status
	`

	rows, err := r.pool.Query(ctx, query, anoMes)
	if err != nil {
		return nil, fmt.Errorf("failed to count tasks by status: %w", err)
	}
	defer rows.Close()

	counts := make(map[domain.ExtractTaskStatus]int)
	for rows.Next() {
		var status domain.ExtractTaskStatus
		var count int
		if err := rows.Scan(&status, &count); err != nil {
			return nil, fmt.Errorf("failed to scan status count: %w", err)
		}
		counts[status] = count
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating status counts: %w", err)
	}

	return counts, nil
}

// UpdateRowsProcessed updates the rows_processed count for a task
func (r *ExtractTaskRepository) UpdateRowsProcessed(ctx context.Context, task *domain.ExtractTask, rowsProcessed int) error {
	query := `
		UPDATE financial_data.bacen_ifdata_extract_task
		SET
			rows_processed = COALESCE(rows_processed, 0) + $1,
			updated_at = NOW()
		WHERE ano_mes = $2
		  AND tipo_instituicao = $3
		  AND nome_relatorio = $4
		  AND numero_relatorio = $5
		  AND page_skip = $6
	`

	_, err := r.pool.Exec(ctx, query, rowsProcessed,
		task.AnoMes, task.TipoInstituicao, task.NomeRelatorio, task.NumeroRelatorio, task.PageSkip)
	if err != nil {
		return fmt.Errorf("failed to update rows processed: %w", err)
	}

	return nil
}
