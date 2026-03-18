package database

import (
	"context"
	"errors"
	"log/slog"
	"sort"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
)

const (
	// upsertAssetSQL upserts one B3 asset, keyed on ticker.
	// The content_hash WHERE guard makes the UPDATE a no-op when data is unchanged,
	// avoiding spurious updated_at writes on re-runs.
	upsertAssetSQL = `
		INSERT INTO financial_data.investment_asset
			(id, ticker, isin_code, name, asset_type, exchange, currency_code,
			 lot_size, price_factor, is_active, source, content_hash, created_at, updated_at)
		VALUES
			(gen_random_uuid(), $1, $2, $3, $4, $5, $6,
			 $7, $8, TRUE, $9, $10, NOW(), NOW())
		ON CONFLICT (ticker)
		DO UPDATE SET
			isin_code    = EXCLUDED.isin_code,
			name         = EXCLUDED.name,
			asset_type   = EXCLUDED.asset_type,
			lot_size     = EXCLUDED.lot_size,
			is_active    = TRUE,
			content_hash = EXCLUDED.content_hash,
			updated_at   = NOW()
		WHERE financial_data.investment_asset.content_hash IS DISTINCT FROM EXCLUDED.content_hash
	`

	// findTickerIDsSQL loads ticker→UUID for a set of tickers in one query.
	// Used after the catalog upsert to build the in-memory map for price inserts.
	findTickerIDsSQL = `
		SELECT ticker, id
		FROM financial_data.investment_asset
		WHERE source = $1
		  AND ticker = ANY($2)
	`
)

// AssetRepository handles investment_asset persistence for B3 assets.
type AssetRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewAssetRepository(pool *pgxpool.Pool, logger *slog.Logger) *AssetRepository {
	return &AssetRepository{pool: pool, logger: logger}
}

// UpsertBatch upserts a slice of asset records using pgx.Batch.
// Records are sorted by ticker before sending to ensure a consistent
// lock-acquisition order across concurrent workers, reducing deadlock probability.
//
// Retry behaviour:
//   - PostgreSQL deadlock (40P01): retried up to 3 times with exponential backoff.
//   - ISIN unique constraint (23505 on uq_investment_asset_isin_code): the batch is
//     retried once with all ISIN codes set to NULL. This is a safety net for the rare
//     case where two non-option assets share an ISIN (e.g. a ticker change mid-year).
//     Options never carry an ISIN — that is enforced upstream in collectAssets.
func (r *AssetRepository) UpsertBatch(ctx context.Context, records []domain.AssetRecord) error {
	if len(records) == 0 {
		return nil
	}

	// Consistent lock order across concurrent workers.
	sort.Slice(records, func(i, j int) bool { return records[i].Ticker < records[j].Ticker })

	const maxRetries = 3
	delay := 50 * time.Millisecond
	isinStripped := false

	for attempt := range maxRetries + 1 {
		err := r.sendAssetBatch(ctx, records)
		if err == nil {
			return nil
		}

		switch {
		case isDeadlock(err) && attempt < maxRetries:
			r.logger.Warn("asset upsert deadlock, retrying",
				"attempt", attempt+1,
				"delay", delay,
			)
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(delay):
			}
			delay *= 2

		case isISINConflict(err) && !isinStripped:
			// Strip all ISINs from the batch and retry once. This happens when two
			// assets in the same batch have the same ISIN (data quality issue in
			// COTAHIST). Storing NULL is safe — tickers remain the primary key.
			r.logger.Warn("asset upsert ISIN conflict, retrying with null ISINs",
				"records", len(records),
			)
			stripped := make([]domain.AssetRecord, len(records))
			copy(stripped, records)
			for i := range stripped {
				stripped[i].IsinCode = ""
			}
			records = stripped
			isinStripped = true

		default:
			return err
		}
	}
	return nil
}

func (r *AssetRepository) sendAssetBatch(ctx context.Context, records []domain.AssetRecord) error {
	batch := &pgx.Batch{}
	for _, rec := range records {
		// Store empty ISIN as NULL to avoid unique-constraint conflicts on "".
		var isin *string
		if rec.IsinCode != "" {
			isin = &rec.IsinCode
		}
		batch.Queue(upsertAssetSQL,
			rec.Ticker,
			isin,
			rec.Name,
			rec.AssetType,
			domain.ExchangeB3,
			domain.CurrencyBRL,
			rec.LotSize,
			domain.PriceFactor,
			domain.SourceB3,
			rec.ContentHash,
		)
	}

	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()

	// Drain every result even on error — leaving unread results corrupts the connection.
	var firstErr error
	for range records {
		if _, execErr := br.Exec(); execErr != nil && firstErr == nil {
			firstErr = execErr
		}
	}
	return firstErr
}

// FindTickerIDs returns a map of ticker → asset UUID for the given tickers.
// Called after UpsertBatch so the map includes newly-inserted rows.
func (r *AssetRepository) FindTickerIDs(ctx context.Context, tickers []string) (map[string]uuid.UUID, error) {
	if len(tickers) == 0 {
		return map[string]uuid.UUID{}, nil
	}

	rows, err := r.pool.Query(ctx, findTickerIDsSQL, domain.SourceB3, tickers)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make(map[string]uuid.UUID, len(tickers))
	for rows.Next() {
		var ticker string
		var id uuid.UUID
		if err := rows.Scan(&ticker, &id); err != nil {
			return nil, err
		}
		result[ticker] = id
	}
	return result, rows.Err()
}

// isDeadlock returns true for PostgreSQL deadlock errors (SQLSTATE 40P01).
func isDeadlock(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "40P01"
}

// isISINConflict returns true when the error is a unique-violation on the ISIN
// constraint specifically (SQLSTATE 23505, constraint uq_investment_asset_isin_code).
func isISINConflict(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) &&
		pgErr.Code == "23505" &&
		pgErr.ConstraintName == "uq_investment_asset_isin_code"
}
