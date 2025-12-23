"""
Kafka Publishing Task

Publishes Kafka events for institutions that were newly created or updated
during registry ingestion. Handles DLQ publishing for failed messages.
"""

from datetime import UTC, datetime
import json
import os
from typing import Any
import uuid

from kafka import KafkaProducer
from kafka.errors import KafkaError
from prefect import get_run_logger, task

# Configuration constants
KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092").split(",")
KAFKA_TOPIC = "financial-data.institution.upserted"
KAFKA_DLQ_TOPIC = "financial-data.institution.upserted.dlq"
KAFKA_COMPRESSION_TYPE = os.getenv("KAFKA_COMPRESSION_TYPE", "gzip")
SCHEMA_VERSION = "1.0"
SOURCE = "BACEN"

# Institution fields to include in Kafka message
INSTITUTION_FIELDS = [
    "nome_reduzido",
    "nome_instituicao",
    "situacao",
    "segmento_tb",
    "atividade",
    "tipo_controle",
    "uf",
    "municipio",
    "cod_conglomerado_financeiro",
    "cod_conglomerado_prudencial",
    "cnpj_instituicao_lider",
]


def build_kafka_message(
    institution: dict[str, Any], change_type: str
) -> dict[str, Any]:
    """
    Build Kafka message from institution data.

    Args:
        institution: Institution dictionary from registry ingestion
        change_type: "CREATED" or "UPDATED"

    Returns:
        Kafka message dictionary with all required fields
    """
    institution_data = institution.get("institution_data", institution)
    bacen_cod_inst = institution.get("bacen_cod_inst") or institution_data.get(
        "bacen_cod_inst"
    )

    if not bacen_cod_inst:
        raise ValueError("bacen_cod_inst is required for Kafka message")

    # Coerce bacen_cod_inst to string to handle int values from CSV/DB
    bacen_cod_inst = str(bacen_cod_inst)

    # Build message with required fields
    message = {
        "schema_version": SCHEMA_VERSION,
        "event_id": str(uuid.uuid4()),  # UUID v4 for event ID (uniqueness)
        "occurred_at": datetime.now(UTC).isoformat(),
        "source": SOURCE,
        "bacen_cod_inst": bacen_cod_inst,
        "change_type": change_type,
    }

    # Add institution fields
    for field in INSTITUTION_FIELDS:
        value = institution_data.get(field)
        # Include field even if None (for schema consistency)
        message[field] = value

    # Add optional fields if present
    if "source_hash" in institution_data:
        message["source_hash"] = institution_data["source_hash"]

    if "ano_mes_ref" in institution_data:
        message["ano_mes_ref"] = institution_data["ano_mes_ref"]

    return message


def create_kafka_producer() -> KafkaProducer:
    """
    Create Kafka producer with required settings per batch workflow standard.

    Producer settings:
    - acks='all': Wait for all replicas
    - enable_idempotence=True: Prevent duplicates
    - compression_type: From env var or 'gzip' default
    - linger_ms=10: Batch small messages
    - retries=5: Retry failed sends

    Returns:
        Configured KafkaProducer instance
    """
    return KafkaProducer(
        bootstrap_servers=KAFKA_BROKERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: str(k).encode("utf-8") if k else None,
        acks="all",
        enable_idempotence=True,
        compression_type=KAFKA_COMPRESSION_TYPE,
        linger_ms=10,
        retries=5,
    )


def publish_to_dlq(
    producer: KafkaProducer, message: dict[str, Any], error: Exception, logger
) -> bool:
    """
    Publish failed message to DLQ topic.

    DLQ message includes:
    - Original message
    - Error information
    - Timestamp of failure

    Args:
        producer: Kafka producer instance
        message: Original Kafka message that failed
        error: Exception that caused the failure
        logger: Logger instance for logging

    Returns:
        True if successfully published to DLQ, False otherwise
    """
    try:
        dlq_message = {
            "original_message": message,
            "error_type": type(error).__name__,
            "error_message": str(error),
            "failed_at": datetime.now(UTC).isoformat(),
        }

        # Use same key as original message for partitioning
        # Coerce to string to handle int values
        key = str(message.get("bacen_cod_inst", ""))

        future = producer.send(
            KAFKA_DLQ_TOPIC,
            key=key,
            value=dlq_message,
        )

        # Wait for confirmation
        record_metadata = future.get(timeout=10)
        logger.info(
            f"Published message to DLQ: topic={record_metadata.topic}, "
            f"partition={record_metadata.partition}, offset={record_metadata.offset}"
        )
        return True

    except Exception:
        logger.exception("Failed to publish message to DLQ")
        return False


@task(
    name="publish_institution_events",
    retries=5,
    retry_delay_seconds=30,
    timeout_seconds=300,
    log_prints=True,
    tags=["kafka", "publish", "institution"],
)
def publish_institution_events(
    changed_institutions: list[dict[str, Any]],
) -> dict[str, int]:
    """
    Publish Kafka events for institutions that were newly created or updated.

    This task:
    1. Builds Kafka messages from changed institutions
    2. Publishes to financial-data.institution.upserted topic
    3. Uses bacen_cod_inst as message key
    4. Handles DLQ publishing for failed messages
    5. Returns statistics about the publish operation

    Args:
        changed_institutions: List of dictionaries from ingest_registry() task,
            each containing:
            - bacen_cod_inst: Institution code
            - change_type: "CREATED" or "UPDATED"
            - institution_data: Full institution record

    Returns:
        Dictionary with statistics:
        - total: Total number of institutions to publish
        - published: Successfully published count
        - failed: Failed count (after retries)
        - dlq_published: Successfully published to DLQ count

    Raises:
        ValueError: If institution data is invalid
        KafkaError: If Kafka operations fail after retries
    """
    logger = get_run_logger()

    if not changed_institutions:
        logger.info("No changed institutions to publish")
        return {
            "total": 0,
            "published": 0,
            "failed": 0,
            "dlq_published": 0,
        }

    logger.info(f"Publishing {len(changed_institutions)} institution events")

    producer = create_kafka_producer()

    stats = {
        "total": len(changed_institutions),
        "published": 0,
        "failed": 0,
        "dlq_published": 0,
    }

    failed_messages = []

    try:
        for institution in changed_institutions:
            bacen_cod_inst = institution.get("bacen_cod_inst") or institution.get(
                "institution_data", {}
            ).get("bacen_cod_inst")
            change_type = institution.get("change_type")

            if not bacen_cod_inst:
                logger.error(
                    f"Skipping institution with missing bacen_cod_inst: {institution}"
                )
                stats["failed"] += 1
                continue

            if change_type not in ("CREATED", "UPDATED"):
                logger.error(
                    f"Invalid change_type '{change_type}' for institution {bacen_cod_inst}"
                )
                stats["failed"] += 1
                continue

            # Coerce bacen_cod_inst to string for use as Kafka key
            bacen_cod_inst = str(bacen_cod_inst)

            # Initialize message to None - will be set if build succeeds
            message = None

            try:
                # Build Kafka message
                message = build_kafka_message(institution, change_type)

                # Publish to main topic
                future = producer.send(
                    KAFKA_TOPIC,
                    key=bacen_cod_inst,
                    value=message,
                )

                # Wait for confirmation
                future.get(timeout=10)
                stats["published"] += 1

                if stats["published"] % 10 == 0:
                    logger.info(
                        f"Published {stats['published']}/{stats['total']} messages"
                    )

            except KafkaError as e:
                logger.exception(
                    f"Kafka error publishing message for {bacen_cod_inst}"
                )
                # Reuse original message if available, otherwise try to rebuild
                if message is not None:
                    # Message was successfully built, reuse it for DLQ
                    failed_messages.append((message, e))
                else:
                    # Message build failed, try to rebuild for DLQ
                    try:
                        message = build_kafka_message(institution, change_type)
                        failed_messages.append((message, e))
                    except Exception:
                        logger.exception(
                            f"Failed to build message for DLQ for {bacen_cod_inst}"
                        )
                        stats["failed"] += 1

            except ValueError as e:
                logger.exception(
                    f"Invalid message data for {bacen_cod_inst}. Sending to DLQ."
                )
                # Reuse original message if available, otherwise try to rebuild
                if message is not None:
                    # Message was successfully built, reuse it for DLQ
                    failed_messages.append((message, e))
                else:
                    # Message build failed, try to rebuild for DLQ
                    try:
                        message = build_kafka_message(institution, change_type)
                        failed_messages.append((message, e))
                    except Exception:
                        # If we can't even build the message, skip it
                        logger.exception(
                            f"Cannot build message for DLQ for {bacen_cod_inst}, skipping"
                        )
                        stats["failed"] += 1

            except Exception as e:
                logger.exception(
                    f"Unexpected error publishing message for {bacen_cod_inst}"
                )
                # Reuse original message if available, otherwise try to rebuild
                if message is not None:
                    # Message was successfully built, reuse it for DLQ
                    failed_messages.append((message, e))
                else:
                    # Message build failed, try to rebuild for DLQ
                    try:
                        message = build_kafka_message(institution, change_type)
                        failed_messages.append((message, e))
                    except Exception:
                        stats["failed"] += 1

        # Flush remaining messages
        producer.flush()

        # Publish failed messages to DLQ
        if failed_messages:
            logger.info(f"Publishing {len(failed_messages)} failed messages to DLQ")
            for message, error in failed_messages:
                if publish_to_dlq(producer, message, error, logger):
                    stats["dlq_published"] += 1
                else:
                    stats["failed"] += 1

        # Final flush for DLQ messages
        producer.flush()

        logger.info(
            f"Publish complete. Published: {stats['published']}, "
            f"Failed: {stats['failed']}, DLQ: {stats['dlq_published']}"
        )

        return stats

    except Exception:
        logger.exception("Error during Kafka publish operation")
        raise
    finally:
        producer.close()
