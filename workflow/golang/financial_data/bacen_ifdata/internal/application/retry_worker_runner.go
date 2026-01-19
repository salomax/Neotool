package application

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/invistus/bacen-ifdata/internal/processor"
)

// RetryWorkerRunner continuously retries failed extract tasks
type RetryWorkerRunner struct {
	config       *Config
	extractor    *processor.Extractor
	healthServer *HealthServer
	logger       *slog.Logger
}

// NewRetryWorkerRunner creates a new retry worker runner
func NewRetryWorkerRunner(
	config *Config,
	extractor *processor.Extractor,
	healthServer *HealthServer,
	logger *slog.Logger,
) *RetryWorkerRunner {
	return &RetryWorkerRunner{
		config:       config,
		extractor:    extractor,
		healthServer: healthServer,
		logger:       logger,
	}
}

// Run starts the retry worker loop
func (rwr *RetryWorkerRunner) Run(ctx context.Context) error {
	runner := NewRunner(rwr.config, rwr.healthServer, rwr.logger)
	return runner.Run(ctx, "retry-worker", func(ctx context.Context) error {
		rwr.logger.Info("retry worker ready",
			"max_workers", rwr.config.Extraction.MaxWorkers,
			"poll_interval", rwr.config.Extraction.TaskPollIntervalDur)
		if rwr.config.HealthCheck.Enabled {
			rwr.healthServer.SetReady(true)
		}

		if err := rwr.extractor.RunExtractionWorkers(ctx, rwr.config.Extraction.MaxWorkers); err != nil {
			if ctx.Err() != nil {
				rwr.logger.Info("retry worker stopped gracefully")
				return nil
			}
			return fmt.Errorf("retry worker failed: %w", err)
		}

		rwr.logger.Info("retry worker completed")
		return nil
	})
}
