package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
)

// NewConnectionPool creates a new PostgreSQL connection pool
func NewConnectionPool(ctx context.Context, connString string, logger *slog.Logger) (*pgxpool.Pool, error) {
	logger.Info("creating PostgreSQL connection pool")

	pool, err := pgxpool.New(ctx, connString)
	if err != nil {
		return nil, fmt.Errorf("failed to create connection pool: %w", err)
	}

	// Verify connection
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	logger.Info("successfully connected to PostgreSQL",
		"max_conns", pool.Config().MaxConns,
		"min_conns", pool.Config().MinConns)

	return pool, nil
}
