"""
Unit tests for the shared Kafka consumer runner.
"""

from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

from neotool_common.consumer_base import KafkaConsumerRunner


def _make_runner(process_message, dlq_publisher, max_retries=2):
    retry_config = SimpleNamespace(
        max_retries=max_retries,
        initial_retry_delay_ms=100,
        max_retry_delay_ms=1000,
        retry_backoff_multiplier=2.0,
        retry_jitter=False,
    )

    runner = KafkaConsumerRunner(
        consumer_factory=lambda: MagicMock(name="consumer"),
        producer_factory=lambda: MagicMock(name="producer"),
        retry_config=retry_config,
        message_factory=lambda data: data,
        process_message=process_message,
        dlq_publisher=dlq_publisher,
    )
    runner._producer = MagicMock(name="producer")
    return runner


@pytest.fixture(autouse=True)
def disable_sleep(monkeypatch):
    """Avoid actually sleeping during retry tests."""
    monkeypatch.setattr("neotool_common.consumer_base.time.sleep", lambda _: None)


def test_retries_until_success():
    """Retryable failures should re-run processing until success without DLQ."""
    process = MagicMock(
        side_effect=[
            {"success": False, "error": "timeout error"},
            {"success": False, "error": "timeout error"},
            {"success": True, "parent_cnpj": "123", "domain": "financial"},
        ]
    )
    dlq = MagicMock(return_value=True)

    runner = _make_runner(process, dlq)
    result = runner._process_with_retry({"payload": "value"}, {"payload": "value"})

    assert result["success"]
    assert result["retry_count"] == 2
    assert not result["dlq_published"]
    assert process.call_count == 3
    dlq.assert_not_called()


def test_non_retryable_error_sends_to_dlq():
    """Non-retryable errors should go to DLQ immediately."""
    process = MagicMock(return_value={"success": False, "error": "Invalid payload"})
    dlq = MagicMock(return_value=True)
    message_data = {"payload": "value"}

    runner = _make_runner(process, dlq)
    result = runner._process_with_retry(message_data, message_data)

    assert not result["success"]
    assert result["retry_count"] == 0
    assert result["dlq_published"]
    dlq.assert_called_once()
    _, _, _, retry_count_arg, error_classification_arg = dlq.call_args[0]
    assert retry_count_arg == 0
    assert error_classification_arg == "non-retryable"


def test_max_retries_exhausted_marks_dlq_retryable():
    """Retryable exceptions that exceed max retries should go to DLQ as retryable."""
    def _always_fails(message):
        raise TimeoutError("transient")

    dlq = MagicMock(return_value=True)
    runner = _make_runner(_always_fails, dlq, max_retries=2)
    result = runner._process_with_retry({"payload": "value"}, {"payload": "value"})

    assert not result["success"]
    assert result["retry_count"] == 2
    assert result["dlq_published"]
    dlq.assert_called_once()
    _, _, _, retry_count_arg, error_classification_arg = dlq.call_args[0]
    assert retry_count_arg == 2
    assert error_classification_arg == "retryable"
