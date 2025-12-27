"""
Unit tests for DLQ publisher.

Tests cover:
- Kafka producer creation
- Message publishing to DLQ
- Message structure
- Error handling
"""

from datetime import datetime, timezone
from unittest.mock import MagicMock, Mock, patch

import pytest
from kafka.errors import KafkaError

from tasks.dlq_publisher import DLQ_TOPIC, create_kafka_producer, publish_to_dlq


# ============================================================================
# Unit Tests - create_kafka_producer
# ============================================================================


class TestCreateKafkaProducer:
    """Test create_kafka_producer function."""

    @patch("tasks.dlq_publisher.KafkaProducer")
    @patch("tasks.dlq_publisher.Config")
    def test_producer_created_with_correct_settings(self, mock_config_class, mock_kafka_producer_class):
        """Test that producer is created with correct settings."""
        # Arrange
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer
        mock_config = MagicMock()
        mock_config.kafka.brokers = ["localhost:9092"]
        mock_config_class.from_env.return_value = mock_config

        # Act
        result = create_kafka_producer()

        # Assert
        mock_kafka_producer_class.assert_called_once()
        call_kwargs = mock_kafka_producer_class.call_args[1]

        assert call_kwargs["acks"] == "all"
        assert call_kwargs["enable_idempotence"] is True
        assert call_kwargs["compression_type"] == "gzip"
        assert call_kwargs["linger_ms"] == 10
        assert call_kwargs["retries"] == 5
        assert "bootstrap_servers" in call_kwargs
        assert "value_serializer" in call_kwargs
        assert "key_serializer" in call_kwargs

    @patch("tasks.dlq_publisher.KafkaProducer")
    @patch("tasks.dlq_publisher.Config")
    def test_producer_uses_environment_brokers(self, mock_config_class, mock_kafka_producer_class):
        """Test that producer uses KAFKA_BROKERS from environment."""
        # Arrange
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer
        mock_config = MagicMock()
        mock_config.kafka.brokers = ["broker1:9092", "broker2:9092"]
        mock_config_class.from_env.return_value = mock_config

        # Act
        create_kafka_producer()

        # Assert
        call_kwargs = mock_kafka_producer_class.call_args[1]
        assert call_kwargs["bootstrap_servers"] == ["broker1:9092", "broker2:9092"]

    @patch("tasks.dlq_publisher.KafkaProducer")
    @patch("tasks.dlq_publisher.Config")
    def test_producer_uses_default_brokers_when_env_not_set(self, mock_config_class, mock_kafka_producer_class):
        """Test that producer uses default brokers when env not set."""
        # Arrange
        mock_producer = MagicMock()
        mock_kafka_producer_class.return_value = mock_producer
        mock_config = MagicMock()
        mock_config.kafka.brokers = ["localhost:9092"]  # Default value
        mock_config_class.from_env.return_value = mock_config

        # Act
        create_kafka_producer()

        # Assert
        call_kwargs = mock_kafka_producer_class.call_args[1]
        assert call_kwargs["bootstrap_servers"] == ["localhost:9092"]


# ============================================================================
# Unit Tests - publish_to_dlq
# ============================================================================


class TestPublishToDlq:
    """Test publish_to_dlq function."""

    @patch("tasks.dlq_publisher.datetime")
    def test_successful_publish_to_dlq(self, mock_datetime):
        """Test successful publishing to DLQ."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_record_metadata = MagicMock()
        mock_future.get.return_value = mock_record_metadata
        mock_producer.send.return_value = mock_future

        original_message = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
        }
        error = ValueError("Test error")

        # Mock datetime
        fixed_time = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
        mock_datetime.now.return_value = fixed_time

        # Act
        result = publish_to_dlq(mock_producer, original_message, error)

        # Assert
        assert result is True

        # Verify producer.send was called
        mock_producer.send.assert_called_once()
        call_args = mock_producer.send.call_args

        assert call_args[0][0] == DLQ_TOPIC
        assert call_args[1]["key"] == "60872504"  # bacen_cod_inst as key

        # Verify DLQ message structure
        dlq_message = call_args[1]["value"]
        assert dlq_message["original_message"] == original_message
        assert dlq_message["error_type"] == "ValueError"
        assert dlq_message["error_message"] == "Test error"
        assert dlq_message["failed_at"] == fixed_time.isoformat()

        # Verify future.get was called with timeout
        mock_future.get.assert_called_once_with(timeout=10)

    @patch("tasks.dlq_publisher.datetime")
    def test_dlq_message_structure(self, mock_datetime):
        """Test that DLQ message has correct structure."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock()
        mock_producer.send.return_value = mock_future

        original_message = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "bacen_cod_inst": "60872504",
        }
        error = RuntimeError("Runtime error occurred")

        fixed_time = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
        mock_datetime.now.return_value = fixed_time

        # Act
        publish_to_dlq(mock_producer, original_message, error)

        # Assert: Verify message structure
        call_args = mock_producer.send.call_args
        dlq_message = call_args[1]["value"]

        assert "original_message" in dlq_message
        assert "error_type" in dlq_message
        assert "error_message" in dlq_message
        assert "failed_at" in dlq_message

        assert dlq_message["original_message"] == original_message
        assert dlq_message["error_type"] == "RuntimeError"
        assert dlq_message["error_message"] == "Runtime error occurred"
        assert isinstance(dlq_message["failed_at"], str)
        assert "T" in dlq_message["failed_at"]  # ISO format

    @patch("tasks.dlq_publisher.datetime")
    def test_uses_bacen_cod_inst_as_key(self, mock_datetime):
        """Test that bacen_cod_inst is used as message key."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock()
        mock_producer.send.return_value = mock_future

        original_message = {"bacen_cod_inst": "60872504"}
        error = Exception("Test error")

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        # Act
        publish_to_dlq(mock_producer, original_message, error)

        # Assert
        call_args = mock_producer.send.call_args
        assert call_args[1]["key"] == "60872504"

    @patch("tasks.dlq_publisher.datetime")
    def test_uses_empty_string_as_key_when_bacen_cod_inst_missing(self, mock_datetime):
        """Test that empty string is used as key when bacen_cod_inst is missing."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock()
        mock_producer.send.return_value = mock_future

        original_message = {}  # No bacen_cod_inst
        error = Exception("Test error")

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        # Act
        publish_to_dlq(mock_producer, original_message, error)

        # Assert
        call_args = mock_producer.send.call_args
        assert call_args[1]["key"] == ""

    @patch("tasks.dlq_publisher.datetime")
    def test_publish_failure_returns_false(self, mock_datetime):
        """Test that publish failure returns False."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.side_effect = KafkaError("Kafka error")
        mock_producer.send.return_value = mock_future

        original_message = {"bacen_cod_inst": "60872504"}
        error = Exception("Test error")

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        # Act
        result = publish_to_dlq(mock_producer, original_message, error)

        # Assert
        assert result is False

    @patch("tasks.dlq_publisher.datetime")
    def test_timeout_error_returns_false(self, mock_datetime):
        """Test that timeout error returns False."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.side_effect = TimeoutError("Timeout")
        mock_producer.send.return_value = mock_future

        original_message = {"bacen_cod_inst": "60872504"}
        error = Exception("Test error")

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        # Act
        result = publish_to_dlq(mock_producer, original_message, error)

        # Assert
        assert result is False

    @patch("tasks.dlq_publisher.datetime")
    def test_send_exception_returns_false(self, mock_datetime):
        """Test that exception during send returns False."""
        # Arrange
        mock_producer = MagicMock()
        mock_producer.send.side_effect = Exception("Send failed")

        original_message = {"bacen_cod_inst": "60872504"}
        error = Exception("Test error")

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        # Act
        result = publish_to_dlq(mock_producer, original_message, error)

        # Assert
        assert result is False

    @patch("tasks.dlq_publisher.datetime")
    def test_error_type_extraction(self, mock_datetime):
        """Test that error type is correctly extracted."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock()
        mock_producer.send.return_value = mock_future

        original_message = {"bacen_cod_inst": "60872504"}

        test_cases = [
            (ValueError("Value error"), "ValueError"),
            (RuntimeError("Runtime error"), "RuntimeError"),
            (KeyError("Key error"), "KeyError"),
            (Exception("Generic error"), "Exception"),
        ]

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        for error, expected_type in test_cases:
            # Act
            publish_to_dlq(mock_producer, original_message, error)

            # Assert
            call_args = mock_producer.send.call_args
            dlq_message = call_args[1]["value"]
            assert dlq_message["error_type"] == expected_type

            # Reset mock for next iteration
            mock_producer.send.reset_mock()

    @patch("tasks.dlq_publisher.datetime")
    def test_error_message_extraction(self, mock_datetime):
        """Test that error message is correctly extracted."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock()
        mock_producer.send.return_value = mock_future

        original_message = {"bacen_cod_inst": "60872504"}
        error = ValueError("Custom error message")

        mock_datetime.now.return_value = datetime.now(timezone.utc)

        # Act
        publish_to_dlq(mock_producer, original_message, error)

        # Assert
        call_args = mock_producer.send.call_args
        dlq_message = call_args[1]["value"]
        assert dlq_message["error_message"] == "Custom error message"

    @patch("tasks.dlq_publisher.datetime")
    def test_failed_at_timestamp_format(self, mock_datetime):
        """Test that failed_at timestamp is in ISO format."""
        # Arrange
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock()
        mock_producer.send.return_value = mock_future

        original_message = {"bacen_cod_inst": "60872504"}
        error = Exception("Test error")

        fixed_time = datetime(2024, 1, 1, 12, 30, 45, tzinfo=timezone.utc)
        mock_datetime.now.return_value = fixed_time

        # Act
        publish_to_dlq(mock_producer, original_message, error)

        # Assert
        call_args = mock_producer.send.call_args
        dlq_message = call_args[1]["value"]
        assert dlq_message["failed_at"] == "2024-01-01T12:30:45+00:00"

