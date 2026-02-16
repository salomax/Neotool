package application

import (
	"context"
	"fmt"
	"log/slog"
	"sync"
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

// Run executes the complete ingestion workflow: resolves all quarters to process
// (from next-after-last to latest closed quarter, or forced single quarter), runs
// registry + Kafka per quarter, then extraction for all quarters with promotion
// running in parallel per quarter as each quarter's extraction completes.
// If institutionOnly is true, skips bank report extraction and promotion (steps 4-7).
func (cr *CronjobRunner) Run(ctx context.Context, forcedAnoMes string, forceRegistryRefresh bool, institutionOnly bool) error {
	runner := NewRunner(cr.config, cr.healthServer, cr.logger)
	return runner.Run(ctx, "cronjob", func(ctx context.Context) error {
		startTime := time.Now()
		cr.logger.Info("starting bacen-ifdata cronjob",
			"forced_ano_mes", forcedAnoMes,
			"force_registry_refresh", forceRegistryRefresh,
			"institution_only", institutionOnly)

		// Step 1: Resolve quarter list
		cr.logger.Info("Step 1: Resolving quarters to process")
		quarters, err := cr.quarterDeterminer.GetQuartersToProcess(ctx, forcedAnoMes)
		if err != nil {
			return fmt.Errorf("failed to get quarters to process: %w", err)
		}
		if len(quarters) == 0 {
			cr.logger.Info("no quarters to process (range empty or already up to date)")
			return domain.ErrPublicationWindowNotReached
		}
		cr.logger.Info("quarters to process", "count", len(quarters), "quarters", quarters)

		totalPublished := 0
		// Step 2 & 3: Registry + Kafka per quarter (sequential)
		for i, anoMes := range quarters {
			cr.logger.Info("Step 2: Ingesting institution registry",
				"quarter", i+1, "ano_mes", anoMes)
			institutionChanges, err := cr.registryIngester.IngestRegistry(ctx, anoMes, forceRegistryRefresh)
			if err != nil {
				return fmt.Errorf("failed to ingest registry for %s: %w", anoMes, err)
			}
			cr.logger.Info("Step 3: Publishing institution change events", "ano_mes", anoMes)
			published, failed, err := cr.kafkaPublisher.PublishChanges(ctx, institutionChanges)
			if err != nil {
				cr.logger.Error("failed to publish some institution changes",
					"ano_mes", anoMes, "published", published, "failed", failed, "error", err)
			} else {
				cr.logger.Info("published institution changes",
					"ano_mes", anoMes, "published", published, "failed", failed)
			}
			totalPublished += published
		}

		if institutionOnly {
			cr.logger.Info("skipping bank report extraction (institution-only mode)")
			duration := time.Since(startTime)
			cr.logger.Info("bacen-ifdata cronjob completed successfully",
				"quarters_processed", len(quarters),
				"duration", duration,
				"institution_only", true,
				"total_institutions_changed", totalPublished)
			return nil
		}

		// Step 4: Fetch report catalog once
		cr.logger.Info("Step 4: Fetching report catalog")
		reports, err := cr.catalogFetcher.FetchReports(ctx)
		if err != nil {
			return fmt.Errorf("failed to fetch report catalog: %w", err)
		}
		cr.logger.Info("fetched report catalog", "report_count", len(reports))

		// Step 5: Create extract tasks for all quarters
		cr.logger.Info("Step 5: Creating extract tasks for all quarters")
		totalTasksCreated := 0
		for _, anoMes := range quarters {
			tasksCreated, err := cr.taskCreator.CreateTasks(ctx, anoMes, reports, cr.config.Extraction.PageSize)
			if err != nil {
				return fmt.Errorf("failed to create extract tasks for %s: %w", anoMes, err)
			}
			totalTasksCreated += tasksCreated
		}
		cr.logger.Info("created extract tasks", "tasks_created", totalTasksCreated, "quarters", len(quarters))

		if cr.config.HealthCheck.Enabled {
			cr.healthServer.SetReady(true)
		}

		// Step 6 & 7: Run extraction workers and per-quarter promotion in parallel.
		// Extraction workers process all quarters' tasks; each quarter's promotion runs as soon as that quarter's extraction is done.
		cr.logger.Info("Step 6: Running extraction workers",
			"worker_count", cr.config.Extraction.MaxWorkers)
		var extractErr error
		var extractDone sync.WaitGroup
		extractDone.Add(1)
		go func() {
			defer extractDone.Done()
			extractErr = cr.extractor.RunExtractionWorkers(ctx, cr.config.Extraction.MaxWorkers)
		}()

		cr.logger.Info("Step 7: Promoting data to hot tables (per quarter as extraction completed)")
		pollInterval := cr.config.Promotion.WaitPollIntervalDur
		if pollInterval <= 0 {
			pollInterval = 5 * time.Second
		}
		var wg sync.WaitGroup
		var firstPromoErrMu sync.Mutex
		var firstPromoErr error
		totalRowsPromoted := 0
		var totalRowsPromotedMu sync.Mutex
		for _, anoMes := range quarters {
			anoMes := anoMes
			wg.Add(1)
			go func() {
				defer wg.Done()
				if err := cr.extractor.WaitForCompletion(ctx, anoMes, pollInterval); err != nil {
					if ctx.Err() != nil {
						return
					}
					cr.logger.Error("wait for extraction completion failed", "ano_mes", anoMes, "error", err)
					firstPromoErrMu.Lock()
					if firstPromoErr == nil {
						firstPromoErr = fmt.Errorf("wait for %s: %w", anoMes, err)
					}
					firstPromoErrMu.Unlock()
					return
				}
				rowsPromoted, err := cr.promoter.PromoteAllReports(ctx, anoMes, reports, cr.config.Promotion.MaxWorkers)
				if err != nil {
					cr.logger.Error("failed to promote reports", "ano_mes", anoMes, "error", err)
					firstPromoErrMu.Lock()
					if firstPromoErr == nil {
						firstPromoErr = fmt.Errorf("promote %s: %w", anoMes, err)
					}
					firstPromoErrMu.Unlock()
					return
				}
				totalRowsPromotedMu.Lock()
				totalRowsPromoted += rowsPromoted
				totalRowsPromotedMu.Unlock()
				stats, _ := cr.promoter.GetPromotionStats(ctx, anoMes)
				cr.logger.Info("quarter completed", "ano_mes", anoMes, "rows_promoted", rowsPromoted, "load_control_stats", stats)
			}()
		}
		extractDone.Wait()
		if extractErr != nil {
			return fmt.Errorf("failed to run extraction workers: %w", extractErr)
		}
		wg.Wait()
		if firstPromoErr != nil {
			return fmt.Errorf("promotion error: %w", firstPromoErr)
		}

		duration := time.Since(startTime)
		cr.logger.Info("bacen-ifdata cronjob completed successfully",
			"quarters_processed", len(quarters),
			"duration", duration,
			"institution_only", false,
			"total_rows_promoted", totalRowsPromoted,
			"total_institutions_changed", totalPublished)
		return nil
	})
}
