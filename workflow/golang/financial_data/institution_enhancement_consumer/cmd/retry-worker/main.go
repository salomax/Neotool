package main

import (
	"context"
	"log"
	"log/slog"
	"os"

	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/application"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/database"
)

func main() {
	// Setup logger
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("institution-enhancement retry worker starting")

	// Load configuration
	config, err := application.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// Create context
	ctx := context.Background()

	// Create database connection pool
	dbPool, err := database.NewConnectionPool(ctx, config.Database.ConnectionString(), logger)
	if err != nil {
		log.Fatalf("Failed to create database pool: %v", err)
	}
	defer dbPool.Close()

	logger.Info("Database connection pool created")

	// Create health server
	healthServer := application.NewHealthServer(config.HealthCheck.Port, dbPool, nil, logger)

	// Create retry worker runner
	runner := application.NewRetryWorkerRunner(
		config,
		dbPool,
		healthServer,
		logger,
	)

	logger.Info("Retry worker runner initialized")

	// Run
	if err := runner.Run(ctx); err != nil {
		logger.Error("retry worker failed", "error", err)
		os.Exit(1)
	}

	logger.Info("retry worker stopped")
}
