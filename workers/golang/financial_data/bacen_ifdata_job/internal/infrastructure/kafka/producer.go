package kafka

import (
	"fmt"
	"log/slog"

	"github.com/IBM/sarama"
)

// VerifyConnectivity checks that Kafka brokers are reachable. Call this at startup
// before running any job so the process exits early if Kafka is not available.
func VerifyConnectivity(brokers []string, logger *slog.Logger) error {
	if len(brokers) == 0 {
		return fmt.Errorf("no Kafka brokers configured")
	}
	config := sarama.NewConfig()
	config.Version = sarama.V2_8_0_0
	client, err := sarama.NewClient(brokers, config)
	if err != nil {
		return fmt.Errorf("Kafka not available: %w", err)
	}
	defer client.Close()
	if err := client.RefreshMetadata(); err != nil {
		return fmt.Errorf("Kafka not available (refresh metadata): %w", err)
	}
	logger.Info("Kafka connectivity verified", "brokers", brokers)
	return nil
}

// NewSyncProducer creates a new Kafka synchronous producer
func NewSyncProducer(brokers []string, compressionType string, logger *slog.Logger) (sarama.SyncProducer, error) {
	config := sarama.NewConfig()
	config.Producer.RequiredAcks = sarama.WaitForAll // Wait for all replicas
	config.Producer.Retry.Max = 5
	config.Producer.Return.Successes = true
	config.Producer.Return.Errors = true

	// Set compression type
	switch compressionType {
	case "gzip":
		config.Producer.Compression = sarama.CompressionGZIP
	case "snappy":
		config.Producer.Compression = sarama.CompressionSnappy
	case "lz4":
		config.Producer.Compression = sarama.CompressionLZ4
	case "zstd":
		config.Producer.Compression = sarama.CompressionZSTD
	default:
		config.Producer.Compression = sarama.CompressionNone
	}

	// Set version to support all features
	config.Version = sarama.V2_8_0_0

	logger.Info("creating Kafka sync producer",
		"brokers", brokers,
		"compression", compressionType)

	producer, err := sarama.NewSyncProducer(brokers, config)
	if err != nil {
		return nil, fmt.Errorf("failed to create sync producer: %w", err)
	}

	logger.Info("successfully created Kafka sync producer")

	return producer, nil
}
