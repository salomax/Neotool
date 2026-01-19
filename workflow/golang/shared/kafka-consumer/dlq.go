package kafkaconsumer

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/IBM/sarama"
)

// DLQPublisher publishes failed messages to Dead Letter Queue
type DLQPublisher struct {
	producer sarama.SyncProducer
	dlqTopic string
	logger   *slog.Logger
}

// NewDLQPublisher creates a new DLQ publisher
func NewDLQPublisher(producer sarama.SyncProducer, dlqTopic string, logger *slog.Logger) *DLQPublisher {
	return &DLQPublisher{
		producer: producer,
		dlqTopic: dlqTopic,
		logger:   logger,
	}
}

// DLQMessage represents a message in the DLQ
type DLQMessage struct {
	OriginalMessage  json.RawMessage `json:"original_message"`
	ErrorType        string          `json:"error_type"`
	ErrorMessage     string          `json:"error_message"`
	FailedTimestamp  string          `json:"failed_timestamp"`
	RetryCount       int             `json:"retry_count"`
	IsRetryable      bool            `json:"is_retryable"`
	OriginalTopic    string          `json:"original_topic"`
	OriginalKey      string          `json:"original_key,omitempty"`
}

// Publish publishes a failed message to the DLQ
func (p *DLQPublisher) Publish(
	originalMessage []byte,
	originalKey string,
	originalTopic string,
	err error,
	retryCount int,
	isRetryable bool,
) error {
	dlqMsg := DLQMessage{
		OriginalMessage: originalMessage,
		ErrorType:       fmt.Sprintf("%T", err),
		ErrorMessage:    err.Error(),
		FailedTimestamp: time.Now().UTC().Format(time.RFC3339),
		RetryCount:      retryCount,
		IsRetryable:     isRetryable,
		OriginalTopic:   originalTopic,
		OriginalKey:     originalKey,
	}

	// Serialize DLQ message
	dlqMsgBytes, marshalErr := json.Marshal(dlqMsg)
	if marshalErr != nil {
		p.logger.Error("Failed to marshal DLQ message", "error", marshalErr)
		return fmt.Errorf("failed to marshal DLQ message: %w", marshalErr)
	}

	// Create Kafka message
	kafkaMsg := &sarama.ProducerMessage{
		Topic: p.dlqTopic,
		Key:   sarama.StringEncoder(originalKey),
		Value: sarama.ByteEncoder(dlqMsgBytes),
	}

	// Publish to DLQ
	partition, offset, produceErr := p.producer.SendMessage(kafkaMsg)
	if produceErr != nil {
		p.logger.Error("Failed to publish to DLQ",
			"dlq_topic", p.dlqTopic,
			"error", produceErr)
		return fmt.Errorf("failed to publish to DLQ: %w", produceErr)
	}

	p.logger.Info("Published message to DLQ",
		"dlq_topic", p.dlqTopic,
		"partition", partition,
		"offset", offset,
		"original_key", originalKey,
		"error_type", dlqMsg.ErrorType,
		"retry_count", retryCount,
		"is_retryable", isRetryable)

	return nil
}

// Close closes the DLQ publisher
func (p *DLQPublisher) Close() error {
	if p.producer != nil {
		return p.producer.Close()
	}
	return nil
}


