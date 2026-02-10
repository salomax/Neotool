package database

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/invistus/bacen-ifdata/internal/shared"
)

// NewConnectionPool creates a new PostgreSQL connection pool with retry logic
func NewConnectionPool(ctx context.Context, connString string, logger *slog.Logger) (*pgxpool.Pool, error) {
	logger.Info("creating PostgreSQL connection pool")

	var pool *pgxpool.Pool

	err := shared.Retry(
		ctx,
		shared.RetryPolicy{
			MaxRetries:        5,
			InitialDelay:      1 * time.Second,
			MaxDelay:          10 * time.Second,
			BackoffMultiplier: 2.0,
			Jitter:            true,
		},
		func(ctx context.Context) error {
			var err error
			pool, err = pgxpool.New(ctx, connString)
			if err != nil {
				return fmt.Errorf("failed to create connection pool: %w", err)
			}

			if err := pool.Ping(ctx); err != nil {
				pool.Close()
				pool = nil
				return fmt.Errorf("failed to ping database: %w", err)
			}

			return nil
		},
		func(err error) bool {
			return true // all connection errors are retryable
		},
		logger,
	)

	if err != nil {
		return nil, err
	}

	logger.Info("successfully connected to PostgreSQL",
		"max_conns", pool.Config().MaxConns,
		"min_conns", pool.Config().MinConns)

	return pool, nil
}
