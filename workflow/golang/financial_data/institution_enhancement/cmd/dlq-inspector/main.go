package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"log/slog"
	"os"
	"sort"
	"time"

	"github.com/IBM/sarama"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/application"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/kafka"
)

type ErrorStats struct {
	ErrorType      string
	Count          int
	IsRetryable    bool
	ExampleMessage string
	SampleCNPJs    []string
}

func main() {
	// Setup logger
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	fmt.Println("=== DLQ Inspector ===")
	fmt.Println()

	// Load configuration
	config, err := application.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	logger.Info("Configuration loaded", "dlq_topic", config.Kafka.DLQTopic)

	// Create Kafka consumer
	kafkaConfig := sarama.NewConfig()
	kafkaConfig.Version = sarama.V2_8_0_0
	kafkaConfig.Consumer.Return.Errors = true
	kafkaConfig.Consumer.Offsets.Initial = sarama.OffsetOldest // Read from beginning

	consumer, err := sarama.NewConsumer(config.Kafka.BrokerList(), kafkaConfig)
	if err != nil {
		log.Fatalf("Failed to create Kafka consumer: %v", err)
	}
	defer consumer.Close()

	// Get partitions for DLQ topic
	partitions, err := consumer.Partitions(config.Kafka.DLQTopic)
	if err != nil {
		log.Fatalf("Failed to get partitions: %v", err)
	}

	logger.Info("Reading DLQ messages", "partitions", len(partitions))

	// Statistics
	totalMessages := 0
	retryableCount := 0
	nonRetryableCount := 0
	errorTypeMap := make(map[string]*ErrorStats)

	// Read messages from all partitions
	for _, partition := range partitions {
		partitionConsumer, err := consumer.ConsumePartition(config.Kafka.DLQTopic, partition, sarama.OffsetOldest)
		if err != nil {
			logger.Error("Failed to consume partition", "partition", partition, "error", err)
			continue
		}

		logger.Info("Reading partition", "partition", partition)

		// Read all messages from this partition
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

	messageLoop:
		for {
			select {
			case msg := <-partitionConsumer.Messages():
				totalMessages++

				// Parse DLQ message
				var dlqMsg kafka.DLQMessage
				if err := json.Unmarshal(msg.Value, &dlqMsg); err != nil {
					logger.Error("Failed to unmarshal DLQ message", "error", err)
					continue
				}

				// Track statistics
				if dlqMsg.IsRetryable {
					retryableCount++
				} else {
					nonRetryableCount++
				}

				// Track error types
				stats, exists := errorTypeMap[dlqMsg.ErrorType]
				if !exists {
					stats = &ErrorStats{
						ErrorType:      dlqMsg.ErrorType,
						Count:          0,
						IsRetryable:    dlqMsg.IsRetryable,
						ExampleMessage: dlqMsg.ErrorMessage,
						SampleCNPJs:    []string{},
					}
					errorTypeMap[dlqMsg.ErrorType] = stats
				}
				stats.Count++
				if len(stats.SampleCNPJs) < 3 {
					stats.SampleCNPJs = append(stats.SampleCNPJs, dlqMsg.OriginalKey)
				}

			case <-ctx.Done():
				// Timeout - assume we've read all messages
				break messageLoop
			}
		}

		partitionConsumer.Close()
	}

	// Print results
	fmt.Println()
	fmt.Println("=== DLQ Analysis Results ===")
	fmt.Println()
	fmt.Printf("Total messages in DLQ: %d\n", totalMessages)
	fmt.Printf("  Retryable: %d (%.1f%%)\n", retryableCount, float64(retryableCount)/float64(totalMessages)*100)
	fmt.Printf("  Non-retryable: %d (%.1f%%)\n", nonRetryableCount, float64(nonRetryableCount)/float64(totalMessages)*100)
	fmt.Println()

	if len(errorTypeMap) == 0 {
		fmt.Println("No messages found in DLQ")
		return
	}

	// Sort error types by count
	type sortableError struct {
		ErrorType string
		Stats     *ErrorStats
	}
	var sortedErrors []sortableError
	for errorType, stats := range errorTypeMap {
		sortedErrors = append(sortedErrors, sortableError{errorType, stats})
	}
	sort.Slice(sortedErrors, func(i, j int) bool {
		return sortedErrors[i].Stats.Count > sortedErrors[j].Stats.Count
	})

	// Print error breakdown
	fmt.Println("=== Error Breakdown ===")
	fmt.Println()
	for i, item := range sortedErrors {
		stats := item.Stats
		percentage := float64(stats.Count) / float64(totalMessages) * 100

		fmt.Printf("%d. %s\n", i+1, stats.ErrorType)
		fmt.Printf("   Count: %d (%.1f%%)\n", stats.Count, percentage)
		fmt.Printf("   Retryable: %v\n", stats.IsRetryable)
		fmt.Printf("   Example error: %s\n", truncate(stats.ExampleMessage, 100))
		if len(stats.SampleCNPJs) > 0 {
			fmt.Printf("   Sample CNPJs: %v\n", stats.SampleCNPJs)
		}
		fmt.Println()
	}

	// Print recommendations
	fmt.Println("=== Recommendations ===")
	fmt.Println()
	if retryableCount > 0 {
		fmt.Printf("✓ You have %d retryable messages\n", retryableCount)
		fmt.Println("  → Run the DLQ consumer to automatically retry these messages")
		fmt.Println("  → Command: make run-dlq-consumer")
		fmt.Println()
	}
	if nonRetryableCount > 0 {
		fmt.Printf("⚠ You have %d non-retryable messages\n", nonRetryableCount)
		fmt.Println("  → These need manual review and fixes")
		fmt.Println("  → Check for validation errors, malformed data, etc.")
		fmt.Println()
	}

	fmt.Println("To enable Serper API for better website discovery:")
	fmt.Println("  export SERPER_ENABLED=true")
	fmt.Println("  export SERPER_API_KEY=your-api-key")
	fmt.Println()
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}
