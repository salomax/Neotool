"""
Institution Enhancement Kafka Consumer

Main consumer that processes financial-data.institution.upserted messages.
"""

import json
import logging
from typing import Any

from kafka import KafkaConsumer
from workflow.common.consumer_base import KafkaConsumerRunner

from tasks.config import Config
from tasks.db_connection import close_pool
from tasks.dlq_publisher import create_kafka_producer, publish_to_dlq
from flows.health import set_consumer_running, start_health_server, stop_health_server
from tasks.message import InstitutionUpsertedMessage
from tasks.processor import process_message

# Configuration
_config = Config.from_env()
KAFKA_BROKERS = _config.kafka.brokers
INPUT_TOPIC = _config.kafka.input_topic
CONSUMER_GROUP = _config.kafka.consumer_group

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

def create_consumer() -> KafkaConsumer:
    """
    Create Kafka consumer with required settings.

    Returns:
        Configured KafkaConsumer instance
    """
    return KafkaConsumer(
        INPUT_TOPIC,
        bootstrap_servers=KAFKA_BROKERS,
        group_id=CONSUMER_GROUP,
        auto_offset_reset="earliest",
        enable_auto_commit=False,  # Manual commit after processing
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        key_deserializer=lambda k: k.decode("utf-8") if k else None,
        max_poll_records=1,  # Process one message at a time for rate limiting
        session_timeout_ms=60000,  # 60 seconds - allows for rate limiting + API calls + DB ops
        heartbeat_interval_ms=10000,  # Send heartbeat every 10 seconds
        max_poll_interval_ms=600000,  # Max time between poll calls (10 minutes) - increased to accommodate retry delays
    )


def _publish_to_dlq(
    producer, message_data: dict, error: Exception, retry_count: int, error_classification: str
) -> bool:
    return publish_to_dlq(
        producer,
        message_data,
        error,
        retry_count=retry_count,
        error_classification=error_classification,
    )


def _format_message_context(message: Any) -> str:
    """
    Format message context for logging.
    
    Handles both dict (raw Kafka message) and InstitutionUpsertedMessage object.
    
    Args:
        message: Either a dict or InstitutionUpsertedMessage instance
        
    Returns:
        Formatted string with bacen_cod_inst
    """
    if hasattr(message, "bacen_cod_inst"):
        return f"bacen_cod_inst={message.bacen_cod_inst}"
    if isinstance(message, dict):
        return f"bacen_cod_inst={message.get('bacen_cod_inst', 'unknown')}"
    return "bacen_cod_inst=unknown"


def run_consumer() -> None:
    """
    Instantiate the shared consumer runner and execute it.
    """
    runner = KafkaConsumerRunner(
        consumer_factory=create_consumer,
        producer_factory=create_kafka_producer,
        retry_config=_config.retry,
        message_factory=InstitutionUpsertedMessage.from_dict,
        process_message=process_message,
        dlq_publisher=_publish_to_dlq,
        rate_limit_min_delay_ms=lambda: _config.cnpja.rate_limit_seconds * 1000,
        health_server_start=lambda: start_health_server(_config.health_check),
        health_server_stop=stop_health_server,
        running_flag_setter=set_consumer_running,
        cleanup_callback=close_pool,
        message_context=_format_message_context,
        logger=logger,
    )
    runner.run()


if __name__ == "__main__":
    run_consumer()
