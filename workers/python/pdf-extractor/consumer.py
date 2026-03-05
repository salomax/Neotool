from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import logging
import threading
import time
from typing import Any

from confluent_kafka import Consumer

from cleaner import clean_extracted_text
from config import WorkerConfig
from downloader import DownloadError, SignedUrlExpiredError, download_pdf
from extractor import extract_pdf_document
from publisher import KafkaPublisher
from schema import DlqPayload, SubmittedPayload


logger = logging.getLogger("pdf_extractor")


class WorkerHealthState:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._ready = False
        self._fatal_error: str | None = None

    def mark_ready(self) -> None:
        with self._lock:
            if self._fatal_error is None:
                self._ready = True

    def mark_fatal(self, error_message: str) -> None:
        with self._lock:
            self._fatal_error = error_message
            self._ready = False

    def snapshot(self) -> tuple[int, str]:
        with self._lock:
            if self._fatal_error is not None:
                return 503, "fatal"
            if self._ready:
                return 200, "ok"
            return 503, "starting"

    def is_ready(self) -> bool:
        with self._lock:
            return self._ready and self._fatal_error is None


def _build_health_handler(state: WorkerHealthState) -> type[BaseHTTPRequestHandler]:
    class HealthHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            if self.path != "/health":
                self.send_response(404)
                self.end_headers()
                return

            status, body = state.snapshot()
            payload = body.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
            return

    return HealthHandler


def _start_health_server(state: WorkerHealthState, port: int = 8080) -> ThreadingHTTPServer:
    server = ThreadingHTTPServer(("0.0.0.0", port), _build_health_handler(state))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def run() -> None:
    _configure_logging()
    health_state = WorkerHealthState()
    health_server = _start_health_server(health_state)
    consumer: Consumer | None = None

    try:
        config = WorkerConfig.from_env()
        logger.info("starting pdf-extractor worker topic_in=%s", config.kafka_topic_in)

        consumer = Consumer(
            {
                "bootstrap.servers": config.kafka_broker,
                "group.id": config.kafka_group_id,
                "auto.offset.reset": config.auto_offset_reset,
                "enable.auto.commit": False,
            }
        )
        publisher = KafkaPublisher(config.kafka_broker, config.kafka_topic_out, config.kafka_topic_dlq)
        consumer.subscribe([config.kafka_topic_in])
        last_connectivity_check = 0.0

        while True:
            last_connectivity_check = _mark_ready_if_connected(
                consumer=consumer,
                health_state=health_state,
                last_check=last_connectivity_check,
            )
            msg = consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error() is not None:
                logger.error("kafka_consumer_error err=%s", msg.error())
                continue

            raw_value = msg.value() or b"{}"
            submitted: SubmittedPayload | None = None

            try:
                submitted = SubmittedPayload.model_validate_json(raw_value)
                extracted = _process_message(submitted, config)
                publisher.publish_extracted(submitted.job_id, extracted)
                consumer.commit(msg)
            except Exception as exc:
                dlq_payload = _build_dlq_payload(submitted, raw_value, exc)
                try:
                    key = dlq_payload.job_id or "unknown-job"
                    publisher.publish_dlq(key, dlq_payload.model_dump())
                    consumer.commit(msg)
                except Exception as dlq_exc:
                    logger.error("failed_to_publish_dlq err=%s", dlq_exc, exc_info=True)
    except Exception as exc:
        health_state.mark_fatal(str(exc))
        logger.error("worker_fatal_error err=%s", exc, exc_info=True)
        raise
    finally:
        if consumer is not None:
            consumer.close()
        health_server.shutdown()
        health_server.server_close()


def _mark_ready_if_connected(
    consumer: Consumer,
    health_state: WorkerHealthState,
    last_check: float,
    check_interval_seconds: float = 1.0,
) -> float:
    if health_state.is_ready():
        return last_check

    now = time.monotonic()
    if now - last_check < check_interval_seconds:
        return last_check

    try:
        metadata = consumer.list_topics(timeout=0.5)
        if metadata.topics:
            health_state.mark_ready()
    except Exception:
        pass

    return now


def _process_message(submitted: SubmittedPayload, config: WorkerConfig) -> dict[str, Any]:
    pdf_bytes = download_pdf(
        submitted.download_url,
        timeout_seconds=config.download_timeout_seconds,
        max_retries=config.download_max_retries,
    )
    result = extract_pdf_document(pdf_bytes)
    cleaned_text = clean_extracted_text(result.extracted_text, max_chars=config.max_output_chars)

    return {
        "job_id": submitted.job_id,
        "asset_id": submitted.asset_id,
        "document_type": submitted.document_type,
        "extracted_text": cleaned_text,
        "extraction_metadata": result.metadata.model_dump(),
        "callback_context": submitted.callback_context,
    }


def _build_dlq_payload(
    submitted: SubmittedPayload | None,
    raw_value: bytes,
    exc: Exception,
) -> DlqPayload:
    if submitted is None:
        job_id = None
        document_type = None
        asset_id = None
        callback_context: dict[str, Any] = {}
        try:
            payload = json.loads(raw_value.decode("utf-8"))
            job_id = payload.get("job_id")
            document_type = payload.get("document_type")
            asset_id = payload.get("asset_id")
            callback_context = payload.get("callback_context") or {}
        except Exception:
            pass
    else:
        job_id = submitted.job_id
        document_type = submitted.document_type
        asset_id = submitted.asset_id
        callback_context = submitted.callback_context

    if isinstance(exc, SignedUrlExpiredError):
        error_message = f"signed_url_expired: {exc}"
    elif isinstance(exc, DownloadError):
        error_message = f"download_failed: {exc}"
    else:
        error_message = f"processing_failed: {exc}"

    logger.error(
        "message_processing_failed job_id=%s document_type=%s err=%s",
        job_id,
        document_type,
        error_message,
        exc_info=True,
    )

    return DlqPayload(
        job_id=str(job_id) if job_id is not None else None,
        asset_id=str(asset_id) if asset_id is not None else None,
        document_type=document_type,
        error=error_message,
        callback_context=callback_context,
    )


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


if __name__ == "__main__":
    run()
