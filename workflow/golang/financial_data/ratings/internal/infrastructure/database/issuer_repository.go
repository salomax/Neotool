package database

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// IssuerRepository implements domain.IssuerRepository
type IssuerRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewIssuerRepository creates a new issuer repository
func NewIssuerRepository(pool *pgxpool.Pool, logger *slog.Logger) *IssuerRepository {
	return &IssuerRepository{
		pool:   pool,
		logger: logger.With("repository", "issuer"),
	}
}

// GetEnabled retrieves all enabled issuers
func (r *IssuerRepository) GetEnabled(ctx context.Context) ([]domain.RatingIssuer, error) {
	query := `
		SELECT id, code, display_name, website, config, logo_asset_id, enabled, created_at, updated_at
		FROM financial_data.rating_issuer
		WHERE enabled = true
		ORDER BY code
	`

	rows, err := r.pool.Query(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to query enabled issuers: %w", err)
	}
	defer rows.Close()

	var issuers []domain.RatingIssuer
	for rows.Next() {
		issuer, err := r.scanIssuer(rows)
		if err != nil {
			return nil, err
		}
		issuers = append(issuers, issuer)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating issuer rows: %w", err)
	}

	r.logger.Debug("fetched enabled issuers", "count", len(issuers))
	return issuers, nil
}

// GetByCode retrieves an issuer by code
func (r *IssuerRepository) GetByCode(ctx context.Context, code string) (*domain.RatingIssuer, error) {
	query := `
		SELECT id, code, display_name, website, config, logo_asset_id, enabled, created_at, updated_at
		FROM financial_data.rating_issuer
		WHERE code = $1
	`

	row := r.pool.QueryRow(ctx, query, code)
	issuer, err := r.scanIssuerRow(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, domain.ErrIssuerNotFound
		}
		return nil, fmt.Errorf("failed to get issuer by code: %w", err)
	}

	return &issuer, nil
}

// GetByID retrieves an issuer by ID
func (r *IssuerRepository) GetByID(ctx context.Context, id uuid.UUID) (*domain.RatingIssuer, error) {
	query := `
		SELECT id, code, display_name, website, config, logo_asset_id, enabled, created_at, updated_at
		FROM financial_data.rating_issuer
		WHERE id = $1
	`

	row := r.pool.QueryRow(ctx, query, id)
	issuer, err := r.scanIssuerRow(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, domain.ErrIssuerNotFound
		}
		return nil, fmt.Errorf("failed to get issuer by id: %w", err)
	}

	return &issuer, nil
}

// scanIssuer scans a row into a RatingIssuer
func (r *IssuerRepository) scanIssuer(rows pgx.Rows) (domain.RatingIssuer, error) {
	var issuer domain.RatingIssuer
	var configJSON []byte
	var website *string
	var logoAssetID *uuid.UUID

	err := rows.Scan(
		&issuer.ID,
		&issuer.Code,
		&issuer.DisplayName,
		&website,
		&configJSON,
		&logoAssetID,
		&issuer.Enabled,
		&issuer.CreatedAt,
		&issuer.UpdatedAt,
	)
	if err != nil {
		return issuer, fmt.Errorf("failed to scan issuer: %w", err)
	}

	if website != nil {
		issuer.Website = *website
	}
	issuer.LogoAssetID = logoAssetID

	// Parse config JSON
	if len(configJSON) > 0 {
		if err := json.Unmarshal(configJSON, &issuer.Config); err != nil {
			r.logger.Warn("failed to parse issuer config JSON",
				"issuer_code", issuer.Code,
				"error", err,
			)
		}
	}

	return issuer, nil
}

// scanIssuerRow scans a single row into a RatingIssuer
func (r *IssuerRepository) scanIssuerRow(row pgx.Row) (domain.RatingIssuer, error) {
	var issuer domain.RatingIssuer
	var configJSON []byte
	var website *string
	var logoAssetID *uuid.UUID

	err := row.Scan(
		&issuer.ID,
		&issuer.Code,
		&issuer.DisplayName,
		&website,
		&configJSON,
		&logoAssetID,
		&issuer.Enabled,
		&issuer.CreatedAt,
		&issuer.UpdatedAt,
	)
	if err != nil {
		return issuer, err
	}

	if website != nil {
		issuer.Website = *website
	}
	issuer.LogoAssetID = logoAssetID

	// Parse config JSON
	if len(configJSON) > 0 {
		if err := json.Unmarshal(configJSON, &issuer.Config); err != nil {
			r.logger.Warn("failed to parse issuer config JSON",
				"issuer_code", issuer.Code,
				"error", err,
			)
		}
	}

	return issuer, nil
}
