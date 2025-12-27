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
) -> bool:
    """
    Publish failed message to DLQ topic.

    DLQ message includes:
    - Original message
    - Error information
    - Timestamp of failure

    Args:
        producer: Kafka producer instance
        original_message: Original Kafka message that failed
        error: Exception that caused the failure

    Returns:
        True if successfully published to DLQ, False otherwise
    """
    bacen_cod_inst = original_message.get("bacen_cod_inst", "")
    error_type = type(error).__name__
    error_message = str(error)

    logger.info(
        f"Publishing to DLQ: bacen_cod_inst={bacen_cod_inst or 'empty'}, "
        f"error_type={error_type}, topic={DLQ_TOPIC}"
    )

    try:
        dlq_message = {
            "original_message": original_message,
            "error_type": error_type,
            "error_message": error_message,
            "failed_at": datetime.now(timezone.utc).isoformat(),
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

