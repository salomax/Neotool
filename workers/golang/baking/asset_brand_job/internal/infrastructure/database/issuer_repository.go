package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

const (
	findPendingIssuersSQL = `
		SELECT id, name, domain
		FROM banking.issuers
		WHERE logo_key IS NULL
		  AND domain IS NOT NULL
		ORDER BY name ASC
		LIMIT $1
	`

	updateIssuerLogoKeySQL = `
		UPDATE banking.issuers
		SET logo_key = $2
		WHERE id = $1
	`
)

type IssuerRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewIssuerRepository(pool *pgxpool.Pool, logger *slog.Logger) *IssuerRepository {
	return &IssuerRepository{pool: pool, logger: logger}
}

func (r *IssuerRepository) FindPendingLogoHydration(ctx context.Context, limit int) ([]domain.PendingIssuer, error) {
	rows, err := r.pool.Query(ctx, findPendingIssuersSQL, limit)
	if err != nil {
		return nil, fmt.Errorf("query pending issuers: %w", err)
	}
	defer rows.Close()

	var issuers []domain.PendingIssuer
	for rows.Next() {
		var issuer domain.PendingIssuer
		if err := rows.Scan(&issuer.ID, &issuer.Name, &issuer.Domain); err != nil {
			return nil, fmt.Errorf("scan pending issuer: %w", err)
		}
		issuers = append(issuers, issuer)
	}

	return issuers, rows.Err()
}

func (r *IssuerRepository) UpdateLogoKey(ctx context.Context, id uuid.UUID, logoKey string) error {
	tag, err := r.pool.Exec(ctx, updateIssuerLogoKeySQL, id, logoKey)
	if err != nil {
		return fmt.Errorf("update issuer logo_key: %w", err)
	}
	if tag.RowsAffected() != 1 {
		return fmt.Errorf("update issuer logo_key: issuer %s not found", id)
	}
	return nil
}
