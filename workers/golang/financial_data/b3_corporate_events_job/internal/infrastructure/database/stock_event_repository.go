package database

import (
	"context"
	"log/slog"
	"sort"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/domain"
)

const upsertStockEventSQL = `
	INSERT INTO financial_data.asset_stock_event
		(asset_id, isin_code, event_type, factor, price_unit,
		 trading_start, trading_end, subscription_date,
		 last_date_prior, approved_on, remarks, source, content_hash, created_at, updated_at)
	VALUES
		($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, 'B3', $12, NOW(), NOW())
	ON CONFLICT (isin_code, event_type, last_date_prior, approved_on)
	DO UPDATE SET
		asset_id          = EXCLUDED.asset_id,
		factor            = EXCLUDED.factor,
		price_unit        = EXCLUDED.price_unit,
		trading_start     = EXCLUDED.trading_start,
		trading_end       = EXCLUDED.trading_end,
		subscription_date = EXCLUDED.subscription_date,
		remarks           = EXCLUDED.remarks,
		content_hash      = EXCLUDED.content_hash,
		updated_at        = NOW()
	WHERE financial_data.asset_stock_event.content_hash IS DISTINCT FROM EXCLUDED.content_hash
`

// StockEventRepository persists stock event records (splits, bonuses, subscriptions).
type StockEventRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewStockEventRepository(pool *pgxpool.Pool, logger *slog.Logger) *StockEventRepository {
	return &StockEventRepository{pool: pool, logger: logger}
}

// UpsertBatch upserts a batch of stock events using pgx.Batch.
func (r *StockEventRepository) UpsertBatch(ctx context.Context, records []domain.StockEventUpsertParam) error {
	if len(records) == 0 {
		return nil
	}

	sort.Slice(records, func(i, j int) bool {
		if records[i].IsinCode != records[j].IsinCode {
			return records[i].IsinCode < records[j].IsinCode
		}
		return records[i].LastDatePrior.Before(records[j].LastDatePrior)
	})

	const maxRetries = 3
	delay := 50 * time.Millisecond

	for attempt := range maxRetries + 1 {
		err := r.sendBatch(ctx, records)
		if err == nil {
			return nil
		}
		if !isDeadlock(err) || attempt == maxRetries {
			return err
		}
		r.logger.Warn("stock event upsert deadlock, retrying",
			"attempt", attempt+1, "delay", delay)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(delay):
		}
		delay *= 2
	}
	return nil
}

func (r *StockEventRepository) sendBatch(ctx context.Context, records []domain.StockEventUpsertParam) error {
	batch := &pgx.Batch{}
	for _, rec := range records {
		batch.Queue(upsertStockEventSQL,
			rec.AssetID,
			rec.IsinCode,
			rec.EventType,
			rec.Factor,
			rec.PriceUnit,
			rec.TradingStart,
			rec.TradingEnd,
			rec.SubscriptionDate,
			rec.LastDatePrior,
			rec.ApprovedOn,
			rec.Remarks,
			rec.ContentHash,
		)
	}

	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()

	var firstErr error
	for range records {
		if _, execErr := br.Exec(); execErr != nil && firstErr == nil {
			firstErr = execErr
		}
	}
	return firstErr
}
