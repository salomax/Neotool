package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"

	"github.com/IBM/sarama"
	"github.com/invistus/bacen-ifdata/internal/domain"
)

// InstitutionPublisher publishes institution change events to Kafka
type InstitutionPublisher struct {
	producer sarama.SyncProducer
	topic    string
	logger   *slog.Logger
}

// NewInstitutionPublisher creates a new institution publisher
func NewInstitutionPublisher(producer sarama.SyncProducer, topic string, logger *slog.Logger) *InstitutionPublisher {
	return &InstitutionPublisher{
		producer: producer,
		topic:    topic,
		logger:   logger,
	}
}

// PublishChanges publishes institution changes to Kafka
func (p *InstitutionPublisher) PublishChanges(ctx context.Context, changes []domain.InstitutionChange) (int, int, error) {
	published := 0
	failed := 0

	for _, change := range changes {
		// Only publish CREATED and UPDATED, skip UNCHANGED
		if change.ChangeType == domain.ChangeTypeUnchanged {
			continue
		}

		// Convert to event
		event := p.toEvent(change)

		// Publish
		if err := p.publish(ctx, event, change.Institution.BacenCodInst); err != nil {
			p.logger.Error("failed to publish institution change",
				"bacen_cod_inst", change.Institution.BacenCodInst,
				"change_type", change.ChangeType,
				"error", err)
			failed++
		} else {
			published++
		}
	}

	p.logger.Info("published institution changes",
		"published", published,
		"failed", failed,
		"skipped_unchanged", len(changes)-published-failed)

	return published, failed, nil
}

// publish sends a single event to Kafka
func (p *InstitutionPublisher) publish(ctx context.Context, event *domain.InstitutionUpsertedEvent, key string) error {
	// Serialize event to JSON
	eventBytes, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("failed to marshal event: %w", err)
	}

	// Create Kafka message
	message := &sarama.ProducerMessage{
		Topic: p.topic,
		Key:   sarama.StringEncoder(key),
		Value: sarama.ByteEncoder(eventBytes),
	}

	// Send synchronously
	partition, offset, err := p.producer.SendMessage(message)
	if err != nil {
		return fmt.Errorf("failed to send message: %w", err)
	}

	p.logger.Debug("published institution event",
		"bacen_cod_inst", key,
		"change_type", event.ChangeType,
		"partition", partition,
		"offset", offset)

	return nil
}

// toEvent converts InstitutionChange to InstitutionUpsertedEvent
func (p *InstitutionPublisher) toEvent(change domain.InstitutionChange) *domain.InstitutionUpsertedEvent {
	inst := change.Institution

	return &domain.InstitutionUpsertedEvent{
		BacenCodInst:               inst.BacenCodInst,
		ChangeType:                 string(change.ChangeType),
		AnoMesRef:                  inst.AnoMesRef,
		NomeInstituicao:            inst.NomeInstituicao,
		Situacao:                   inst.Situacao,
		SegmentoTB:                 inst.SegmentoTB,
		Atividade:                  inst.Atividade,
		TipoConsolidadoBancario:    inst.TipoConsolidadoBancario,
		TipoConsolidacao:           inst.TipoConsolidacao,
		TipoControle:               inst.TipoControle,
		UF:                         inst.UF,
		Municipio:                  inst.Municipio,
		CodConglomeradoFinanceiro:  inst.CodConglomeradoFinanceiro,
		CodConglomeradoPrudencial:  inst.CodConglomeradoPrudencial,
		CNPJInstituicaoLider:       inst.CNPJInstituicaoLider,
		SourceHash:                 inst.SourceHash,
	}
}

// Close closes the Kafka producer
func (p *InstitutionPublisher) Close() error {
	if p.producer != nil {
		return p.producer.Close()
	}
	return nil
}
