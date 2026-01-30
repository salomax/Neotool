package repository

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
)

// LoadControlRepository manages indicator_load_control
type LoadControlRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewLoadControlRepository(pool *pgxpool.Pool, logger *slog.Logger) *LoadControlRepository {
	return &LoadControlRepository{pool: pool, logger: logger}
}

// GetLastSuccess returns the last successful reference date for a variation
func (r *LoadControlRepository) GetLastSuccess(ctx context.Context, variationID string) (*time.Time, map[string]any, error) {
	query := `
		SELECT last_reference_date, cursor
		FROM financial_data.indicator_load_control
		WHERE indicator_variation_id = $1
		  AND status = 'SUCCESS'
		ORDER BY finished_at DESC
		LIMIT 1
	`

	var lastRef *time.Time
	var cursorBytes []byte
	if err := r.pool.QueryRow(ctx, query, variationID).Scan(&lastRef, &cursorBytes); err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil, nil
		}
		return nil, nil, err
	}

	var cursor map[string]any
	if len(cursorBytes) > 0 {
		_ = json.Unmarshal(cursorBytes, &cursor)
	}

	return lastRef, cursor, nil
}

// Create creates a new load control record and returns its ID
func (r *LoadControlRepository) Create(ctx context.Context, control *domain.LoadControl) (string, error) {
	query := `
		INSERT INTO financial_data.indicator_load_control (
			indicator_variation_id,
			run_type,
			status,
			scheduled_at,
			started_at,
			attempts
		) VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING id
	`

	var id string
	err := r.pool.QueryRow(
		ctx,
		query,
		control.IndicatorVariationID,
		control.RunType,
		control.Status,
		control.ScheduledAt,
		control.StartedAt,
		control.Attempts,
	).Scan(&id)
	if err != nil {
		return "", err
	}

	return id, nil
}

// UpdateCompletion updates status and timestamps for a load control row
func (r *LoadControlRepository) UpdateCompletion(
	ctx context.Context,
	id string,
	status domain.LoadStatus,
	lastReferenceDate *time.Time,
	lastPublicationDate *time.Time,
	cursor map[string]any,
	errMsg *string,
) error {
	var cursorJSON []byte
	if cursor != nil {
		cursorJSON, _ = json.Marshal(cursor)
	}
	query := `
		UPDATE financial_data.indicator_load_control
		SET status = $2,
			finished_at = now(),
			last_reference_date = $3,
			last_publication_date = $4,
			cursor = $5,
			error_message = $6,
			updated_at = now()
		WHERE id = $1
	`

	_, err := r.pool.Exec(
		ctx,
		query,
		id,
		status,
		lastReferenceDate,
		lastPublicationDate,
		cursorJSON,
		errMsg,
	)
	return err
}
