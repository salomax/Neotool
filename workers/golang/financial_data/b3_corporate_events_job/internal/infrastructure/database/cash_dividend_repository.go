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
	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/domain"
)

const upsertCashDividendSQL = `
	INSERT INTO financial_data.asset_cash_dividend
		(asset_id, isin_code, dividend_type, rate,
		 last_date_prior, payment_date, approved_on,
		 related_to, remarks, source, content_hash, created_at, updated_at)
	VALUES
		($1, $2, $3, $4, $5, $6, $7, $8, $9, 'B3', $10, NOW(), NOW())
	ON CONFLICT (isin_code, dividend_type, last_date_prior, payment_date, approved_on)
	DO UPDATE SET
		asset_id      = EXCLUDED.asset_id,
		rate          = EXCLUDED.rate,
		related_to    = EXCLUDED.related_to,
		remarks       = EXCLUDED.remarks,
		content_hash  = EXCLUDED.content_hash,
		updated_at    = NOW()
	WHERE financial_data.asset_cash_dividend.content_hash IS DISTINCT FROM EXCLUDED.content_hash
`

// CashDividendRepository persists cash dividend records.
type CashDividendRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewCashDividendRepository(pool *pgxpool.Pool, logger *slog.Logger) *CashDividendRepository {
	return &CashDividendRepository{pool: pool, logger: logger}
}

// UpsertBatch upserts a batch of cash dividend records using pgx.Batch.
// Records are sorted by (isin_code, last_date_prior) for consistent lock ordering.
// Retries on deadlock up to 3 times with exponential backoff.
func (r *CashDividendRepository) UpsertBatch(ctx context.Context, records []domain.CashDividendUpsertParam) error {
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
		r.logger.Warn("cash dividend upsert deadlock, retrying",
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

func (r *CashDividendRepository) sendBatch(ctx context.Context, records []domain.CashDividendUpsertParam) error {
	batch := &pgx.Batch{}
	for _, rec := range records {
		batch.Queue(upsertCashDividendSQL,
			rec.AssetID,
			rec.IsinCode,
			rec.DividendType,
			rec.Rate,
			rec.LastDatePrior,
			rec.PaymentDate,
			rec.ApprovedOn,
			rec.RelatedTo,
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

func isDeadlock(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "40P01"
}
