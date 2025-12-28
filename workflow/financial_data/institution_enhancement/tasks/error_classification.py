"""
Error Classification

Classifies errors as retryable or non-retryable according to Kafka Consumer Retry Standard.
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

    Args:
        error: Exception instance, error string, or error dict

    Returns:
        True if error is retryable, False otherwise
    """
    # Handle string errors (from result dict)
    if isinstance(error, str):
        # Check for common retryable error patterns in strings
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
        # Validation errors are typically non-retryable
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
        # Default for string errors: assume retryable (conservative approach)
        return True

    # Handle Exception instances
    if isinstance(error, Exception):
        error_type = type(error).__name__

        # Network/connection errors - retryable
        if isinstance(error, (ConnectionError, TimeoutError)):
            logger.debug(f"Error classified as retryable (network): {error_type}")
            return True

        # HTTP errors (if requests is available)
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

        # HTTP errors
        if requests and isinstance(error, requests.exceptions.HTTPError):
            if hasattr(error, "response") and error.response is not None:
                status_code = error.response.status_code
                # Retryable HTTP status codes
                if status_code in [429, 502, 503, 504]:
                    logger.debug(f"Error classified as retryable (HTTP {status_code}): {error_type}")
                    return True
                # Non-retryable HTTP status codes
                if status_code in [400, 401, 403, 404]:
                    logger.debug(f"Error classified as non-retryable (HTTP {status_code}): {error_type}")
                    return False
            # Default for HTTPError without response: retryable
            return True

        # Database transient errors - retryable (if psycopg2 is available)
        if psycopg2:
            if isinstance(error, psycopg2.OperationalError):
                logger.debug(f"Error classified as retryable (database operational): {error_type}")
                return True

            # Database integrity errors - non-retryable (constraint violations)
            if isinstance(error, psycopg2.IntegrityError):
                logger.debug(f"Error classified as non-retryable (database integrity): {error_type}")
                return False

            # Database interface errors - may be retryable (connection issues)
            if isinstance(error, psycopg2.InterfaceError):
                logger.debug(f"Error classified as retryable (database interface): {error_type}")
                return True

        # Validation errors - non-retryable
        if isinstance(error, ValueError):
            logger.debug(f"Error classified as non-retryable (validation): {error_type}")
            return False

        # KeyError, AttributeError - typically non-retryable (data issues)
        if isinstance(error, (KeyError, AttributeError, TypeError)):
            logger.debug(f"Error classified as non-retryable (data error): {error_type}")
            return False

        # Kafka errors - check specific types
        try:
            from kafka.errors import KafkaError

            if isinstance(error, KafkaError):
                # Most Kafka errors are transient and retryable
                logger.debug(f"Error classified as retryable (Kafka): {error_type}")
                return True
        except ImportError:
            pass

        # Default: assume retryable for unknown exceptions (conservative approach)
        logger.warning(
            f"Unknown error type {error_type}, assuming retryable: {error}. "
            f"Consider adding explicit classification."
        )
        return True

    # For other types, default to retryable (conservative)
    logger.warning(f"Unknown error type {type(error)}, assuming retryable: {error}")
    return True

