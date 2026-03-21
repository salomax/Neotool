package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/shared"
)

func NewConnectionPool(ctx context.Context, connString string, logger *slog.Logger) (*pgxpool.Pool, error) {
	logger.Info("creating PostgreSQL connection pool")

	var pool *pgxpool.Pool
	err := shared.Retry(
		ctx,
		shared.DBConnRetryPolicy,
		func(ctx context.Context) error {
			var err error
			cfg, err := pgxpool.ParseConfig(connString)
			if err != nil {
				return fmt.Errorf("parsing pool config: %w", err)
			}

			// Disable implicit prepared statements to stay compatible with
			// transaction-pooled/proxied PostgreSQL connections.
			cfg.ConnConfig.DefaultQueryExecMode = pgx.QueryExecModeExec

			pool, err = pgxpool.NewWithConfig(ctx, cfg)
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
		func(error) bool { return true },
		logger,
	)
	if err != nil {
		return nil, err
	}

	logger.Info("connected to PostgreSQL", "max_conns", pool.Config().MaxConns, "min_conns", pool.Config().MinConns)
	return pool, nil
}
