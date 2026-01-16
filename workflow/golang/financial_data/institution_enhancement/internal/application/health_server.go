package application

import (
	"log/slog"

	"github.com/IBM/sarama"
	"github.com/jackc/pgx/v5/pgxpool"
	sharedhealth "github.com/salomax/invistus/workflow/go/shared/health"
)

// HealthServer wraps the shared health server to maintain compatibility
type HealthServer struct {
	*sharedhealth.HealthServer
}

// NewHealthServer creates a new health check server using the shared implementation
func NewHealthServer(
	port int,
	dbPool *pgxpool.Pool,
	consumerGroup sarama.ConsumerGroup,
	logger *slog.Logger,
) *HealthServer {
	// Create health checks
	checks := []sharedhealth.HealthCheck{
		sharedhealth.NewDatabaseHealthCheck(dbPool, logger),
		sharedhealth.NewKafkaHealthCheck(consumerGroup, logger),
	}

	return &HealthServer{
		HealthServer: sharedhealth.NewHealthServer(port, checks, logger),
	}
}
