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

// RatingRepository implements domain.RatingRepository
type RatingRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewRatingRepository creates a new rating repository
func NewRatingRepository(pool *pgxpool.Pool, logger *slog.Logger) *RatingRepository {
	return &RatingRepository{
		pool:   pool,
		logger: logger.With("repository", "rating"),
	}
}

// GetByInstitutionAndIssuer retrieves all ratings for an institution from an issuer
func (r *RatingRepository) GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) ([]domain.Rating, error) {
	query := `
		SELECT id, financial_institution_id, issuer_id, rating_name, rating_label,
		       rating_date, custom_params, source_evidence_id, content_hash,
		       created_at, updated_at
		FROM financial_data.financial_institution_rating
		WHERE financial_institution_id = $1 AND issuer_id = $2
		ORDER BY rating_name
	`

	rows, err := r.pool.Query(ctx, query, institutionID, issuerID)
	if err != nil {
		return nil, fmt.Errorf("failed to query ratings: %w", err)
	}
	defer rows.Close()

	var ratings []domain.Rating
	for rows.Next() {
		rating, err := r.scanRating(rows)
		if err != nil {
			return nil, err
		}
		ratings = append(ratings, rating)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating rating rows: %w", err)
	}

	return ratings, nil
}

// GetByInstitutionIssuerAndName retrieves a specific rating
func (r *RatingRepository) GetByInstitutionIssuerAndName(ctx context.Context, institutionID, issuerID uuid.UUID, ratingName string) (*domain.Rating, error) {
	query := `
		SELECT id, financial_institution_id, issuer_id, rating_name, rating_label,
		       rating_date, custom_params, source_evidence_id, content_hash,
		       created_at, updated_at
		FROM financial_data.financial_institution_rating
		WHERE financial_institution_id = $1 AND issuer_id = $2 AND rating_name = $3
	`

	row := r.pool.QueryRow(ctx, query, institutionID, issuerID, ratingName)
	rating, err := r.scanRatingRow(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, domain.ErrRatingNotFound
		}
		return nil, fmt.Errorf("failed to get rating: %w", err)
	}

	return &rating, nil
}

// Upsert inserts or updates a rating, returns true if created (vs updated)
func (r *RatingRepository) Upsert(ctx context.Context, rating *domain.Rating) (bool, error) {
	customParamsJSON, err := json.Marshal(rating.CustomParams)
	if err != nil {
		return false, fmt.Errorf("failed to marshal custom_params: %w", err)
	}

	query := `
		INSERT INTO financial_data.financial_institution_rating (
			id, financial_institution_id, issuer_id, rating_name, rating_label,
			rating_date, custom_params, source_evidence_id, content_hash
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		ON CONFLICT (financial_institution_id, issuer_id, rating_name)
		DO UPDATE SET
			rating_label = EXCLUDED.rating_label,
			rating_date = EXCLUDED.rating_date,
			custom_params = EXCLUDED.custom_params,
			source_evidence_id = EXCLUDED.source_evidence_id,
			content_hash = EXCLUDED.content_hash,
			updated_at = NOW()
		RETURNING created_at = updated_at AS is_new
	`

	var isNew bool
	err = r.pool.QueryRow(ctx, query,
		rating.ID,
		rating.FinancialInstitutionID,
		rating.IssuerID,
		rating.RatingName,
		rating.RatingLabel,
		rating.RatingDate,
		customParamsJSON,
		rating.SourceEvidenceID,
		rating.ContentHash,
	).Scan(&isNew)

	if err != nil {
		return false, fmt.Errorf("failed to upsert rating: %w", err)
	}

	r.logger.Debug("upserted rating",
		"rating_name", rating.RatingName,
		"rating_label", rating.RatingLabel,
		"is_new", isNew,
	)

	return isNew, nil
}

// UpsertBatch inserts or updates multiple ratings in a transaction
func (r *RatingRepository) UpsertBatch(ctx context.Context, ratings []domain.Rating) (created, updated int, err error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return 0, 0, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	for i := range ratings {
		rating := &ratings[i]

		customParamsJSON, err := json.Marshal(rating.CustomParams)
		if err != nil {
			return 0, 0, fmt.Errorf("failed to marshal custom_params: %w", err)
		}

		query := `
			INSERT INTO financial_data.financial_institution_rating (
				id, financial_institution_id, issuer_id, rating_name, rating_label,
				rating_date, custom_params, source_evidence_id, content_hash
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			ON CONFLICT (financial_institution_id, issuer_id, rating_name)
			DO UPDATE SET
				rating_label = EXCLUDED.rating_label,
				rating_date = EXCLUDED.rating_date,
				custom_params = EXCLUDED.custom_params,
				source_evidence_id = EXCLUDED.source_evidence_id,
				content_hash = EXCLUDED.content_hash,
				updated_at = NOW()
			RETURNING created_at = updated_at AS is_new
		`

		var isNew bool
		err = tx.QueryRow(ctx, query,
			rating.ID,
			rating.FinancialInstitutionID,
			rating.IssuerID,
			rating.RatingName,
			rating.RatingLabel,
			rating.RatingDate,
			customParamsJSON,
			rating.SourceEvidenceID,
			rating.ContentHash,
		).Scan(&isNew)

		if err != nil {
			return 0, 0, fmt.Errorf("failed to upsert rating %s: %w", rating.RatingName, err)
		}

		if isNew {
			created++
		} else {
			updated++
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return 0, 0, fmt.Errorf("failed to commit transaction: %w", err)
	}

	return created, updated, nil
}

// scanRating scans a row into a Rating
func (r *RatingRepository) scanRating(rows pgx.Rows) (domain.Rating, error) {
	var rating domain.Rating
	var customParamsJSON []byte

	err := rows.Scan(
		&rating.ID,
		&rating.FinancialInstitutionID,
		&rating.IssuerID,
		&rating.RatingName,
		&rating.RatingLabel,
		&rating.RatingDate,
		&customParamsJSON,
		&rating.SourceEvidenceID,
		&rating.ContentHash,
		&rating.CreatedAt,
		&rating.UpdatedAt,
	)
	if err != nil {
		return rating, fmt.Errorf("failed to scan rating: %w", err)
	}

	// Parse custom_params JSON
	if len(customParamsJSON) > 0 {
		if err := json.Unmarshal(customParamsJSON, &rating.CustomParams); err != nil {
			r.logger.Warn("failed to parse rating custom_params JSON",
				"rating_name", rating.RatingName,
				"error", err,
			)
			rating.CustomParams = make(map[string]interface{})
		}
	} else {
		rating.CustomParams = make(map[string]interface{})
	}

	return rating, nil
}

// scanRatingRow scans a single row into a Rating
func (r *RatingRepository) scanRatingRow(row pgx.Row) (domain.Rating, error) {
	var rating domain.Rating
	var customParamsJSON []byte

	err := row.Scan(
		&rating.ID,
		&rating.FinancialInstitutionID,
		&rating.IssuerID,
		&rating.RatingName,
		&rating.RatingLabel,
		&rating.RatingDate,
		&customParamsJSON,
		&rating.SourceEvidenceID,
		&rating.ContentHash,
		&rating.CreatedAt,
		&rating.UpdatedAt,
	)
	if err != nil {
		return rating, err
	}

	// Parse custom_params JSON
	if len(customParamsJSON) > 0 {
		if err := json.Unmarshal(customParamsJSON, &rating.CustomParams); err != nil {
			r.logger.Warn("failed to parse rating custom_params JSON",
				"rating_name", rating.RatingName,
				"error", err,
			)
			rating.CustomParams = make(map[string]interface{})
		}
	} else {
		rating.CustomParams = make(map[string]interface{})
	}

	return rating, nil
}
