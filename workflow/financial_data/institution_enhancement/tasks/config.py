"""
Configuration Management

Centralized configuration for institution enhancement consumer.
"""

import os
from dataclasses import dataclass
from typing import List


@dataclass
class DatabaseConfig:
    """Database configuration."""

    host: str
    port: int
    database: str
    user: str
    password: str
    pool_min: int
    pool_max: int

    @classmethod
    def from_env(cls) -> "DatabaseConfig":
        """
        Create database configuration from environment variables.

        Returns:
            DatabaseConfig instance

        Raises:
            ValueError: If required environment variables are missing
        """
        host = os.getenv("POSTGRES_HOST", "localhost")
        port = int(os.getenv("POSTGRES_PORT", "6432"))
        database = os.getenv("POSTGRES_DB", "neotool_db")
        user = os.getenv("POSTGRES_USER", "neotool")
        password = os.getenv("POSTGRES_PASSWORD", "neotool")
        pool_min = int(os.getenv("POSTGRES_POOL_MIN", "2"))
        pool_max = int(os.getenv("POSTGRES_POOL_MAX", "10"))

        return cls(
            host=host,
            port=port,
            database=database,
            user=user,
            password=password,
            pool_min=pool_min,
            pool_max=pool_max,
        )


@dataclass
class KafkaConfig:
    """Kafka configuration."""

    brokers: List[str]
    input_topic: str
    dlq_topic: str
    consumer_group: str

    @classmethod
    def from_env(cls) -> "KafkaConfig":
        """
        Create Kafka configuration from environment variables.

        Returns:
            KafkaConfig instance
        """
        brokers_str = os.getenv("KAFKA_BROKERS", "localhost:9092")
        brokers = [b.strip() for b in brokers_str.split(",")]
        input_topic = os.getenv("KAFKA_INPUT_TOPIC", "financial-data.institution.upserted")
        dlq_topic = os.getenv(
            "KAFKA_DLQ_TOPIC", "financial-data.institution.upserted.enhancement.dlq"
        )
        consumer_group = os.getenv(
            "KAFKA_CONSUMER_GROUP", "institution-enhancement-consumer-group"
        )

        return cls(
            brokers=brokers,
            input_topic=input_topic,
            dlq_topic=dlq_topic,
            consumer_group=consumer_group,
        )


@dataclass
class CNPJAConfig:
    """CNPJá API configuration."""

    base_url: str
    timeout: int
    rate_limit_seconds: int

    @classmethod
    def from_env(cls) -> "CNPJAConfig":
        """
        Create CNPJá API configuration from environment variables.

        Returns:
            CNPJAConfig instance
        """
        base_url = os.getenv("CNPJA_BASE_URL", "https://open.cnpja.com/office")
        timeout = int(os.getenv("CNPJA_API_TIMEOUT", "30"))
        rate_limit_seconds = int(os.getenv("CNPJA_RATE_LIMIT_SECONDS", "15"))

        return cls(
            base_url=base_url,
            timeout=timeout,
            rate_limit_seconds=rate_limit_seconds,
        )


@dataclass
class HealthCheckConfig:
    """Health check server configuration."""

    port: int
    enabled: bool

    @classmethod
    def from_env(cls) -> "HealthCheckConfig":
        """
        Create health check configuration from environment variables.

        Returns:
            HealthCheckConfig instance
        """
        port = int(os.getenv("HEALTH_CHECK_PORT", "8080"))
        enabled = os.getenv("HEALTH_CHECK_ENABLED", "true").lower() in ("true", "1", "yes")

        return cls(port=port, enabled=enabled)


@dataclass
class RetryConfig:
    """Retry configuration for message processing."""

    max_retries: int
    initial_retry_delay_ms: int
    max_retry_delay_ms: int
    retry_backoff_multiplier: float
    retry_jitter: bool

    @classmethod
    def from_env(cls) -> "RetryConfig":
        """
        Create retry configuration from environment variables.

        Returns:
            RetryConfig instance
        """
        max_retries = int(os.getenv("MAX_RETRIES", "3"))
        initial_retry_delay_ms = int(os.getenv("INITIAL_RETRY_DELAY_MS", "1000"))
        max_retry_delay_ms = int(os.getenv("MAX_RETRY_DELAY_MS", "30000"))
        retry_backoff_multiplier = float(os.getenv("RETRY_BACKOFF_MULTIPLIER", "2.0"))
        retry_jitter = os.getenv("RETRY_JITTER", "true").lower() in ("true", "1", "yes")

        return cls(
            max_retries=max_retries,
            initial_retry_delay_ms=initial_retry_delay_ms,
            max_retry_delay_ms=max_retry_delay_ms,
            retry_backoff_multiplier=retry_backoff_multiplier,
            retry_jitter=retry_jitter,
        )


@dataclass
class Config:
    """Main configuration class."""

    database: DatabaseConfig
    kafka: KafkaConfig
    cnpja: CNPJAConfig
    health_check: HealthCheckConfig
    retry: RetryConfig

    @classmethod
    def from_env(cls) -> "Config":
        """
        Create configuration from environment variables.

        Returns:
            Config instance
        """
        return cls(
            database=DatabaseConfig.from_env(),
            kafka=KafkaConfig.from_env(),
            cnpja=CNPJAConfig.from_env(),
            health_check=HealthCheckConfig.from_env(),
            retry=RetryConfig.from_env(),
        )


