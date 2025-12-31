"""
Shared retry utilities for Kafka consumers.
"""

import random
from typing import Any


def calculate_backoff(
    retry_count: int,
    config: Any,
    rate_limit_min_delay_ms: int = 0,
) -> int:
    """
    Calculate exponential backoff delay with optional jitter and rate-limit floor.
    """
    base_delay = min(
        config.initial_retry_delay_ms * (config.retry_backoff_multiplier ** retry_count),
        config.max_retry_delay_ms,
    )

    if config.retry_jitter:
        jitter = random.uniform(0, base_delay * 0.1)
        base_delay = base_delay + jitter

    if rate_limit_min_delay_ms > 0:
        base_delay = max(base_delay, rate_limit_min_delay_ms)

    return int(base_delay)
