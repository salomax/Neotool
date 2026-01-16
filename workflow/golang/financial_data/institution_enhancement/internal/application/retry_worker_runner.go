package application

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/database"
)

// RetryWorkerRunner continuously retries failed institution enhancement tasks
type RetryWorkerRunner struct {
	config       *Config
	dbPool       *pgxpool.Pool
	healthServer *HealthServer
	logger       *slog.Logger
}

// NewRetryWorkerRunner creates a new retry worker runner
func NewRetryWorkerRunner(
	config *Config,
	dbPool *pgxpool.Pool,
	healthServer *HealthServer,
	logger *slog.Logger,
) *RetryWorkerRunner {
	return &RetryWorkerRunner{
		config:       config,
		dbPool:       dbPool,
		healthServer: healthServer,
		logger:       logger,
	}
}

// Run starts the retry worker loop
func (rwr *RetryWorkerRunner) Run(ctx context.Context) error {
	rwr.logger.Info("starting retry worker")

	// Setup context with cancellation
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Setup signal handling for graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		rwr.logger.Info("received shutdown signal", "signal", sig)
		if rwr.healthServer != nil {
			rwr.healthServer.SetLive(false)
			rwr.healthServer.SetReady(false)
		}
		cancel()
	}()

	// Start health server if enabled
	if rwr.config.HealthCheck.Enabled && rwr.healthServer != nil {
		if err := rwr.healthServer.Start(ctx); err != nil {
			return fmt.Errorf("failed to start health server: %w", err)
		}
		defer rwr.healthServer.Shutdown(context.Background())
		rwr.healthServer.SetLive(true)
		rwr.healthServer.SetReady(true)
	}

	// Create repository for querying failed institutions
	repo := database.NewParentInstitutionRepository(rwr.dbPool, rwr.logger)

	// Poll interval for checking failed institutions
	pollInterval := 5 * time.Minute
	rwr.logger.Info("retry worker ready", "poll_interval", pollInterval)

	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	// Run initial check immediately
	if err := rwr.processFailedInstitutions(ctx, repo); err != nil {
		rwr.logger.Error("failed to process failed institutions", "error", err)
	}

	// Continuous polling loop
	for {
		select {
		case <-ticker.C:
			if err := rwr.processFailedInstitutions(ctx, repo); err != nil {
				rwr.logger.Error("failed to process failed institutions", "error", err)
			}
		case <-ctx.Done():
			rwr.logger.Info("retry worker stopped gracefully")
			return nil
		}
	}
}

// processFailedInstitutions queries and reprocesses failed institutions
func (rwr *RetryWorkerRunner) processFailedInstitutions(ctx context.Context, repo *database.ParentInstitutionRepository) error {
	rwr.logger.Info("checking for failed institutions to retry")

	// Query institutions that need retry
	// This would typically query institutions with certain error states
	// and attempt to reprocess them

	// For now, this is a placeholder that logs the intent
	// The actual implementation would depend on how failures are tracked in the database
	rwr.logger.Info("retry check completed - implementation pending based on failure tracking mechanism")

	return nil
}
