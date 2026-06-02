package database

import (
	"context"
	"errors"
	"log/slog"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
)

const (
	// upsertAssetSQL upserts one B3 asset, keyed on ticker.
	// The content_hash WHERE guard makes the UPDATE a no-op when data is unchanged.
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

// UpsertBatch upserts a slice of asset records in a single explicit transaction.
//
// Records are sorted by ticker before sending so that concurrent workers acquire
// row locks in a consistent order, reducing deadlock probability.
//
// ISIN handling:
//   - Intra-batch duplicates (two tickers in the same file sharing an ISIN) are
//     stripped in a single O(n) pass before the batch reaches the DB.
//   - Any residual conflict with an existing DB row (different ticker, same ISIN)
//     is resolved by stripping that specific ISIN and retrying — up to maxRetries.
//
// Retry behaviour:
//   - PostgreSQL deadlock (40P01): retried up to maxRetries with exponential backoff.
//   - Residual ISIN conflict (23505): strip conflicting ISIN and retry immediately.
func (r *AssetRepository) UpsertBatch(ctx context.Context, records []domain.AssetRecord) error {
	if len(records) == 0 {
		return nil
	}

	// Consistent lock order across concurrent workers.
	sort.Slice(records, func(i, j int) bool { return records[i].Ticker < records[j].Ticker })

	// Strip ISINs that appear more than once in this batch before hitting the DB.
	// This handles the common B3 data quality issue (same ISIN on two tickers in
	// the same file) without needing any round-trips.
	records = stripDuplicateISINs(records)

	const maxRetries = 3
	delay := 50 * time.Millisecond

	for attempt := range maxRetries + 1 {
		err := r.sendAssetBatchTx(ctx, records)
		if err == nil {
			return nil
		}

		switch {
		case isDeadlock(err) && attempt < maxRetries:
			r.logger.Warn("asset upsert deadlock, retrying",
				"attempt", attempt+1,
				"delay_ms", delay.Milliseconds(),
			)
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(delay):
			}
			delay *= 2

		case isISINConflict(err) && attempt < maxRetries:
			// Residual conflict: this ISIN is already owned by a different ticker in
			// the DB (e.g. from a previously processed chunk). Strip it and retry.
			records = stripISIN(records, extractConflictingISIN(err))

		default:
			return err
		}
	}
	return nil
}

func (r *AssetRepository) sendAssetBatchTx(ctx context.Context, records []domain.AssetRecord) error {
	conn, err := r.pool.Acquire(ctx)
	if err != nil {
		return err
	}
	defer conn.Release()

	tx, err := conn.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx) // no-op if Commit succeeds

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

	br := tx.SendBatch(ctx, batch)

	var firstErr error
	for range records {
		if _, execErr := br.Exec(); execErr != nil && firstErr == nil {
			firstErr = execErr
		}
	}
	br.Close()

	if firstErr != nil {
		return firstErr
	}
	return tx.Commit(ctx)
}

// FindTickerIDs returns a map of ticker → asset UUID for the given tickers.
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
// constraint (SQLSTATE 23505, constraint uq_investment_asset_isin_code).
func isISINConflict(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) &&
		pgErr.Code == "23505" &&
		pgErr.ConstraintName == "uq_investment_asset_isin_code"
}

// extractConflictingISIN parses the ISIN value from a pgconn.PgError detail string.
// PostgreSQL formats the detail as: "Key (isin_code)=(BR...) conflicts with existing row."
// Returns an empty string if the value cannot be extracted.
func extractConflictingISIN(err error) string {
	var pgErr *pgconn.PgError
	if !errors.As(err, &pgErr) {
		return ""
	}
	// Detail example: "Key (isin_code)=(BRPETRACNPR6) conflicts with existing row."
	detail := pgErr.Detail
	start := strings.Index(detail, ")=(")
	if start == -1 {
		return ""
	}
	start += 3
	end := strings.Index(detail[start:], ")")
	if end == -1 {
		return ""
	}
	return detail[start : start+end]
}

// stripDuplicateISINs nullifies any ISIN that appears on more than one record in
// the batch. Single O(n) pass: no DB round-trip required.
func stripDuplicateISINs(records []domain.AssetRecord) []domain.AssetRecord {
	counts := make(map[string]int, len(records))
	for _, r := range records {
		if r.IsinCode != "" {
			counts[r.IsinCode]++
		}
	}
	for i := range records {
		if records[i].IsinCode != "" && counts[records[i].IsinCode] > 1 {
			records[i].IsinCode = ""
		}
	}
	return records
}

// stripISIN sets IsinCode to "" for all records whose IsinCode matches target.
// If target is empty, strips all ISINs as a fallback.
func stripISIN(records []domain.AssetRecord, target string) []domain.AssetRecord {
	for i := range records {
		if target == "" || records[i].IsinCode == target {
			records[i].IsinCode = ""
		}
	}
	return records
}

