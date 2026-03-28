package database

import (
	"context"
	"log/slog"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
)

const findAssetsByIsinSQL = `
	SELECT isin_code, id
	FROM financial_data.investment_asset
	WHERE source = 'B3'
	  AND isin_code IS NOT NULL
`

// AssetLookupRepository provides a bulk isin_code → UUID map loaded once at startup.
type AssetLookupRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewAssetLookupRepository(pool *pgxpool.Pool, logger *slog.Logger) *AssetLookupRepository {
	return &AssetLookupRepository{pool: pool, logger: logger}
}

// FindAllByIsinCode returns a map of isin_code → asset UUID for all B3 assets.
// Loaded once by the dispatcher and shared read-only across all company tasks.
func (r *AssetLookupRepository) FindAllByIsinCode(ctx context.Context) (map[string]uuid.UUID, error) {
	rows, err := r.pool.Query(ctx, findAssetsByIsinSQL)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make(map[string]uuid.UUID)
	for rows.Next() {
		var isin string
		var id uuid.UUID
		if err := rows.Scan(&isin, &id); err != nil {
			return nil, err
		}
		result[isin] = id
	}
	return result, rows.Err()
}
