package health

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/IBM/sarama"
	"github.com/jackc/pgx/v5/pgxpool"
)

// DatabaseHealthCheck checks database connectivity
type DatabaseHealthCheck struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewDatabaseHealthCheck creates a new database health check
func NewDatabaseHealthCheck(pool *pgxpool.Pool, logger *slog.Logger) *DatabaseHealthCheck {
	return &DatabaseHealthCheck{
		pool:   pool,
		logger: logger,
	}
}

// Name returns the name of the health check
func (c *DatabaseHealthCheck) Name() string {
	return "DATABASE"
}

// Check performs the health check
func (c *DatabaseHealthCheck) Check(ctx context.Context) error {
	return c.pool.Ping(ctx)
}

// KafkaHealthCheck checks Kafka consumer group health
// Note: This doesn't consume from the error channel to avoid interfering
// with the main error monitoring goroutine
type KafkaHealthCheck struct {
	consumerGroup sarama.ConsumerGroup
	logger        *slog.Logger
}

// NewKafkaHealthCheck creates a new Kafka health check
func NewKafkaHealthCheck(consumerGroup sarama.ConsumerGroup, logger *slog.Logger) *KafkaHealthCheck {
	return &KafkaHealthCheck{
		consumerGroup: consumerGroup,
		logger:        logger,
	}
}

// Name returns the name of the health check
func (c *KafkaHealthCheck) Name() string {
	return "KAFKA"
}

// Check performs the health check
// Note: We don't check the error channel here to avoid consuming errors
// that should be handled by the main error monitoring goroutine.
// The readiness check relies on the isReady flag being set correctly
// by the consumer runner based on actual connectivity.
func (c *KafkaHealthCheck) Check(ctx context.Context) error {
	// Kafka consumer group exists, assume it's healthy if we got this far
	// The main consumer loop will set isReady=false if there are actual issues
	if c.consumerGroup == nil {
		return fmt.Errorf("Kafka consumer group is nil")
	}
	return nil
}

