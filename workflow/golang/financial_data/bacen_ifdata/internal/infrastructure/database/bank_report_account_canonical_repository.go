package database

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	"github.com/google/uuid"
	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5/pgxpool"
)

// BankReportAccountCanonicalRepository handles canonical account persistence
type BankReportAccountCanonicalRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewBankReportAccountCanonicalRepository creates a new canonical account repository
func NewBankReportAccountCanonicalRepository(pool *pgxpool.Pool, logger *slog.Logger) *BankReportAccountCanonicalRepository {
	return &BankReportAccountCanonicalRepository{
		pool:   pool,
		logger: logger,
	}
}

// GetOrCreateByName gets or creates a canonical account by display name
func (r *BankReportAccountCanonicalRepository) GetOrCreateByName(
	ctx context.Context,
	nomeColuna *string,
	conta *string,
) (*domain.InstitutionReportAccountCanonical, error) {
	displayName := normalizeCanonicalDisplayName(nomeColuna, conta)
	if displayName == nil {
		return nil, nil
	}

	query := `
		INSERT INTO financial_data.bacen_inst_report_account_canonical (
			id, normalized_name, display_name
		) VALUES ($1, financial_data.normalize_report_account_name($2), financial_data.clean_report_account_display_name($2))
		ON CONFLICT (normalized_name)
		DO UPDATE SET updated_at = NOW()
		RETURNING id, normalized_name, display_name, created_at, updated_at
	`

	canonical := &domain.InstitutionReportAccountCanonical{}
	canonical.ID = uuid.New().String()

	err := r.pool.QueryRow(ctx, query, canonical.ID, *displayName).Scan(
		&canonical.ID,
		&canonical.NormalizedName,
		&canonical.DisplayName,
		&canonical.CreatedAt,
		&canonical.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to upsert canonical account: %w", err)
	}

	r.logger.Debug("upserted canonical account",
		"id", canonical.ID,
		"normalized_name", canonical.NormalizedName,
		"display_name", canonical.DisplayName)

	return canonical, nil
}

func normalizeCanonicalDisplayName(nomeColuna *string, conta *string) *string {
	candidates := []*string{nomeColuna, conta}
	for _, candidate := range candidates {
		if candidate == nil {
			continue
		}
		trimmed := strings.TrimSpace(*candidate)
		if trimmed == "" {
			continue
		}
		return &trimmed
	}
	return nil
}
