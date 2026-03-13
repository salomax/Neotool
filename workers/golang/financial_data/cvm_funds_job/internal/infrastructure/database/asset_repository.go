package database

import (
	"context"
	"errors"
	"log/slog"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/domain"
)

const (
	findAllFundHashesSQL = `
		SELECT fund_cnpj, COALESCE(content_hash, '') AS content_hash
		FROM financial_data.investment_asset
		WHERE source = 'CVM'
		  AND fund_cnpj IS NOT NULL
	`

	findAllActiveFundIDsSQL = `
		SELECT fund_cnpj, id
		FROM financial_data.investment_asset
		WHERE source = 'CVM'
		  AND fund_cnpj IS NOT NULL
	`

	upsertFundSQL = `
		INSERT INTO financial_data.investment_asset
			(id, name, asset_type, fund_cnpj, is_active, source, content_hash,
			 currency_code, lot_size, price_factor, created_at, updated_at)
		VALUES
			(gen_random_uuid(), $1, $2, $3, $4, 'CVM', $5,
			 'BRL', 1, 1, NOW(), NOW())
		ON CONFLICT (fund_cnpj)
		DO UPDATE SET
			name         = EXCLUDED.name,
			asset_type   = EXCLUDED.asset_type,
			is_active    = EXCLUDED.is_active,
			content_hash = EXCLUDED.content_hash,
			updated_at   = NOW()
		WHERE financial_data.investment_asset.content_hash IS DISTINCT FROM EXCLUDED.content_hash
	`
)

// AssetRepository handles investment_asset persistence.
type AssetRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewAssetRepository(pool *pgxpool.Pool, logger *slog.Logger) *AssetRepository {
	return &AssetRepository{pool: pool, logger: logger}
}

// FindAllFundHashes returns a map of fund_cnpj → content_hash for all CVM assets.
// Used by CatalogIngester to detect which records need updating before task submission.
func (r *AssetRepository) FindAllFundHashes(ctx context.Context) (map[string]string, error) {
	rows, err := r.pool.Query(ctx, findAllFundHashesSQL)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make(map[string]string)
	for rows.Next() {
		var cnpj, hash string
		if err := rows.Scan(&cnpj, &hash); err != nil {
			return nil, err
		}
		result[cnpj] = hash
	}
	return result, rows.Err()
}

// FindAllActiveFundIDs returns a map of fund_cnpj → asset UUID for all CVM funds.
// Loaded once by NAVDispatcher; shared read-only across all NAVMonthTasks.
func (r *AssetRepository) FindAllActiveFundIDs(ctx context.Context) (map[string]uuid.UUID, error) {
	rows, err := r.pool.Query(ctx, findAllActiveFundIDsSQL)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make(map[string]uuid.UUID)
	for rows.Next() {
		var cnpj string
		var id uuid.UUID
		if err := rows.Scan(&cnpj, &id); err != nil {
			return nil, err
		}
		result[cnpj] = id
	}
	return result, rows.Err()
}

// UpsertFundBatch upserts a slice of fund records using pgx.Batch.
// Only rows where content_hash differs from the stored value are written (SQL guard).
func (r *AssetRepository) UpsertFundBatch(ctx context.Context, records []domain.FundRecord) error {
	if len(records) == 0 {
		return nil
	}

	batch := &pgx.Batch{}
	for _, rec := range records {
		batch.Queue(upsertFundSQL, rec.Name, rec.AssetType, rec.CNPJ, rec.IsActive, rec.ContentHash)
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

// IsFundNotFound returns true when the error indicates a missing row.
func IsFundNotFound(err error) bool {
	return errors.Is(err, pgx.ErrNoRows)
}
