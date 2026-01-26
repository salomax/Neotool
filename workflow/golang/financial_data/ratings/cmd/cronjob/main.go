package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/invistus/ratings/internal/application"
	"github.com/invistus/ratings/internal/domain"
	"github.com/invistus/ratings/internal/infrastructure/database"
	"github.com/invistus/ratings/internal/infrastructure/issuer"
	"github.com/invistus/ratings/internal/processor"
)

func main() {
	// Parse command line flags
	issuerCode := flag.String("issuer", "", "Process only this issuer code (e.g., FITCH). If empty, processes all enabled issuers.")
	flag.Parse()

	// Setup structured logging
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
	slog.SetDefault(logger)

	logger.Info("starting ratings cronjob")

	// Load configuration
	cfg, err := application.LoadConfig()
	if err != nil {
		logger.Error("failed to load config", "error", err)
		os.Exit(1)
	}

	// Setup context with cancellation
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Handle graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigCh
		logger.Info("received signal, shutting down", "signal", sig)
		cancel()
	}()

	// Initialize database connection
	pool, err := database.NewConnectionPool(ctx, cfg.Database.ConnectionString(), logger)
	if err != nil {
		logger.Error("failed to connect to database", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	// Initialize health server
	var healthServer *application.HealthServer
	if cfg.HealthCheck.Enabled {
		healthServer = application.NewHealthServer(cfg.HealthCheck.Port, pool, logger)
		healthServer.Start()
		defer func() {
			shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer shutdownCancel()
			healthServer.Stop(shutdownCtx)
		}()
	}

	// Initialize repositories
	issuerRepo := database.NewIssuerRepository(pool, logger)
	mappingRepo := database.NewMappingRepository(pool, logger)
	ratingRepo := database.NewRatingRepository(pool, logger)
	historyRepo := database.NewHistoryRepository(pool, logger)
	evidenceRepo := database.NewEvidenceRepository(pool, logger)

	// Initialize issuer client factory
	clientFactory := issuer.NewFactory(
		cfg.Fitch.Timeout,
		cfg.Fitch.Throttle,
		cfg.Retry.ToRetryPolicy(),
		logger,
	)

	// Initialize strategy factory and registrar
	strategyFactory := processor.NewStrategyFactory(mappingRepo, clientFactory, logger)
	registrar := processor.NewDefaultRegistrar(ratingRepo, historyRepo, evidenceRepo, logger)

	// Initialize ingester
	ingester := processor.NewRatingIngester(
		issuerRepo,
		strategyFactory,
		registrar,
		logger,
	)

	// Run ingestion
	var stats *domain.IngestionStats
	if *issuerCode != "" {
		logger.Info("processing single issuer", "issuer", *issuerCode)
		stats, err = ingester.ProcessIssuer(ctx, *issuerCode)
	} else {
		stats, err = ingester.ProcessAll(ctx)
	}

	if err != nil {
		logger.Error("ingestion failed", "error", err)
		os.Exit(1)
	}

	logger.Info("ratings cronjob completed successfully",
		"issuers_processed", stats.IssuersProcessed,
		"institutions_processed", stats.InstitutionsProcessed,
		"ratings_created", stats.RatingsCreated,
		"ratings_updated", stats.RatingsUpdated,
		"ratings_unchanged", stats.RatingsUnchanged,
		"errors", stats.Errors,
		"duration", stats.Duration,
	)
}
