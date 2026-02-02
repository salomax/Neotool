package main

import (
	"context"
	"log"
	"log/slog"
	"os"
	"time"

	"github.com/invistus/bacen-ifdata/internal/application"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/bacen"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
	"github.com/invistus/bacen-ifdata/internal/processor"
	"github.com/invistus/bacen-ifdata/internal/shared"
)

func main() {
	// Setup logger (text output for terminal readability)
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("bacen-ifdata retry worker starting")

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

	// Create repositories
	extractTaskRepo := database.NewExtractTaskRepository(dbPool, logger)
	rawDataRepo := database.NewRawDataRepository(dbPool, logger)

	// Create extractor
	extractor := processor.NewExtractor(bacenClient, extractTaskRepo, rawDataRepo, config.Extraction.ToDomain(), logger)

	// Create health server (no Kafka in retry worker)
	healthServer := application.NewHealthServer(config.HealthCheck.Port, dbPool, nil, logger)

	// Create retry worker runner
	runner := application.NewRetryWorkerRunner(
		config,
		extractor,
		healthServer,
		logger,
	)

	// Run
	if err := runner.Run(ctx); err != nil {
		logger.Error("retry worker failed", "error", err)
		os.Exit(1)
	}

	logger.Info("retry worker stopped")
}
