"""
CNPJá API client with rate limiting.

Provides functions to query the CNPJá API for company information,
with built-in rate limiting to respect API constraints.
"""

import os
import time
from typing import Any, Dict, Optional

import requests

# Rate limit constant (15 seconds minimum between requests)
CNPJA_RATE_LIMIT_SECONDS = 15

# Global state for rate limiting
_last_cnpja_query_time: Optional[float] = None


def enforce_rate_limit() -> None:
    """
    Enforce rate limiting for CNPJá API calls.
    
    Ensures at least CNPJA_RATE_LIMIT_SECONDS seconds have passed
    since the last API call. If not, waits for the remaining time.
    """
    global _last_cnpja_query_time
    
    if _last_cnpja_query_time is not None:
        elapsed = time.time() - _last_cnpja_query_time
        if elapsed < CNPJA_RATE_LIMIT_SECONDS:
            wait_time = CNPJA_RATE_LIMIT_SECONDS - elapsed
            time.sleep(wait_time)
    
    _last_cnpja_query_time = time.time()


def query_cnpja(cnpj: str) -> Dict[str, Any]:
    """
    Query CNPJá API for company information.
    
    Args:
        cnpj: 14-digit CNPJ string
        
    Returns:
        Dictionary with:
        - found: bool|None - True if company found, False if not found (404), None if error
        - error: str|None - Error message if request failed, None otherwise
        - data: dict|None - Company data if found, None otherwise
    """
    enforce_rate_limit()
    
    # Get configuration from environment variables
    base_url = os.getenv("CNPJA_BASE_URL", "https://open.cnpja.com/office")
    timeout = int(os.getenv("CNPJA_API_TIMEOUT", "30"))
    
    url = f"{base_url}/{cnpj}"
    headers = {
        "User-Agent": "Invistus Institution Enhancement Consumer",
        "Accept": "application/json",
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=timeout)
        
        if response.status_code == 200:
            return {"found": True, "error": None, "data": response.json()}
        elif response.status_code == 404:
            return {"found": False, "error": None, "data": None}
        else:
            return {
                "found": None,
                "error": f"HTTP {response.status_code}: {response.text[:200]}",
                "data": None,
            }
    except requests.exceptions.Timeout as e:
        return {"found": None, "error": f"Request timeout: {str(e)}", "data": None}
    except requests.exceptions.ConnectionError as e:
        return {"found": None, "error": f"Connection error: {str(e)}", "data": None}
    except requests.exceptions.RequestException as e:
        return {"found": None, "error": str(e), "data": None}
