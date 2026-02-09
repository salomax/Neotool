package main

import (
	"context"
	"flag"
	"log"
	"log/slog"
	"os"
	"time"

	"github.com/invistus/bacen-ifdata/internal/application"
	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/bacen"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/kafka"
	"github.com/invistus/bacen-ifdata/internal/processor"
	"github.com/invistus/bacen-ifdata/internal/shared"
)

func main() {
	// Parse command-line flags
	anoMes := flag.String("ano-mes", "", "Target quarter (YYYYMM) - required for manual execution")
	forceRegistryRefresh := flag.Bool("force-registry-refresh", false, "Force registry refresh even if exists")
	promoteOnly := flag.Bool("promote-only", false, "Only promote data (skip extraction) - requires ano-mes")
	institutionOnly := flag.Bool("institution-only", false, "Only ingest institution registry (skip bank report extraction)")
	republishAll := flag.Bool("republish-all", false, "Republish all institution events for ano-mes from DB")
	flag.Parse()

	// Setup logger (text output for terminal readability)
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("bacen-ifdata CLI starting",
		"ano_mes", *anoMes,
		"force_registry_refresh", *forceRegistryRefresh,
		"promote_only", *promoteOnly,
		"institution_only", *institutionOnly,
		"republish_all", *republishAll)

	// Load configuration
	config, err := application.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	if err := config.Validate(); err != nil {
		log.Fatalf("Invalid config: %v", err)
	}

	// Create context
	ctx := context.Background()

	// Create database connection pool
	dbPool, err := database.NewConnectionPool(ctx, config.Database.ConnectionString(), logger)
	if err != nil {
		log.Fatalf("Failed to create database pool: %v", err)
	}
	defer dbPool.Close()

	// Create BACEN API client
	bacenRetryPolicy := shared.RetryPolicy{
		MaxRetries:        5,
		InitialDelay:      1 * time.Second,
		MaxDelay:          16 * time.Second,
		BackoffMultiplier: 2.0,
		Jitter:            true,
	}
	bacenClient := bacen.NewClient(
		config.BACEN.BaseURL,
		config.BACEN.Timeout,
		bacenRetryPolicy,
		config.BACEN.MaxRequestsPerSecond,
		config.BACEN.BurstSize,
		logger,
	)

	// Create Kafka producer
	kafkaProducer, err := kafka.NewSyncProducer(
		config.Kafka.BrokerList(),
		config.Kafka.CompressionType,
		logger,
	)
	if err != nil {
		log.Fatalf("Failed to create Kafka producer: %v", err)
	}
	defer kafkaProducer.Close()

	// Create Kafka publisher
	kafkaPublisher := kafka.NewInstitutionPublisher(
		kafkaProducer,
		config.Kafka.Topic,
		logger,
	)

	// Create repositories
	institutionRepo := database.NewInstitutionRepository(dbPool, logger)
	extractTaskRepo := database.NewExtractTaskRepository(dbPool, logger)
	rawDataRepo := database.NewRawDataRepository(dbPool, logger)
	bankReportRepo := database.NewBankReportRepository(dbPool, logger)
	loadControlRepo := database.NewLoadControlRepository(dbPool, logger)
	catalogRepo := database.NewReportCatalogRepository(dbPool, logger)

	// Create processors
	quarterDeterminer := processor.NewQuarterDeterminer(institutionRepo, logger)
	registryIngester := processor.NewRegistryIngester(bacenClient, institutionRepo, logger)
	catalogFetcher := processor.NewReportCatalogFetcher(bacenClient, catalogRepo, logger)
	taskCreator := processor.NewExtractTaskCreator(extractTaskRepo, logger)
	extractor := processor.NewExtractor(bacenClient, extractTaskRepo, rawDataRepo, config.Extraction.ToDomain(), logger)
	promoter := processor.NewPromoter(rawDataRepo, bankReportRepo, loadControlRepo, extractTaskRepo, logger)

	// Create health server (disabled for CLI, no Kafka in CLI mode)
	healthServer := application.NewHealthServer(config.HealthCheck.Port, dbPool, nil, logger)

	if *republishAll {
		// Determine quarters to republish
		var quarters []string
		if *anoMes != "" {
			quarters = []string{*anoMes}
		} else {
			quarters, err = institutionRepo.ListDistinctAnoMes(ctx)
			if err != nil {
				log.Fatalf("Failed to list quarters: %v", err)
			}
			if len(quarters) == 0 {
				log.Fatalf("No quarters found in database")
			}
		}

		logger.Info("republishing institution events from database", "quarters", len(quarters))

		totalPublished := 0
		totalFailed := 0
		for _, q := range quarters {
			institutions, err := institutionRepo.ListByAnoMes(ctx, q)
			if err != nil {
				log.Fatalf("Failed to list institutions for %s: %v", q, err)
			}
			if len(institutions) == 0 {
				logger.Info("no institutions found, skipping", "ano_mes", q)
				continue
			}

			changes := make([]domain.InstitutionChange, 0, len(institutions))
			for _, inst := range institutions {
				changes = append(changes, domain.InstitutionChange{
					Institution: *inst,
					ChangeType:  domain.ChangeTypeUpdated,
				})
			}

			published, failed, err := kafkaPublisher.PublishChanges(ctx, changes)
			if err != nil {
				logger.Error("failed to republish", "ano_mes", q, "error", err)
				totalFailed += len(changes)
				continue
			}

			totalPublished += published
			totalFailed += failed
			logger.Info("quarter republished", "ano_mes", q, "published", published, "failed", failed)
		}

		logger.Info("republish completed",
			"quarters_processed", len(quarters),
			"total_published", totalPublished,
			"total_failed", totalFailed)

		if totalFailed > 0 {
			os.Exit(1)
		}
		return
	}

	// Handle promote-only mode
	if *promoteOnly {
		if *anoMes == "" {
			log.Fatalf("ano-mes is required when using --promote-only flag")
		}

		logger.Info("running in promote-only mode", "ano_mes", *anoMes)

		// Fetch report catalog
		reports, err := catalogFetcher.FetchReports(ctx)
		if err != nil {
			log.Fatalf("Failed to fetch report catalog: %v", err)
		}

		if len(reports) == 0 {
			log.Fatalf("No reports found in catalog")
		}

		logger.Info("fetched report catalog", "report_count", len(reports))

		// Promote all reports (with parallel workers)
		rowsPromoted, err := promoter.PromoteAllReports(ctx, *anoMes, reports, config.Promotion.MaxWorkers)
		if err != nil {
			logger.Error("promotion failed", "error", err)
			os.Exit(1)
		}

		// Get final statistics
		stats, err := promoter.GetPromotionStats(ctx, *anoMes)
		if err != nil {
			logger.Error("failed to get promotion stats", "error", err)
		}

		logger.Info("promotion completed successfully",
			"ano_mes", *anoMes,
			"reports_processed", len(reports),
			"rows_promoted", rowsPromoted,
			"load_control_stats", stats)

		return
	}

	// Create cronjob runner
	runner := application.NewCronjobRunner(
		config,
		quarterDeterminer,
		registryIngester,
		catalogFetcher,
		taskCreator,
		extractor,
		promoter,
		kafkaPublisher,
		healthServer,
		logger,
	)

	// Run
	logger.Info("starting bacen-ifdata ingestion")
	if err := runner.Run(ctx, *anoMes, *forceRegistryRefresh, *institutionOnly); err != nil {
		logger.Error("ingestion failed", "error", err)
		os.Exit(1)
	}

	logger.Info("ingestion completed successfully")
}
