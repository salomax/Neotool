"""
Reusable Kafka consumer base that handles retry, DLQ, commit, and lifecycle concerns.
"""

import logging
import signal
import time
from threading import Thread
from typing import Any, Callable, Dict, Optional, Protocol, Union

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import CommitFailedError

from workflow.common.error_classification import is_retryable_error
from workflow.common.retry_utils import calculate_backoff


class RetryConfigProtocol(Protocol):
    """Protocol describing the retry configuration used by consumers."""

    max_retries: int
    initial_retry_delay_ms: int
    max_retry_delay_ms: int
    retry_backoff_multiplier: float
    retry_jitter: bool


MessageFactory = Callable[[Dict[str, Any]], Any]
ProcessMessage = Callable[[Any], Dict[str, Any]]
DlqPublisher = Callable[[KafkaProducer, Dict[str, Any], Exception, int, str], bool]
RateLimitProvider = Union[int, Callable[[], int]]
HealthServerStarter = Callable[[], Optional[Thread]]


class KafkaConsumerRunner:
    """
    Encapsulates the retry, DLQ, commit, and shutdown wiring for Python consumers.
    """

    def __init__(
        self,
        consumer_factory: Callable[[], KafkaConsumer],
        producer_factory: Callable[[], KafkaProducer],
        retry_config: RetryConfigProtocol,
        message_factory: MessageFactory,
        process_message: ProcessMessage,
        dlq_publisher: DlqPublisher,
        *,
        rate_limit_min_delay_ms: RateLimitProvider = 0,
        error_classifier: Callable[[Union[Exception, str, Any]], bool] = is_retryable_error,
        backoff_fn: Callable[[int, RetryConfigProtocol, int], int] = calculate_backoff,
        health_server_start: Optional[HealthServerStarter] = None,
        health_server_stop: Optional[Callable[[], None]] = None,
        running_flag_setter: Optional[Callable[[bool], None]] = None,
        cleanup_callback: Optional[Callable[[], None]] = None,
        message_context: Optional[Callable[[Any], str]] = None,
        logger: Optional[logging.Logger] = None,
    ) -> None:
        self._consumer_factory = consumer_factory
        self._producer_factory = producer_factory
        self._retry_config = retry_config
        self._message_factory = message_factory
        self._process_message = process_message
        self._dlq_publisher = dlq_publisher
        self._rate_limit_min_delay_ms = rate_limit_min_delay_ms
        self._error_classifier = error_classifier
        self._backoff_fn = backoff_fn
        self._health_server_start = health_server_start
        self._health_server_stop = health_server_stop
        self._running_flag_setter = running_flag_setter
        self._cleanup_callback = cleanup_callback
        self._message_context = message_context
        self._logger = logger or logging.getLogger(__name__)

        self._consumer: Optional[KafkaConsumer] = None
        self._producer: Optional[KafkaProducer] = None
        self._health_thread: Optional[Thread] = None
        self._running = False
        self._signal_handlers: Dict[int, Any] = {}

    def run(self) -> None:
        """
        Start the consumer loop. Blocks until shutdown.
        """
        try:
            self._consumer = self._consumer_factory()
            self._producer = self._producer_factory()
        except Exception:
            self._logger.exception("Failed to create Kafka consumer or producer")
            self._cleanup()
            raise

        self._register_signal_handlers()
        if self._health_server_start:
            self._health_thread = self._health_server_start()

        self._set_running_flag(True)
        self._running = True
        self._logger.info("Kafka consumer started")

        try:
            for record in self._consumer:
                if not self._running:
                    self._logger.info("Shutdown requested, exiting consumer loop")
                    break
                self._handle_record(record)
        except KeyboardInterrupt:
            self._logger.info("KeyboardInterrupt received, shutting down consumer")
        except Exception:
            self._logger.exception("Fatal error in consumer")
            raise
        finally:
            self._running = False
            self._set_running_flag(False)
            self.stop()
            self._cleanup()

    def stop(self) -> None:
        """Request shutdown of the consumer loop."""
        self._running = False

    def _handle_record(self, record: Any) -> None:
        message_data = record.value
        try:
            message = self._message_factory(message_data)
        except Exception as exc:
            self._logger.error(
                "Invalid message, sending to DLQ immediately",
                exc_info=exc,
            )
            result = self._send_to_dlq_result(
                message_data,
                exc,
                retry_count=0,
                error_classification="non-retryable",
            )
            self._commit_after_result(result, record)
            return

        message_desc = self._format_message(message)
        self._logger.info(
            f"Processing message: {message_desc}, partition={record.partition}, offset={record.offset}"
        )

        result = self._process_with_retry(message, message_data)
        self._commit_after_result(result, record)

    def _process_with_retry(self, message: Any, message_data: Dict[str, Any]) -> Dict[str, Any]:
        retry_count = 0
        max_retries = self._retry_config.max_retries

        while retry_count <= max_retries:
            try:
                result = self._process_message(message)
                if result.get("success"):
                    result["retry_count"] = retry_count
                    result["dlq_published"] = False
                    return result

                error_value = result.get("error", "Unknown error")
                error = error_value if isinstance(error_value, Exception) else Exception(str(error_value))
                classification_input = (
                    error_value
                    if isinstance(error_value, (Exception, str))
                    else str(error_value)
                )

                if not self._error_classifier(classification_input):
                    self._logger.warning("Non-retryable error, sending to DLQ immediately")
                    return self._send_to_dlq_result(
                        message_data,
                        error,
                        retry_count=retry_count,
                        error_classification="non-retryable",
                    )

                if retry_count >= max_retries:
                    self._logger.error("Max retries exceeded, sending to DLQ")
                    return self._send_to_dlq_result(
                        message_data,
                        error,
                        retry_count=retry_count,
                        error_classification="retryable",
                    )

                delay_ms = self._calculate_backoff(retry_count)
                self._logger.warning(
                    f"Retryable error (attempt {retry_count + 1}/{max_retries + 1}), "
                    f"retrying in {delay_ms}ms: {error}"
                )
                time.sleep(delay_ms / 1000.0)
                retry_count += 1

            except Exception as exc:
                classification = (
                    "retryable" if self._error_classifier(exc) else "non-retryable"
                )

                if classification == "non-retryable":
                    self._logger.warning(
                        "Non-retryable exception escaped processing, sending to DLQ"
                    )
                    return self._send_to_dlq_result(
                        message_data,
                        exc,
                        retry_count=retry_count,
                        error_classification=classification,
                    )

                if retry_count >= max_retries:
                    self._logger.error("Max retries exceeded after exception, sending to DLQ")
                    return self._send_to_dlq_result(
                        message_data,
                        exc,
                        retry_count=retry_count,
                        error_classification=classification,
                    )

                delay_ms = self._calculate_backoff(retry_count)
                self._logger.warning(
                    f"Retryable exception (attempt {retry_count + 1}/{max_retries + 1}), "
                    f"retrying in {delay_ms}ms: {exc}"
                )
                time.sleep(delay_ms / 1000.0)
                retry_count += 1

        self._logger.error("Retry loop exhausted without return, sending to DLQ")
        return self._send_to_dlq_result(
            message_data,
            Exception("Retry loop exhausted"),
            retry_count=retry_count,
            error_classification="retryable",
        )

    def _send_to_dlq_result(
        self,
        message_data: Dict[str, Any],
        error: Exception,
        retry_count: int,
        error_classification: str,
    ) -> Dict[str, Any]:
        dlq_published = self._publish_to_dlq(
            message_data,
            error,
            retry_count,
            error_classification,
        )
        return {
            "success": False,
            "error": str(error),
            "retry_count": retry_count,
            "dlq_published": dlq_published,
        }

    def _publish_to_dlq(
        self,
        message_data: Dict[str, Any],
        error: Exception,
        retry_count: int,
        error_classification: str,
    ) -> bool:
        if self._producer is None:
            self._logger.error("DLQ publishing skipped: producer is not initialized")
            return False

        try:
            return self._dlq_publisher(
                self._producer,
                message_data,
                error,
                retry_count,
                error_classification,
            )
        except Exception:
            self._logger.exception("DLQ publish failed unexpectedly")
            return False

    def _calculate_backoff(self, retry_count: int) -> int:
        delay_floor = self._resolve_rate_limit_ms()
        return self._backoff_fn(retry_count, self._retry_config, delay_floor)

    def _resolve_rate_limit_ms(self) -> int:
        if callable(self._rate_limit_min_delay_ms):
            return self._rate_limit_min_delay_ms()
        return self._rate_limit_min_delay_ms

    def _commit_after_result(self, result: Dict[str, Any], record: Any) -> None:
        if self._consumer is None:
            return

        message_desc = self._format_record(record)
        if result.get("success"):
            self._logger.info(f"Successfully processed: {message_desc}")
            self._safe_commit("after successful processing")
            return

        error_msg = result.get("error", "Unknown error")
        retry_count = result.get("retry_count", 0)
        self._logger.error(
            f"Processing failed after retries: {message_desc}, retry_count={retry_count}, error={error_msg}"
        )

        if result.get("dlq_published"):
            if self._safe_commit("after DLQ publish"):
                self._logger.info("Processing failure published to DLQ and committed")
            else:
                self._logger.warning(
                    "Processing failure published to DLQ but commit failed. "
                    "Message will be reprocessed but DLQ already has it."
                )
        else:
            self._logger.error(
                "Failed to publish processing failure to DLQ, not committed (will be retried)"
            )

    def _safe_commit(self, context: str) -> bool:
        if self._consumer is None:
            return False

        try:
            self._consumer.commit()
            return True
        except CommitFailedError as exc:
            self._logger.warning(
                f"Failed to commit offsets {context}: {exc}. "
                "Consumer may have been kicked out of the group."
            )
            return False
        except Exception:
            self._logger.exception(f"Unexpected error during commit {context}")
            return False

    def _format_message(self, message: Any) -> str:
        if self._message_context:
            return self._message_context(message)
        return str(message)

    def _format_record(self, record: Any) -> str:
        message_desc = self._format_message(record.value)
        return (
            f"{message_desc}, partition={record.partition}, offset={record.offset}"
            if hasattr(record, "partition")
            else message_desc
        )

    def _set_running_flag(self, running: bool) -> None:
        if self._running_flag_setter:
            try:
                self._running_flag_setter(running)
            except Exception:
                self._logger.exception("Error setting running flag")

    def _register_signal_handlers(self) -> None:
        for sig in (signal.SIGTERM, signal.SIGINT):
            self._signal_handlers[sig] = signal.getsignal(sig)
            signal.signal(sig, self._signal_handler)

    def _restore_signal_handlers(self) -> None:
        for sig, handler in self._signal_handlers.items():
            signal.signal(sig, handler)

    def _signal_handler(self, *_: Any) -> None:
        self._logger.info("Shutdown signal received")
        self.stop()

    def _cleanup(self) -> None:
        if self._consumer:
            self._consumer.close()
        if self._producer:
            self._producer.close()
        if self._cleanup_callback:
            try:
                self._cleanup_callback()
            except Exception:
                self._logger.exception("Cleanup callback failed")
        if self._health_server_stop:
            try:
                self._health_server_stop()
            except Exception:
                self._logger.exception("Health server stop hook failed")
        self._restore_signal_handlers()
