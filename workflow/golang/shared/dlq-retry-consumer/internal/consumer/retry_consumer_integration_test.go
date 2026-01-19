// +build integration

package consumer

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"github.com/IBM/sarama"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/salomax/invistus/workflow/go/shared/dlq-retry-consumer/internal/config"
)

// setupKafkaContainer starts a Kafka container for integration testing
func setupKafkaContainer(ctx context.Context) (testcontainers.Container, []string, error) {
	req := testcontainers.ContainerRequest{
		Image:        "confluentinc/cp-kafka:7.5.0",
		ExposedPorts: []string{"9093/tcp"},
		Env: map[string]string{
			"KAFKA_BROKER_ID":                        "1",
			"KAFKA_LISTENER_SECURITY_PROTOCOL_MAP":   "PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT",
			"KAFKA_ADVERTISED_LISTENERS":             "PLAINTEXT://localhost:9092,PLAINTEXT_HOST://localhost:9093",
			"KAFKA_LISTENERS":                        "PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:9093",
			"KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR": "1",
			"KAFKA_TRANSACTION_STATE_LOG_MIN_ISR":    "1",
			"KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR": "1",
			"KAFKA_PROCESS_ROLES":                            "broker,controller",
			"KAFKA_NODE_ID":                                  "1",
			"KAFKA_CONTROLLER_QUORUM_VOTERS":                 "1@localhost:9094",
			"KAFKA_CONTROLLER_LISTENER_NAMES":                "CONTROLLER",
			"KAFKA_INTER_BROKER_LISTENER_NAME":               "PLAINTEXT",
			"CLUSTER_ID":                                     "MkU3OEVBNTcwNTJENDM2Qk",
		},
		WaitingFor: wait.ForLog("Kafka Server started").WithStartupTimeout(60 * time.Second),
	}

	kafkaContainer, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("failed to start Kafka container: %w", err)
	}

	host, err := kafkaContainer.Host(ctx)
	if err != nil {
		return nil, nil, err
	}

	port, err := kafkaContainer.MappedPort(ctx, "9093")
	if err != nil {
		return nil, nil, err
	}

	brokers := []string{fmt.Sprintf("%s:%s", host, port.Port())}
	return kafkaContainer, brokers, nil
}

// createTopics creates the required Kafka topics
func createTopics(brokers []string, topics []string) error {
	config := sarama.NewConfig()
	config.Version = sarama.V2_8_0_0

	admin, err := sarama.NewClusterAdmin(brokers, config)
	if err != nil {
		return fmt.Errorf("failed to create admin client: %w", err)
	}
	defer admin.Close()

	for _, topic := range topics {
		err = admin.CreateTopic(topic, &sarama.TopicDetail{
			NumPartitions:     1,
			ReplicationFactor: 1,
		}, false)
		if err != nil {
			return fmt.Errorf("failed to create topic %s: %w", topic, err)
		}
	}

	// Wait for topics to be ready
	time.Sleep(2 * time.Second)
	return nil
}

// produceMessage sends a message to a Kafka topic
func produceMessage(brokers []string, topic string, key string, value []byte) error {
	config := sarama.NewConfig()
	config.Version = sarama.V2_8_0_0
	config.Producer.Return.Successes = true

	producer, err := sarama.NewSyncProducer(brokers, config)
	if err != nil {
		return err
	}
	defer producer.Close()

	msg := &sarama.ProducerMessage{
		Topic: topic,
		Key:   sarama.StringEncoder(key),
		Value: sarama.ByteEncoder(value),
	}

	_, _, err = producer.SendMessage(msg)
	return err
}

// consumeMessages reads all messages from a topic
func consumeMessages(brokers []string, topic string, timeout time.Duration) ([]*sarama.ConsumerMessage, error) {
	config := sarama.NewConfig()
	config.Version = sarama.V2_8_0_0
	config.Consumer.Offsets.Initial = sarama.OffsetOldest

	consumer, err := sarama.NewConsumer(brokers, config)
	if err != nil {
		return nil, err
	}
	defer consumer.Close()

	partitions, err := consumer.Partitions(topic)
	if err != nil {
		return nil, err
	}

	var messages []*sarama.ConsumerMessage
	for _, partition := range partitions {
		pc, err := consumer.ConsumePartition(topic, partition, sarama.OffsetOldest)
		if err != nil {
			return nil, err
		}
		defer pc.Close()

		timeoutTimer := time.After(timeout)
		for {
			select {
			case msg := <-pc.Messages():
				messages = append(messages, msg)
			case <-timeoutTimer:
				return messages, nil
			}
		}
	}

	return messages, nil
}

func TestRetryConsumer_IntegrationTest(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration test")
	}

	ctx := context.Background()

	// Start Kafka container
	kafkaContainer, brokers, err := setupKafkaContainer(ctx)
	require.NoError(t, err)
	defer kafkaContainer.Terminate(ctx)

	// Create topics
	originalTopic := "test.original"
	dlqTopic := "test.dlq"
	require.NoError(t, createTopics(brokers, []string{originalTopic, dlqTopic}))

	// Create test configuration
	cfg := &config.Config{
		Version: "1.0",
		Kafka: config.KafkaConfig{
			Brokers: brokers[0],
		},
		Defaults: config.DefaultConfig{
			RetryStrategy: config.RetryStrategyConfig{
				MaxRetries: 3,
				Backoff: []config.BackoffConfig{
					{RetryCount: 0, Delay: 1 * time.Second},
					{RetryCount: 1, Delay: 2 * time.Second},
					{RetryCount: 2, Delay: 3 * time.Second},
				},
			},
			ErrorCategorization: config.ErrorCategorizationConfig{
				TransientPatterns: []string{"timeout", "503"},
				PermanentPatterns: []string{"validation", "404"},
			},
		},
	}

	mapping := &config.DLQMapping{
		Name:          "test-service",
		Enabled:       true,
		DLQTopic:      dlqTopic,
		OriginalTopic: originalTopic,
		ConsumerGroup: "test-consumer-group",
	}

	t.Run("processes ready transient messages", func(t *testing.T) {
		// Create a DLQ message that's ready for retry
		pastTime := time.Now().Add(-10 * time.Second)
		dlqMsg := &DLQMessage{
			OriginalMessage: json.RawMessage(`{"test": "data"}`),
			OriginalKey:     "test-key",
			OriginalTopic:   originalTopic,
			ErrorMessage:    "timeout error",
			ErrorType:       "timeout",
			ErrorCategory:   "transient",
			RetryCount:      0,
			FailedTimestamp: time.Now().Add(-1 * time.Hour),
			NextRetryAfter:  &pastTime,
			RetryHistory:    []RetryAttempt{},
		}

		msgBytes, err := dlqMsg.ToJSON()
		require.NoError(t, err)

		// Produce to DLQ
		err = produceMessage(brokers, dlqTopic, "test-key", msgBytes)
		require.NoError(t, err)

		// Wait for message to be available
		time.Sleep(1 * time.Second)

		// Create consumer and producer
		kafkaConfig := sarama.NewConfig()
		kafkaConfig.Version = sarama.V2_8_0_0
		kafkaConfig.Consumer.Return.Errors = true
		kafkaConfig.Consumer.Offsets.Initial = sarama.OffsetOldest

		kafkaConsumer, err := sarama.NewConsumer(brokers, kafkaConfig)
		require.NoError(t, err)
		defer kafkaConsumer.Close()

		producerConfig := sarama.NewConfig()
		producerConfig.Version = sarama.V2_8_0_0
		producerConfig.Producer.Return.Successes = true

		kafkaProducer, err := sarama.NewSyncProducer(brokers, producerConfig)
		require.NoError(t, err)
		defer kafkaProducer.Close()

		// Create retry consumer
		retryConsumer := NewRetryConsumer(cfg, mapping, kafkaConsumer, kafkaProducer, nil)

		// Process DLQ
		err = retryConsumer.ProcessDLQ(ctx)
		require.NoError(t, err)

		// Verify message was republished to original topic
		time.Sleep(1 * time.Second)
		messages, err := consumeMessages(brokers, originalTopic, 3*time.Second)
		require.NoError(t, err)
		assert.NotEmpty(t, messages, "Expected at least one message in original topic")

		// Verify message content
		if len(messages) > 0 {
			assert.Equal(t, "test-key", string(messages[0].Key))
			assert.JSONEq(t, `{"test": "data"}`, string(messages[0].Value))
		}
	})

	t.Run("skips permanent error messages", func(t *testing.T) {
		// Create a permanent error message
		dlqMsg := &DLQMessage{
			OriginalMessage: json.RawMessage(`{"test": "permanent"}`),
			OriginalKey:     "permanent-key",
			OriginalTopic:   originalTopic,
			ErrorMessage:    "validation error",
			ErrorType:       "validation_error",
			ErrorCategory:   "permanent",
			RetryCount:      0,
			FailedTimestamp: time.Now(),
			NextRetryAfter:  nil,
			RetryHistory:    []RetryAttempt{},
		}

		msgBytes, err := dlqMsg.ToJSON()
		require.NoError(t, err)

		// Produce to DLQ
		err = produceMessage(brokers, dlqTopic, "permanent-key", msgBytes)
		require.NoError(t, err)

		time.Sleep(1 * time.Second)

		// Create consumer and producer
		kafkaConfig := sarama.NewConfig()
		kafkaConfig.Version = sarama.V2_8_0_0
		kafkaConfig.Consumer.Offsets.Initial = sarama.OffsetOldest

		kafkaConsumer, err := sarama.NewConsumer(brokers, kafkaConfig)
		require.NoError(t, err)
		defer kafkaConsumer.Close()

		producerConfig := sarama.NewConfig()
		producerConfig.Version = sarama.V2_8_0_0
		producerConfig.Producer.Return.Successes = true

		kafkaProducer, err := sarama.NewSyncProducer(brokers, producerConfig)
		require.NoError(t, err)
		defer kafkaProducer.Close()

		// Create retry consumer
		retryConsumer := NewRetryConsumer(cfg, mapping, kafkaConsumer, kafkaProducer, nil)

		// Get current message count in original topic
		messagesBefore, _ := consumeMessages(brokers, originalTopic, 1*time.Second)
		countBefore := len(messagesBefore)

		// Process DLQ
		err = retryConsumer.ProcessDLQ(ctx)
		require.NoError(t, err)

		// Verify no new messages in original topic
		time.Sleep(1 * time.Second)
		messagesAfter, _ := consumeMessages(brokers, originalTopic, 1*time.Second)
		countAfter := len(messagesAfter)

		assert.Equal(t, countBefore, countAfter, "Permanent error should not be retried")
	})

	t.Run("skips messages not ready for retry", func(t *testing.T) {
		// Create a message with future retry time
		futureTime := time.Now().Add(1 * time.Hour)
		dlqMsg := &DLQMessage{
			OriginalMessage: json.RawMessage(`{"test": "not-ready"}`),
			OriginalKey:     "not-ready-key",
			OriginalTopic:   originalTopic,
			ErrorMessage:    "timeout",
			ErrorType:       "timeout",
			ErrorCategory:   "transient",
			RetryCount:      1,
			FailedTimestamp: time.Now().Add(-30 * time.Minute),
			NextRetryAfter:  &futureTime,
			RetryHistory:    []RetryAttempt{},
		}

		msgBytes, err := dlqMsg.ToJSON()
		require.NoError(t, err)

		// Produce to DLQ
		err = produceMessage(brokers, dlqTopic, "not-ready-key", msgBytes)
		require.NoError(t, err)

		time.Sleep(1 * time.Second)

		// Create consumer and producer
		kafkaConfig := sarama.NewConfig()
		kafkaConfig.Version = sarama.V2_8_0_0
		kafkaConfig.Consumer.Offsets.Initial = sarama.OffsetOldest

		kafkaConsumer, err := sarama.NewConsumer(brokers, kafkaConfig)
		require.NoError(t, err)
		defer kafkaConsumer.Close()

		producerConfig := sarama.NewConfig()
		producerConfig.Version = sarama.V2_8_0_0
		producerConfig.Producer.Return.Successes = true

		kafkaProducer, err := sarama.NewSyncProducer(brokers, producerConfig)
		require.NoError(t, err)
		defer kafkaProducer.Close()

		// Create retry consumer
		retryConsumer := NewRetryConsumer(cfg, mapping, kafkaConsumer, kafkaProducer, nil)

		// Get current message count in original topic
		messagesBefore, _ := consumeMessages(brokers, originalTopic, 1*time.Second)
		countBefore := len(messagesBefore)

		// Process DLQ
		err = retryConsumer.ProcessDLQ(ctx)
		require.NoError(t, err)

		// Verify no new messages in original topic
		time.Sleep(1 * time.Second)
		messagesAfter, _ := consumeMessages(brokers, originalTopic, 1*time.Second)
		countAfter := len(messagesAfter)

		assert.Equal(t, countBefore, countAfter, "Message not ready should not be retried")
	})
}
