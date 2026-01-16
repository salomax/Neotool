package kafkaconsumer

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/IBM/sarama"
)

// MessageProcessor defines the interface for processing messages
type MessageProcessor interface {
	ProcessMessage(ctx context.Context, message []byte, key string, topic string, partition int32, offset int64) error
}

// DLQPublisherInterface defines the interface for DLQ publishing
type DLQPublisherInterface interface {
	Publish(
		originalMessage []byte,
		originalKey string,
		originalTopic string,
		err error,
		retryCount int,
		isRetryable bool,
	) error
	Close() error
}

// ConsumerConfig holds configuration for the base consumer
type ConsumerConfig struct {
	Brokers       []string
	Topic         string
	ConsumerGroup string
	DLQTopic      string
}

// BaseConsumer provides common Kafka consumer functionality
type BaseConsumer struct {
	config        ConsumerConfig
	consumerGroup sarama.ConsumerGroup
	processor     MessageProcessor
	dlqPublisher  DLQPublisherInterface
	logger        *slog.Logger
}

// NewBaseConsumer creates a new base consumer
func NewBaseConsumer(
	config ConsumerConfig,
	consumerGroup sarama.ConsumerGroup,
	processor MessageProcessor,
	dlqPublisher DLQPublisherInterface,
	logger *slog.Logger,
) *BaseConsumer {
	return &BaseConsumer{
		config:        config,
		consumerGroup: consumerGroup,
		processor:     processor,
		dlqPublisher:  dlqPublisher,
		logger:        logger,
	}
}

// Run starts the consumer loop with graceful shutdown
func (bc *BaseConsumer) Run(ctx context.Context) error {
	bc.logger.Info("Starting base consumer",
		"consumer_group", bc.config.ConsumerGroup,
		"topic", bc.config.Topic)

	// Create cancellable context
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Setup signal handling for graceful shutdown
	sigterm := make(chan os.Signal, 1)
	signal.Notify(sigterm, syscall.SIGINT, syscall.SIGTERM)

	// Create consumer handler
	handler := &baseConsumerHandler{
		consumer: bc,
		logger:   bc.logger,
	}

	// Consumer loop
	go func() {
		for {
			// Consume should be called inside an infinite loop, when a
			// server-side rebalance happens, the consumer session will need to be
			// recreated to get the new claims
			if err := bc.consumerGroup.Consume(ctx, []string{bc.config.Topic}, handler); err != nil {
				bc.logger.Error("Error from consumer", "error", err)
			}

			// Check if context was cancelled, signaling that the consumer should stop
			if ctx.Err() != nil {
				bc.logger.Info("Context cancelled, stopping consumer")
				return
			}

			bc.logger.Info("Rebalancing...")
		}
	}()

	// Monitor consumer errors
	go func() {
		for err := range bc.consumerGroup.Errors() {
			bc.logger.Error("Consumer group error", "error", err)
		}
	}()

	bc.logger.Info("Base consumer started, consuming messages")

	// Wait for signals
	select {
	case <-sigterm:
		bc.logger.Info("Received termination signal")
	case <-ctx.Done():
		bc.logger.Info("Context cancelled")
	}

	// Cancel context to stop consumer
	cancel()

	bc.logger.Info("Shutting down base consumer")

	return nil
}

// Close closes the consumer and DLQ publisher
func (bc *BaseConsumer) Close() error {
	if bc.dlqPublisher != nil {
		if err := bc.dlqPublisher.Close(); err != nil {
			return fmt.Errorf("failed to close DLQ publisher: %w", err)
		}
	}
	if bc.consumerGroup != nil {
		return bc.consumerGroup.Close()
	}
	return nil
}

// baseConsumerHandler implements sarama.ConsumerGroupHandler
type baseConsumerHandler struct {
	consumer *BaseConsumer
	logger   *slog.Logger
}

// Setup is run at the beginning of a new session, before ConsumeClaim
func (h *baseConsumerHandler) Setup(sarama.ConsumerGroupSession) error {
	h.logger.Info("Consumer session setup")
	return nil
}

// Cleanup is run at the end of a session, once all ConsumeClaim goroutines have exited
func (h *baseConsumerHandler) Cleanup(sarama.ConsumerGroupSession) error {
	h.logger.Info("Consumer session cleanup")
	return nil
}

// ConsumeClaim processes messages from the topic
func (h *baseConsumerHandler) ConsumeClaim(session sarama.ConsumerGroupSession, claim sarama.ConsumerGroupClaim) error {
	h.logger.Info("ConsumeClaim started",
		"topic", claim.Topic(),
		"partition", claim.Partition(),
		"initial_offset", claim.InitialOffset(),
		"high_water_mark", claim.HighWaterMarkOffset())

	// Process messages
	for {
		select {
		case message := <-claim.Messages():
			if message == nil {
				h.logger.Warn("Received nil message, exiting claim")
				return nil
			}

			h.logger.Info("Received message",
				"topic", message.Topic,
				"partition", message.Partition,
				"offset", message.Offset,
				"key", string(message.Key))

			// Process message
			if err := h.consumer.processor.ProcessMessage(
				session.Context(),
				message.Value,
				string(message.Key),
				message.Topic,
				message.Partition,
				message.Offset,
			); err != nil {
				h.logger.Error("Failed to process message",
					"error", err,
					"offset", message.Offset)

				// Publish to DLQ if publisher is available
				if h.consumer.dlqPublisher != nil {
					// Note: retryCount and isRetryable should be determined by the processor
					// For now, we'll use default values
					if dlqErr := h.consumer.dlqPublisher.Publish(
						message.Value,
						string(message.Key),
						message.Topic,
						err,
						0, // retryCount - should be passed from processor
						true, // isRetryable - should be determined by error type
					); dlqErr != nil {
						h.logger.Error("Failed to publish to DLQ", "error", dlqErr)
					}
				}
				// Don't mark message - it will be reprocessed
				continue
			}

			// Mark message as processed
			session.MarkMessage(message, "")
			h.logger.Info("Message processed successfully",
				"offset", message.Offset)

		case <-session.Context().Done():
			return nil
		}
	}
}


