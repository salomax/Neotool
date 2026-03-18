package application

import (
	"context"
	"log/slog"
)

// Dispatcher orchestrates tasks for indicator ingestion
type Dispatcher interface {
	Dispatch(ctx context.Context) error
}

// CronjobRunner runs the indicators ingestion job
type CronjobRunner struct {
	dispatcher Dispatcher
	logger     *slog.Logger
}

// NewCronjobRunner creates a new cronjob runner
func NewCronjobRunner(dispatcher Dispatcher, logger *slog.Logger) *CronjobRunner {
	return &CronjobRunner{dispatcher: dispatcher, logger: logger}
}

// Run executes the job
func (r *CronjobRunner) Run(ctx context.Context) error {
	r.logger.Info("starting indicators job")
	return r.dispatcher.Dispatch(ctx)
}
