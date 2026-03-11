from __future__ import annotations

import time
from typing import Optional
from unittest.mock import patch

import httpx

from consumer import (
    WorkerHealthState,
    _build_dlq_payload,
    _mark_ready_if_connected,
    _process_message,
    _start_health_server,
)
from config import WorkerConfig
from schema import ExtractionMetadata, ExtractionResult, SubmittedPayload


def _config() -> WorkerConfig:
    return WorkerConfig(
        kafka_broker="localhost:9094",
        kafka_group_id="pdf-extractor",
        kafka_topic_in="pdf-extractor.submitted",
        kafka_topic_out="pdf-extractor.extracted",
        kafka_topic_dlq="pdf-extractor.dlq",
        auto_offset_reset="earliest",
        download_timeout_seconds=30,
        download_max_retries=3,
        max_output_chars=12000,
        health_port=8080,
    )


@patch("consumer.clean_extracted_text")
@patch("consumer.extract_pdf_document")
@patch("consumer.download_pdf")
def test_process_message_keeps_callback_context_unchanged(
    mock_download,
    mock_extract,
    mock_clean,
):
    callback = {"job_id": "job-1", "account_id": "acc-1", "user_id": "usr-1"}
    submitted = SubmittedPayload(
        job_id="job-1",
        asset_id="asset-1",
        download_url="https://signed-url",
        document_type="CREDIT_CARD_BILL",
        callback_context=callback,
    )

    mock_download.return_value = b"%PDF-1.7 fake"
    mock_extract.return_value = ExtractionResult(
        extracted_text="raw text",
        metadata=ExtractionMetadata(
            page_count=1,
            tables_found=1,
            bank_name_hint="Nubank",
            extraction_duration_ms=12,
        ),
    )
    mock_clean.return_value = "clean text"

    payload = _process_message(submitted, _config())

    assert payload["job_id"] == submitted.job_id
    assert payload["callback_context"] == callback
    assert submitted.callback_context == callback


def test_build_dlq_payload_preserves_callback_context() -> None:
    callback = {"job_id": "job-2", "account_id": "acc-2", "user_id": "usr-2"}
    submitted = SubmittedPayload(
        job_id="job-2",
        asset_id="asset-2",
        download_url="https://signed-url",
        document_type="CREDIT_CARD_BILL",
        callback_context=callback,
    )

    dlq = _build_dlq_payload(submitted, b"{}", RuntimeError("boom"))

    assert dlq.job_id == "job-2"
    assert dlq.callback_context == callback
    assert "processing_failed" in dlq.error


def test_worker_health_state_transitions() -> None:
    state = WorkerHealthState()
    assert state.snapshot() == (503, "starting")

    state.mark_ready()
    assert state.snapshot() == (200, "ok")

    state.mark_fatal("boom")
    assert state.snapshot() == (503, "fatal")


def test_health_endpoint_reflects_state() -> None:
    state = WorkerHealthState()
    server = _start_health_server(state, port=0)

    try:
        url = f"http://127.0.0.1:{server.server_port}/health"
        with httpx.Client(timeout=2.0) as client:
            response = _get_health(client, url)
            assert response.status_code == 503
            assert response.text == "starting"

            state.mark_ready()
            response = _get_health(client, url)
            assert response.status_code == 200
            assert response.text == "ok"

            state.mark_fatal("fatal")
            response = _get_health(client, url)
            assert response.status_code == 503
            assert response.text == "fatal"
    finally:
        server.shutdown()
        server.server_close()


def _get_health(client: httpx.Client, url: str) -> httpx.Response:
    last_error: Optional[Exception] = None
    for _ in range(5):
        try:
            return client.get(url)
        except Exception as exc:
            last_error = exc
            time.sleep(0.05)
    if last_error is not None:
        raise last_error
    raise RuntimeError("unexpected_health_probe_failure")


def test_mark_ready_if_connected_requires_metadata_success() -> None:
    class _Metadata:
        def __init__(self, topics):
            self.topics = topics

    class _ConnectedConsumer:
        def list_topics(self, timeout):
            return _Metadata({"pdf-extractor.submitted": object()})

    class _DisconnectedConsumer:
        def list_topics(self, timeout):
            raise RuntimeError("broker_down")

    disconnected_state = WorkerHealthState()
    _mark_ready_if_connected(_DisconnectedConsumer(), disconnected_state, last_check=0.0, check_interval_seconds=0.0)
    assert disconnected_state.snapshot() == (503, "starting")

    connected_state = WorkerHealthState()
    _mark_ready_if_connected(_ConnectedConsumer(), connected_state, last_check=0.0, check_interval_seconds=0.0)
    assert connected_state.snapshot() == (200, "ok")
