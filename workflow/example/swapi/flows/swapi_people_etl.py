"""
SWAPI People ETL Flow

Fetches people data from SWAPI, transforms it, and publishes to Kafka.
"""
import os
import uuid
from datetime import datetime, timezone
from typing import List, Dict, Any
import json

from prefect import flow, task, get_run_logger
from prefect.artifacts import create_markdown_artifact
from kafka import KafkaProducer
from kafka.errors import KafkaError
import requests
from requests.exceptions import RequestException


# Configuration
SWAPI_BASE_URL = os.getenv("SWAPI_BASE_URL", "https://swapi.dev/api")
KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092").split(",")
KAFKA_TOPIC = "swapi.people.v1"
# Compression type: 'gzip' (default, no extra libs), 'snappy' (requires python-snappy), 'lz4', 'zstd', or 'none'
KAFKA_COMPRESSION_TYPE = os.getenv("KAFKA_COMPRESSION_TYPE", "gzip")


@task(
    name="extract_people",
    retries=3,
    retry_delay_seconds=60,
    timeout_seconds=120,
    log_prints=True,
    tags=["extract", "swapi"]
)
def extract_people() -> List[Dict[str, Any]]:
    """
    Fetch all people from SWAPI with pagination.
    
    Returns:
        List of raw people data from SWAPI
    """
    logger = get_run_logger()
    all_people = []
    url = f"{SWAPI_BASE_URL}/people/"
    
    logger.info(f"Starting extraction from {url}")
    
    while url:
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            data = response.json()
            
            people = data.get("results", [])
            all_people.extend(people)
            logger.info(f"Fetched {len(people)} people from {url}. Total: {len(all_people)}")
            
            url = data.get("next")  # Pagination
            
        except RequestException as e:
            logger.error(f"Error fetching from SWAPI: {e}")
            raise
    
    logger.info(f"Extraction complete. Total people fetched: {len(all_people)}")
    return all_people


@task(
    name="transform_people",
    retries=2,
    retry_delay_seconds=30,
    timeout_seconds=60,
    log_prints=True,
    tags=["transform", "swapi"]
)
def transform_people(raw_people: List[Dict[str, Any]], batch_id: str) -> List[Dict[str, Any]]:
    """
    Transform SWAPI format to internal schema.
    
    Args:
        raw_people: Raw people data from SWAPI
        batch_id: Batch identifier for this run
        
    Returns:
        List of transformed messages ready for Kafka
    """
    logger = get_run_logger()
    transformed = []
    
    for person in raw_people:
        # Extract record_id from URL (e.g., "https://swapi.dev/api/people/1/" -> "1")
        url = person.get("url", "")
        record_id = url.split("/")[-2] if url else str(uuid.uuid4())
        
        message = {
            "batch_id": batch_id,
            "record_id": record_id,
            "ingested_at": datetime.now(timezone.utc).isoformat(),
            "payload": {
                "name": person.get("name", ""),
                "height": person.get("height", ""),
                "mass": person.get("mass", ""),
                "hair_color": person.get("hair_color", ""),
                "skin_color": person.get("skin_color", ""),
                "eye_color": person.get("eye_color", ""),
                "birth_year": person.get("birth_year", ""),
                "gender": person.get("gender", ""),
                "homeworld_url": person.get("homeworld", ""),
                "films": person.get("films", []),
                "species": person.get("species", []),
                "vehicles": person.get("vehicles", []),
                "starships": person.get("starships", [])
            }
        }
        transformed.append(message)
    
    logger.info(f"Transformed {len(transformed)} people records")
    return transformed


@task(
    name="publish_to_kafka",
    retries=5,
    retry_delay_seconds=30,
    timeout_seconds=300,
    log_prints=True,
    tags=["publish", "kafka", "swapi"]
)
def publish_to_kafka(messages: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Publish messages to Kafka topic.
    
    Args:
        messages: List of transformed messages
        
    Returns:
        Statistics about the publish operation
    """
    logger = get_run_logger()
    
    # Log compression type being used
    logger.info(f"Using Kafka compression type: {KAFKA_COMPRESSION_TYPE}")
    
    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BROKERS,
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        key_serializer=lambda k: k.encode('utf-8') if k else None,
        acks='all',
        enable_idempotence=True,
        compression_type=KAFKA_COMPRESSION_TYPE,
        linger_ms=10,
        retries=5
    )
    
    published = 0
    failed = 0
    
    try:
        for message in messages:
            record_id = message.get("record_id", "")
            try:
                future = producer.send(
                    KAFKA_TOPIC,
                    key=record_id,
                    value=message
                )
                # Wait for confirmation
                record_metadata = future.get(timeout=10)
                published += 1
                if published % 10 == 0:
                    logger.info(f"Published {published}/{len(messages)} messages")
            except KafkaError as e:
                logger.error(f"Failed to publish message {record_id}: {e}")
                failed += 1
        
        # Flush remaining messages
        producer.flush()
        
        stats = {
            "total": len(messages),
            "published": published,
            "failed": failed,
            "topic": KAFKA_TOPIC
        }
        
        logger.info(f"Publish complete. Published: {published}, Failed: {failed}")
        return stats
        
    except Exception as e:
        logger.error(f"Error during Kafka publish: {e}")
        raise
    finally:
        producer.close()


@task(
    name="handle_fallback",
    log_prints=True,
    tags=["fallback", "swapi"]
)
def handle_fallback(error: Exception, batch_id: str, context: Dict[str, Any]) -> None:
    """
    Fallback handler for failed runs.
    
    Args:
        error: The exception that caused the failure
        batch_id: Batch identifier
        context: Additional context about the failure
    """
    logger = get_run_logger()
    
    logger.error(f"Flow failed for batch {batch_id}: {error}")
    logger.error(f"Context: {context}")
    
    # In a real implementation, this would:
    # 1. Persist payload to durable storage (S3, GCS, disk)
    # 2. Notify responsible channel (Slack/PagerDuty/email)
    # 3. Optionally trigger compensating run
    
    # For now, we just log the failure
    logger.warning("Fallback handler executed. In production, this would persist to storage and notify operators.")


@flow(
    name="swapi-people-etl",
    log_prints=True,
    retries=0  # Flow-level retries disabled, task-level retries handle it
)
def swapi_people_etl_flow() -> Dict[str, Any]:
    """
    Main ETL flow for SWAPI people data.
    
    Returns:
        Flow execution statistics
    """
    logger = get_run_logger()
    batch_id = str(uuid.uuid4())
    
    logger.info(f"Starting SWAPI People ETL flow. Batch ID: {batch_id}")
    
    try:
        # Extract
        raw_people = extract_people()
        
        # Transform
        transformed_messages = transform_people(raw_people, batch_id)
        
        # Publish
        publish_stats = publish_to_kafka(transformed_messages)
        
        # Create summary artifact
        summary = f"""
# SWAPI People ETL Summary

**Batch ID**: `{batch_id}`

**Execution Time**: {datetime.now(timezone.utc).isoformat()}

## Statistics

- **People Fetched**: {len(raw_people)}
- **Messages Transformed**: {len(transformed_messages)}
- **Messages Published**: {publish_stats['published']}
- **Messages Failed**: {publish_stats['failed']}
- **Kafka Topic**: `{publish_stats['topic']}`

## Status

✅ Flow completed successfully
"""
        create_markdown_artifact(
            key=f"swapi-etl-summary-{batch_id}",
            markdown=summary
        )
        
        result = {
            "batch_id": batch_id,
            "status": "success",
            "people_fetched": len(raw_people),
            "messages_published": publish_stats["published"],
            "messages_failed": publish_stats["failed"]
        }
        
        logger.info(f"Flow completed successfully. Batch ID: {batch_id}")
        return result
        
    except Exception as e:
        logger.error(f"Flow failed: {e}")
        handle_fallback(e, batch_id, {"stage": "unknown"})
        raise


if __name__ == "__main__":
    swapi_people_etl_flow()
