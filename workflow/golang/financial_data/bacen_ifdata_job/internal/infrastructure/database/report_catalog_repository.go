package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ReportCatalogRepository handles report catalog persistence
type ReportCatalogRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewReportCatalogRepository creates a new report catalog repository
func NewReportCatalogRepository(pool *pgxpool.Pool, logger *slog.Logger) *ReportCatalogRepository {
	return &ReportCatalogRepository{
		pool:   pool,
		logger: logger,
	}
}

// UpsertReportCatalog upserts a list of reports into the catalog
func (r *ReportCatalogRepository) UpsertReportCatalog(ctx context.Context, reports []domain.Report) error {
	if len(reports) == 0 {
		return nil
	}

	query := `
		INSERT INTO financial_data.bacen_inst_report_catalog (
			numero_relatorio, nome_relatorio
		) VALUES ($1, $2)
		ON CONFLICT (numero_relatorio) DO UPDATE SET
			nome_relatorio = EXCLUDED.nome_relatorio,
			updated_at = NOW()
	`

	// Use a transaction for batch processing
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	for _, report := range reports {
		_, err := tx.Exec(ctx, query, report.NumeroRelatorio, report.NomeRelatorio)
		if err != nil {
			return fmt.Errorf("failed to upsert report %s (%s): %w", report.NumeroRelatorio, report.NomeRelatorio, err)
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("failed to commit transaction: %w", err)
	}

	r.logger.Info("upserted report catalog", "count", len(reports))

	return nil
}
