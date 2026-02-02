package application

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"
)

// Runner orchestrates the lifecycle of a reusable ETL job.
type Runner struct {
	config       *Config
	healthServer *HealthServer
	logger       *slog.Logger
}

// NewRunner creates a new Runner that can wrap any ETL job.
func NewRunner(config *Config, healthServer *HealthServer, logger *slog.Logger) *Runner {
	return &Runner{
		config:       config,
		healthServer: healthServer,
		logger:       logger,
	}
}

// Run executes the provided job function within a managed context that handles signals, health checks, and logging.
func (r *Runner) Run(ctx context.Context, jobName string, job func(context.Context) error) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	healthStarted := false
	if r.config.HealthCheck.Enabled && r.healthServer != nil {
		if err := r.healthServer.Start(); err != nil {
			return fmt.Errorf("failed to start health server: %w", err)
		}
		healthStarted = true
		r.healthServer.SetLive(true)
		r.healthServer.SetReady(false)
	}

	if healthStarted {
		defer func() {
			shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer shutdownCancel()
			if err := r.healthServer.Stop(shutdownCtx); err != nil {
				r.logger.Error("failed to shut down health server", "error", err)
			}
		}()
	}

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	done := make(chan struct{})
	go func() {
		select {
		case sig := <-sigChan:
			r.logger.Info("shutdown signal received", "signal", sig)
			if r.healthServer != nil {
				r.healthServer.SetLive(false)
				r.healthServer.SetReady(false)
			}
			cancel()
		case <-done:
		}
	}()
	defer func() {
		signal.Stop(sigChan)
		close(done)
	}()

	start := time.Now()
	r.logger.Info("starting ETL job", "job", jobName)

	err := job(ctx)
	duration := time.Since(start)

	if r.healthServer != nil {
		r.healthServer.SetLive(false)
		r.healthServer.SetReady(false)
	}

	if err != nil {
		r.logger.Error("ETL job failed", "job", jobName, "duration", duration, "error", err)
		return err
	}

	r.logger.Info("ETL job completed", "job", jobName, "duration", duration)
	return nil
}
