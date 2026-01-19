package kafka

import (
	"log/slog"

	"github.com/IBM/sarama"
	shareddlq "github.com/salomax/invistus/workflow/go/shared/kafka-consumer"
)

// DLQPublisher wraps the shared DLQ publisher to maintain compatibility
type DLQPublisher struct {
	*shareddlq.DLQPublisher
}

// NewDLQPublisher creates a new DLQ publisher using the shared implementation
func NewDLQPublisher(producer sarama.SyncProducer, dlqTopic string, logger *slog.Logger) *DLQPublisher {
	return &DLQPublisher{
		DLQPublisher: shareddlq.NewDLQPublisher(producer, dlqTopic, logger),
	}
}

// DLQMessage is re-exported from the shared package for backward compatibility
type DLQMessage = shareddlq.DLQMessage
