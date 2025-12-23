"""
Fallback Handling Task

Handles fallback operations when task retries are exhausted:
- Persists failed payloads to durable storage
- Sends notifications (mocked for now, extensible to Slack/PagerDuty/email)
- Logs comprehensive error details
"""

from datetime import UTC, datetime
import json
import os
from pathlib import Path
from typing import Any
import uuid

from prefect import get_run_logger, task

# Configuration constants (defaults, for backward compatibility)
# Note: Runtime config functions below read env vars at call time to support per-run overrides
FALLBACK_STORAGE_TYPE = os.getenv("FALLBACK_STORAGE_TYPE", "local")
FALLBACK_STORAGE_PATH = os.getenv("FALLBACK_STORAGE_PATH", "./fallback_storage")
NOTIFICATION_TYPE = os.getenv("NOTIFICATION_TYPE", "mock")


def get_storage_type() -> str:
    """Read FALLBACK_STORAGE_TYPE from environment at runtime."""
    return os.getenv("FALLBACK_STORAGE_TYPE", "local")


def get_storage_path() -> str:
    """Read FALLBACK_STORAGE_PATH from environment at runtime."""
    return os.getenv("FALLBACK_STORAGE_PATH", "./fallback_storage")


def get_notification_type() -> str:
    """Read NOTIFICATION_TYPE from environment at runtime."""
    return os.getenv("NOTIFICATION_TYPE", "mock")


def build_fallback_metadata(
    task_name: str,
    error: Exception | str,
    context: dict[str, Any] | None = None,
    storage_path: str | None = None,
    notification_sent: bool = False,
    retry_info: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """
    Build comprehensive metadata for fallback payload.

    Args:
        task_name: Name of the task that failed
        error: Exception object or error message string
        context: Additional context (batch_id, ano_mes, etc.)
        storage_path: Path where payload was persisted
        notification_sent: Boolean indicating if notification was sent
        retry_info: Retry attempt information if available

    Returns:
        Dictionary with fallback metadata
    """
    # Extract error information
    if isinstance(error, Exception):
        error_type = type(error).__name__
        error_message = str(error)
    else:
        error_type = "UnknownError"
        error_message = str(error)

    metadata = {
        "task_name": task_name,
        "error_type": error_type,
        "error_message": error_message,
        "failed_at": datetime.now(UTC).isoformat(),
        "notification_sent": notification_sent,
    }

    if context:
        metadata["context"] = context

    if storage_path:
        metadata["storage_path"] = storage_path

    if retry_info:
        metadata["retry_info"] = retry_info

    return metadata


def persist_to_storage(
    payload: dict[str, Any],
    task_name: str,
    error: Exception | str,
    context: dict[str, Any] | None = None,
    logger=None,
) -> str | None:
    """
    Persist failed payload to durable storage.

    Currently supports local disk storage. Extensible for S3/GCS in the future.

    Args:
        payload: The failed payload/data to persist
        task_name: Name of the task that failed
        error: Exception object or error message string
        context: Additional context (batch_id, ano_mes, etc.)
        logger: Logger instance for logging (optional)

    Returns:
        Path where payload was persisted, or None if persistence failed
    """
    storage_type = get_storage_type()
    if storage_type != "local" and logger:
        logger.warning(
            f"Storage type '{storage_type}' not yet implemented, "
            "falling back to local storage"
        )
        # For now, fall back to local storage
        # Future: implement S3/GCS storage backends

    try:
        # Build metadata
        metadata = build_fallback_metadata(
            task_name=task_name,
            error=error,
            context=context,
        )

        # Create directory structure: {storage_path}/{task_name}/{YYYY-MM-DD}/
        storage_base = Path(get_storage_path())
        today = datetime.now(UTC).strftime("%Y-%m-%d")
        task_dir = storage_base / task_name / today
        task_dir.mkdir(parents=True, exist_ok=True)

        # Generate filename: {timestamp}_{task_name}_{uuid}.json
        timestamp = datetime.now(UTC).strftime("%Y%m%d_%H%M%S_%f")
        file_uuid = str(uuid.uuid4())[:8]  # Short UUID for filename
        filename = f"{timestamp}_{task_name}_{file_uuid}.json"
        file_path = task_dir / filename

        # Build complete payload with metadata
        fallback_payload = {
            "original_payload": payload,
            "metadata": metadata,
        }

        # Update metadata with storage path
        metadata["storage_path"] = str(file_path)
        fallback_payload["metadata"] = metadata

        # Log original payload representation before serialization
        if logger:
            logger.debug(
                f"Serializing payload: type={type(payload)}, "
                f"repr={repr(payload)[:200]}"
            )

        # Write JSON file (use default=str to handle non-serializable objects)
        with Path(file_path).open("w", encoding="utf-8") as f:
            json.dump(fallback_payload, f, indent=2, ensure_ascii=False, default=str)

        if logger:
            logger.info(
                f"Persisted fallback payload to storage: path={file_path}, "
                f"task_name={task_name}"
            )

        return str(file_path)

    except Exception:
        if logger:
            logger.exception(
                f"Failed to persist fallback payload to storage: task_name={task_name}"
            )
        return None


def send_notification(
    task_name: str,
    error: Exception | str,
    context: dict[str, Any] | None = None,
    payload_summary: dict[str, Any] | None = None,
    logger=None,
) -> bool:
    """
    Send notification about failed task.

    Currently mocked - logs notification details. Extensible for
    Slack/PagerDuty/email in the future.

    Args:
        task_name: Name of the task that failed
        error: Exception object or error message string
        context: Additional context (batch_id, ano_mes, etc.)
        payload_summary: Summary of the failed payload (for logging)
        logger: Logger instance for logging (optional)

    Returns:
        True if notification was sent successfully, False otherwise
    """
    notification_type = get_notification_type()
    if notification_type != "mock" and logger:
        logger.warning(
            f"Notification type '{notification_type}' not yet implemented, "
            "using mock notification"
        )
        # Future: implement Slack/PagerDuty/email integrations

    try:
        # Extract error information
        if isinstance(error, Exception):
            error_type = type(error).__name__
            error_message = str(error)
        else:
            error_type = "UnknownError"
            error_message = str(error)

        # Build notification payload
        notification_payload: dict[str, Any] = {
            "notification_type": "mock",
            "task_name": task_name,
            "error_type": error_type,
            "error_message": error_message,
            "failed_at": datetime.now(UTC).isoformat(),
        }

        if context:
            notification_payload["context"] = context

        if payload_summary:
            notification_payload["payload_summary"] = payload_summary

        # Mock notification: just log it
        if logger:
            logger.info(
                f"Notification sent (mocked): task_name={task_name}, "
                f"error_type={error_type}, error_message={error_message[:100]}"
            )

        return True

    except Exception:
        if logger:
            logger.exception(f"Failed to send notification: task_name={task_name}")
        return False


def update_metadata_notification_status(
    storage_path: str,
    notification_sent: bool,
    logger=None,
) -> bool:
    """
    Update the notification_sent field in a persisted fallback JSON file.

    Args:
        storage_path: Path to the persisted JSON file
        notification_sent: Boolean indicating if notification was sent
        logger: Logger instance for logging (optional)

    Returns:
        True if update was successful, False otherwise
    """
    try:
        # Read existing file
        with Path(storage_path).open(encoding="utf-8") as f:
            fallback_data = json.load(f)

        # Update notification_sent in metadata
        if "metadata" in fallback_data:
            fallback_data["metadata"]["notification_sent"] = notification_sent

        # Write updated file
        with Path(storage_path).open("w", encoding="utf-8") as f:
            json.dump(fallback_data, f, indent=2, ensure_ascii=False, default=str)

        if logger:
            logger.debug(
                f"Updated metadata notification_sent={notification_sent} "
                f"in file: {storage_path}"
            )

        return True

    except Exception as e:
        if logger:
            logger.warning(
                f"Failed to update metadata notification status in {storage_path}: {e}"
            )
        return False


@task(
    name="handle_fallback",
    retries=0,  # Fallback should not retry - it's the last resort
    timeout_seconds=60,
    log_prints=True,
    tags=["fallback"],
)
def handle_fallback(
    payload: dict[str, Any],
    error: Exception | str,
    task_name: str,
    context: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """
    Handle fallback operations when task retries are exhausted.

    This task:
    1. Persists failed payloads to durable storage (local disk by default)
    2. Sends notifications (mocked for now, extensible to Slack/PagerDuty/email)
    3. Logs comprehensive error details

    Args:
        payload: The failed payload/data to persist
        error: Exception object or error message string
        task_name: Name of the task that failed
        context: Optional additional context (batch_id, ano_mes, etc.)

    Returns:
        Dictionary with statistics:
        - persisted: True if payload was persisted successfully
        - storage_path: Path where payload was persisted (if successful)
        - notified: True if notification was sent successfully
        - errors: List of any errors that occurred during fallback operations

    Note:
        This task never raises exceptions - it handles all errors gracefully
        and returns partial success statistics.
    """
    logger = get_run_logger()

    logger.warning(
        f"Handling fallback for failed task: task_name={task_name}, "
        f"error={str(error)[:200]}"
    )

    stats: dict[str, Any] = {
        "persisted": False,
        "storage_path": None,
        "notified": False,
        "errors": [],
    }

    # Try to persist payload to storage
    try:
        storage_path = persist_to_storage(
            payload=payload,
            task_name=task_name,
            error=error,
            context=context,
            logger=logger,
        )

        if storage_path:
            stats["persisted"] = True
            stats["storage_path"] = storage_path
            logger.info(f"Successfully persisted fallback payload: path={storage_path}")
        else:
            stats["errors"].append("Failed to persist payload to storage")
            logger.error("Failed to persist fallback payload to storage")

    except Exception as e:
        stats["errors"].append(f"Exception during storage persistence: {e!s}")
        logger.exception("Exception during storage persistence")

    # Try to send notification
    try:
        # Build payload summary for notification (avoid logging full payload)
        payload_summary = {}
        if isinstance(payload, dict):
            payload_summary = {
                "keys": list(payload.keys()),
                "size": len(str(payload)),
            }

        notified = send_notification(
            task_name=task_name,
            error=error,
            context=context,
            payload_summary=payload_summary,
            logger=logger,
        )

        if notified:
            stats["notified"] = True
            logger.info("Successfully sent fallback notification")
        else:
            stats["errors"].append("Failed to send notification")
            logger.error("Failed to send fallback notification")

    except Exception as e:
        stats["errors"].append(f"Exception during notification: {e!s}")
        logger.exception("Exception during notification")

    # Update metadata file with actual notification status
    # This runs even if notification raised an exception (using stats["notified"])
    if stats["persisted"] and stats["storage_path"]:
        try:
            update_metadata_notification_status(
                storage_path=stats["storage_path"],
                notification_sent=stats["notified"],
                logger=logger,
            )
        except Exception as e:
            # Log but don't fail - file was already persisted successfully
            logger.warning(f"Failed to update metadata notification status: {e}")

    # Log final statistics
    if stats["errors"]:
        logger.warning(
            f"Fallback handling completed with errors: "
            f"persisted={stats['persisted']}, notified={stats['notified']}, "
            f"errors={len(stats['errors'])}"
        )
    else:
        logger.info(
            f"Fallback handling completed successfully: "
            f"persisted={stats['persisted']}, notified={stats['notified']}"
        )

    return stats
