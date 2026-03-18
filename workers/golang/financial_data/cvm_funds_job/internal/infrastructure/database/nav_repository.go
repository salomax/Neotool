package database

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/domain"
)

const upsertNavSQL = `
	INSERT INTO financial_data.asset_daily_price
		(id, asset_id, price_date, close_price, volume, source, content_hash, created_at)
	VALUES
		(gen_random_uuid(), $1, $2, $3, $4, $5, $6, NOW())
	ON CONFLICT (asset_id, price_date)
	DO UPDATE SET
		close_price  = EXCLUDED.close_price,
		volume       = EXCLUDED.volume,
		content_hash = EXCLUDED.content_hash
	WHERE financial_data.asset_daily_price.content_hash IS DISTINCT FROM EXCLUDED.content_hash
`

// NavRepository handles asset_daily_price persistence.
type NavRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewNavRepository(pool *pgxpool.Pool, logger *slog.Logger) *NavRepository {
	return &NavRepository{pool: pool, logger: logger}
}

// UpsertNavBatch submits a pgx.Batch for the given NAV params.
// Returns counts of rows actually written (inserted/updated) vs skipped (hash unchanged).
func (r *NavRepository) UpsertNavBatch(ctx context.Context, params []domain.NavUpsertParam) (inserted int, unchanged int, err error) {
	if len(params) == 0 {
		return 0, 0, nil
	}

	batch := &pgx.Batch{}
	for _, p := range params {
		batch.Queue(upsertNavSQL,
			p.AssetID,
			p.PriceDate,
			p.ClosePrice,
			p.TotalNetAssets,
			p.Source,
			p.ContentHash,
		)
	}

	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()

	// Drain every result even on error — leaving unread results corrupts the connection.
	var firstErr error
	for range params {
		tag, execErr := br.Exec()
		if execErr != nil {
			if firstErr == nil {
				firstErr = execErr
			}
			continue
		}
		if tag.RowsAffected() > 0 {
			inserted++
		} else {
			unchanged++
		}
	}
	return inserted, unchanged, firstErr
}
