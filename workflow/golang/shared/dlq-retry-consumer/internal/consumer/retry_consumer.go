package consumer

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/IBM/sarama"
	"github.com/salomax/invistus/workflow/go/shared/dlq-retry-consumer/internal/categorizer"
	"github.com/salomax/invistus/workflow/go/shared/dlq-retry-consumer/internal/config"
)

// RetryConsumer handles DLQ message processing and retry logic
type RetryConsumer struct {
	config      *config.Config
	mapping     *config.DLQMapping
	categorizer *categorizer.Categorizer
	consumer    sarama.Consumer
	producer    sarama.SyncProducer
	logger      *slog.Logger
}

// NewRetryConsumer creates a new DLQ retry consumer
func NewRetryConsumer(
	cfg *config.Config,
	mapping *config.DLQMapping,
	consumer sarama.Consumer,
	producer sarama.SyncProducer,
	logger *slog.Logger,
) *RetryConsumer {
	errorCat := mapping.GetErrorCategorization(&cfg.Defaults)
	cat := categorizer.NewCategorizer(errorCat)

	return &RetryConsumer{
		config:      cfg,
		mapping:     mapping,
		categorizer: cat,
		consumer:    consumer,
		producer:    producer,
		logger:      logger,
	}
}

// ProcessDLQ processes all messages in the DLQ
func (rc *RetryConsumer) ProcessDLQ(ctx context.Context) error {
	rc.logger.Info("Starting DLQ processing",
		"dlq", rc.mapping.Name,
		"dlq_topic", rc.mapping.DLQTopic)

	// Get partitions for DLQ topic
	partitions, err := rc.consumer.Partitions(rc.mapping.DLQTopic)
	if err != nil {
		return fmt.Errorf("failed to get partitions for %s: %w", rc.mapping.DLQTopic, err)
	}

	rc.logger.Info("Found partitions", "count", len(partitions))

	totalProcessed := 0
	totalRetried := 0
	totalSkipped := 0
	totalErrors := 0

	// Process each partition
	for _, partition := range partitions {
		processed, retried, skipped, errors := rc.processPartition(ctx, partition)
		totalProcessed += processed
		totalRetried += retried
		totalSkipped += skipped
		totalErrors += errors
	}

	rc.logger.Info("DLQ processing completed",
		"dlq", rc.mapping.Name,
		"total_processed", totalProcessed,
		"retried", totalRetried,
		"skipped", totalSkipped,
		"errors", totalErrors)

	return nil
}

// processPartition processes all messages in a single partition
func (rc *RetryConsumer) processPartition(ctx context.Context, partition int32) (processed, retried, skipped, errors int) {
	rc.logger.Info("Processing partition",
		"partition", partition,
		"dlq_topic", rc.mapping.DLQTopic)

	// Create partition consumer starting from oldest offset
	pc, err := rc.consumer.ConsumePartition(rc.mapping.DLQTopic, partition, sarama.OffsetOldest)
	if err != nil {
		rc.logger.Error("Failed to create partition consumer",
			"partition", partition,
			"error", err)
		return 0, 0, 0, 1
	}
	defer pc.Close()

	// Process messages with timeout
	timeout := time.After(30 * time.Second) // Stop after 30s of no messages

	for {
		select {
		case msg := <-pc.Messages():
			processed++

			if err := rc.processMessage(ctx, msg); err != nil {
				rc.logger.Error("Failed to process message",
					"partition", partition,
					"offset", msg.Offset,
					"error", err)
				errors++
			} else {
				// Check if message was retried or skipped based on logs
				// This is tracked in processMessage
				retried++
			}

		case err := <-pc.Errors():
			rc.logger.Error("Partition consumer error",
				"partition", partition,
				"error", err)
			errors++

		case <-timeout:
			rc.logger.Info("No more messages in partition",
				"partition", partition,
				"processed", processed)
			return

		case <-ctx.Done():
			rc.logger.Info("Context cancelled",
				"partition", partition)
			return
		}
	}
}

// processMessage processes a single DLQ message
func (rc *RetryConsumer) processMessage(ctx context.Context, msg *sarama.ConsumerMessage) error {
	// Parse DLQ message
	dlqMsg, err := FromJSON(msg.Value)
	if err != nil {
		return fmt.Errorf("failed to parse DLQ message: %w", err)
	}

	rc.logger.Info("Processing DLQ message",
		"key", dlqMsg.OriginalKey,
		"error_category", dlqMsg.ErrorCategory,
		"retry_count", dlqMsg.RetryCount,
		"age", dlqMsg.GetAge().Round(time.Second))

	// Check if message is permanent
	if dlqMsg.ErrorCategory == "permanent" {
		rc.logger.Info("Skipping permanent error",
			"key", dlqMsg.OriginalKey,
			"error", dlqMsg.ErrorMessage)
		return nil
	}

	// Get retry strategy
	retryStrategy := rc.mapping.GetRetryStrategy(&rc.config.Defaults)

	// Check if message should be retried
	if !dlqMsg.ShouldRetry(retryStrategy.MaxRetries) {
		rc.logger.Warn("Message exceeded max retries, converting to permanent",
			"key", dlqMsg.OriginalKey,
			"retry_count", dlqMsg.RetryCount,
			"max_retries", retryStrategy.MaxRetries)

		// Convert to permanent and republish to DLQ
		reason := fmt.Sprintf("Exceeded maximum retry attempts (%d)", retryStrategy.MaxRetries)
		permanentMsg := dlqMsg.ConvertToPermanent(reason)
		return rc.publishToDLQ(permanentMsg)
	}

	// Check if ready for retry (cooling-off period elapsed)
	if !dlqMsg.IsReadyForRetry() {
		rc.logger.Debug("Message not ready for retry",
			"key", dlqMsg.OriginalKey,
			"next_retry_after", dlqMsg.NextRetryAfter)
		return nil
	}

	// Republish to original topic
	rc.logger.Info("Republishing to original topic",
		"key", dlqMsg.OriginalKey,
		"original_topic", dlqMsg.OriginalTopic,
		"retry_count", dlqMsg.RetryCount)

	err = rc.publishToOriginalTopic(dlqMsg)
	if err != nil {
		// Failed to republish - update retry count and republish to DLQ
		rc.logger.Error("Failed to republish to original topic",
			"key", dlqMsg.OriginalKey,
			"error", err)

		// Calculate next retry delay
		nextRetryDelay, delayErr := retryStrategy.GetBackoffDelay(dlqMsg.RetryCount)
		if delayErr != nil {
			return fmt.Errorf("failed to get backoff delay: %w", delayErr)
		}

		nextRetryTime := time.Now().Add(nextRetryDelay)
		errorType := categorizer.GetErrorType(err)

		// Increment retry count and republish to DLQ
		updatedMsg := dlqMsg.IncrementRetryCount(err, errorType, &nextRetryTime)
		return rc.publishToDLQ(updatedMsg)
	}

	rc.logger.Info("Successfully republished to original topic",
		"key", dlqMsg.OriginalKey,
		"retry_count", dlqMsg.RetryCount)

	return nil
}

// publishToOriginalTopic republishes the original message to its original topic
func (rc *RetryConsumer) publishToOriginalTopic(dlqMsg *DLQMessage) error {
	kafkaMsg := &sarama.ProducerMessage{
		Topic: dlqMsg.OriginalTopic,
		Key:   sarama.StringEncoder(dlqMsg.OriginalKey),
		Value: sarama.ByteEncoder(dlqMsg.OriginalMessage),
	}

	partition, offset, err := rc.producer.SendMessage(kafkaMsg)
	if err != nil {
		return fmt.Errorf("failed to send message to %s: %w", dlqMsg.OriginalTopic, err)
	}

	rc.logger.Debug("Message published",
		"topic", dlqMsg.OriginalTopic,
		"partition", partition,
		"offset", offset)

	return nil
}

// publishToDLQ publishes a message back to the DLQ
func (rc *RetryConsumer) publishToDLQ(dlqMsg *DLQMessage) error {
	data, err := dlqMsg.ToJSON()
	if err != nil {
		return fmt.Errorf("failed to serialize DLQ message: %w", err)
	}

	kafkaMsg := &sarama.ProducerMessage{
		Topic: rc.mapping.DLQTopic,
		Key:   sarama.StringEncoder(dlqMsg.OriginalKey),
		Value: sarama.ByteEncoder(data),
	}

	partition, offset, err := rc.producer.SendMessage(kafkaMsg)
	if err != nil {
		return fmt.Errorf("failed to send message to DLQ %s: %w", rc.mapping.DLQTopic, err)
	}

	rc.logger.Debug("Message published to DLQ",
		"topic", rc.mapping.DLQTopic,
		"partition", partition,
		"offset", offset,
		"retry_count", dlqMsg.RetryCount)

	return nil
}
