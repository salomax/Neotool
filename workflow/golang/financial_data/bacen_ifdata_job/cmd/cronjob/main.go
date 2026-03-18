package main

import (
	"context"
	"flag"
	"log"
	"log/slog"
	"os"
	"time"

	"github.com/invistus/bacen-ifdata/internal/application"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/bacen"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/kafka"
	"github.com/invistus/bacen-ifdata/internal/processor"
	"github.com/invistus/bacen-ifdata/internal/shared"
)

func main() {
	// Parse command-line flags
	anoMes := flag.String("ano-mes", "", "Force specific quarter (YYYYMM)")
	forceRegistryRefresh := flag.Bool("force-registry-refresh", false, "Force registry refresh even if exists")
	flag.Parse()

	// Setup logger (text output for terminal readability)
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("bacen-ifdata cronjob starting",
		"ano_mes", *anoMes,
		"force_registry_refresh", *forceRegistryRefresh)

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

	// Create health server (with Kafka producer for health checks)
	healthServer := application.NewHealthServer(config.HealthCheck.Port, dbPool, kafkaProducer, logger)

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
	if err := runner.Run(ctx, *anoMes, *forceRegistryRefresh); err != nil {
		logger.Error("cronjob failed", "error", err)
		os.Exit(1)
	}

	logger.Info("cronjob completed successfully")
}
