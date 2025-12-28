"""
Institution Enhancement Kafka Consumer

Main consumer that processes financial-data.institution.upserted messages.
"""

import json
import logging
import sys
from pathlib import Path

# Add parent directory to path for imports
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

DOMAIN_ROOT = Path(__file__).parent.parent
if str(DOMAIN_ROOT) not in sys.path:
    sys.path.insert(0, str(DOMAIN_ROOT))

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
        message_context=lambda message: f"bacen_cod_inst={message.bacen_cod_inst}",
        logger=logger,
    )
    runner.run()


if __name__ == "__main__":
    run_consumer()
