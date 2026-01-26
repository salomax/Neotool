package main

import (
	"context"
	"flag"
	"fmt"
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
	issuerCode := flag.String("issuer", "", "Process only this issuer code (e.g., FITCH). Required for standalone mode.")
	institutionID := flag.String("institution", "", "Process only this institution ID (UUID). Optional.")
	dryRun := flag.Bool("dry-run", false, "Dry run mode: fetch but don't persist changes.")
	verbose := flag.Bool("verbose", false, "Enable verbose logging (debug level).")
	flag.Parse()

	// Validate flags
	if *issuerCode == "" {
		fmt.Fprintln(os.Stderr, "Error: --issuer flag is required for standalone mode")
		flag.Usage()
		os.Exit(1)
	}

	// Setup structured logging
	logLevel := slog.LevelInfo
	if *verbose {
		logLevel = slog.LevelDebug
	}

	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	}))
	slog.SetDefault(logger)

	logger.Info("starting ratings standalone",
		"issuer", *issuerCode,
		"institution_id", *institutionID,
		"dry_run", *dryRun,
	)

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

	// Initialize repositories
	issuerRepo := database.NewIssuerRepository(pool, logger)
	mappingRepo := database.NewMappingRepository(pool, logger)
	ratingRepo := database.NewRatingRepository(pool, logger)
	evidenceRepo := database.NewEvidenceRepository(pool, logger)
	historyRepo := database.NewHistoryRepository(pool, logger)

	// Initialize issuer client factory
	clientFactory := issuer.NewFactory(
		cfg.Fitch.Timeout,
		cfg.Fitch.Throttle,
		cfg.Retry.ToRetryPolicy(),
		logger,
	)

	// Get issuer
	issuerEntity, err := issuerRepo.GetByCode(ctx, *issuerCode)
	if err != nil {
		logger.Error("failed to get issuer", "error", err, "issuer", *issuerCode)
		os.Exit(1)
	}

	// Initialize strategy and registrar
	strategyFactory := processor.NewStrategyFactory(mappingRepo, clientFactory, logger)
	registrar := processor.NewDefaultRegistrar(ratingRepo, historyRepo, evidenceRepo, logger)

	strategy, err := strategyFactory.CreateStrategy(*issuerEntity)
	if err != nil {
		logger.Error("failed to create strategy", "error", err)
		os.Exit(1)
	}

	// Get mappings
	mappings, _, err := strategy.ListMappings(ctx, *issuerEntity)
	if err != nil {
		logger.Error("failed to get mappings", "error", err)
		os.Exit(1)
	}

	if len(mappings) == 0 {
		logger.Error("no mappings found for issuer", "issuer", *issuerCode)
		os.Exit(1)
	}

	// Filter to specific institution if provided
	if *institutionID != "" {
		var filtered []domain.InstitutionIssuerMapping
		for _, m := range mappings {
			if m.FinancialInstitutionID.String() == *institutionID {
				filtered = append(filtered, m)
				break
			}
		}
		if len(filtered) == 0 {
			logger.Error("institution not found in mappings",
				"institution_id", *institutionID,
				"issuer", *issuerCode,
			)
			os.Exit(1)
		}
		mappings = filtered
	}

	logger.Info("processing mappings",
		"count", len(mappings),
		"issuer", *issuerCode,
	)

	// Process each mapping
	startTime := time.Now()
	var totalCreated, totalUpdated, totalUnchanged, totalErrors int

	for _, mapping := range mappings {
		logger.Info("fetching ratings",
			"institution_id", mapping.FinancialInstitutionID,
			"slug", mapping.Config.Slug,
		)

		// Fetch ratings
		result, err := strategy.FetchRatings(ctx, mapping)
		if err != nil {
			logger.Error("failed to fetch ratings",
				"institution_id", mapping.FinancialInstitutionID,
				"error", err,
			)
			totalErrors++
			continue
		}

		if result.Error != nil {
			logger.Error("fetch returned error",
				"institution_id", mapping.FinancialInstitutionID,
				"error", result.Error,
			)
			totalErrors++
			continue
		}

		logger.Info("fetched ratings",
			"institution_id", mapping.FinancialInstitutionID,
			"rating_count", len(result.Ratings),
		)

		// Print ratings
		for _, rating := range result.Ratings {
			logger.Info("rating",
				"name", rating.RatingName,
				"label", rating.RatingLabel,
				"date", rating.RatingDate.Format("2006-01-02"),
				"hash", rating.ContentHash[:16]+"...",
			)
		}

		if *dryRun {
			logger.Info("dry run: skipping persistence",
				"institution_id", mapping.FinancialInstitutionID,
			)
			continue
		}

		instStats, err := registrar.Register(ctx, mapping, result)
		if err != nil {
			logger.Error("failed to register ratings",
				"institution_id", mapping.FinancialInstitutionID,
				"error", err,
			)
			totalErrors++
			continue
		}

		totalCreated += instStats.RatingsCreated
		totalUpdated += instStats.RatingsUpdated
		totalUnchanged += instStats.RatingsUnchanged
	}

	duration := time.Since(startTime)

	logger.Info("standalone completed",
		"created", totalCreated,
		"updated", totalUpdated,
		"unchanged", totalUnchanged,
		"errors", totalErrors,
		"duration", duration,
	)

	if totalErrors > 0 {
		os.Exit(1)
	}
}
