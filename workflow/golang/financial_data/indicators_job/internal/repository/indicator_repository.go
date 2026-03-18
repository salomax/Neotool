package repository

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
)

// IndicatorRepository provides access to indicator definitions
type IndicatorRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewIndicatorRepository(pool *pgxpool.Pool, logger *slog.Logger) *IndicatorRepository {
	return &IndicatorRepository{pool: pool, logger: logger}
}

// ListActiveVariations returns all active indicator variations
func (r *IndicatorRepository) ListActiveVariations(ctx context.Context) ([]domain.IndicatorVariation, error) {
	query := `
		SELECT
			iv.id,
			iv.indicator_id,
			iv.label,
			COALESCE(iv.unit, ''),
			iv.is_default,
			iv.incremental_months,
			iv.incremental_years,
			iv.incremental_days,
			i.label,
			i.issuer,
			i.frequency
		FROM financial_data.economic_indicator_variation iv
		JOIN financial_data.economic_indicator i ON i.id = iv.indicator_id
		WHERE i.is_active = true
	`

	rows, err := r.pool.Query(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	variations := make([]domain.IndicatorVariation, 0)
	for rows.Next() {
		var v domain.IndicatorVariation
		if err := rows.Scan(
			&v.ID,
			&v.IndicatorID,
			&v.Label,
			&v.Unit,
			&v.IsDefault,
			&v.IncrementalMonths,
			&v.IncrementalYears,
			&v.IncrementalDays,
			&v.IndicatorLabel,
			&v.IndicatorIssuer,
			&v.IndicatorFrequency,
		); err != nil {
			return nil, err
		}
		variations = append(variations, v)
	}

	if err := rows.Err(); err != nil {
		return nil, err
	}

	return variations, nil
}
