package database

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
)

const upsertPriceSQL = `
	INSERT INTO financial_data.asset_daily_price
		(id, asset_id, price_date, open_price, high_price, low_price, close_price,
		 avg_price, volume, trades, source, content_hash, created_at)
	VALUES
		(gen_random_uuid(), $1, $2, $3, $4, $5, $6,
		 $7, $8, $9, $10, $11, NOW())
	ON CONFLICT (asset_id, price_date)
	DO UPDATE SET
		open_price   = EXCLUDED.open_price,
		high_price   = EXCLUDED.high_price,
		low_price    = EXCLUDED.low_price,
		close_price  = EXCLUDED.close_price,
		avg_price    = EXCLUDED.avg_price,
		volume       = EXCLUDED.volume,
		trades       = EXCLUDED.trades,
		content_hash = EXCLUDED.content_hash
	WHERE financial_data.asset_daily_price.content_hash IS DISTINCT FROM EXCLUDED.content_hash
`

// PriceRepository handles asset_daily_price persistence for B3 OHLCV data.
type PriceRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewPriceRepository(pool *pgxpool.Pool, logger *slog.Logger) *PriceRepository {
	return &PriceRepository{pool: pool, logger: logger}
}

// UpsertBatch submits a pgx.Batch for the given price params.
// Returns counts of rows actually written (inserted/updated) vs skipped (hash unchanged).
func (r *PriceRepository) UpsertBatch(ctx context.Context, params []domain.PriceUpsertParam) (inserted int, unchanged int, err error) {
	if len(params) == 0 {
		return 0, 0, nil
	}

	batch := &pgx.Batch{}
	for _, p := range params {
		batch.Queue(upsertPriceSQL,
			p.AssetID,
			p.PriceDate,
			p.OpenPrice,
			p.HighPrice,
			p.LowPrice,
			p.ClosePrice,
			p.AvgPrice,
			p.Volume,
			p.Trades,
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
