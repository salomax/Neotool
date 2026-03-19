package database

import (
	"context"
	"errors"
	"log/slog"
	"sort"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
)

// upsertPriceSQL inserts or updates one OHLCV row.
//
// Volume is passed as a raw int64 (VOLTOT with 2 implicit decimal places) and
// divided by 100 inside the statement — avoids float64 precision loss for large
// annual volumes (SQLSTATE 22003).
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

// UpsertBatch upserts a chunk of price rows in a single explicit transaction.
//
// Each call acquires a connection, opens BEGIN, sends all rows as a pipeline,
// then COMMITs. On deadlock (SQLSTATE 40P01) the transaction is rolled back and
// retried up to 3 times with exponential back-off. Because the chunk size is
// bounded (default 1000), the lock scope per transaction is small, which greatly
// reduces deadlock probability compared to sending one giant batch per file.
func (r *PriceRepository) UpsertBatch(ctx context.Context, params []domain.PriceUpsertParam) (inserted int, unchanged int, err error) {
	if len(params) == 0 {
		return 0, 0, nil
	}

	const maxRetries = 3
	delay := 50 * time.Millisecond

	for attempt := range maxRetries + 1 {
		ins, unc, batchErr := r.sendPriceBatchTx(ctx, params)
		if batchErr == nil {
			return ins, unc, nil
		}

		if isPriceDeadlock(batchErr) && attempt < maxRetries {
			r.logger.Warn("price upsert deadlock, retrying",
				"attempt", attempt+1,
				"delay_ms", delay.Milliseconds(),
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

// sendPriceBatchTx runs all params in one BEGIN…COMMIT transaction.
// On any error the transaction is rolled back (via defer) and the error returned.
func (r *PriceRepository) sendPriceBatchTx(ctx context.Context, params []domain.PriceUpsertParam) (inserted int, unchanged int, err error) {
	// Consistent lock order across concurrent workers: sort by (asset_id, price_date)
	// so all transactions acquire B-tree index page locks in the same sequence,
	// eliminating the cyclic dependency that causes deadlocks.
	sort.Slice(params, func(i, j int) bool {
		if params[i].AssetID != params[j].AssetID {
			return params[i].AssetID.String() < params[j].AssetID.String()
		}
		return params[i].PriceDate.Before(params[j].PriceDate)
	})

	conn, err := r.pool.Acquire(ctx)
	if err != nil {
		return 0, 0, err
	}
	defer conn.Release()

	tx, err := conn.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return 0, 0, err
	}
	defer tx.Rollback(ctx) // no-op if Commit succeeds

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

	br := tx.SendBatch(ctx, batch)

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
	br.Close()

	if firstErr != nil {
		return 0, 0, firstErr // defer tx.Rollback handles cleanup
	}
	if err := tx.Commit(ctx); err != nil {
		return 0, 0, err
	}
	return inserted, unchanged, nil
}

// isPriceDeadlock returns true for PostgreSQL deadlock errors (SQLSTATE 40P01).
func isPriceDeadlock(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "40P01"
}
