package application

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/IBM/sarama"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/domain"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/kafka"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/processor"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/shared"
)

// DLQConsumerRunner handles DLQ message consumption and retry
type DLQConsumerRunner struct {
	config         *Config
	consumerGroup  sarama.ConsumerGroup
	processor      *processor.Processor
	producer       sarama.SyncProducer
	dlqPublisher   *kafka.DLQPublisher
	healthServer   *HealthServer
	logger         *slog.Logger
	ready          chan bool
}

// NewDLQConsumerRunner creates a new DLQ consumer runner
func NewDLQConsumerRunner(
	config *Config,
	consumerGroup sarama.ConsumerGroup,
	processor *processor.Processor,
	producer sarama.SyncProducer,
	dlqPublisher *kafka.DLQPublisher,
	healthServer *HealthServer,
	logger *slog.Logger,
) *DLQConsumerRunner {
	return &DLQConsumerRunner{
		config:        config,
		consumerGroup: consumerGroup,
		processor:     processor,
		producer:      producer,
		dlqPublisher:  dlqPublisher,
		healthServer:  healthServer,
		logger:        logger,
		ready:         make(chan bool),
	}
}

// Run starts the DLQ consumer loop with graceful shutdown
func (cr *DLQConsumerRunner) Run(ctx context.Context) error {
	cr.logger.Info("Starting DLQ consumer runner",
		"consumer_group", cr.config.Kafka.ConsumerGroup+"-dlq",
		"topic", cr.config.Kafka.DLQTopic)

	// Create cancellable context
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Setup signal handling for graceful shutdown
	sigterm := make(chan os.Signal, 1)
	signal.Notify(sigterm, syscall.SIGINT, syscall.SIGTERM)

	// Start health server
	if cr.healthServer != nil && cr.config.HealthCheck.Enabled {
		if err := cr.healthServer.Start(ctx); err != nil {
			return fmt.Errorf("failed to start health server: %w", err)
		}
		defer cr.healthServer.Shutdown(context.Background())

		// Mark as live and ready
		cr.healthServer.SetLive(true)
		cr.healthServer.SetReady(true)
	}

	// Create consumer handler
	handler := &dlqConsumerHandler{
		runner: cr,
		logger: cr.logger,
	}

	// Consumer loop
	go func() {
		for {
			// Consume should be called inside an infinite loop, when a
			// server-side rebalance happens, the consumer session will need to be
			// recreated to get the new claims
			if err := cr.consumerGroup.Consume(ctx, []string{cr.config.Kafka.DLQTopic}, handler); err != nil {
				cr.logger.Error("Error from DLQ consumer", "error", err)
			}

			// Check if context was cancelled, signaling that the consumer should stop
			if ctx.Err() != nil {
				cr.logger.Info("Context cancelled, stopping DLQ consumer")
				return
			}

			cr.logger.Info("Rebalancing...")
		}
	}()

	// Monitor consumer errors
	go func() {
		for err := range cr.consumerGroup.Errors() {
			cr.logger.Error("DLQ consumer group error", "error", err)
		}
	}()

	cr.logger.Info("DLQ consumer runner started, consuming messages")

	// Wait for signals
	select {
	case <-sigterm:
		cr.logger.Info("Received termination signal")
	case <-ctx.Done():
		cr.logger.Info("Context cancelled")
	}

	// Mark as not live/ready before shutdown
	if cr.healthServer != nil {
		cr.healthServer.SetLive(false)
		cr.healthServer.SetReady(false)
	}

	// Cancel context to stop consumer
	cancel()

	// Wait for consumer to finish
	cr.logger.Info("Shutting down DLQ consumer runner")

	return nil
}

// dlqConsumerHandler implements sarama.ConsumerGroupHandler
type dlqConsumerHandler struct {
	runner *DLQConsumerRunner
	logger *slog.Logger
}

// Setup is run at the beginning of a new session, before ConsumeClaim
func (h *dlqConsumerHandler) Setup(sarama.ConsumerGroupSession) error {
	h.logger.Info("DLQ consumer session setup")
	return nil
}

// Cleanup is run at the end of a session, once all ConsumeClaim goroutines have exited
func (h *dlqConsumerHandler) Cleanup(sarama.ConsumerGroupSession) error {
	h.logger.Info("DLQ consumer session cleanup")
	return nil
}

// ConsumeClaim processes messages from the DLQ topic
func (h *dlqConsumerHandler) ConsumeClaim(session sarama.ConsumerGroupSession, claim sarama.ConsumerGroupClaim) error {
	h.logger.Info("DLQ ConsumeClaim started",
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

			h.logger.Info("Received DLQ message",
				"topic", message.Topic,
				"partition", message.Partition,
				"offset", message.Offset,
				"key", string(message.Key))

			// Process DLQ message
			if err := h.processDLQMessage(session.Context(), message); err != nil {
				h.logger.Error("Failed to process DLQ message",
					"error", err,
					"offset", message.Offset)
				// Continue processing other messages
			}

			// Mark message as processed
			session.MarkMessage(message, "")

		case <-session.Context().Done():
			return nil
		}
	}
}

// processDLQMessage processes a DLQ message
func (h *dlqConsumerHandler) processDLQMessage(ctx context.Context, message *sarama.ConsumerMessage) error {
	// Parse DLQ message
	var dlqMsg kafka.DLQMessage
	if err := json.Unmarshal(message.Value, &dlqMsg); err != nil {
		h.logger.Error("Failed to unmarshal DLQ message", "error", err, "offset", message.Offset)
		return fmt.Errorf("failed to unmarshal DLQ message: %w", err)
	}

	h.logger.Info("Processing DLQ message",
		"original_topic", dlqMsg.OriginalTopic,
		"error_type", dlqMsg.ErrorType,
		"retry_count", dlqMsg.RetryCount,
		"is_retryable", dlqMsg.IsRetryable,
		"failed_timestamp", dlqMsg.FailedTimestamp)

	// Check if message is retryable
	if !dlqMsg.IsRetryable {
		h.logger.Warn("Skipping non-retryable DLQ message",
			"error_type", dlqMsg.ErrorType,
			"error_message", dlqMsg.ErrorMessage)
		return nil
	}

	// Check retry limit (don't retry if already retried too many times)
	maxDLQRetries := h.runner.config.Retry.DLQMaxRetries
	if dlqMsg.RetryCount >= maxDLQRetries {
		h.logger.Warn("DLQ message exceeded max retries, skipping",
			"retry_count", dlqMsg.RetryCount,
			"max_retries", maxDLQRetries)
		return nil
	}

	// Parse original message
	var originalMsg domain.InstitutionUpsertedMessage
	if err := json.Unmarshal(dlqMsg.OriginalMessage, &originalMsg); err != nil {
		h.logger.Error("Failed to unmarshal original message from DLQ", "error", err)
		return fmt.Errorf("failed to unmarshal original message: %w", err)
	}

	// Validate original message
	if err := originalMsg.Validate(); err != nil {
		h.logger.Error("Original message validation failed", "error", err)
		return fmt.Errorf("original message validation failed: %w", err)
	}

	// Create retry policy with longer delays for DLQ retries
	retryPolicy := shared.RetryPolicy{
		MaxRetries:        h.runner.config.Retry.MaxRetries,
		InitialDelay:      time.Duration(h.runner.config.Retry.InitialDelayMs*2) * time.Millisecond, // Double delay for DLQ
		MaxDelay:          time.Duration(h.runner.config.Retry.MaxDelayMs*2) * time.Millisecond,
		BackoffMultiplier: h.runner.config.Retry.BackoffMultiplier,
		Jitter:            h.runner.config.Retry.Jitter,
	}

	// Process with retry
	operation := func() error {
		return h.runner.processor.ProcessMessage(ctx, &originalMsg)
	}

	err := shared.Retry(ctx, retryPolicy, operation)
	if err != nil {
		h.logger.Error("Failed to reprocess DLQ message after retries",
			"error", err,
			"bacen_cod_inst", originalMsg.BacenCodInst,
			"retry_count", dlqMsg.RetryCount)

		// Republish to DLQ with incremented retry count
		isRetryable := shared.IsRetryableError(err)
		if dlqErr := h.runner.dlqPublisher.Publish(
			dlqMsg.OriginalMessage,
			dlqMsg.OriginalKey,
			dlqMsg.OriginalTopic,
			err,
			dlqMsg.RetryCount+1,
			isRetryable,
		); dlqErr != nil {
			h.logger.Error("Failed to republish to DLQ", "error", dlqErr)
			return dlqErr
		}

		h.logger.Info("Republished message to DLQ",
			"bacen_cod_inst", originalMsg.BacenCodInst,
			"new_retry_count", dlqMsg.RetryCount+1)

		return err
	}

	h.logger.Info("Successfully reprocessed DLQ message",
		"bacen_cod_inst", originalMsg.BacenCodInst,
		"original_error", dlqMsg.ErrorMessage)

	return nil
}
