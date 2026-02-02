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

// HistoryRepository implements domain.HistoryRepository
type HistoryRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewHistoryRepository creates a new history repository
func NewHistoryRepository(pool *pgxpool.Pool, logger *slog.Logger) *HistoryRepository {
	return &HistoryRepository{
		pool:   pool,
		logger: logger.With("repository", "history"),
	}
}

// Insert adds a history record
func (r *HistoryRepository) Insert(ctx context.Context, history domain.RatingHistory) error {
	customParamsJSON, err := json.Marshal(history.CustomParams)
	if err != nil {
		return fmt.Errorf("failed to marshal custom_params: %w", err)
	}

	query := `
		INSERT INTO financial_data.financial_institution_rating_history (
			id, financial_institution_id, issuer_id, rating_name, rating_label,
			rating_date, custom_params, source_evidence_id, content_hash, changed_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`

	_, err = r.pool.Exec(ctx, query,
		history.ID,
		history.FinancialInstitutionID,
		history.IssuerID,
		history.RatingName,
		history.RatingLabel,
		history.RatingDate,
		customParamsJSON,
		history.SourceEvidenceID,
		history.ContentHash,
		history.ChangedAt,
	)

	if err != nil {
		return fmt.Errorf("failed to insert history: %w", err)
	}

	r.logger.Debug("inserted rating history",
		"rating_name", history.RatingName,
		"rating_label", history.RatingLabel,
	)

	return nil
}

// InsertBatch adds multiple history records in a transaction
func (r *HistoryRepository) InsertBatch(ctx context.Context, histories []domain.RatingHistory) error {
	if len(histories) == 0 {
		return nil
	}

	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	query := `
		INSERT INTO financial_data.financial_institution_rating_history (
			id, financial_institution_id, issuer_id, rating_name, rating_label,
			rating_date, custom_params, source_evidence_id, content_hash, changed_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`

	for _, history := range histories {
		customParamsJSON, err := json.Marshal(history.CustomParams)
		if err != nil {
			return fmt.Errorf("failed to marshal custom_params: %w", err)
		}

		_, err = tx.Exec(ctx, query,
			history.ID,
			history.FinancialInstitutionID,
			history.IssuerID,
			history.RatingName,
			history.RatingLabel,
			history.RatingDate,
			customParamsJSON,
			history.SourceEvidenceID,
			history.ContentHash,
			history.ChangedAt,
		)

		if err != nil {
			return fmt.Errorf("failed to insert history for %s: %w", history.RatingName, err)
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("failed to commit transaction: %w", err)
	}

	r.logger.Debug("inserted rating history batch", "count", len(histories))

	return nil
}

// GetByInstitution retrieves all history for an institution (ordered by changed_at DESC)
func (r *HistoryRepository) GetByInstitution(ctx context.Context, institutionID uuid.UUID, limit int) ([]domain.RatingHistory, error) {
	query := `
		SELECT id, financial_institution_id, issuer_id, rating_name, rating_label,
		       rating_date, custom_params, source_evidence_id, content_hash, changed_at
		FROM financial_data.financial_institution_rating_history
		WHERE financial_institution_id = $1
		ORDER BY changed_at DESC
		LIMIT $2
	`

	rows, err := r.pool.Query(ctx, query, institutionID, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to query history: %w", err)
	}
	defer rows.Close()

	var histories []domain.RatingHistory
	for rows.Next() {
		history, err := r.scanHistory(rows)
		if err != nil {
			return nil, err
		}
		histories = append(histories, history)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating history rows: %w", err)
	}

	return histories, nil
}

// GetByInstitutionAndRatingName retrieves history for a specific rating
func (r *HistoryRepository) GetByInstitutionAndRatingName(ctx context.Context, institutionID uuid.UUID, ratingName string, limit int) ([]domain.RatingHistory, error) {
	query := `
		SELECT id, financial_institution_id, issuer_id, rating_name, rating_label,
		       rating_date, custom_params, source_evidence_id, content_hash, changed_at
		FROM financial_data.financial_institution_rating_history
		WHERE financial_institution_id = $1 AND rating_name = $2
		ORDER BY changed_at DESC
		LIMIT $3
	`

	rows, err := r.pool.Query(ctx, query, institutionID, ratingName, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to query history: %w", err)
	}
	defer rows.Close()

	var histories []domain.RatingHistory
	for rows.Next() {
		history, err := r.scanHistory(rows)
		if err != nil {
			return nil, err
		}
		histories = append(histories, history)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating history rows: %w", err)
	}

	return histories, nil
}

// scanHistory scans a row into a RatingHistory
func (r *HistoryRepository) scanHistory(rows pgx.Rows) (domain.RatingHistory, error) {
	var history domain.RatingHistory
	var customParamsJSON []byte

	err := rows.Scan(
		&history.ID,
		&history.FinancialInstitutionID,
		&history.IssuerID,
		&history.RatingName,
		&history.RatingLabel,
		&history.RatingDate,
		&customParamsJSON,
		&history.SourceEvidenceID,
		&history.ContentHash,
		&history.ChangedAt,
	)
	if err != nil {
		return history, fmt.Errorf("failed to scan history: %w", err)
	}

	// Parse custom_params JSON
	if len(customParamsJSON) > 0 {
		if err := json.Unmarshal(customParamsJSON, &history.CustomParams); err != nil {
			r.logger.Warn("failed to parse history custom_params JSON",
				"rating_name", history.RatingName,
				"error", err,
			)
			history.CustomParams = make(map[string]interface{})
		}
	} else {
		history.CustomParams = make(map[string]interface{})
	}

	return history, nil
}
