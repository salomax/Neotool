from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class WorkerConfig:
    kafka_broker: str
    kafka_group_id: str
    kafka_topic_in: str
    kafka_topic_out: str
    kafka_topic_dlq: str
    auto_offset_reset: str
    download_timeout_seconds: int
    download_max_retries: int
    max_output_chars: int
    health_port: int

    @staticmethod
    def from_env() -> "WorkerConfig":
        return WorkerConfig(
            kafka_broker=os.getenv("KAFKA_BROKER", "localhost:9094"),
            kafka_group_id=os.getenv("KAFKA_GROUP_ID", "pdf-extractor"),
            kafka_topic_in=os.getenv("KAFKA_TOPIC_IN", "pdf-extractor.submitted"),
            kafka_topic_out=os.getenv("KAFKA_TOPIC_OUT", "pdf-extractor.extracted"),
            kafka_topic_dlq=os.getenv("KAFKA_TOPIC_DLQ", "pdf-extractor.dlq"),
            auto_offset_reset=os.getenv("KAFKA_AUTO_OFFSET_RESET", "earliest"),
            download_timeout_seconds=int(os.getenv("DOWNLOAD_TIMEOUT_SECONDS", "30")),
            download_max_retries=int(os.getenv("DOWNLOAD_MAX_RETRIES", "3")),
            max_output_chars=int(os.getenv("MAX_OUTPUT_CHARS", "12000")),
            health_port=int(os.getenv("HEALTH_PORT", "8080")),
        )
