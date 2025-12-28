"""
Institution Enhancement Kafka Consumer

Main consumer that processes financial-data.institution.upserted messages.
"""

import json
import logging
import signal
import sys
import time
from pathlib import Path

from kafka import KafkaConsumer
from kafka.errors import KafkaError, CommitFailedError

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from tasks.config import Config
from tasks.db_connection import close_pool
from tasks.dlq_publisher import create_kafka_producer, publish_to_dlq
from tasks.error_classification import is_retryable_error
from tasks.retry_utils import calculate_backoff
from flows.health import set_consumer_running, start_health_server
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


def _process_with_retry(
    message: InstitutionUpsertedMessage,
    message_data: dict,
    producer,
    consumer,
) -> dict:
    """
    Process message with retry logic and exponential backoff.

    Implements Kafka Consumer Retry Standard:
    - Classifies errors as retryable vs non-retryable
    - Retries retryable errors with exponential backoff
    - Sends non-retryable errors to DLQ immediately
    - Sends retryable errors to DLQ after max retries exceeded

    Args:
        message: InstitutionUpsertedMessage instance
        message_data: Original message dict for DLQ
        producer: Kafka producer for DLQ publishing
        consumer: Kafka consumer (for potential pause/resume)

    Returns:
        Dictionary with processing results including retry_count and dlq_published
    """
    retry_count = 0
    max_retries = _config.retry.max_retries
    rate_limit_min_delay_ms = _config.cnpja.rate_limit_seconds * 1000

    while retry_count <= max_retries:
        try:
            # Process message
            result = process_message(message)

            if result["success"]:
                return result

            # Processing failed - check if error is retryable
            error_msg = result.get("error", "Unknown error")
            error = Exception(error_msg) if error_msg else Exception("Unknown error")

            if not is_retryable_error(error):
                # Non-retryable error - send to DLQ immediately
                logger.warning(
                    f"Non-retryable error (attempt {retry_count + 1}), sending to DLQ immediately: "
                    f"bacen_cod_inst={message.bacen_cod_inst}, error={error_msg}"
                )
                dlq_published = publish_to_dlq(
                    producer,
                    message_data,
                    error,
                    retry_count=retry_count,
                    error_classification="non-retryable",
                )
                result["retry_count"] = retry_count
                result["dlq_published"] = dlq_published
                return result

            # Retryable error - check if we should retry
            if retry_count >= max_retries:
                # Max retries exceeded - send to DLQ
                logger.error(
                    f"Max retries exceeded ({max_retries}), sending to DLQ: "
                    f"bacen_cod_inst={message.bacen_cod_inst}, error={error_msg}"
                )
                dlq_published = publish_to_dlq(
                    producer,
                    message_data,
                    error,
                    retry_count=retry_count,
                    error_classification="retryable",
                )
                result["retry_count"] = retry_count
                result["dlq_published"] = dlq_published
                return result

            # Calculate and apply backoff
            delay_ms = calculate_backoff(retry_count, _config.retry, rate_limit_min_delay_ms)
            logger.warning(
                f"Retryable error (attempt {retry_count + 1}/{max_retries + 1}), "
                f"retrying in {delay_ms}ms: bacen_cod_inst={message.bacen_cod_inst}, error={error_msg}"
            )
            time.sleep(delay_ms / 1000.0)
            retry_count += 1

        except Exception as e:
            # Exception during processing
            if not is_retryable_error(e):
                # Non-retryable exception - send to DLQ immediately
                logger.warning(
                    f"Non-retryable exception (attempt {retry_count + 1}), sending to DLQ immediately: "
                    f"bacen_cod_inst={message.bacen_cod_inst}, error={e}"
                )
                dlq_published = publish_to_dlq(
                    producer,
                    message_data,
                    e,
                    retry_count=retry_count,
                    error_classification="non-retryable",
                )
                return {
                    "success": False,
                    "parent_cnpj": None,
                    "domain": None,
                    "error": str(e),
                    "retry_count": retry_count,
                    "dlq_published": dlq_published,
                }

            # Retryable exception - check if we should retry
            if retry_count >= max_retries:
                # Max retries exceeded - send to DLQ
                logger.error(
                    f"Max retries exceeded ({max_retries}), sending to DLQ: "
                    f"bacen_cod_inst={message.bacen_cod_inst}, error={e}"
                )
                dlq_published = publish_to_dlq(
                    producer,
                    message_data,
                    e,
                    retry_count=retry_count,
                    error_classification="retryable",
                )
                return {
                    "success": False,
                    "parent_cnpj": None,
                    "domain": None,
                    "error": str(e),
                    "retry_count": retry_count,
                    "dlq_published": dlq_published,
                }

            # Calculate and apply backoff
            delay_ms = calculate_backoff(retry_count, _config.retry, rate_limit_min_delay_ms)
            logger.warning(
                f"Retryable exception (attempt {retry_count + 1}/{max_retries + 1}), "
                f"retrying in {delay_ms}ms: bacen_cod_inst={message.bacen_cod_inst}, error={e}"
            )
            time.sleep(delay_ms / 1000.0)
            retry_count += 1

    # Should not reach here, but handle gracefully
    logger.error(f"Retry loop exhausted without returning: bacen_cod_inst={message.bacen_cod_inst}")
    return {
        "success": False,
        "parent_cnpj": None,
        "domain": None,
        "error": "Retry loop exhausted",
        "retry_count": retry_count,
        "dlq_published": False,
    }


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

    def safe_commit(consumer: KafkaConsumer, context: str = "") -> bool:
        """
        Safely commit offsets, handling CommitFailedError gracefully.
        
        Args:
            consumer: Kafka consumer instance
            context: Context string for logging
            
        Returns:
            True if commit succeeded, False otherwise
        """
        try:
            consumer.commit()
            return True
        except CommitFailedError as e:
            logger.warning(
                f"Failed to commit offsets{(' ' + context) if context else ''}: "
                f"Consumer was kicked out of group. This is expected if processing took too long. "
                f"Message will be reprocessed. Error: {e}"
            )
            return False
        except Exception as e:
            logger.error(
                f"Unexpected error during commit{(' ' + context) if context else ''}: {e}",
                exc_info=True
            )
            return False

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

                # Process message with retry logic
                result = _process_with_retry(
                    institution_message, message_data, producer, consumer
                )

                if result["success"]:
                    logger.info(
                        f"Successfully processed: parent_cnpj={result['parent_cnpj']}, "
                        f"domain={result['domain']}"
                    )
                    # Commit only on successful processing
                    safe_commit(consumer, "after successful processing")
                else:
                    # Processing failed after all retries - already sent to DLQ
                    error_msg = result.get("error", "Unknown error")
                    logger.error(
                        f"Processing failed after retries: parent_cnpj={result.get('parent_cnpj', 'unknown')}, "
                        f"bacen_cod_inst={institution_message.bacen_cod_inst}, "
                        f"retry_count={result.get('retry_count', 0)}, error={error_msg}"
                    )
                    # DLQ publish was attempted in retry logic, commit if it succeeded
                    if result.get("dlq_published", False):
                        if safe_commit(consumer, "after DLQ publish"):
                            logger.info("Processing failure published to DLQ and committed")
                        else:
                            logger.warning(
                                "Processing failure published to DLQ but commit failed. "
                                "Message will be reprocessed but DLQ already has it."
                            )
                    else:
                        logger.error(
                            f"Failed to publish processing failure to DLQ, "
                            f"message not committed (will be retried): "
                            f"bacen_cod_inst={institution_message.bacen_cod_inst}"
                        )

            except ValueError as e:
                # Validation error - message is invalid, should go to DLQ immediately (non-retryable)
                logger.error(
                    f"Invalid message: bacen_cod_inst={message_data.get('bacen_cod_inst', 'unknown')}, "
                    f"error={e}"
                )
                # Publish to DLQ for validation errors - only commit if DLQ succeeds
                try:
                    if publish_to_dlq(
                        producer,
                        message.value,
                        e,
                        retry_count=0,
                        error_classification="non-retryable",
                    ):
                        if safe_commit(consumer, "after invalid message DLQ publish"):
                            logger.info("Invalid message published to DLQ and committed")
                        else:
                            logger.warning(
                                "Invalid message published to DLQ but commit failed. "
                                "Message will be reprocessed but DLQ already has it."
                            )
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
                # Check if error is retryable
                if is_retryable_error(e):
                    # Retryable error - should have been handled in retry logic
                    # This shouldn't happen, but handle gracefully
                    logger.warning(
                        f"Retryable exception escaped retry logic: {e}. "
                        f"Sending to DLQ with retry_count=0"
                    )
                    error_classification = "retryable"
                else:
                    # Non-retryable error - send to DLQ immediately
                    error_classification = "non-retryable"

                # Publish to DLQ for processing errors - only commit if DLQ succeeds
                try:
                    if publish_to_dlq(
                        producer,
                        message.value,
                        e,
                        retry_count=0,
                        error_classification=error_classification,
                    ):
                        if safe_commit(consumer, "after processing error DLQ publish"):
                            logger.info("Processing error published to DLQ and committed")
                        else:
                            logger.warning(
                                "Processing error published to DLQ but commit failed. "
                                "Message will be reprocessed but DLQ already has it."
                            )
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

