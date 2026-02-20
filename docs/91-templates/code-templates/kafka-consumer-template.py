"""
Kafka Consumer Template (Package-Based Approach)

This template provides a starting point for implementing Kafka consumers
following the Kafka Consumer Pattern using proper Python packaging.

**IMPORTANT**: This uses the new package-based approach with neotool-common.

Package Structure:
  workflow/<domain>/<feature>/
  ├── pyproject.toml              # Package configuration
  ├── Dockerfile                  # Docker deployment
  ├── src/<package_name>/         # Source code
  │   ├── flows/consumer.py       # Main consumer
  │   └── tasks/                  # Business logic
  └── tests/                      # Tests

Replace:
- <domain> with your domain name (e.g., financial_data, swapi, order)
- <feature> with feature name (e.g., institution_enhancement, people, order_processing)
- <package_name> with snake_case package name (e.g., institution_enhancement, swapi_people)
- <Domain> with PascalCase domain (e.g., FinancialData, Swapi, Order)
- <Feature> with PascalCase feature (e.g., InstitutionEnhancement, People, OrderProcessing)

See: docs/04-patterns/backend-patterns/kafka-consumer-pattern.md
See: docs/05-standards/workflow-standards/kafka-consumer-standard.md
See: docs/05-standards/workflow-standards/batch-workflow-standard.md#45-python-package-structure

Additional Features to Consider:
- Health Check: Implement health check endpoint (see flows/health.py example)
- Idempotency: Ensure processing is idempotent using record_id (see processor example)
- Rate Limiting: Enforce rate limits for external API calls (see cnpja_client.py example)
"""

"""
flows/consumer.py - Main consumer loop

Location: src/<package_name>/flows/consumer.py
"""

import json
import logging
from typing import Any, Dict

from kafka import KafkaConsumer, KafkaProducer

# Import from neotool-common (shared utilities)
from neotool_common.consumer_base import KafkaConsumerRunner

# Import from this package
from <package_name>.tasks.config import Config
from <package_name>.tasks.db_connection import close_pool
from <package_name>.tasks.dlq_publisher import create_kafka_producer, publish_to_dlq
from <package_name>.tasks.message import <Domain><Feature>Message
from <package_name>.tasks.processor import process_message

# Configuration
_config = Config.from_env()
KAFKA_BROKERS = _config.kafka.brokers
INPUT_TOPIC = _config.kafka.input_topic
CONSUMER_GROUP = _config.kafka.consumer_group

logger = logging.getLogger(__name__)


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)


def create_consumer() -> KafkaConsumer:
    """Create Kafka consumer with required settings."""
    return KafkaConsumer(
        INPUT_TOPIC,
        bootstrap_servers=KAFKA_BROKERS,
        group_id=CONSUMER_GROUP,
        auto_offset_reset="earliest",
        enable_auto_commit=False,  # Manual commit after processing
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        key_deserializer=lambda k: k.decode("utf-8") if k else None,
        max_poll_records=1,  # Process one message at a time for rate limiting
        session_timeout_ms=60000,
        heartbeat_interval_ms=10000,
        max_poll_interval_ms=600000,  # Increased to accommodate retry delays
    )


def run_consumer() -> None:
    """
    Run the Kafka consumer using KafkaConsumerRunner.

    This uses the shared neotool-common runner which handles:
    - Retry logic with exponential backoff
    - DLQ publishing
    - Manual commits
    - Graceful shutdown
    - Signal handling
    """
    runner = KafkaConsumerRunner(
        consumer_factory=create_consumer,
        producer_factory=create_kafka_producer,
        retry_config=_config.retry,
        message_factory=<Domain><Feature>Message.from_dict,
        process_message=process_message,
        dlq_publisher=publish_to_dlq,
        # Optional: add rate limiting if needed
        # rate_limit_min_delay_ms=lambda: _config.api.rate_limit_seconds * 1000,
        # Optional: add health check hooks if needed
        # health_server_start=start_health_server,
        # health_server_stop=stop_health_server,
        # running_flag_setter=set_consumer_running,
        cleanup_callback=close_pool,
        message_context=lambda msg: f"event_id={msg.event_id}",
        logger=logger,
    )
    runner.run()


def main():
    """Entry point for the consumer (used by console_scripts in pyproject.toml)."""
    run_consumer()


if __name__ == "__main__":
    main()


"""
tasks/message.py - Message data class
"""

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class <Domain><Feature>Message:
    """
    Message model for <domain>.<feature> Kafka topic.
    
    Matches the schema defined in the Kafka topic.
    """
    
    # Add your message fields here
    schema_version: str
    event_id: str
    occurred_at: str
    source: str
    # ... other fields ...
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "<Domain><Feature>Message":
        """Create message from dictionary."""
        return cls(**data)
    
    def get_record_id(self) -> str:
        """
        Get record ID for idempotency (used by consumer).
        
        Returns:
            Unique identifier for this message
        """
        return self.event_id  # Or appropriate unique identifier
    
    def validate(self) -> None:
        """
        Validate message fields.
        
        Raises:
            ValueError: If required fields are missing or invalid
        """
        if not self.event_id or not self.event_id.strip():
            raise ValueError("event_id is required")
        # Add other validation rules...


"""
tasks/processor.py - Business logic processor

Location: src/<package_name>/tasks/processor.py
"""

import logging
import time
from typing import Any, Dict

from <package_name>.tasks.message import <Domain><Feature>Message

logger = logging.getLogger(__name__)


def process_message(message: <Domain><Feature>Message) -> Dict[str, Any]:
    """
    Process <domain> <feature> message.
    
    Args:
        message: Message instance
        
    Returns:
        Dictionary with processing results:
        - success: bool
        - error: Optional[str]
    """
    # Validate message
    message.validate()
    
    start_time = time.time()
    
    try:
        # Business logic here
        logger.info(f"Processing message: {message.event_id}")
        
        # ... processing logic ...
        
        processing_time = time.time() - start_time
        logger.info(f"Successfully processed: processing_time={processing_time:.2f}s")
        
        return {
            "success": True,
            "error": None,
        }
        
    except ValueError as e:
        # Validation error - non-retryable
        logger.error(f"Validation error: {e}")
        return {
            "success": False,
            "error": str(e),
        }
    except Exception as e:
        # Processing error - may be retryable
        logger.error(f"Processing error: {e}", exc_info=True)
        return {
            "success": False,
            "error": str(e),
        }


"""
tasks/config.py - Configuration management
"""

from dataclasses import dataclass
import os


@dataclass
class DatabaseConfig:
    host: str
    port: int
    database: str
    user: str
    password: str
    pool_min: int = 2
    pool_max: int = 10


@dataclass
class KafkaConfig:
    brokers: str
    input_topic: str
    consumer_group: str


@dataclass
class RetryConfig:
    max_retries: int = 3
    initial_retry_delay_ms: int = 1000
    max_retry_delay_ms: int = 30000
    retry_backoff_multiplier: float = 2.0
    retry_jitter: bool = True


@dataclass
class Config:
    database: DatabaseConfig
    kafka: KafkaConfig
    retry: RetryConfig
    
    @classmethod
    def from_env(cls) -> "Config":
        """Load configuration from environment variables."""
        return cls(
            database=DatabaseConfig(
                host=os.getenv("POSTGRES_HOST", "localhost"),
                port=int(os.getenv("POSTGRES_PORT", "5432")),
                database=os.getenv("POSTGRES_DB", "neotool_db"),
                user=os.getenv("POSTGRES_USER", "neotool"),
                password=os.getenv("POSTGRES_PASSWORD", ""),
                pool_min=int(os.getenv("POSTGRES_POOL_MIN", "2")),
                pool_max=int(os.getenv("POSTGRES_POOL_MAX", "10")),
            ),
            kafka=KafkaConfig(
                brokers=os.getenv("KAFKA_BROKERS", "localhost:9092"),
                input_topic=os.getenv("KAFKA_INPUT_TOPIC", "<domain>.<feature>.v1"),
                consumer_group=os.getenv("KAFKA_CONSUMER_GROUP", "<domain>-<feature>-consumer-group"),
            ),
            retry=RetryConfig(
                max_retries=int(os.getenv("RETRY_MAX_RETRIES", "3")),
                initial_retry_delay_ms=int(os.getenv("RETRY_INITIAL_DELAY_MS", "1000")),
                max_retry_delay_ms=int(os.getenv("RETRY_MAX_DELAY_MS", "30000")),
                retry_backoff_multiplier=float(os.getenv("RETRY_BACKOFF_MULTIPLIER", "2.0")),
                retry_jitter=os.getenv("RETRY_JITTER", "true").lower() == "true",
            ),
        )


"""
pyproject.toml - Package configuration

Place this file at: workflow/<domain>/<feature>/pyproject.toml

This defines the package, its dependencies, and entry points.
"""

"""
[build-system]
requires = ["setuptools>=68.0", "wheel"]
build-backend = "setuptools.build_meta"

[project]
name = "<feature-name>"
version = "0.1.0"
description = "<Feature> Kafka consumer for <domain>"
readme = "README.md"
requires-python = ">=3.14"
dependencies = [
    "neotool-common",  # REQUIRED: Shared Neotool utilities
    "kafka-python>=2.0.0",
    "psycopg2-binary>=2.9.0",
    "requests>=2.31.0",
    "python-dotenv>=1.0.0",
    # Add other dependencies as needed
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0.0",
    "pytest-mock>=3.12.0",
    "pytest-cov>=4.1.0",
    "black>=24.0.0",
    "ruff>=0.1.0",
    "mypy>=1.8.0",
]

[project.scripts]
# Entry point that maps to main() function in consumer.py
<feature-name>-consumer = "<package_name>.flows.consumer:main"

[tool.setuptools.packages.find]
where = ["src"]

[tool.pytest.ini_options]
testpaths = ["tests"]

[tool.black]
line-length = 100
target-version = ["py314"]

[tool.ruff]
line-length = 100
target-version = "py314"

[tool.mypy]
python_version = "3.14"
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = false
"""


"""
Dockerfile - Docker deployment configuration

Place this file at: workflow/<domain>/<feature>/Dockerfile

Build from workflow/ directory:
  docker build -f <domain>/<feature>/Dockerfile -t <consumer-name> .

See: docs/04-patterns/backend-patterns/kafka-consumer-pattern.md#docker-deployment
"""

"""
FROM python:3.14-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

# Install system dependencies
RUN apk add --no-cache gcc musl-dev libpq-dev

# Install neotool-common from local path
COPY --chown=consumer:consumer ../../common /tmp/neotool-common/
RUN pip install --no-cache-dir /tmp/neotool-common && \
    rm -rf /tmp/neotool-common

# Copy application code
COPY --chown=consumer:consumer . /app/

# Install the application
RUN pip install --no-cache-dir /app

# Switch to non-root user
USER consumer

# Expose health check port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')"

# Entry point (uses script defined in pyproject.toml)
ENTRYPOINT ["<feature-name>-consumer"]
"""

