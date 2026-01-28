package repository

import (
	"context"
	"encoding/json"
	"log/slog"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/domain"
)

// ValueRepository persists indicator values
type ValueRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewValueRepository(pool *pgxpool.Pool, logger *slog.Logger) *ValueRepository {
	return &ValueRepository{pool: pool, logger: logger}
}

// UpsertValues upserts indicator values
func (r *ValueRepository) UpsertValues(ctx context.Context, values []domain.ValueRecord) error {
	if len(values) == 0 {
		return nil
	}

	batch := &pgx.Batch{}
	query := `
		INSERT INTO financial_data.indicator_value (
			indicator_variation_id,
			reference_date,
			publication_date,
			value,
			source_meta
		) VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (indicator_variation_id, reference_date)
		DO UPDATE SET
			publication_date = EXCLUDED.publication_date,
			value = EXCLUDED.value,
			source_meta = EXCLUDED.source_meta,
			updated_at = now()
	`

	for _, v := range values {
		meta, _ := json.Marshal(v.SourceMeta)
		batch.Queue(query, v.IndicatorVariationID, v.ReferenceDate, v.PublicationDate, v.Value, meta)
	}

	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()

	for range values {
		if _, err := br.Exec(); err != nil {
			return err
		}
	}

	return nil
}
