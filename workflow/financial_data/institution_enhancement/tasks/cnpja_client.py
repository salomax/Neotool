"""
CNPJá API Client

HTTP client for querying CNPJá API with rate limiting enforcement.
"""

import logging
import time
from typing import Any, Dict, Optional

import requests
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
    before_sleep_log,
    after_log,
)

from tasks.config import Config

logger = logging.getLogger(__name__)

# Configuration (loaded from config module)
_config = Config.from_env()
CNPJA_BASE_URL = _config.cnpja.base_url
API_TIMEOUT = _config.cnpja.timeout
CNPJA_RATE_LIMIT_SECONDS = _config.cnpja.rate_limit_seconds

# Rate limiting state (global across all instances)
_last_cnpja_query_time: Optional[float] = None


def enforce_rate_limit() -> None:
    """Enforce rate limiting for CNPJá API calls."""
    global _last_cnpja_query_time

    if _last_cnpja_query_time is not None:
        elapsed = time.time() - _last_cnpja_query_time
        if elapsed < CNPJA_RATE_LIMIT_SECONDS:
            wait_time = CNPJA_RATE_LIMIT_SECONDS - elapsed
            logger.debug(f"Rate limiting: waiting {wait_time:.2f}s before next API call")
            time.sleep(wait_time)

    _last_cnpja_query_time = time.time()


@retry(
    retry=retry_if_exception_type((requests.exceptions.Timeout, requests.exceptions.ConnectionError)),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=30),
    before_sleep=before_sleep_log(logger, logging.WARNING),
    after=after_log(logger, logging.INFO),
    reraise=False,  # Don't re-raise, return error dict instead
)
def query_cnpja(cnpj: str) -> Dict[str, Any]:
    """
    Query CNPJá API for company information.

    Args:
        cnpj: 14-digit CNPJ string

    Returns:
        Dictionary with:
        - found: Optional[bool] (True if found, False if 404, None on error)
        - error: Optional[str] (error message if found is None)
        - data: Optional[Dict] (API response data if found is True)
    """
    enforce_rate_limit()

    url = f"{CNPJA_BASE_URL}/{cnpj}"
    headers = {
        "User-Agent": "Invistus Institution Enhancement Consumer",
        "Accept": "application/json",
    }

    logger.info(f"Querying CNPJá API: cnpj={cnpj}, url={url}")

    try:
        response = requests.get(url, headers=headers, timeout=API_TIMEOUT)

        if response.status_code == 200:
            logger.info(f"CNPJá API success: cnpj={cnpj}, found=True")
            return {"found": True, "error": None, "data": response.json()}
        elif response.status_code == 404:
            logger.info(f"CNPJá API not found: cnpj={cnpj}")
            return {"found": False, "error": None, "data": None}
        elif response.status_code == 429:
            # Rate limit error - will be retried by tenacity
            error_msg = f"HTTP 429: Rate limit exceeded"
            logger.warning(f"CNPJá API rate limit: cnpj={cnpj}")
            raise requests.exceptions.HTTPError(error_msg, response=response)
        else:
            # Log full error response (with size limit)
            error_text = response.text
            max_error_size = 10240  # 10KB limit
            # For error message in return dict, limit to 200 chars for backward compatibility
            error_preview = error_text[:200] if len(error_text) > 200 else error_text
            # For logging, use full error (up to max_error_size)
            error_text_for_log = error_text
            if len(error_text_for_log) > max_error_size:
                error_text_for_log = error_text_for_log[:max_error_size] + f"... (truncated, total size: {len(response.text)} bytes)"

            error_msg = f"HTTP {response.status_code}: {error_preview}"
            logger.error(
                f"CNPJá API error: cnpj={cnpj}, status={response.status_code}, "
                f"error_preview={error_preview}"
            )
            # Log full error to debug level
            logger.debug(f"CNPJá API full error response: {error_text_for_log}")

            return {
                "found": None,
                "error": error_msg,
                "data": None,
            }
    except requests.exceptions.Timeout as e:
        logger.warning(f"CNPJá API timeout: cnpj={cnpj}, error={e}")
        return {"found": None, "error": f"Request timeout: {str(e)}", "data": None}
    except requests.exceptions.ConnectionError as e:
        logger.warning(f"CNPJá API connection error: cnpj={cnpj}, error={e}")
        return {"found": None, "error": f"Connection error: {str(e)}", "data": None}
    except requests.exceptions.RequestException as e:
        logger.error(f"CNPJá API request error: cnpj={cnpj}, error={e}", exc_info=True)
        return {"found": None, "error": str(e), "data": None}

