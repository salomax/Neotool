package kafka

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"

	"github.com/IBM/sarama"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
)

type MarketDataPublisher struct {
	producer sarama.SyncProducer
	topic    string
	logger   *slog.Logger
}

func NewMarketDataPublisher(brokers string, topic string, logger *slog.Logger) (*MarketDataPublisher, error) {
	if strings.TrimSpace(brokers) == "" || strings.TrimSpace(topic) == "" {
		return nil, nil
	}
	cfg := sarama.NewConfig()
	cfg.Version = sarama.V2_8_0_0
	cfg.Producer.RequiredAcks = sarama.WaitForAll
	cfg.Producer.Retry.Max = 3
	cfg.Producer.Return.Successes = true

	producer, err := sarama.NewSyncProducer(splitBrokers(brokers), cfg)
	if err != nil {
		return nil, fmt.Errorf("creating kafka producer: %w", err)
	}

	return &MarketDataPublisher{producer: producer, topic: topic, logger: logger}, nil
}

func (p *MarketDataPublisher) Publish(event domain.MarketDataJobSucceededEvent) error {
	if p == nil || p.producer == nil {
		return nil
	}
	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal event: %w", err)
	}
	_, _, err = p.producer.SendMessage(&sarama.ProducerMessage{
		Topic: p.topic,
		Key:   sarama.StringEncoder(event.JobName),
		Value: sarama.ByteEncoder(payload),
	})
	if err != nil {
		return fmt.Errorf("publish event: %w", err)
	}
	p.logger.Info("market data event published", "job_name", event.JobName, "last_data_date", event.LastDataDate.Format("2006-01-02"))
	return nil
}

func (p *MarketDataPublisher) Close() error {
	if p == nil || p.producer == nil {
		return nil
	}
	return p.producer.Close()
}

func splitBrokers(brokers string) []string {
	parts := strings.Split(brokers, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}
