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

// LoadControlRepository handles load control tracking
type LoadControlRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewLoadControlRepository creates a new load control repository
func NewLoadControlRepository(pool *pgxpool.Pool, logger *slog.Logger) *LoadControlRepository {
	return &LoadControlRepository{
		pool:   pool,
		logger: logger,
	}
}

// InitializeLoadControl creates or gets load control record for a report
func (r *LoadControlRepository) InitializeLoadControl(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
) (*domain.LoadControl, error) {
	query := `
		INSERT INTO financial_data.bacen_ifdata_load_control (
			ano_mes, nome_relatorio, extracted, promoted, completed
		) VALUES ($1, $2, false, false, false)
		ON CONFLICT (ano_mes, nome_relatorio) DO NOTHING
		RETURNING ano_mes, nome_relatorio, extracted, promoted, completed, created_at, updated_at
	`

	lc := &domain.LoadControl{}
	err := r.pool.QueryRow(ctx, query, anoMes, nomeRelatorio).Scan(
		&lc.AnoMes, &lc.NomeRelatorio, &lc.Extracted, &lc.Promoted, &lc.Completed,
		&lc.CreatedAt, &lc.UpdatedAt,
	)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			// Already exists, fetch it
			return r.GetLoadControl(ctx, anoMes, nomeRelatorio)
		}
		return nil, fmt.Errorf("failed to initialize load control: %w", err)
	}

	r.logger.Debug("initialized load control",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio)

	return lc, nil
}

// GetLoadControl retrieves load control record
func (r *LoadControlRepository) GetLoadControl(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
) (*domain.LoadControl, error) {
	query := `
		SELECT
			ano_mes, nome_relatorio, extracted, promoted, completed,
			created_at, updated_at
		FROM financial_data.bacen_ifdata_load_control
		WHERE ano_mes = $1 AND nome_relatorio = $2
	`

	lc := &domain.LoadControl{}
	err := r.pool.QueryRow(ctx, query, anoMes, nomeRelatorio).Scan(
		&lc.AnoMes, &lc.NomeRelatorio, &lc.Extracted, &lc.Promoted, &lc.Completed,
		&lc.CreatedAt, &lc.UpdatedAt,
	)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("load control not found for %s/%s", anoMes, nomeRelatorio)
		}
		return nil, fmt.Errorf("failed to get load control: %w", err)
	}

	return lc, nil
}

// MarkExtracted marks extraction as complete for a report
func (r *LoadControlRepository) MarkExtracted(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
) error {
	query := `
		UPDATE financial_data.bacen_ifdata_load_control
		SET extracted = true, updated_at = NOW()
		WHERE ano_mes = $1 AND nome_relatorio = $2
	`

	result, err := r.pool.Exec(ctx, query, anoMes, nomeRelatorio)
	if err != nil {
		return fmt.Errorf("failed to mark extracted: %w", err)
	}

	if result.RowsAffected() == 0 {
		return fmt.Errorf("load control not found for %s/%s", anoMes, nomeRelatorio)
	}

	r.logger.Info("marked extraction complete",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio)

	return nil
}

// MarkPromoted marks promotion as complete for a report
func (r *LoadControlRepository) MarkPromoted(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
) error {
	query := `
		UPDATE financial_data.bacen_ifdata_load_control
		SET promoted = true, updated_at = NOW()
		WHERE ano_mes = $1 AND nome_relatorio = $2
	`

	result, err := r.pool.Exec(ctx, query, anoMes, nomeRelatorio)
	if err != nil {
		return fmt.Errorf("failed to mark promoted: %w", err)
	}

	if result.RowsAffected() == 0 {
		return fmt.Errorf("load control not found for %s/%s", anoMes, nomeRelatorio)
	}

	r.logger.Info("marked promotion complete",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio)

	return nil
}

// MarkCompleted marks the entire load as complete for a report
func (r *LoadControlRepository) MarkCompleted(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
) error {
	query := `
		UPDATE financial_data.bacen_ifdata_load_control
		SET completed = true, updated_at = NOW()
		WHERE ano_mes = $1 AND nome_relatorio = $2
	`

	result, err := r.pool.Exec(ctx, query, anoMes, nomeRelatorio)
	if err != nil {
		return fmt.Errorf("failed to mark completed: %w", err)
	}

	if result.RowsAffected() == 0 {
		return fmt.Errorf("load control not found for %s/%s", anoMes, nomeRelatorio)
	}

	r.logger.Info("marked load complete",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio)

	return nil
}

// MarkQuarterCompleted marks all reports for a quarter as complete
func (r *LoadControlRepository) MarkQuarterCompleted(ctx context.Context, anoMes string) error {
	query := `
		UPDATE financial_data.bacen_ifdata_load_control
		SET completed = true, updated_at = NOW()
		WHERE ano_mes = $1 AND extracted = true AND promoted = true
	`

	result, err := r.pool.Exec(ctx, query, anoMes)
	if err != nil {
		return fmt.Errorf("failed to mark quarter completed: %w", err)
	}

	rowsAffected := result.RowsAffected()
	r.logger.Info("marked quarter complete",
		"ano_mes", anoMes,
		"reports_completed", rowsAffected)

	return nil
}

// IsQuarterCompleted checks if all reports for a quarter are complete
func (r *LoadControlRepository) IsQuarterCompleted(ctx context.Context, anoMes string) (bool, error) {
	query := `
		SELECT COUNT(*) = 0
		FROM financial_data.bacen_ifdata_load_control
		WHERE ano_mes = $1 AND completed = false
	`

	var isComplete bool
	err := r.pool.QueryRow(ctx, query, anoMes).Scan(&isComplete)
	if err != nil {
		return false, fmt.Errorf("failed to check quarter completion: %w", err)
	}

	return isComplete, nil
}

// GetCompletionStats returns completion statistics for a quarter
func (r *LoadControlRepository) GetCompletionStats(ctx context.Context, anoMes string) (map[string]int, error) {
	query := `
		SELECT
			COUNT(*) as total,
			COUNT(*) FILTER (WHERE extracted = true) as extracted,
			COUNT(*) FILTER (WHERE promoted = true) as promoted,
			COUNT(*) FILTER (WHERE completed = true) as completed
		FROM financial_data.bacen_ifdata_load_control
		WHERE ano_mes = $1
	`

	var total, extracted, promoted, completed int
	err := r.pool.QueryRow(ctx, query, anoMes).Scan(&total, &extracted, &promoted, &completed)
	if err != nil {
		return nil, fmt.Errorf("failed to get completion stats: %w", err)
	}

	stats := map[string]int{
		"total":     total,
		"extracted": extracted,
		"promoted":  promoted,
		"completed": completed,
	}

	return stats, nil
}
