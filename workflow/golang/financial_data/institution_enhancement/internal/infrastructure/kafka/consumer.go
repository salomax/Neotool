package kafka

import (
	"log/slog"

	"github.com/IBM/sarama"
)

// NewConsumerGroup creates a new Kafka consumer group
func NewConsumerGroup(brokers []string, consumerGroup string, logger *slog.Logger) (sarama.ConsumerGroup, error) {
	config := sarama.NewConfig()
	config.Version = sarama.V3_0_0_0 // Kafka 3.0+
	config.Consumer.Group.Rebalance.Strategy = sarama.BalanceStrategyRoundRobin
	config.Consumer.Offsets.Initial = sarama.OffsetOldest // Start from earliest if no committed offset

	// Consumer configuration
	config.Consumer.MaxProcessingTime = 600_000_000_000 // 10 minutes (600 seconds in nanoseconds)
	config.Consumer.Group.Session.Timeout = 60_000_000_000 // 60 seconds
	config.Consumer.Group.Heartbeat.Interval = 10_000_000_000 // 10 seconds

	// Fetch configuration for sequential processing
	config.Consumer.Fetch.Min = 1
	config.Consumer.Fetch.Default = 1024 * 1024 // 1MB

	// Return errors from consumer
	config.Consumer.Return.Errors = true

	logger.Info("Creating Kafka consumer group",
		"brokers", brokers,
		"consumer_group", consumerGroup,
		"version", config.Version)

	cg, err := sarama.NewConsumerGroup(brokers, consumerGroup, config)
	if err != nil {
		return nil, err
	}

	logger.Info("Kafka consumer group created successfully")

	return cg, nil
}
