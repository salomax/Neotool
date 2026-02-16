package database

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// MappingRepository implements domain.MappingRepository
type MappingRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewMappingRepository creates a new mapping repository
func NewMappingRepository(pool *pgxpool.Pool, logger *slog.Logger) *MappingRepository {
	return &MappingRepository{
		pool:   pool,
		logger: logger.With("repository", "mapping"),
	}
}

// GetEnabledByIssuer retrieves all enabled mappings for an issuer
func (r *MappingRepository) GetEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) ([]domain.InstitutionIssuerMapping, error) {
	query := `
		SELECT id, rating_issuer_id, financial_institution_id, config, enabled, created_at, updated_at
		FROM financial_data.financial_institution_rating_issuer
		WHERE rating_issuer_id = $1 AND enabled = true
		ORDER BY created_at
	`

	rows, err := r.pool.Query(ctx, query, issuerID)
	if err != nil {
		return nil, fmt.Errorf("failed to query mappings: %w", err)
	}
	defer rows.Close()

	var mappings []domain.InstitutionIssuerMapping
	for rows.Next() {
		mapping, err := r.scanMapping(rows)
		if err != nil {
			return nil, err
		}
		mappings = append(mappings, mapping)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating mapping rows: %w", err)
	}

	r.logger.Debug("fetched enabled mappings",
		"issuer_id", issuerID,
		"count", len(mappings),
	)

	return mappings, nil
}

// ListInstitutionsWithIssuerMapping returns all institutions with optional issuer mapping.
func (r *MappingRepository) ListInstitutionsWithIssuerMapping(ctx context.Context, issuerID uuid.UUID) ([]domain.InstitutionMapping, error) {
	query := `
		SELECT fi.id, fi.legal_name,
		       m.id, m.rating_issuer_id, m.financial_institution_id, m.config, m.enabled, m.created_at, m.updated_at
		FROM financial_data.financial_institution fi
		LEFT JOIN financial_data.financial_institution_rating_issuer m
		  ON m.financial_institution_id = fi.id AND m.rating_issuer_id = $1
		WHERE fi.legal_name IS NOT NULL
		ORDER BY fi.legal_name
	`

	rows, err := r.pool.Query(ctx, query, issuerID)
	if err != nil {
		return nil, fmt.Errorf("failed to query institution mappings: %w", err)
	}
	defer rows.Close()

	var results []domain.InstitutionMapping
	for rows.Next() {
		var inst domain.FinancialInstitution
		var legalName sql.NullString
		var mapping domain.InstitutionIssuerMapping
		var mappingID *uuid.UUID
		var mappingIssuerID *uuid.UUID
		var mappingInstitutionID *uuid.UUID
		var configJSON []byte
		var enabled *bool
		var createdAt, updatedAt *time.Time

		err := rows.Scan(
			&inst.ID,
			&legalName,
			&mappingID,
			&mappingIssuerID,
			&mappingInstitutionID,
			&configJSON,
			&enabled,
			&createdAt,
			&updatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to scan institution mapping: %w", err)
		}

		if legalName.Valid {
			inst.LegalName = legalName.String
		}

		var mappingPtr *domain.InstitutionIssuerMapping
		if mappingID != nil {
			mapping.ID = *mappingID
			if mappingIssuerID != nil {
				mapping.RatingIssuerID = *mappingIssuerID
			}
			if mappingInstitutionID != nil {
				mapping.FinancialInstitutionID = *mappingInstitutionID
			}
			if enabled != nil {
				mapping.Enabled = *enabled
			}
			if createdAt != nil {
				mapping.CreatedAt = *createdAt
			}
			if updatedAt != nil {
				mapping.UpdatedAt = *updatedAt
			}
			if len(configJSON) > 0 {
				if err := json.Unmarshal(configJSON, &mapping.Config); err != nil {
					r.logger.Warn("failed to parse mapping config JSON",
						"mapping_id", mapping.ID,
						"error", err,
					)
				}
			}
			mappingPtr = &mapping
		}

		results = append(results, domain.InstitutionMapping{
			Institution: inst,
			Mapping:     mappingPtr,
		})
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating institution mapping rows: %w", err)
	}

	return results, nil
}

// GetByInstitutionAndIssuer retrieves a specific mapping
func (r *MappingRepository) GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) (*domain.InstitutionIssuerMapping, error) {
	query := `
		SELECT id, rating_issuer_id, financial_institution_id, config, enabled, created_at, updated_at
		FROM financial_data.financial_institution_rating_issuer
		WHERE financial_institution_id = $1 AND rating_issuer_id = $2
	`

	row := r.pool.QueryRow(ctx, query, institutionID, issuerID)
	mapping, err := r.scanMappingRow(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, domain.ErrMappingNotFound
		}
		return nil, fmt.Errorf("failed to get mapping: %w", err)
	}

	return &mapping, nil
}

// Upsert inserts or updates a mapping
func (r *MappingRepository) Upsert(ctx context.Context, mapping *domain.InstitutionIssuerMapping) error {
	configJSON, err := r.marshalConfig(mapping.Config)
	if err != nil {
		return err
	}

	query := `
		INSERT INTO financial_data.financial_institution_rating_issuer (
			id, rating_issuer_id, financial_institution_id, config, enabled
		)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (rating_issuer_id, financial_institution_id)
		DO UPDATE SET
			config = EXCLUDED.config,
			enabled = EXCLUDED.enabled
	`

	_, err = r.pool.Exec(
		ctx,
		query,
		mapping.ID,
		mapping.RatingIssuerID,
		mapping.FinancialInstitutionID,
		configJSON,
		mapping.Enabled,
	)
	if err != nil {
		return fmt.Errorf("failed to upsert mapping: %w", err)
	}

	return nil
}

// CountEnabledByIssuer counts enabled mappings for an issuer
func (r *MappingRepository) CountEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) (int, error) {
	query := `
		SELECT COUNT(*)
		FROM financial_data.financial_institution_rating_issuer
		WHERE rating_issuer_id = $1 AND enabled = true
	`

	var count int
	err := r.pool.QueryRow(ctx, query, issuerID).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to count mappings: %w", err)
	}

	return count, nil
}

// scanMapping scans a row into an InstitutionIssuerMapping
func (r *MappingRepository) scanMapping(rows pgx.Rows) (domain.InstitutionIssuerMapping, error) {
	var mapping domain.InstitutionIssuerMapping
	var configJSON []byte

	err := rows.Scan(
		&mapping.ID,
		&mapping.RatingIssuerID,
		&mapping.FinancialInstitutionID,
		&configJSON,
		&mapping.Enabled,
		&mapping.CreatedAt,
		&mapping.UpdatedAt,
	)
	if err != nil {
		return mapping, fmt.Errorf("failed to scan mapping: %w", err)
	}

	// Parse config JSON
	if len(configJSON) > 0 {
		if err := json.Unmarshal(configJSON, &mapping.Config); err != nil {
			r.logger.Warn("failed to parse mapping config JSON",
				"mapping_id", mapping.ID,
				"error", err,
			)
		}
	}

	return mapping, nil
}

// scanMappingRow scans a single row into an InstitutionIssuerMapping
func (r *MappingRepository) scanMappingRow(row pgx.Row) (domain.InstitutionIssuerMapping, error) {
	var mapping domain.InstitutionIssuerMapping
	var configJSON []byte

	err := row.Scan(
		&mapping.ID,
		&mapping.RatingIssuerID,
		&mapping.FinancialInstitutionID,
		&configJSON,
		&mapping.Enabled,
		&mapping.CreatedAt,
		&mapping.UpdatedAt,
	)
	if err != nil {
		return mapping, err
	}

	// Parse config JSON
	if len(configJSON) > 0 {
		if err := json.Unmarshal(configJSON, &mapping.Config); err != nil {
			r.logger.Warn("failed to parse mapping config JSON",
				"mapping_id", mapping.ID,
				"error", err,
			)
		}
	}

	return mapping, nil
}

func (r *MappingRepository) marshalConfig(config domain.MappingConfig) ([]byte, error) {
	if config.Slug == "" && config.EntityID == "" && config.OrgID == "" {
		return nil, nil
	}

	configJSON, err := json.Marshal(config)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal mapping config: %w", err)
	}

	return configJSON, nil
}
