package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

const (
	findPendingMerchantDomainsSQL = `
		SELECT DISTINCT m.domain
		FROM banking.merchants m
		LEFT JOIN banking.merchant_logo_cache c ON c.domain = m.domain
		WHERE m.domain IS NOT NULL
		  AND m.domain <> ''
		  AND c.domain IS NULL
		ORDER BY m.domain ASC
		LIMIT CASE WHEN $1 = 0 THEN NULL ELSE $1 END
	`

	upsertMerchantLogoCacheSQL = `
		INSERT INTO banking.merchant_logo_cache (domain, asset_id, fetched_at)
		VALUES ($1, $2, NOW())
		ON CONFLICT (domain)
		DO UPDATE SET
			asset_id = EXCLUDED.asset_id,
			fetched_at = NOW()
	`
)

type MerchantRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewMerchantRepository(pool *pgxpool.Pool, logger *slog.Logger) *MerchantRepository {
	return &MerchantRepository{pool: pool, logger: logger}
}

func (r *MerchantRepository) FindPendingLogoHydration(ctx context.Context, limit int) ([]domain.PendingMerchantDomain, error) {
	rows, err := r.pool.Query(ctx, findPendingMerchantDomainsSQL, limit)
	if err != nil {
		return nil, fmt.Errorf("query pending merchant domains: %w", err)
	}
	defer rows.Close()

	var out []domain.PendingMerchantDomain
	for rows.Next() {
		var item domain.PendingMerchantDomain
		if err := rows.Scan(&item.Domain); err != nil {
			return nil, fmt.Errorf("scan pending merchant domain: %w", err)
		}
		out = append(out, item)
	}

	return out, rows.Err()
}

func (r *MerchantRepository) UpsertLogoAssetID(ctx context.Context, domainName string, assetID string) error {
	if _, err := r.pool.Exec(ctx, upsertMerchantLogoCacheSQL, domainName, assetID); err != nil {
		return fmt.Errorf("upsert merchant logo cache: %w", err)
	}
	return nil
}
