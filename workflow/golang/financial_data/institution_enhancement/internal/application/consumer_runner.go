package application

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/IBM/sarama"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/domain"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/shared"
)

// workItem represents a message to be processed by a worker
type workItem struct {
	message *sarama.ConsumerMessage
	session sarama.ConsumerGroupSession
}

// ConsumerRunner handles Kafka message consumption with retry and DLQ logic
type ConsumerRunner struct {
	config         *Config
	consumerGroup  sarama.ConsumerGroup
	processor      domain.MessageProcessor
	dlqPublisher   domain.DLQPublisher
	healthServer   *HealthServer
	logger         *slog.Logger
	ready          chan bool
	workQueue      chan workItem
	workersDone    chan bool
	workersWg      sync.WaitGroup // Tracks worker completion for graceful shutdown
}

// NewConsumerRunner creates a new consumer runner
func NewConsumerRunner(
	config *Config,
	consumerGroup sarama.ConsumerGroup,
	processor domain.MessageProcessor,
	dlqPublisher domain.DLQPublisher,
	healthServer *HealthServer,
	logger *slog.Logger,
) *ConsumerRunner {
	return &ConsumerRunner{
		config:        config,
		consumerGroup: consumerGroup,
		processor:     processor,
		dlqPublisher:  dlqPublisher,
		healthServer:  healthServer,
		logger:        logger,
		ready:         make(chan bool),
		workQueue:     make(chan workItem, config.WorkerPool.Size*2), // Buffer 2x worker count
		workersDone:   make(chan bool),
	}
}

// Run starts the consumer loop with graceful shutdown
func (cr *ConsumerRunner) Run(ctx context.Context) error {
	cr.logger.Info("Starting consumer runner",
		"consumer_group", cr.config.Kafka.ConsumerGroup,
		"topic", cr.config.Kafka.Topic,
		"worker_pool_size", cr.config.WorkerPool.Size)

	// Create cancellable context
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Setup signal handling for graceful shutdown
	sigterm := make(chan os.Signal, 1)
	signal.Notify(sigterm, syscall.SIGINT, syscall.SIGTERM)

	// Start worker pool
	cr.startWorkerPool(ctx)

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
	handler := &consumerHandler{
		runner: cr,
		logger: cr.logger,
	}

	// Consumer loop
	go func() {
		for {
			// Consume should be called inside an infinite loop, when a
			// server-side rebalance happens, the consumer session will need to be
			// recreated to get the new claims
			if err := cr.consumerGroup.Consume(ctx, []string{cr.config.Kafka.Topic}, handler); err != nil {
				cr.logger.Error("Error from consumer", "error", err)
			}

			// Check if context was cancelled, signaling that the consumer should stop
			if ctx.Err() != nil {
				cr.logger.Info("Context cancelled, stopping consumer")
				return
			}

			cr.logger.Info("Rebalancing...")
		}
	}()

	// Monitor consumer errors
	go func() {
		for err := range cr.consumerGroup.Errors() {
			cr.logger.Error("Consumer group error", "error", err)
		}
	}()

	cr.logger.Info("Consumer runner started, consuming messages")

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

	// Close work queue to signal workers to stop
	close(cr.workQueue)

	// Wait for all workers to finish
	cr.logger.Info("Waiting for workers to finish")
	<-cr.workersDone

	// Wait for consumer to finish
	cr.logger.Info("Shutting down consumer runner")

	return nil
}

// consumerHandler implements sarama.ConsumerGroupHandler
type consumerHandler struct {
	runner *ConsumerRunner
	logger *slog.Logger
}

// Setup is run at the beginning of a new session, before ConsumeClaim
func (h *consumerHandler) Setup(sarama.ConsumerGroupSession) error {
	h.logger.Info("Consumer session setup")
	return nil
}

// Cleanup is run at the end of a session, once all ConsumeClaim goroutines have exited
func (h *consumerHandler) Cleanup(sarama.ConsumerGroupSession) error {
	h.logger.Info("Consumer session cleanup")
	return nil
}

// ConsumeClaim processes messages from the topic
func (h *consumerHandler) ConsumeClaim(session sarama.ConsumerGroupSession, claim sarama.ConsumerGroupClaim) error {
	h.logger.Info("ConsumeClaim started",
		"topic", claim.Topic(),
		"partition", claim.Partition(),
		"initial_offset", claim.InitialOffset(),
		"high_water_mark", claim.HighWaterMarkOffset())

	// Dispatch messages to worker pool
	for {
		select {
		case message := <-claim.Messages():
			if message == nil {
				h.logger.Warn("Received nil message, exiting claim")
				return nil
			}

			h.logger.Info("Received message, dispatching to worker pool",
				"topic", message.Topic,
				"partition", message.Partition,
				"offset", message.Offset,
				"key", string(message.Key))

			// Send message to worker pool (non-blocking with context support)
			select {
			case h.runner.workQueue <- workItem{message: message, session: session}:
				// Message dispatched successfully
				h.logger.Debug("Message dispatched to worker pool", "offset", message.Offset)

			case <-session.Context().Done():
				h.logger.Info("Session context cancelled while dispatching")
				return nil
			}

		case <-session.Context().Done():
			return nil
		}
	}
}

// startWorkerPool starts N worker goroutines to process messages concurrently
func (cr *ConsumerRunner) startWorkerPool(ctx context.Context) {
	cr.logger.Info("Starting worker pool", "size", cr.config.WorkerPool.Size)

	// Start worker goroutines
	for i := 0; i < cr.config.WorkerPool.Size; i++ {
		cr.workersWg.Add(1)
		workerID := i + 1
		go func(id int) {
			defer cr.workersWg.Done()
			cr.worker(ctx, id)
		}(workerID)
	}

	// Monitor worker completion
	go func() {
		// Wait for all workers to actually finish processing
		cr.workersWg.Wait()
		cr.logger.Info("All workers completed")

		// Signal that all workers are done
		cr.workersDone <- true
	}()
}

// worker processes messages from the work queue
func (cr *ConsumerRunner) worker(ctx context.Context, workerID int) {
	cr.logger.Info("Worker started", "worker_id", workerID)
	defer cr.logger.Info("Worker stopped", "worker_id", workerID)

	for {
		select {
		case <-ctx.Done():
			// Context cancelled - exit immediately
			return

		case item, ok := <-cr.workQueue:
			if !ok {
				// Work queue closed - exit gracefully
				return
			}

			// Process the message
			cr.logger.Info("Worker processing message",
				"worker_id", workerID,
				"topic", item.message.Topic,
				"partition", item.message.Partition,
				"offset", item.message.Offset)

			// Process message with retry
			if err := cr.processMessageWithRetry(ctx, item.message); err != nil {
				cr.logger.Error("Worker failed to process message after retries",
					"worker_id", workerID,
					"error", err,
					"offset", item.message.Offset)

				// Publish to DLQ
				isRetryable := shared.IsRetryableError(err)
				if dlqErr := cr.dlqPublisher.Publish(
					item.message.Value,
					string(item.message.Key),
					item.message.Topic,
					err,
					cr.config.Retry.MaxRetries,
					isRetryable,
				); dlqErr != nil {
					cr.logger.Error("Worker failed to publish to DLQ",
						"worker_id", workerID,
						"error", dlqErr)
					// Don't mark message - it will be reprocessed on restart
					continue
				}
			}

			// Mark message as processed
			item.session.MarkMessage(item.message, "")
			cr.logger.Info("Worker completed message",
				"worker_id", workerID,
				"offset", item.message.Offset)
		}
	}
}

// processMessageWithRetry is shared logic extracted from consumerHandler
func (cr *ConsumerRunner) processMessageWithRetry(ctx context.Context, message *sarama.ConsumerMessage) error {
	// Parse message
	var msg domain.InstitutionUpsertedMessage
	if err := json.Unmarshal(message.Value, &msg); err != nil {
		cr.logger.Error("Failed to unmarshal message", "error", err, "offset", message.Offset)
		return fmt.Errorf("failed to unmarshal message: %w (non-retryable)", err)
	}

	// Validate message
	if err := msg.Validate(); err != nil {
		cr.logger.Error("Message validation failed", "error", err, "offset", message.Offset)
		return fmt.Errorf("message validation failed: %w (non-retryable)", err)
	}

	// Create retry policy
	retryPolicy := shared.RetryPolicy{
		MaxRetries:        cr.config.Retry.MaxRetries,
		InitialDelay:      time.Duration(cr.config.Retry.InitialDelayMs) * time.Millisecond,
		MaxDelay:          time.Duration(cr.config.Retry.MaxDelayMs) * time.Millisecond,
		BackoffMultiplier: cr.config.Retry.BackoffMultiplier,
		Jitter:            cr.config.Retry.Jitter,
	}

	// Process with retry
	operation := func() error {
		return cr.processor.ProcessMessage(ctx, &msg)
	}

	return shared.Retry(ctx, retryPolicy, operation)
}
