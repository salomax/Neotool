"""
Retry Utilities

Exponential backoff calculation and retry utilities.
"""

import random
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from tasks.config import RetryConfig


def calculate_backoff(retry_count: int, config: "RetryConfig", rate_limit_min_delay_ms: int = 0) -> int:
    """
    Calculate exponential backoff delay with jitter.

    Formula: delay = min(initial_delay_ms * (multiplier ^ retry_count), max_retry_delay_ms)
    With jitter: delay = base_delay + random(0, base_delay * 0.1)

    Args:
        retry_count: Current retry attempt number (0-indexed)
        config: RetryConfig instance
        rate_limit_min_delay_ms: Minimum delay to respect rate limits (default: 0)

    Returns:
        Delay in milliseconds
    """
    base_delay = min(
        config.initial_retry_delay_ms * (config.retry_backoff_multiplier ** retry_count),
        config.max_retry_delay_ms,
    )

    if config.retry_jitter:
        jitter = random.uniform(0, base_delay * 0.1)  # 10% jitter
        base_delay = base_delay + jitter

    # Ensure we respect rate limit minimum delay
    if rate_limit_min_delay_ms > 0:
        base_delay = max(base_delay, rate_limit_min_delay_ms)

    return int(base_delay)

