package processor

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/bacen"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
	"github.com/invistus/bacen-ifdata/internal/shared"
)

// ReportCatalogFetcher fetches the list of available reports from BACEN
type ReportCatalogFetcher struct {
	bacenClient *bacen.Client
	catalogRepo *database.ReportCatalogRepository
	logger      *slog.Logger
}

// NewReportCatalogFetcher creates a new report catalog fetcher
func NewReportCatalogFetcher(bacenClient *bacen.Client, catalogRepo *database.ReportCatalogRepository, logger *slog.Logger) *ReportCatalogFetcher {
	return &ReportCatalogFetcher{
		bacenClient: bacenClient,
		catalogRepo: catalogRepo,
		logger:      logger,
	}
}

// FetchReports fetches the list of available reports from BACEN
func (rcf *ReportCatalogFetcher) FetchReports(ctx context.Context) ([]domain.Report, error) {
	rcf.logger.Info("fetching report catalog from BACEN")

	// Fetch catalog CSV
	csvData, err := rcf.bacenClient.FetchReportCatalog(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch report catalog: %w", err)
	}

	// Parse CSV
	header, rows, err := shared.ParseCSVWithHeader(csvData)
	if err != nil {
		return nil, fmt.Errorf("failed to parse catalog CSV: %w", err)
	}

	if len(rows) == 0 {
		rcf.logger.Warn("no reports found in catalog")
		return []domain.Report{}, nil
	}

	rcf.logger.Debug("parsed catalog CSV",
		"row_count", len(rows),
		"header", header)

	// Parse reports
	var reports []domain.Report
	for _, row := range rows {
		if len(row) < 2 {
			rcf.logger.Warn("insufficient fields in catalog row",
				"field_count", len(row))
			continue
		}

		report := domain.Report{
			NomeRelatorio:   strings.TrimSpace(row[0]),
			NumeroRelatorio: strings.TrimSpace(row[1]),
		}

		if report.NomeRelatorio == "" || report.NumeroRelatorio == "" {
			rcf.logger.Warn("empty report name or number",
				"nome", report.NomeRelatorio,
				"numero", report.NumeroRelatorio)
			continue
		}

		reports = append(reports, report)
	}

	rcf.logger.Info("fetched report catalog",
		"report_count", len(reports))

	// Upsert to database
	if err := rcf.catalogRepo.UpsertReportCatalog(ctx, reports); err != nil {
		return nil, fmt.Errorf("failed to upsert report catalog: %w", err)
	}

	return reports, nil
}
