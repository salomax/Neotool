package main

import (
	"context"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"sync"
	"sync/atomic"
	"syscall"

	"github.com/IBM/sarama"
	"github.com/salomax/invistus/workflow/golang/shared/dlq-retry-consumer/internal/config"
	"github.com/salomax/invistus/workflow/golang/shared/dlq-retry-consumer/internal/consumer"
)

func main() {
	// Setup logger
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("Starting DLQ Retry Consumer")

	// Load configuration
	cfg, err := config.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	logger.Info("Configuration loaded",
		"version", cfg.Version,
		"dlq_mappings", len(cfg.Mappings),
		"kafka_brokers", cfg.Kafka.Brokers)

	// Create context with cancellation
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Setup signal handling
	sigterm := make(chan os.Signal, 1)
	signal.Notify(sigterm, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigterm
		logger.Info("Received termination signal, shutting down...")
		cancel()
	}()

	// Create Kafka consumer
	kafkaConfig := sarama.NewConfig()
	kafkaConfig.Version = sarama.V2_8_0_0
	kafkaConfig.Consumer.Return.Errors = true
	kafkaConfig.Consumer.Offsets.Initial = sarama.OffsetOldest

	brokers := []string{cfg.Kafka.Brokers}
	kafkaConsumer, err := sarama.NewConsumer(brokers, kafkaConfig)
	if err != nil {
		log.Fatalf("Failed to create Kafka consumer: %v", err)
	}
	defer kafkaConsumer.Close()

	logger.Info("Kafka consumer created")

	// Create Kafka producer
	producerConfig := sarama.NewConfig()
	producerConfig.Version = sarama.V2_8_0_0
	producerConfig.Producer.RequiredAcks = sarama.WaitForAll
	producerConfig.Producer.Retry.Max = 3
	producerConfig.Producer.Return.Successes = true

	kafkaProducer, err := sarama.NewSyncProducer(brokers, producerConfig)
	if err != nil {
		log.Fatalf("Failed to create Kafka producer: %v", err)
	}
	defer kafkaProducer.Close()

	logger.Info("Kafka producer created")

	// Process each enabled DLQ mapping in parallel using goroutines
	var wg sync.WaitGroup
	var totalProcessed, totalErrors atomic.Int32

	// Collect enabled mappings
	var enabledMappings []config.DLQMapping
	for _, mapping := range cfg.Mappings {
		if !mapping.Enabled {
			logger.Info("Skipping disabled DLQ", "dlq", mapping.Name)
			continue
		}
		enabledMappings = append(enabledMappings, mapping)
	}

	logger.Info("Starting parallel DLQ processing",
		"total_dlqs", len(enabledMappings),
		"parallelism", len(enabledMappings))

	// Process each DLQ in a separate goroutine
	for i := range enabledMappings {
		mapping := enabledMappings[i] // Capture loop variable

		wg.Add(1)
		go func(m config.DLQMapping) {
			defer wg.Done()

			logger.Info("Processing DLQ",
				"dlq", m.Name,
				"dlq_topic", m.DLQTopic,
				"original_topic", m.OriginalTopic)

			// Create retry consumer for this DLQ
			retryConsumer := consumer.NewRetryConsumer(
				cfg,
				&m,
				kafkaConsumer,
				kafkaProducer,
				logger.With("dlq", m.Name), // Add DLQ name to logger context
			)

			// Process DLQ
			if err := retryConsumer.ProcessDLQ(ctx); err != nil {
				logger.Error("Failed to process DLQ",
					"dlq", m.Name,
					"error", err)
				totalErrors.Add(1)
			} else {
				logger.Info("Successfully processed DLQ", "dlq", m.Name)
				totalProcessed.Add(1)
			}
		}(mapping)

		// Check if context was cancelled before starting next goroutine
		select {
		case <-ctx.Done():
			logger.Info("Shutdown signal received, stopping new DLQ processing")
			break
		default:
		}
	}

	// Wait for all goroutines to complete
	logger.Info("Waiting for all DLQ processing to complete...")
	wg.Wait()

	logger.Info("DLQ Retry Consumer completed",
		"total_dlqs_processed", totalProcessed.Load(),
		"total_errors", totalErrors.Load())

	if totalErrors.Load() > 0 {
		os.Exit(1)
	}
}
