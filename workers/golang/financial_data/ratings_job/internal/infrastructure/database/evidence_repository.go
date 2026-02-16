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

// EvidenceRepository implements domain.EvidenceRepository
type EvidenceRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewEvidenceRepository creates a new evidence repository
func NewEvidenceRepository(pool *pgxpool.Pool, logger *slog.Logger) *EvidenceRepository {
	return &EvidenceRepository{
		pool:   pool,
		logger: logger.With("repository", "evidence"),
	}
}

// Insert adds a new evidence record
func (r *EvidenceRepository) Insert(ctx context.Context, evidence *domain.SourceEvidence) error {
	metadataJSON, err := json.Marshal(evidence.Metadata)
	if err != nil {
		return fmt.Errorf("failed to marshal metadata: %w", err)
	}

	query := `
		INSERT INTO financial_data.rating_source_evidence (
			id, issuer_id, fetched_at, metadata, raw_payload, content_hash
		) VALUES ($1, $2, $3, $4, $5, $6)
	`

	_, err = r.pool.Exec(ctx, query,
		evidence.ID,
		evidence.IssuerID,
		evidence.FetchedAt,
		metadataJSON,
		evidence.RawPayload,
		evidence.ContentHash,
	)

	if err != nil {
		return fmt.Errorf("failed to insert evidence: %w", err)
	}

	r.logger.Debug("inserted source evidence",
		"evidence_id", evidence.ID,
		"content_hash", evidence.ContentHash[:16]+"...",
	)

	return nil
}

// GetByHash retrieves evidence by content hash
func (r *EvidenceRepository) GetByHash(ctx context.Context, hash string) (*domain.SourceEvidence, error) {
	query := `
		SELECT id, issuer_id, fetched_at, metadata, raw_payload, content_hash, created_at
		FROM financial_data.rating_source_evidence
		WHERE content_hash = $1
		ORDER BY fetched_at DESC
		LIMIT 1
	`

	row := r.pool.QueryRow(ctx, query, hash)
	evidence, err := r.scanEvidenceRow(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, domain.ErrEvidenceNotFound
		}
		return nil, fmt.Errorf("failed to get evidence by hash: %w", err)
	}

	return &evidence, nil
}

// Exists checks if evidence with the given hash exists
func (r *EvidenceRepository) Exists(ctx context.Context, hash string) (bool, error) {
	query := `
		SELECT EXISTS(
			SELECT 1 FROM financial_data.rating_source_evidence WHERE content_hash = $1
		)
	`

	var exists bool
	err := r.pool.QueryRow(ctx, query, hash).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("failed to check evidence existence: %w", err)
	}

	return exists, nil
}

// GetByID retrieves evidence by ID
func (r *EvidenceRepository) GetByID(ctx context.Context, id uuid.UUID) (*domain.SourceEvidence, error) {
	query := `
		SELECT id, issuer_id, fetched_at, metadata, raw_payload, content_hash, created_at
		FROM financial_data.rating_source_evidence
		WHERE id = $1
	`

	row := r.pool.QueryRow(ctx, query, id)
	evidence, err := r.scanEvidenceRow(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, domain.ErrEvidenceNotFound
		}
		return nil, fmt.Errorf("failed to get evidence by id: %w", err)
	}

	return &evidence, nil
}

// scanEvidenceRow scans a single row into a SourceEvidence
func (r *EvidenceRepository) scanEvidenceRow(row pgx.Row) (domain.SourceEvidence, error) {
	var evidence domain.SourceEvidence
	var metadataJSON []byte

	err := row.Scan(
		&evidence.ID,
		&evidence.IssuerID,
		&evidence.FetchedAt,
		&metadataJSON,
		&evidence.RawPayload,
		&evidence.ContentHash,
		&evidence.CreatedAt,
	)
	if err != nil {
		return evidence, err
	}

	// Parse metadata JSON
	if len(metadataJSON) > 0 {
		if err := json.Unmarshal(metadataJSON, &evidence.Metadata); err != nil {
			r.logger.Warn("failed to parse evidence metadata JSON",
				"evidence_id", evidence.ID,
				"error", err,
			)
		}
	}

	return evidence, nil
}
