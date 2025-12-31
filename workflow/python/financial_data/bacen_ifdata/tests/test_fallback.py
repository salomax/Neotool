"""
Unit and integration tests for fallback handling task.

Tests cover:
- Metadata building logic (pure functions)
- Storage persistence (local disk)
- Notification sending (mocked)
- Error handling and graceful degradation
- Prefect task integration
"""

from datetime import UTC, datetime
import json
import os
from pathlib import Path
import tempfile
from unittest.mock import Mock, patch

from tasks.fallback import (
    build_fallback_metadata,
    handle_fallback,
    send_notification,
)

# ============================================================================
# Unit Tests - Pure Functions (No I/O)
# ============================================================================


class TestBuildFallbackMetadata:
    """Test build_fallback_metadata function."""

    def test_basic_metadata_with_exception(self):
        """Test metadata building with Exception object."""
        error = ValueError("Test error message")
        metadata = build_fallback_metadata(
            task_name="test_task",
            error=error,
        )

        assert metadata["task_name"] == "test_task"
        assert metadata["error_type"] == "ValueError"
        assert metadata["error_message"] == "Test error message"
        assert "failed_at" in metadata
        assert metadata["notification_sent"] is False

        # Verify timestamp is valid ISO 8601
        datetime.fromisoformat(metadata["failed_at"].replace("Z", "+00:00"))

    def test_basic_metadata_with_string_error(self):
        """Test metadata building with string error."""
        error = "Test error message"
        metadata = build_fallback_metadata(
            task_name="test_task",
            error=error,
        )

        assert metadata["task_name"] == "test_task"
        assert metadata["error_type"] == "UnknownError"
        assert metadata["error_message"] == "Test error message"
        assert "failed_at" in metadata

    def test_metadata_with_context(self):
        """Test metadata building with additional context."""
        error = ValueError("Test error")
        context = {
            "batch_id": "12345",
            "ano_mes": "202403",
        }
        metadata = build_fallback_metadata(
            task_name="test_task",
            error=error,
            context=context,
        )

        assert metadata["context"] == context
        assert metadata["context"]["batch_id"] == "12345"
        assert metadata["context"]["ano_mes"] == "202403"

    def test_metadata_with_storage_path(self):
        """Test metadata building with storage path."""
        error = ValueError("Test error")
        storage_path = "/path/to/storage/file.json"
        metadata = build_fallback_metadata(
            task_name="test_task",
            error=error,
            storage_path=storage_path,
        )

        assert metadata["storage_path"] == storage_path

    def test_metadata_with_notification_sent(self):
        """Test metadata building with notification status."""
        error = ValueError("Test error")
        metadata = build_fallback_metadata(
            task_name="test_task",
            error=error,
            notification_sent=True,
        )

        assert metadata["notification_sent"] is True

    def test_metadata_with_retry_info(self):
        """Test metadata building with retry information."""
        error = ValueError("Test error")
        retry_info = {
            "attempt_count": 3,
            "max_attempts": 5,
        }
        metadata = build_fallback_metadata(
            task_name="test_task",
            error=error,
            retry_info=retry_info,
        )

        assert metadata["retry_info"] == retry_info
        assert metadata["retry_info"]["attempt_count"] == 3


# ============================================================================
# Unit Tests - Storage Persistence
# ============================================================================


class TestPersistToStorage:
    """Test persist_to_storage function."""

    def test_persist_to_storage_creates_directory(self):
        """Test that storage persistence creates directory structure."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                # Reload module to pick up new env var
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                payload = {"test": "data"}
                error = ValueError("Test error")
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path is not None
                assert Path(storage_path).exists()

                # Verify directory structure: {storage_path}/{task_name}/{YYYY-MM-DD}/
                file_path = Path(storage_path)
                assert file_path.parent.name == datetime.now(UTC).strftime("%Y-%m-%d")
                assert file_path.parent.parent.name == "test_task"
                assert file_path.parent.parent.parent == Path(tmpdir)

    def test_persist_to_storage_writes_json(self):
        """Test that storage persistence writes valid JSON."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                payload = {"test": "data", "nested": {"key": "value"}}
                error = ValueError("Test error")
                context = {"batch_id": "12345"}
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="test_task",
                    error=error,
                    context=context,
                    logger=logger,
                )

                assert storage_path is not None

                # Read and verify JSON
                with open(storage_path, encoding="utf-8") as f:
                    saved_data = json.load(f)

                assert "original_payload" in saved_data
                assert "metadata" in saved_data
                assert saved_data["original_payload"] == payload
                assert saved_data["metadata"]["task_name"] == "test_task"
                assert saved_data["metadata"]["error_type"] == "ValueError"
                assert saved_data["metadata"]["context"] == context
                assert saved_data["metadata"]["storage_path"] == storage_path

    def test_persist_to_storage_filename_format(self):
        """Test that storage persistence uses correct filename format."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                payload = {"test": "data"}
                error = ValueError("Test error")
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path is not None

                # Verify filename format: {timestamp}_{task_name}_{uuid}.json
                filename = Path(storage_path).name
                assert filename.endswith(".json")
                assert "test_task" in filename
                parts = filename.replace(".json", "").split("_")
                assert len(parts) >= 3  # timestamp parts + task_name + uuid

    def test_persist_to_storage_handles_failures(self):
        """Test that storage persistence handles failures gracefully."""
        with tempfile.TemporaryDirectory() as tmpdir:
            # Create a file with the same name as the directory we want to create
            # This will cause a failure
            task_dir = (
                Path(tmpdir) / "test_task" / datetime.now(UTC).strftime("%Y-%m-%d")
            )
            task_dir.parent.mkdir(parents=True, exist_ok=True)
            task_dir.touch()  # Create file instead of directory

            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                payload = {"test": "data"}
                error = ValueError("Test error")
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                # Should return None on failure
                assert storage_path is None
                logger.exception.assert_called()

    def test_persist_to_storage_with_string_error(self):
        """Test storage persistence with string error."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                payload = {"test": "data"}
                error = "String error message"
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path is not None

                # Verify error type is UnknownError for string errors
                with open(storage_path, encoding="utf-8") as f:
                    saved_data = json.load(f)

                assert saved_data["metadata"]["error_type"] == "UnknownError"
                assert saved_data["metadata"]["error_message"] == "String error message"


# ============================================================================
# Unit Tests - Notification
# ============================================================================


class TestSendNotification:
    """Test send_notification function."""

    def test_send_notification_mock_success(self):
        """Test mocked notification sends successfully."""
        error = ValueError("Test error")
        logger = Mock()

        result = send_notification(
            task_name="test_task",
            error=error,
            logger=logger,
        )

        assert result is True
        logger.info.assert_called()
        # Verify log message contains task name
        log_call = logger.info.call_args[0][0]
        assert "test_task" in log_call

    def test_send_notification_with_context(self):
        """Test notification with additional context."""
        error = ValueError("Test error")
        context = {"batch_id": "12345", "ano_mes": "202403"}
        logger = Mock()

        result = send_notification(
            task_name="test_task",
            error=error,
            context=context,
            logger=logger,
        )

        assert result is True
        logger.info.assert_called()

    def test_send_notification_with_payload_summary(self):
        """Test notification with payload summary."""
        error = ValueError("Test error")
        payload_summary = {"keys": ["key1", "key2"], "size": 100}
        logger = Mock()

        result = send_notification(
            task_name="test_task",
            error=error,
            payload_summary=payload_summary,
            logger=logger,
        )

        assert result is True
        logger.info.assert_called()

    def test_send_notification_with_string_error(self):
        """Test notification with string error."""
        error = "String error message"
        logger = Mock()

        result = send_notification(
            task_name="test_task",
            error=error,
            logger=logger,
        )

        assert result is True
        # Verify error type is logged as UnknownError
        log_call = logger.info.call_args[0][0]
        assert "UnknownError" in log_call or "error_type" in log_call

    def test_send_notification_handles_exceptions(self):
        """Test that notification handles exceptions gracefully."""
        error = ValueError("Test error")
        logger = Mock()
        logger.info.side_effect = Exception("Logging failed")

        result = send_notification(
            task_name="test_task",
            error=error,
            logger=logger,
        )

        # Should return False on exception
        assert result is False
        logger.exception.assert_called()


# ============================================================================
# Prefect Task Tests
# ============================================================================


class TestHandleFallback:
    """Test handle_fallback Prefect task."""

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_success(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test successful fallback handling."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = "/path/to/storage/file.json"
        mock_send_notification.return_value = True

        payload = {"test": "data"}
        error = ValueError("Test error")

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is True
        assert result["storage_path"] == "/path/to/storage/file.json"
        assert result["notified"] is True
        assert len(result["errors"]) == 0

        mock_persist_to_storage.assert_called_once()
        mock_send_notification.assert_called_once()

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_storage_failure(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling when storage fails."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = None
        mock_send_notification.return_value = True

        payload = {"test": "data"}
        error = ValueError("Test error")

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is False
        assert result["storage_path"] is None
        assert result["notified"] is True
        assert len(result["errors"]) > 0
        assert "Failed to persist payload to storage" in result["errors"]

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_notification_failure(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling when notification fails."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = "/path/to/storage/file.json"
        mock_send_notification.return_value = False

        payload = {"test": "data"}
        error = ValueError("Test error")

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is True
        assert result["notified"] is False
        assert len(result["errors"]) > 0
        assert "Failed to send notification" in result["errors"]

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_with_context(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling with additional context."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = "/path/to/storage/file.json"
        mock_send_notification.return_value = True

        payload = {"test": "data"}
        error = ValueError("Test error")
        context = {"batch_id": "12345", "ano_mes": "202403"}

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
            context=context,
        )

        assert result["persisted"] is True
        assert result["notified"] is True

        # Verify context was passed to storage and notification
        persist_call = mock_persist_to_storage.call_args
        assert persist_call[1]["context"] == context

        notify_call = mock_send_notification.call_args
        assert notify_call[1]["context"] == context

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_with_exception(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling with Exception object."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = "/path/to/storage/file.json"
        mock_send_notification.return_value = True

        payload = {"test": "data"}
        error = ValueError("Test error message")

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is True
        assert result["notified"] is True

        # Verify error was passed correctly
        persist_call = mock_persist_to_storage.call_args
        assert persist_call[1]["error"] == error

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_with_string_error(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling with string error."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = "/path/to/storage/file.json"
        mock_send_notification.return_value = True

        payload = {"test": "data"}
        error = "String error message"

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is True
        assert result["notified"] is True

        # Verify string error was passed correctly
        persist_call = mock_persist_to_storage.call_args
        assert persist_call[1]["error"] == error

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_storage_exception(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling when storage raises exception."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.side_effect = Exception("Storage exception")
        mock_send_notification.return_value = True

        payload = {"test": "data"}
        error = ValueError("Test error")

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is False
        assert result["notified"] is True
        assert len(result["errors"]) > 0
        assert "Exception during storage persistence" in result["errors"][0]

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_notification_exception(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test fallback handling when notification raises exception."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.return_value = "/path/to/storage/file.json"
        mock_send_notification.side_effect = Exception("Notification exception")

        payload = {"test": "data"}
        error = ValueError("Test error")

        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        assert result["persisted"] is True
        assert result["notified"] is False
        assert len(result["errors"]) > 0
        assert "Exception during notification" in result["errors"][0]

    @patch("tasks.fallback.get_run_logger")
    @patch("tasks.fallback.persist_to_storage")
    @patch("tasks.fallback.send_notification")
    def test_handle_fallback_never_raises(
        self, mock_send_notification, mock_persist_to_storage, mock_get_logger
    ):
        """Test that handle_fallback never raises exceptions."""
        mock_logger = Mock()
        mock_get_logger.return_value = mock_logger
        mock_persist_to_storage.side_effect = Exception("Unexpected error")
        mock_send_notification.side_effect = Exception("Unexpected error")

        payload = {"test": "data"}
        error = ValueError("Test error")

        # Should not raise, even if both operations fail
        result = handle_fallback(
            payload=payload,
            error=error,
            task_name="test_task",
        )

        # Should return partial success statistics
        assert isinstance(result, dict)
        assert "persisted" in result
        assert "notified" in result
        assert "errors" in result


# ============================================================================
# Integration Tests
# ============================================================================


class TestFallbackIntegration:
    """Integration tests with real file system."""

    def test_integration_persist_and_read(self):
        """Integration test: persist payload and read it back."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                payload = {
                    "test": "data",
                    "nested": {"key": "value", "number": 42},
                }
                error = ValueError("Integration test error")
                context = {"batch_id": "integration-123", "ano_mes": "202403"}
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="integration_test",
                    error=error,
                    context=context,
                    logger=logger,
                )

                assert storage_path is not None
                assert Path(storage_path).exists()

                # Read back and verify
                with open(storage_path, encoding="utf-8") as f:
                    saved_data = json.load(f)

                assert saved_data["original_payload"] == payload
                assert saved_data["metadata"]["task_name"] == "integration_test"
                assert saved_data["metadata"]["error_type"] == "ValueError"
                assert saved_data["metadata"]["context"] == context
                assert saved_data["metadata"]["storage_path"] == storage_path

                # Verify timestamp is recent
                failed_at = datetime.fromisoformat(
                    saved_data["metadata"]["failed_at"].replace("Z", "+00:00")
                )
                now = datetime.now(UTC)
                assert (now - failed_at).total_seconds() < 10  # Within 10 seconds

    def test_metadata_notification_sent_updated_after_notification(self):
        """Test that metadata file is updated with correct notification status."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import (
                    persist_to_storage,
                    update_metadata_notification_status,
                )

                payload = {"test": "data"}
                error = ValueError("Test error")
                logger = Mock()

                # Persist payload (initially with notification_sent=False)
                storage_path = persist_to_storage(
                    payload=payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path is not None

                # Verify initial state
                with open(storage_path, encoding="utf-8") as f:
                    initial_data = json.load(f)
                assert initial_data["metadata"]["notification_sent"] is False

                # Update notification status
                update_success = update_metadata_notification_status(
                    storage_path=storage_path,
                    notification_sent=True,
                    logger=logger,
                )

                assert update_success is True

                # Verify updated state
                with open(storage_path, encoding="utf-8") as f:
                    updated_data = json.load(f)
                assert updated_data["metadata"]["notification_sent"] is True

    def test_persist_to_storage_with_non_serializable_payload(self):
        """Test persistence with non-JSON-serializable payloads (bytes, Decimal, Path)."""
        from decimal import Decimal

        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                # Test with bytes
                payload_with_bytes = {
                    "data": b"binary data",
                    "text": "normal text",
                }
                error = ValueError("Test error")
                logger = Mock()

                storage_path = persist_to_storage(
                    payload=payload_with_bytes,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path is not None

                # Verify file was created and can be read
                with open(storage_path, encoding="utf-8") as f:
                    saved_data = json.load(f)

                # Bytes should be converted to string representation
                assert "data" in saved_data["original_payload"]

                # Test with Decimal
                payload_with_decimal = {
                    "amount": Decimal("123.45"),
                    "currency": "USD",
                }

                storage_path2 = persist_to_storage(
                    payload=payload_with_decimal,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path2 is not None

                # Verify Decimal was serialized
                with open(storage_path2, encoding="utf-8") as f:
                    saved_data2 = json.load(f)

                assert "amount" in saved_data2["original_payload"]

                # Test with Path object
                payload_with_path = {
                    "file_path": Path("/some/path/to/file.txt"),
                    "other": "data",
                }

                storage_path3 = persist_to_storage(
                    payload=payload_with_path,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path3 is not None

                # Verify Path was serialized
                with open(storage_path3, encoding="utf-8") as f:
                    saved_data3 = json.load(f)

                assert "file_path" in saved_data3["original_payload"]

    def test_persist_to_storage_with_non_dict_payload(self):
        """Test persistence with non-dict payloads."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import persist_to_storage

                error = ValueError("Test error")
                logger = Mock()

                # Test with list payload
                list_payload = [1, 2, 3, "test"]
                storage_path = persist_to_storage(
                    payload=list_payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path is not None

                with open(storage_path, encoding="utf-8") as f:
                    saved_data = json.load(f)

                assert saved_data["original_payload"] == list_payload

                # Test with string payload
                string_payload = "simple string payload"
                storage_path2 = persist_to_storage(
                    payload=string_payload,
                    task_name="test_task",
                    error=error,
                    logger=logger,
                )

                assert storage_path2 is not None

                with open(storage_path2, encoding="utf-8") as f:
                    saved_data2 = json.load(f)

                assert saved_data2["original_payload"] == string_payload

    def test_handle_fallback_updates_metadata_after_notification(self):
        """Integration test: verify handle_fallback updates metadata after notification."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import handle_fallback

                payload = {"test": "data"}
                error = ValueError("Test error")

                result = handle_fallback(
                    payload=payload,
                    error=error,
                    task_name="test_task",
                )

                assert result["persisted"] is True
                assert result["notified"] is True
                assert result["storage_path"] is not None

                # Verify metadata file was updated with notification status
                storage_path = result["storage_path"]
                with open(storage_path, encoding="utf-8") as f:
                    saved_data = json.load(f)

                # Since notification succeeded, notification_sent should be True
                assert saved_data["metadata"]["notification_sent"] is True

    def test_handle_fallback_updates_metadata_when_notification_fails(self):
        """Test that metadata is updated even when notification fails."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.dict(os.environ, {"FALLBACK_STORAGE_PATH": tmpdir}):
                import importlib

                import tasks.fallback

                importlib.reload(tasks.fallback)
                from tasks.fallback import handle_fallback

                # Mock send_notification to fail
                with patch("tasks.fallback.send_notification", return_value=False):
                    payload = {"test": "data"}
                    error = ValueError("Test error")

                    result = handle_fallback(
                        payload=payload,
                        error=error,
                        task_name="test_task",
                    )

                    assert result["persisted"] is True
                    assert result["notified"] is False
                    assert result["storage_path"] is not None

                    # Verify metadata file was updated with notification status
                    storage_path = result["storage_path"]
                    with open(storage_path, encoding="utf-8") as f:
                        saved_data = json.load(f)

                    # Since notification failed, notification_sent should be False
                    assert saved_data["metadata"]["notification_sent"] is False
