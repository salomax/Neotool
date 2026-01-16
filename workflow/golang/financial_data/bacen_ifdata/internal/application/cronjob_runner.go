package application

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/kafka"
	"github.com/invistus/bacen-ifdata/internal/processor"
)

// CronjobRunner orchestrates the entire bacen-ifdata ingestion workflow
type CronjobRunner struct {
	config            *Config
	quarterDeterminer *processor.QuarterDeterminer
	registryIngester  *processor.RegistryIngester
	catalogFetcher    *processor.ReportCatalogFetcher
	taskCreator       *processor.ExtractTaskCreator
	extractor         *processor.Extractor
	promoter          *processor.Promoter
	kafkaPublisher    *kafka.InstitutionPublisher
	healthServer      *HealthServer
	logger            *slog.Logger
}

// NewCronjobRunner creates a new cronjob runner
func NewCronjobRunner(
	config *Config,
	quarterDeterminer *processor.QuarterDeterminer,
	registryIngester *processor.RegistryIngester,
	catalogFetcher *processor.ReportCatalogFetcher,
	taskCreator *processor.ExtractTaskCreator,
	extractor *processor.Extractor,
	promoter *processor.Promoter,
	kafkaPublisher *kafka.InstitutionPublisher,
	healthServer *HealthServer,
	logger *slog.Logger,
) *CronjobRunner {
	return &CronjobRunner{
		config:            config,
		quarterDeterminer: quarterDeterminer,
		registryIngester:  registryIngester,
		catalogFetcher:    catalogFetcher,
		taskCreator:       taskCreator,
		extractor:         extractor,
		promoter:          promoter,
		kafkaPublisher:    kafkaPublisher,
		healthServer:      healthServer,
		logger:            logger,
	}
}

// Run executes the complete ingestion workflow
// If institutionOnly is true, skips bank report extraction and promotion (steps 4-7)
func (cr *CronjobRunner) Run(ctx context.Context, forcedAnoMes string, forceRegistryRefresh bool, institutionOnly bool) error {
	runner := NewRunner(cr.config, cr.healthServer, cr.logger)
	return runner.Run(ctx, "cronjob", func(ctx context.Context) error {
		startTime := time.Now()
		cr.logger.Info("starting bacen-ifdata cronjob",
			"forced_ano_mes", forcedAnoMes,
			"force_registry_refresh", forceRegistryRefresh,
			"institution_only", institutionOnly)

		// Step 1: Determine target quarter
		cr.logger.Info("Step 1: Determining target quarter")
		quarterInfo, err := cr.quarterDeterminer.DetermineTargetQuarter(ctx, forcedAnoMes)
		if err != nil {
			return fmt.Errorf("failed to determine target quarter: %w", err)
		}

		if !quarterInfo.IsPublicationReady {
			cr.logger.Warn("publication window not reached, skipping ingestion",
				"target_ano_mes", quarterInfo.AnoMes,
				"publication_date", quarterInfo.PublicationDate)
			return domain.ErrPublicationWindowNotReached
		}

		anoMes := quarterInfo.AnoMes
		cr.logger.Info("target quarter determined",
			"ano_mes", anoMes,
			"publication_date", quarterInfo.PublicationDate)

		// Step 2: Ingest registry with change detection
		cr.logger.Info("Step 2: Ingesting institution registry")
		institutionChanges, err := cr.registryIngester.IngestRegistry(ctx, anoMes, forceRegistryRefresh)
		if err != nil {
			return fmt.Errorf("failed to ingest registry: %w", err)
		}

		// Step 3: Publish Kafka events for changed institutions
		cr.logger.Info("Step 3: Publishing institution change events")
		published, failed, err := cr.kafkaPublisher.PublishChanges(ctx, institutionChanges)
		if err != nil {
			cr.logger.Error("failed to publish some institution changes",
				"published", published,
				"failed", failed,
				"error", err)
		} else {
			cr.logger.Info("published institution changes",
				"published", published,
				"failed", failed)
		}

		// Steps 4-7: Bank report extraction (skipped if institution-only mode)
		var reports []domain.Report
		var rowsPromoted int
		var stats map[string]int

		if !institutionOnly {
			// Step 4: Fetch report catalog
			cr.logger.Info("Step 4: Fetching report catalog")
			reports, err = cr.catalogFetcher.FetchReports(ctx)
			if err != nil {
				return fmt.Errorf("failed to fetch report catalog: %w", err)
			}
			cr.logger.Info("fetched report catalog", "report_count", len(reports))

			// Step 5: Create extract tasks
			cr.logger.Info("Step 5: Creating extract tasks")
			tasksCreated, err := cr.taskCreator.CreateTasks(ctx, anoMes, reports, cr.config.Extraction.PageSize)
			if err != nil {
				return fmt.Errorf("failed to create extract tasks: %w", err)
			}
			cr.logger.Info("created extract tasks", "tasks_created", tasksCreated)

			// Mark as ready
			if cr.config.HealthCheck.Enabled {
				cr.healthServer.SetReady(true)
			}

			// Step 6: Run extraction workers
			cr.logger.Info("Step 6: Running extraction workers",
				"worker_count", cr.config.Extraction.MaxWorkers)
			if err := cr.extractor.RunExtractionWorkers(ctx, cr.config.Extraction.MaxWorkers); err != nil {
				return fmt.Errorf("failed to run extraction workers: %w", err)
			}

			// Step 7: Promote all reports to hot tables
			cr.logger.Info("Step 7: Promoting data to hot tables",
				"worker_count", cr.config.Promotion.MaxWorkers)
			rowsPromoted, err = cr.promoter.PromoteAllReports(ctx, anoMes, reports, cr.config.Promotion.MaxWorkers)
			if err != nil {
				return fmt.Errorf("failed to promote reports: %w", err)
			}
			cr.logger.Info("promoted data to hot tables", "rows_promoted", rowsPromoted)

			// Get final statistics
			stats, err = cr.promoter.GetPromotionStats(ctx, anoMes)
			if err != nil {
				cr.logger.Error("failed to get promotion stats", "error", err)
			}
		} else {
			cr.logger.Info("skipping bank report extraction (institution-only mode)")
		}

		duration := time.Since(startTime)
		cr.logger.Info("bacen-ifdata cronjob completed successfully",
			"ano_mes", anoMes,
			"duration", duration,
			"institution_only", institutionOnly,
			"reports_processed", len(reports),
			"rows_promoted", rowsPromoted,
			"institutions_changed", published,
			"load_control_stats", stats)

		return nil
	})
}
