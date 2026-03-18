package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/shared"
)

// NewConnectionPool creates a pgxpool with retry logic on startup.
func NewConnectionPool(ctx context.Context, connString string, logger *slog.Logger) (*pgxpool.Pool, error) {
	logger.Info("creating PostgreSQL connection pool")

	var pool *pgxpool.Pool

	err := shared.Retry(
		ctx,
		shared.DBConnRetryPolicy,
		func(ctx context.Context) error {
			var err error
			pool, err = pgxpool.New(ctx, connString)
			if err != nil {
				return fmt.Errorf("creating pool: %w", err)
			}
			if err = pool.Ping(ctx); err != nil {
				pool.Close()
				pool = nil
				return fmt.Errorf("pinging database: %w", err)
			}
			return nil
		},
		func(error) bool { return true }, // all connection errors are retryable on startup
		logger,
	)
	if err != nil {
		return nil, err
	}

	logger.Info("connected to PostgreSQL",
		"max_conns", pool.Config().MaxConns,
		"min_conns", pool.Config().MinConns,
	)
	return pool, nil
}
