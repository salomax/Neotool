package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
)

// NewConnectionPool creates a new PostgreSQL connection pool
func NewConnectionPool(ctx context.Context, connString string, logger *slog.Logger) (*pgxpool.Pool, error) {
	logger.Info("Creating PostgreSQL connection pool", "connString", maskPassword(connString))

	config, err := pgxpool.ParseConfig(connString)
	if err != nil {
		return nil, fmt.Errorf("failed to parse connection string: %w", err)
	}

	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, fmt.Errorf("failed to create connection pool: %w", err)
	}

	// Test connection
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	logger.Info("PostgreSQL connection pool created successfully",
		"minConns", config.MinConns,
		"maxConns", config.MaxConns)

	return pool, nil
}

// maskPassword masks the password in connection string for logging
func maskPassword(connString string) string {
	// Simple masking - replace password value with ****
	// This is a basic implementation, could be improved with regex
	return "postgresql://****"
}
