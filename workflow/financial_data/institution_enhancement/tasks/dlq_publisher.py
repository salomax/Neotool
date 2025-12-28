"""
DLQ Publisher

Publishes failed messages to Dead Letter Queue topic.
"""

import json
import logging
from datetime import datetime, timezone
from typing import Any, Dict

from kafka import KafkaProducer
from kafka.errors import KafkaError, KafkaTimeoutError

from tasks.config import Config

logger = logging.getLogger(__name__)

# Lazy import to avoid circular dependencies
_is_retryable_error = None


def _get_is_retryable_error():
    """Lazy import of is_retryable_error to avoid circular dependencies."""
    global _is_retryable_error
    if _is_retryable_error is None:
        try:
            from tasks.error_classification import is_retryable_error

            _is_retryable_error = is_retryable_error
        except ImportError:
            # Fallback function if module not available
            def _fallback_is_retryable_error(error):
                return True  # Default to retryable

            _is_retryable_error = _fallback_is_retryable_error
    return _is_retryable_error


DLQ_TOPIC = "financial-data.institution.upserted.enhancement.dlq"


def create_kafka_producer() -> KafkaProducer:
    """
    Create Kafka producer with required settings.

    Returns:
        Configured KafkaProducer instance
    """
    config = Config.from_env()
    return KafkaProducer(
        bootstrap_servers=config.kafka.brokers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: str(k).encode("utf-8") if k else None,
        acks="all",
        enable_idempotence=True,
        compression_type="gzip",
        linger_ms=10,
        retries=5,
    )


def publish_to_dlq(
    producer: KafkaProducer,
    original_message: Dict[str, Any],
    error: Exception,
    retry_count: int = 0,
    error_classification: str = "unknown",
) -> bool:
    """
    Publish failed message to DLQ topic.

    DLQ message includes:
    - Original message
    - Error information
    - Timestamp of failure
    - Retry count
    - Error classification

    Args:
        producer: Kafka producer instance
        original_message: Original Kafka message that failed
        error: Exception that caused the failure
        retry_count: Number of retry attempts made before DLQ
        error_classification: "retryable" or "non-retryable"

    Returns:
        True if successfully published to DLQ, False otherwise
    """
    bacen_cod_inst = original_message.get("bacen_cod_inst", "")
    error_type = type(error).__name__ if isinstance(error, Exception) else "Unknown"
    error_message = str(error)

    # Determine classification if not provided
    if error_classification == "unknown":
        is_retryable_error = _get_is_retryable_error()
        error_classification = "retryable" if is_retryable_error(error) else "non-retryable"

    logger.info(
        f"Publishing to DLQ: bacen_cod_inst={bacen_cod_inst or 'empty'}, "
        f"error_type={error_type}, retry_count={retry_count}, "
        f"classification={error_classification}, topic={DLQ_TOPIC}"
    )

    try:
        dlq_message = {
            "original_message": original_message,
            "error_type": error_type,
            "error_message": error_message,
            "failed_at": datetime.now(timezone.utc).isoformat(),
            "retry_count": retry_count,
            "error_classification": error_classification,
        }

        # Use bacen_cod_inst as key for partitioning (empty string if missing)
        key = str(bacen_cod_inst) if bacen_cod_inst else ""

        future = producer.send(
            DLQ_TOPIC,
            key=key,
            value=dlq_message,
        )

        # Wait for confirmation
        record_metadata = future.get(timeout=10)
        logger.info(
            f"Successfully published to DLQ: bacen_cod_inst={bacen_cod_inst}, "
            f"topic={record_metadata.topic}, partition={record_metadata.partition}, "
            f"offset={record_metadata.offset}"
        )
        return True

    except KafkaTimeoutError as e:
        logger.error(
            f"DLQ publish timeout: bacen_cod_inst={bacen_cod_inst or 'empty'}, "
            f"error_type={error_type}, error={e}"
        )
        return False
    except KafkaError as e:
        logger.error(
            f"DLQ publish Kafka error: bacen_cod_inst={bacen_cod_inst or 'empty'}, "
            f"error_type={error_type}, kafka_error={e}",
            exc_info=True,
        )
        return False
    except Exception as e:
        logger.error(
            f"DLQ publish unexpected error: bacen_cod_inst={bacen_cod_inst or 'empty'}, "
            f"error_type={error_type}, unexpected_error={e}",
            exc_info=True,
        )
        return False

