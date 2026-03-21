package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

const findPendingAssetsSQL = `
	SELECT
		ia.id,
		ia.ticker,
		ia.name
	FROM financial_data.investment_asset ia
	WHERE ia.source = 'B3'
	  AND ia.ticker IS NOT NULL
	  AND ia.asset_type IN ('EQUITY', 'BDR', 'FII')
	  AND NOT EXISTS (
	    SELECT 1
	    FROM financial_data.investment_asset_brand_match m
	    WHERE m.investment_asset_id = ia.id
	  )
	ORDER BY ia.ticker ASC
	LIMIT CASE WHEN $1 = 0 THEN NULL ELSE $1 END
`

type AssetRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewAssetRepository(pool *pgxpool.Pool, logger *slog.Logger) *AssetRepository {
	return &AssetRepository{pool: pool, logger: logger}
}

func (r *AssetRepository) FindPendingBrandHydration(ctx context.Context, limit int) ([]domain.PendingAsset, error) {
	rows, err := r.pool.Query(ctx, findPendingAssetsSQL, limit)
	if err != nil {
		return nil, fmt.Errorf("query pending assets: %w", err)
	}
	defer rows.Close()

	var assets []domain.PendingAsset
	for rows.Next() {
		var asset domain.PendingAsset
		var id uuid.UUID
		if err := rows.Scan(&id, &asset.Ticker, &asset.Name); err != nil {
			return nil, fmt.Errorf("scan pending asset: %w", err)
		}
		asset.ID = id
		assets = append(assets, asset)
	}

	return assets, rows.Err()
}
