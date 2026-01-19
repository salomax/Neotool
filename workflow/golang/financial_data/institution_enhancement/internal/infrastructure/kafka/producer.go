package kafka

import (
	"log/slog"

	"github.com/IBM/sarama"
)

// NewSyncProducer creates a new Kafka sync producer for DLQ
func NewSyncProducer(brokers []string, logger *slog.Logger) (sarama.SyncProducer, error) {
	config := sarama.NewConfig()
	config.Version = sarama.V3_0_0_0
	config.Producer.RequiredAcks = sarama.WaitForAll // Wait for all in-sync replicas
	config.Producer.Retry.Max = 3
	config.Producer.Return.Successes = true
	config.Producer.Return.Errors = true
	config.Producer.Idempotent = true // Enable idempotence
	config.Producer.Compression = sarama.CompressionGZIP
	config.Net.MaxOpenRequests = 1 // Required for idempotent producer

	logger.Info("Creating Kafka sync producer", "brokers", brokers)

	producer, err := sarama.NewSyncProducer(brokers, config)
	if err != nil {
		return nil, err
	}

	logger.Info("Kafka sync producer created successfully")

	return producer, nil
}
