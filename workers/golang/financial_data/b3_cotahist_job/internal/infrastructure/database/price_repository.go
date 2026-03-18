package database

import (
	"context"
	"errors"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
)

// upsertPriceSQL inserts or updates one OHLCV row.
//
// Volume is passed as a raw int64 (VOLTOT with 2 implicit decimal places) and
// divided by 100 inside the statement — this avoids float64 precision loss that
// caused SQLSTATE 22003 (numeric field overflow) for large annual volumes.
const upsertPriceSQL = `
	INSERT INTO financial_data.asset_daily_price
		(id, asset_id, price_date, open_price, high_price, low_price, close_price,
		 avg_price, volume, trades, source, content_hash, created_at)
	VALUES
		(gen_random_uuid(), $1, $2, $3, $4, $5, $6,
		 $7, $8::numeric / 100, $9, $10, $11, NOW())
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
//
// Deadlock handling: concurrent workers writing prices for overlapping assets can
// deadlock (SQLSTATE 40P01). The batch is retried up to 3 times with exponential
// backoff — same strategy as AssetRepository.UpsertBatch.
func (r *PriceRepository) UpsertBatch(ctx context.Context, params []domain.PriceUpsertParam) (inserted int, unchanged int, err error) {
	if len(params) == 0 {
		return 0, 0, nil
	}

	const maxRetries = 3
	delay := 50 * time.Millisecond

	for attempt := range maxRetries + 1 {
		ins, unc, batchErr := r.sendPriceBatch(ctx, params)
		if batchErr == nil {
			return ins, unc, nil
		}

		if isPriceDeadlock(batchErr) && attempt < maxRetries {
			r.logger.Warn("price upsert deadlock, retrying",
				"attempt", attempt+1,
				"delay", delay,
			)
			select {
			case <-ctx.Done():
				return 0, 0, ctx.Err()
			case <-time.After(delay):
			}
			delay *= 2
			continue
		}

		return 0, 0, batchErr
	}
	return 0, 0, nil
}

func (r *PriceRepository) sendPriceBatch(ctx context.Context, params []domain.PriceUpsertParam) (inserted int, unchanged int, err error) {
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

// isPriceDeadlock returns true for PostgreSQL deadlock errors (SQLSTATE 40P01).
func isPriceDeadlock(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "40P01"
}
