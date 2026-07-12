"""Unit tests for checkpoint.py."""
from __future__ import annotations

import json
from unittest.mock import patch

import pytest

from checkpoint import (
    Checkpoint,
    current_stage_from_checkpoint,
    fetch_last_checkpoint,
    format_checkpoint_comment,
    parse_checkpoint_from_comment,
    write_checkpoint,
)


def _sample_checkpoint(**overrides) -> Checkpoint:
    data = {
        "v": 1,
        "stage": "design",
        "status": "completed",
        "task_id": "cli-abc",
        "timestamp": "2026-07-12T21:00:00Z",
        "issue_number": 369,
        "output_summary": "design done",
    }
    data.update(overrides)
    return Checkpoint.from_dict(data)  # type: ignore[return-value]


def test_parse_checkpoint_from_valid_comment() -> None:
    cp = _sample_checkpoint()
    body = format_checkpoint_comment(cp)
    parsed = parse_checkpoint_from_comment(body)
    assert parsed == cp


def test_parse_checkpoint_legacy_marker() -> None:
    payload = json.dumps(_sample_checkpoint().to_dict())
    body = f"<!-- checkpoint: {payload} -->\nvisible"
    parsed = parse_checkpoint_from_comment(body)
    assert parsed is not None
    assert parsed.stage == "design"
    assert parsed.status == "completed"


def test_parse_checkpoint_missing_returns_none() -> None:
    assert parse_checkpoint_from_comment("plain comment") is None


def test_parse_checkpoint_malformed_json_returns_none() -> None:
    body = "<!-- neotool-checkpoint:{not-json} -->"
    assert parse_checkpoint_from_comment(body) is None


def test_parse_checkpoint_invalid_status_returns_none() -> None:
    payload = json.dumps(
        {
            "stage": "design",
            "status": "unknown",
            "task_id": "t",
            "timestamp": "2026-07-12T21:00:00Z",
            "issue_number": 1,
        }
    )
    assert parse_checkpoint_from_comment(f"<!-- neotool-checkpoint:{payload} -->") is None


def test_current_stage_from_completed_checkpoint() -> None:
    stages = ["issue", "design", "backlog", "impl", "review", "validate"]
    cp = _sample_checkpoint(stage="design", status="completed")
    assert current_stage_from_checkpoint(cp, stages) == 2  # backlog index


def test_current_stage_from_in_progress_checkpoint() -> None:
    stages = ["issue", "design", "backlog", "impl", "review", "validate"]
    cp = _sample_checkpoint(stage="design", status="in_progress")
    assert current_stage_from_checkpoint(cp, stages) == 1  # design index


@patch("checkpoint.subprocess.run")
def test_write_checkpoint_posts_gh_comment(mock_run) -> None:
    mock_run.return_value.returncode = 0
    ok = write_checkpoint(
        369,
        stage="backlog",
        status="completed",
        task_id="cli-test",
        output_summary="tasks listed",
        workspace="/tmp",
    )
    assert ok is True
    mock_run.assert_called_once()
    args = mock_run.call_args.args[0]
    assert args[:3] == ["gh", "issue", "comment"]
    body = mock_run.call_args.kwargs.get("input") or mock_run.call_args[0][0]
    # body passed via -b flag in argv
    cmd = mock_run.call_args.args[0]
    assert "369" in cmd
    assert "-b" in cmd
    body_arg = cmd[cmd.index("-b") + 1]
    assert "neotool-checkpoint:" in body_arg
    assert "backlog" in body_arg


@patch("checkpoint.subprocess.run")
def test_fetch_last_checkpoint_returns_newest(mock_run) -> None:
    older = format_checkpoint_comment(_sample_checkpoint(stage="issue", status="completed"))
    newer = format_checkpoint_comment(_sample_checkpoint(stage="design", status="completed"))
    comments_payload = {
        "comments": [
            {"body": older, "createdAt": "2026-07-12T20:00:00Z"},
            {"body": newer, "createdAt": "2026-07-12T21:00:00Z"},
        ]
    }
    mock_run.return_value.returncode = 0
    mock_run.return_value.stdout = json.dumps(comments_payload)

    cp = fetch_last_checkpoint(369, workspace="/tmp")
    assert cp is not None
    assert cp.stage == "design"


@patch("checkpoint.subprocess.run")
def test_fetch_last_checkpoint_no_marker_returns_none(mock_run) -> None:
    mock_run.return_value.returncode = 0
    mock_run.return_value.stdout = json.dumps({"comments": [{"body": "hello"}]})
    assert fetch_last_checkpoint(1, workspace="/tmp") is None


@patch("checkpoint.subprocess.run")
def test_fetch_last_checkpoint_gh_failure_returns_none(mock_run) -> None:
    mock_run.return_value.returncode = 1
    mock_run.return_value.stderr = "not found"
    assert fetch_last_checkpoint(1, workspace="/tmp") is None
