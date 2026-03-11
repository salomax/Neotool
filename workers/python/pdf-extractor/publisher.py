from __future__ import annotations

import json
import threading
from typing import Any, Optional

from confluent_kafka import Producer


class KafkaPublisher:
    def __init__(self, broker: str, topic_out: str, topic_dlq: str) -> None:
        self._producer = Producer({"bootstrap.servers": broker})
        self._topic_out = topic_out
        self._topic_dlq = topic_dlq

    def publish_extracted(self, key: str, payload: dict[str, Any]) -> None:
        self._publish(self._topic_out, key, payload)

    def publish_dlq(self, key: str, payload: dict[str, Any]) -> None:
        self._publish(self._topic_dlq, key, payload)

    def _publish(self, topic: str, key: str, payload: dict[str, Any]) -> None:
        delivered = threading.Event()
        result: dict[str, Optional[Exception]] = {"error": None}

        def callback(err, _msg) -> None:
            if err is not None:
                result["error"] = RuntimeError(str(err))
            delivered.set()

        self._producer.produce(
            topic=topic,
            key=key.encode("utf-8"),
            value=json.dumps(payload, ensure_ascii=True).encode("utf-8"),
            callback=callback,
        )
        self._producer.poll(0)

        if not delivered.wait(timeout=10):
            self._producer.flush(10)

        if result["error"] is not None:
            raise result["error"]  # type: ignore[misc]

        remaining = self._producer.flush(10)
        if remaining > 0:
            raise RuntimeError(f"kafka_publish_timeout topic={topic} remaining={remaining}")
