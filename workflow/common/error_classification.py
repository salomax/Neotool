"""
Shared error classification helpers for Kafka consumers.

This module implements the Kafka Consumer Retry Standard classification logic.
"""

import logging
from typing import Any, Union

try:
    import psycopg2
except ImportError:
    psycopg2 = None

try:
    import requests
except ImportError:
    requests = None

logger = logging.getLogger(__name__)


def is_retryable_error(error: Union[Exception, str, Any]) -> bool:
    """
    Classify error as retryable or non-retryable.

    According to Kafka Consumer Retry Standard:
    - Retryable: Network errors, service unavailable, rate limiting, database transient errors
    - Non-retryable: Validation errors, authentication/authorization, business logic errors
    """
    if isinstance(error, str):
        error_lower = error.lower()
        if any(
            pattern in error_lower
            for pattern in [
                "timeout",
                "connection",
                "network",
                "unavailable",
                "rate limit",
                "429",
                "502",
                "503",
                "504",
            ]
        ):
            return True
        if any(
            pattern in error_lower
            for pattern in [
                "invalid",
                "validation",
                "missing",
                "required",
                "format",
                "not found",
                "404",
            ]
        ):
            return False
        return True

    if isinstance(error, Exception):
        error_type = type(error).__name__

        if isinstance(error, (ConnectionError, TimeoutError)):
            logger.debug(f"Error classified as retryable (network): {error_type}")
            return True

        if requests and isinstance(
            error,
            (
                requests.exceptions.Timeout,
                requests.exceptions.ConnectionError,
                requests.exceptions.RequestException,
            ),
        ):
            logger.debug(f"Error classified as retryable (network): {error_type}")
            return True

        if requests and isinstance(error, requests.exceptions.HTTPError):
            if hasattr(error, "response") and error.response is not None:
                status_code = error.response.status_code
                if status_code in [429, 502, 503, 504]:
                    logger.debug(
                        f"Error classified as retryable (HTTP {status_code}): {error_type}"
                    )
                    return True
                if status_code in [400, 401, 403, 404]:
                    logger.debug(
                        f"Error classified as non-retryable (HTTP {status_code}): {error_type}"
                    )
                    return False
            return True

        if psycopg2:
            if isinstance(error, psycopg2.OperationalError):
                logger.debug(
                    f"Error classified as retryable (database operational): {error_type}"
                )
                return True

            if isinstance(error, psycopg2.IntegrityError):
                logger.debug(
                    f"Error classified as non-retryable (database integrity): {error_type}"
                )
                return False

            if isinstance(error, psycopg2.InterfaceError):
                logger.debug(
                    f"Error classified as retryable (database interface): {error_type}"
                )
                return True

        if isinstance(error, ValueError):
            logger.debug(f"Error classified as non-retryable (validation): {error_type}")
            return False

        if isinstance(error, (KeyError, AttributeError, TypeError)):
            logger.debug(f"Error classified as non-retryable (data error): {error_type}")
            return False

        try:
            from kafka.errors import KafkaError

            if isinstance(error, KafkaError):
                logger.debug(f"Error classified as retryable (Kafka): {error_type}")
                return True
        except ImportError:
            pass

        logger.warning(
            f"Unknown error type {error_type}, assuming retryable: {error}. "
            f"Consider adding explicit classification."
        )
        return True

    logger.warning(f"Unknown error type {type(error)}, assuming retryable: {error}")
    return True
