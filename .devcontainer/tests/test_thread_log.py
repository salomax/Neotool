"""Unit tests for thread_log JSONL writer and cost aggregation."""
from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from agents.backend import BackendResult
from thread_log import (
    LoggingBackend,
    aggregate_cost,
    append_entry,
    list_tasks,
    log_file,
    log_stage_for_intent,
    read_entries,
)


@pytest.fixture
def log_root(tmp_path: Path) -> Path:
    return tmp_path / "threads"


def test_log_stage_for_intent() -> None:
    assert log_stage_for_intent("stage_impl") == "stage_impl"
    assert log_stage_for_intent("stage_design") == "stage_design"
    assert log_stage_for_intent("question") == "question"
    assert log_stage_for_intent("advance") == "question"
    assert log_stage_for_intent("fast_track") == "question"


def test_append_entry_writes_jsonl_line(log_root: Path) -> None:
    entry = append_entry(
        "slack-C123-1234-5678",
        "question",
        direction="out",
        agent="claude",
        model="sonnet",
        tokens_in=100,
        tokens_out=50,
        latency_ms=1500,
        content="hello world",
        root=log_root,
        ts=1_700_000_000.0,
    )

    path = log_file("slack-C123-1234-5678", "question", log_root)
    assert path.exists()
    lines = path.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 1

    record = json.loads(lines[0])
    assert record["direction"] == "out"
    assert record["agent"] == "claude"
    assert record["model"] == "sonnet"
    assert record["tokens_in"] == 100
    assert record["tokens_out"] == 50
    assert record["latency_ms"] == 1500
    assert record["content"] == "hello world"
    assert record["ts"].startswith("2023-")
    assert entry == record


def test_append_entry_is_append_only(log_root: Path) -> None:
    task_id = "task-1"
    stage = "stage_impl"

    append_entry(
        task_id, stage,
        direction="out", agent="codex", model="gpt-5",
        latency_ms=100, content="first",
        root=log_root,
    )
    append_entry(
        task_id, stage,
        direction="out", agent="codex", model="gpt-5",
        latency_ms=200, content="second",
        root=log_root,
    )

    path = log_file(task_id, stage, log_root)
    lines = path.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 2
    assert json.loads(lines[0])["content"] == "first"
    assert json.loads(lines[1])["content"] == "second"


def test_aggregate_cost_sums_by_stage(log_root: Path) -> None:
    append_entry(
        "task-cost", "question",
        direction="out", agent="claude", model="sonnet",
        tokens_in=10, tokens_out=20, latency_ms=1, content="a",
        root=log_root,
    )
    append_entry(
        "task-cost", "question",
        direction="out", agent="claude", model="sonnet",
        tokens_in=5, tokens_out=15, latency_ms=1, content="b",
        root=log_root,
    )
    append_entry(
        "task-cost", "stage_impl",
        direction="out", agent="codex", model="gpt-5",
        tokens_in=100, tokens_out=200, latency_ms=1, content="c",
        root=log_root,
    )

    summary = aggregate_cost("task-cost", log_root)
    assert summary["stages"]["question"] == {"tokens_in": 15, "tokens_out": 35}
    assert summary["stages"]["stage_impl"] == {"tokens_in": 100, "tokens_out": 200}
    assert summary["total"] == {"tokens_in": 115, "tokens_out": 235}


def test_list_tasks_returns_newest_first(log_root: Path) -> None:
    append_entry(
        "older-task", "question",
        direction="out", agent="claude", model="",
        latency_ms=1, content="x", root=log_root, ts=1_000.0,
    )
    append_entry(
        "newer-task", "stage_design",
        direction="out", agent="claude", model="",
        latency_ms=1, content="y", root=log_root, ts=2_000.0,
    )

    tasks = list_tasks(log_root)
    assert len(tasks) == 2
    assert tasks[0]["task_id"] == "newer-task"
    assert tasks[0]["last_stage"] == "stage_design"
    assert tasks[1]["task_id"] == "older-task"


def test_read_entries_includes_stage(log_root: Path) -> None:
    append_entry(
        "task-read", "stage_review",
        direction="out", agent="claude", model="",
        latency_ms=1, content="review done", root=log_root,
    )

    entries = read_entries("task-read", log_root)
    assert len(entries) == 1
    assert entries[0]["stage"] == "stage_review"
    assert entries[0]["content"] == "review done"


def test_logging_backend_writes_in_and_out(log_root: Path) -> None:
    inner = MagicMock()
    inner.invoke.return_value = BackendResult(
        output="done",
        exit_code=0,
        elapsed_ms=900,
        tokens_in=11,
        tokens_out=22,
    )
    backend = LoggingBackend(
        inner,
        task_id="slack-C1-999",
        stage="question",
        root=log_root,
    )

    result = backend.invoke(
        "hello prompt",
        agent="claude",
        model="sonnet",
        thread="slack-C1-999",
    )

    assert result.output == "done"
    inner.invoke.assert_called_once()
    entries = read_entries("slack-C1-999", log_root)
    assert len(entries) == 2
    assert entries[0]["direction"] == "in"
    assert entries[0]["content"] == "hello prompt"
    assert entries[0]["latency_ms"] == 0
    assert entries[1]["direction"] == "out"
    assert entries[1]["content"] == "done"
    assert entries[1]["tokens_in"] == 11
    assert entries[1]["tokens_out"] == 22
    assert entries[1]["latency_ms"] == 900
