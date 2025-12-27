"""
Institution Enhancement Kafka Consumer

Main consumer that processes financial-data.institution.upserted messages.
"""

import json
import logging
import signal
import sys
from pathlib import Path

from kafka import KafkaConsumer
from kafka.errors import KafkaError

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from tasks.config import Config
from tasks.db_connection import close_pool
from tasks.dlq_publisher import create_kafka_producer, publish_to_dlq
from tasks.health import set_consumer_running, start_health_server
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
        max_poll_records=100,
        session_timeout_ms=30000,
        heartbeat_interval_ms=10000,
    )


def run_consumer() -> None:
    """
    Run the Kafka consumer in a loop.

    Processes messages sequentially per partition to enforce rate limiting.
    """
    consumer = create_consumer()
    producer = create_kafka_producer()

    # Flag for graceful shutdown
    running = True

    def signal_handler(sig, frame):
        """Handle shutdown signals gracefully."""
        nonlocal running
        logger.info("Received shutdown signal, stopping consumer...")
        running = False

    # Register signal handlers for graceful shutdown
    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    logger.info(f"Starting consumer for topic: {INPUT_TOPIC}")
    logger.info(f"Consumer group: {CONSUMER_GROUP}")

    # Start health check server
    health_thread = start_health_server()
    if health_thread:
        logger.info("Health check server started")

    # Set consumer as running for liveness probe
    set_consumer_running(True)

    try:
        for message in consumer:
            if not running:
                logger.info("Shutdown flag set, exiting consumer loop...")
                break

            try:
                # Parse message
                message_data = message.value
                institution_message = InstitutionUpsertedMessage.from_dict(message_data)

                logger.info(
                    f"Processing message: bacen_cod_inst={institution_message.bacen_cod_inst}, "
                    f"partition={message.partition}, offset={message.offset}"
                )

                # Process message
                result = process_message(institution_message)

                if result["success"]:
                    logger.info(
                        f"Successfully processed: parent_cnpj={result['parent_cnpj']}, "
                        f"domain={result['domain']}"
                    )
                    # Commit only on successful processing
                    consumer.commit()
                else:
                    # Processing failed - categorize error and handle accordingly
                    error_msg = result.get("error", "Unknown error")
                    logger.error(
                        f"Processing failed: parent_cnpj={result['parent_cnpj']}, "
                        f"bacen_cod_inst={institution_message.bacen_cod_inst}, "
                        f"error={error_msg}"
                    )
                    # Publish to DLQ and commit only if DLQ publish succeeds
                    processing_error = Exception(error_msg)
                    if publish_to_dlq(producer, message_data, processing_error):
                        consumer.commit()
                        logger.info("Processing failure published to DLQ and committed")
                    else:
                        logger.error(
                            f"Failed to publish processing failure to DLQ, "
                            f"message not committed (will be retried): "
                            f"bacen_cod_inst={institution_message.bacen_cod_inst}"
                        )

            except ValueError as e:
                # Validation error - message is invalid, should go to DLQ
                logger.error(
                    f"Invalid message: bacen_cod_inst={message_data.get('bacen_cod_inst', 'unknown')}, "
                    f"error={e}"
                )
                # Publish to DLQ for validation errors - only commit if DLQ succeeds
                try:
                    if publish_to_dlq(producer, message.value, e):
                        consumer.commit()
                        logger.info("Invalid message published to DLQ and committed")
                    else:
                        logger.error(
                            f"Failed to publish invalid message to DLQ, "
                            f"not committed (will be retried): "
                            f"bacen_cod_inst={message_data.get('bacen_cod_inst', 'unknown')}"
                        )
                except Exception as dlq_error:
                    logger.exception(
                        f"Exception while publishing invalid message to DLQ: {dlq_error}, "
                        f"original_error={e}"
                    )
                    # Don't commit if DLQ publish fails
            except Exception as e:
                # Processing error - unexpected exception during processing
                logger.exception(
                    f"Error processing message: bacen_cod_inst={message_data.get('bacen_cod_inst', 'unknown')}, "
                    f"error={e}"
                )
                # Publish to DLQ for processing errors - only commit if DLQ succeeds
                try:
                    if publish_to_dlq(producer, message.value, e):
                        consumer.commit()
                        logger.info("Processing error published to DLQ and committed")
                    else:
                        logger.error(
                            f"Failed to publish processing error to DLQ, "
                            f"not committed (will be retried): "
                            f"bacen_cod_inst={message_data.get('bacen_cod_inst', 'unknown')}"
                        )
                except Exception as dlq_error:
                    logger.exception(
                        f"Exception while publishing processing error to DLQ: {dlq_error}, "
                        f"original_error={e}"
                    )
                    # Don't commit if DLQ publish fails

    except KeyboardInterrupt:
        logger.info("Shutting down consumer...")
    except Exception:
        logger.exception("Fatal error in consumer")
        raise
    finally:
        # Set consumer as not running for liveness probe
        set_consumer_running(False)
        consumer.close()
        producer.close()
        close_pool()  # Close database connection pool
        logger.info("Consumer stopped")


if __name__ == "__main__":
    run_consumer()

