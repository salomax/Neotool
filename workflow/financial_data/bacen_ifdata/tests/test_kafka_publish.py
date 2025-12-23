"""
Unit and integration tests for Kafka publishing task.

Tests cover:
- Message building logic (pure functions)
- Kafka producer configuration
- Publishing logic with mocked producer
- DLQ handling
- Error handling and retries
"""

from datetime import datetime
from unittest.mock import MagicMock, patch
import uuid

import pytest

from kafka.errors import KafkaError
from tasks.kafka_publish import (
    KAFKA_DLQ_TOPIC,
    KAFKA_TOPIC,
    SCHEMA_VERSION,
    SOURCE,
    build_kafka_message,
    create_kafka_producer,
    publish_institution_events,
    publish_to_dlq,
)

# ============================================================================
# Unit Tests - Pure Functions (No Kafka)
# ============================================================================


class TestBuildKafkaMessage:
    """Test build_kafka_message function."""

    def test_required_fields_present(self):
        """Test that all required fields are present in message."""
        institution = {
            "bacen_cod_inst": "001",
            "change_type": "CREATED",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": "Test Bank",
                "situacao": "A",
            },
        }

        message = build_kafka_message(institution, "CREATED")

        assert message["schema_version"] == SCHEMA_VERSION
        assert message["source"] == SOURCE
        assert message["bacen_cod_inst"] == "001"
        assert message["change_type"] == "CREATED"
        assert "event_id" in message
        assert "occurred_at" in message

        # Verify event_id is valid UUID
        uuid.UUID(message["event_id"])

        # Verify occurred_at is valid ISO 8601 timestamp
        datetime.fromisoformat(message["occurred_at"].replace("Z", "+00:00"))

    def test_institution_fields_included(self):
        """Test that institution fields are included in message."""
        institution = {
            "bacen_cod_inst": "001",
            "change_type": "CREATED",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": "Test Bank S.A.",
                "situacao": "A",
                "segmento_tb": "Commercial Bank",
                "tipo_consolidado_bancario": "b1",
                "tipo_consolidacao": "C",
                "tipo_controle": "1",
                "uf": "SP",
                "municipio": "São Paulo",
            },
        }

        message = build_kafka_message(institution, "CREATED")

        assert message["nome_instituicao"] == "Test Bank S.A."
        assert message["situacao"] == "A"
        assert message["segmento_tb"] == "Commercial Bank"
        assert message["tipo_consolidado_bancario"] == "b1"
        assert message["tipo_consolidacao"] == "C"
        assert message["tipo_controle"] == "1"
        assert message["uf"] == "SP"
        assert message["municipio"] == "São Paulo"

    def test_optional_fields_included(self):
        """Test that optional fields (source_hash, ano_mes_ref) are included if present."""
        institution = {
            "bacen_cod_inst": "001",
            "change_type": "UPDATED",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": "Test Bank",
                "source_hash": "abc123" * 10,  # 64 char hash
                "ano_mes_ref": "202403",
            },
        }

        message = build_kafka_message(institution, "UPDATED")

        assert message["source_hash"] == "abc123" * 10
        assert message["ano_mes_ref"] == "202403"

    def test_optional_fields_omitted_when_missing(self):
        """Test that optional fields are omitted when not present."""
        institution = {
            "bacen_cod_inst": "001",
            "change_type": "CREATED",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": "Test Bank",
            },
        }

        message = build_kafka_message(institution, "CREATED")

        # Optional fields should not be in message if not in institution_data
        # (They may be None, but shouldn't be present if not provided)
        assert "source_hash" not in message or message["source_hash"] is None
        assert "ano_mes_ref" not in message or message["ano_mes_ref"] is None

    def test_null_values_preserved(self):
        """Test that None values are preserved for nullable fields."""
        institution = {
            "bacen_cod_inst": "001",
            "change_type": "CREATED",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": None,
                "situacao": "A",
                "uf": None,
            },
        }

        message = build_kafka_message(institution, "CREATED")

        assert message["nome_instituicao"] is None
        assert message["situacao"] == "A"
        assert message["uf"] is None

    def test_bacen_cod_inst_from_top_level(self):
        """Test that bacen_cod_inst can come from top-level dict."""
        institution = {
            "bacen_cod_inst": "001",
            "change_type": "CREATED",
            "institution_data": {
                "nome_instituicao": "Test Bank",
            },
        }

        message = build_kafka_message(institution, "CREATED")

        assert message["bacen_cod_inst"] == "001"

    def test_bacen_cod_inst_from_institution_data(self):
        """Test that bacen_cod_inst can come from institution_data."""
        institution = {
            "change_type": "CREATED",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": "Test Bank",
            },
        }

        message = build_kafka_message(institution, "CREATED")

        assert message["bacen_cod_inst"] == "001"

    def test_missing_bacen_cod_inst_raises_error(self):
        """Test that missing bacen_cod_inst raises ValueError."""
        institution = {
            "change_type": "CREATED",
            "institution_data": {
                "nome_instituicao": "Test Bank",
            },
        }

        with pytest.raises(ValueError, match="bacen_cod_inst is required"):
            build_kafka_message(institution, "CREATED")

    def test_change_type_creates_correct_message(self):
        """Test that change_type is correctly set in message."""
        institution = {
            "bacen_cod_inst": "001",
            "institution_data": {
                "bacen_cod_inst": "001",
                "nome_instituicao": "Test Bank",
            },
        }

        message_created = build_kafka_message(institution, "CREATED")
        assert message_created["change_type"] == "CREATED"

        message_updated = build_kafka_message(institution, "UPDATED")
        assert message_updated["change_type"] == "UPDATED"


class TestCreateKafkaProducer:
    """Test create_kafka_producer function."""

    @patch("tasks.kafka_publish.KafkaProducer")
    def test_producer_configuration(self, mock_producer_class):
        """Test that producer is created with correct configuration."""
        create_kafka_producer()

        # Verify KafkaProducer was called with correct arguments
        mock_producer_class.assert_called_once()
        call_kwargs = mock_producer_class.call_args[1]

        assert call_kwargs["acks"] == "all"
        assert call_kwargs["enable_idempotence"] is True
        assert call_kwargs["compression_type"] == "gzip"  # Default
        assert call_kwargs["linger_ms"] == 10
        assert call_kwargs["retries"] == 5
        assert "value_serializer" in call_kwargs
        assert "key_serializer" in call_kwargs

    @patch("tasks.kafka_publish.KafkaProducer")
    def test_producer_uses_env_compression(self, mock_producer_class):
        """Test that producer uses compression type from environment variable."""
        # Patch the constant in the module namespace
        import tasks.kafka_publish

        original_value = tasks.kafka_publish.KAFKA_COMPRESSION_TYPE
        try:
            tasks.kafka_publish.KAFKA_COMPRESSION_TYPE = "snappy"
            create_kafka_producer()

            call_kwargs = mock_producer_class.call_args[1]
            assert call_kwargs["compression_type"] == "snappy"
        finally:
            # Restore original value
            tasks.kafka_publish.KAFKA_COMPRESSION_TYPE = original_value


class TestPublishToDlq:
    """Test publish_to_dlq function."""

    def test_dlq_message_structure(self):
        """Test that DLQ message has correct structure."""
        producer = MagicMock()
        future = MagicMock()
        future.get.return_value = MagicMock(
            topic=KAFKA_DLQ_TOPIC, partition=0, offset=123
        )
        producer.send.return_value = future

        message = {
            "schema_version": "1.0",
            "bacen_cod_inst": "001",
            "change_type": "CREATED",
        }
        error = ValueError("Test error")
        logger = MagicMock()

        result = publish_to_dlq(producer, message, error, logger)

        assert result is True
        producer.send.assert_called_once()
        call_args = producer.send.call_args

        # Check positional arguments (topic is first)
        assert call_args[0][0] == KAFKA_DLQ_TOPIC

        # Check keyword arguments (key and value)
        # The value is a dict (not serialized yet - serializer is applied by producer)
        assert call_args[1]["key"] == "001"  # Key is bacen_cod_inst
        assert "value" in call_args[1]
        dlq_message = call_args[1]["value"]  # Already a dict, not bytes
        assert "original_message" in dlq_message
        assert "error_type" in dlq_message
        assert "error_message" in dlq_message
        assert "failed_at" in dlq_message
        assert dlq_message["error_type"] == "ValueError"
        assert dlq_message["error_message"] == "Test error"

    def test_dlq_publish_failure_returns_false(self):
        """Test that DLQ publish failure returns False."""
        producer = MagicMock()
        producer.send.side_effect = KafkaError("Connection failed")
        logger = MagicMock()

        message = {"bacen_cod_inst": "001"}
        error = ValueError("Test error")

        result = publish_to_dlq(producer, message, error, logger)

        assert result is False
        logger.exception.assert_called()


# ============================================================================
# Unit Tests - Prefect Task (Mocked Kafka)
# ============================================================================


class TestPublishInstitutionEvents:
    """Test publish_institution_events Prefect task."""

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_empty_list_returns_zero_stats(self, mock_create_producer, mock_logger):
        """Test that empty list returns zero statistics."""
        mock_logger.return_value = MagicMock()
        result = publish_institution_events.fn([])

        assert result["total"] == 0
        assert result["published"] == 0
        assert result["failed"] == 0
        assert result["dlq_published"] == 0

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_successful_publish(self, mock_create_producer, mock_logger):
        """Test successful publishing of institution events."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        future = MagicMock()
        future.get.return_value = MagicMock(topic=KAFKA_TOPIC, partition=0, offset=123)
        producer.send.return_value = future
        mock_create_producer.return_value = producer

        changed_institutions = [
            {
                "bacen_cod_inst": "001",
                "change_type": "CREATED",
                "institution_data": {
                    "bacen_cod_inst": "001",
                    "nome_instituicao": "Test Bank",
                    "situacao": "A",
                },
            },
            {
                "bacen_cod_inst": "002",
                "change_type": "UPDATED",
                "institution_data": {
                    "bacen_cod_inst": "002",
                    "nome_instituicao": "Another Bank",
                    "situacao": "A",
                },
            },
        ]

        result = publish_institution_events.fn(changed_institutions)

        assert result["total"] == 2
        assert result["published"] == 2
        assert result["failed"] == 0
        assert result["dlq_published"] == 0

        # Verify producer.send was called twice
        assert producer.send.call_count == 2

        # Verify correct topic and keys
        calls = producer.send.call_args_list
        # First call: topic (positional), key (keyword)
        assert calls[0][0][0] == KAFKA_TOPIC
        assert calls[0][1]["key"] == "001"
        # Second call
        assert calls[1][0][0] == KAFKA_TOPIC
        assert calls[1][1]["key"] == "002"

        # Verify flush was called
        assert producer.flush.call_count >= 1

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    @patch("tasks.kafka_publish.publish_to_dlq")
    def test_kafka_error_sends_to_dlq(
        self, mock_publish_dlq, mock_create_producer, mock_logger
    ):
        """Test that Kafka errors result in DLQ publishing."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        future = MagicMock()
        future.get.side_effect = KafkaError("Connection failed")
        producer.send.return_value = future
        mock_create_producer.return_value = producer
        mock_publish_dlq.return_value = True

        changed_institutions = [
            {
                "bacen_cod_inst": "001",
                "change_type": "CREATED",
                "institution_data": {
                    "bacen_cod_inst": "001",
                    "nome_instituicao": "Test Bank",
                },
            },
        ]

        result = publish_institution_events.fn(changed_institutions)

        assert result["total"] == 1
        assert result["published"] == 0
        assert result["failed"] == 0  # Not failed because DLQ succeeded
        assert result["dlq_published"] == 1

        # Verify DLQ was called
        mock_publish_dlq.assert_called_once()

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    @patch("tasks.kafka_publish.publish_to_dlq")
    def test_dlq_failure_increments_failed(
        self, mock_publish_dlq, mock_create_producer, mock_logger
    ):
        """Test that DLQ publishing failure increments failed count."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        future = MagicMock()
        future.get.side_effect = KafkaError("Connection failed")
        producer.send.return_value = future
        mock_create_producer.return_value = producer
        mock_publish_dlq.return_value = False  # DLQ publish failed

        changed_institutions = [
            {
                "bacen_cod_inst": "001",
                "change_type": "CREATED",
                "institution_data": {
                    "bacen_cod_inst": "001",
                    "nome_instituicao": "Test Bank",
                },
            },
        ]

        result = publish_institution_events.fn(changed_institutions)

        assert result["total"] == 1
        assert result["published"] == 0
        assert result["failed"] == 1
        assert result["dlq_published"] == 0

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_missing_bacen_cod_inst_skipped(self, mock_create_producer, mock_logger):
        """Test that institutions with missing bacen_cod_inst are skipped."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        mock_create_producer.return_value = producer

        changed_institutions = [
            {
                "change_type": "CREATED",
                "institution_data": {
                    "nome_instituicao": "Test Bank",
                },
            },
        ]

        result = publish_institution_events.fn(changed_institutions)

        assert result["total"] == 1
        assert result["published"] == 0
        assert result["failed"] == 1
        assert result["dlq_published"] == 0

        # Verify producer.send was never called
        producer.send.assert_not_called()

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_invalid_change_type_skipped(self, mock_create_producer, mock_logger):
        """Test that institutions with invalid change_type are skipped."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        mock_create_producer.return_value = producer

        changed_institutions = [
            {
                "bacen_cod_inst": "001",
                "change_type": "INVALID",
                "institution_data": {
                    "bacen_cod_inst": "001",
                    "nome_instituicao": "Test Bank",
                },
            },
        ]

        result = publish_institution_events.fn(changed_institutions)

        assert result["total"] == 1
        assert result["published"] == 0
        assert result["failed"] == 1
        assert result["dlq_published"] == 0

        # Verify producer.send was never called
        producer.send.assert_not_called()

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_value_error_sends_to_dlq(self, mock_create_producer, mock_logger):
        """Test that ValueError during message building sends to DLQ."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        mock_create_producer.return_value = producer

        # Mock build_kafka_message: first call raises ValueError, second call succeeds (for DLQ)
        def build_side_effect(*args, **kwargs):
            # First call raises, second call (for DLQ) returns a message
            if not hasattr(build_side_effect, "call_count"):
                build_side_effect.call_count = 0
            build_side_effect.call_count += 1
            if build_side_effect.call_count == 1:
                raise ValueError("Invalid data")
            else:
                return {"bacen_cod_inst": "001", "change_type": "CREATED"}

        with patch("tasks.kafka_publish.build_kafka_message") as mock_build:
            mock_build.side_effect = build_side_effect

            # Mock publish_to_dlq to succeed
            with patch("tasks.kafka_publish.publish_to_dlq") as mock_dlq:
                mock_dlq.return_value = True

                changed_institutions = [
                    {
                        "bacen_cod_inst": "001",
                        "change_type": "CREATED",
                        "institution_data": {},
                    },
                ]

                result = publish_institution_events.fn(changed_institutions)

                assert result["total"] == 1
                assert result["published"] == 0
                assert result["failed"] == 0  # Not failed because DLQ succeeded
                assert result["dlq_published"] == 1

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_producer_close_called(self, mock_create_producer, mock_logger):
        """Test that producer.close() is called in finally block."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        future = MagicMock()
        future.get.return_value = MagicMock(topic=KAFKA_TOPIC, partition=0, offset=123)
        producer.send.return_value = future
        mock_create_producer.return_value = producer

        changed_institutions = [
            {
                "bacen_cod_inst": "001",
                "change_type": "CREATED",
                "institution_data": {
                    "bacen_cod_inst": "001",
                    "nome_instituicao": "Test Bank",
                },
            },
        ]

        publish_institution_events.fn(changed_institutions)

        # Verify producer.close was called
        producer.close.assert_called_once()

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_producer_close_called_on_error(self, mock_create_producer, mock_logger):
        """Test that producer.close() is called even when errors occur."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        # Make flush raise an error to trigger the outer exception handler
        producer.flush.side_effect = Exception("Flush failed")
        future = MagicMock()
        future.get.return_value = MagicMock(topic=KAFKA_TOPIC, partition=0, offset=123)
        producer.send.return_value = future
        mock_create_producer.return_value = producer

        changed_institutions = [
            {
                "bacen_cod_inst": "001",
                "change_type": "CREATED",
                "institution_data": {
                    "bacen_cod_inst": "001",
                    "nome_instituicao": "Test Bank",
                },
            },
        ]

        # The function should raise the exception, but close should still be called
        with pytest.raises(Exception, match="Flush failed"):
            publish_institution_events.fn(changed_institutions)

        # Verify producer.close was called even on error (finally block executed)
        producer.close.assert_called_once()

    @patch("tasks.kafka_publish.get_run_logger")
    @patch("tasks.kafka_publish.create_kafka_producer")
    def test_large_batch_publishing(self, mock_create_producer, mock_logger):
        """Test publishing a large batch of institutions."""
        mock_logger.return_value = MagicMock()
        producer = MagicMock()
        future = MagicMock()
        future.get.return_value = MagicMock(topic=KAFKA_TOPIC, partition=0, offset=123)
        producer.send.return_value = future
        mock_create_producer.return_value = producer

        # Create 100 institutions
        changed_institutions = [
            {
                "bacen_cod_inst": f"{i:03d}",
                "change_type": "CREATED" if i % 2 == 0 else "UPDATED",
                "institution_data": {
                    "bacen_cod_inst": f"{i:03d}",
                    "nome_instituicao": f"Bank {i}",
                },
            }
            for i in range(100)
        ]

        result = publish_institution_events.fn(changed_institutions)

        assert result["total"] == 100
        assert result["published"] == 100
        assert result["failed"] == 0
        assert result["dlq_published"] == 0

        # Verify all messages were sent
        assert producer.send.call_count == 100
