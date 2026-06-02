from __future__ import annotations

import time
from typing import Optional

import httpx


class DownloadError(RuntimeError):
    pass


class SignedUrlExpiredError(DownloadError):
    pass


def download_pdf(download_url: str, timeout_seconds: int, max_retries: int = 3) -> bytes:
    last_error: Optional[Exception] = None

    with httpx.Client(timeout=timeout_seconds) as client:
        for attempt in range(max_retries):
            try:
                response = client.get(download_url)
                status = response.status_code
                if status in (403, 410):
                    raise SignedUrlExpiredError(f"signed_url_expired status={status}")
                if 200 <= status < 300:
                    return response.content
                if 500 <= status < 600 or status == 429:
                    raise DownloadError(f"retryable_http_error status={status}")
                raise DownloadError(f"http_error status={status}")
            except SignedUrlExpiredError:
                raise
            except (DownloadError, httpx.RequestError) as exc:
                last_error = exc
                if attempt < max_retries - 1:
                    time.sleep(0.5 * (2**attempt))
                continue

    if isinstance(last_error, Exception):
        raise DownloadError(str(last_error)) from last_error
    raise DownloadError("download_failed")
