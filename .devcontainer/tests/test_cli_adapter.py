"""Unit tests for CLI adapter argv parsing."""
from __future__ import annotations

import pytest

from agents.cli_adapter import parse_argv


def test_parse_argv_minimal() -> None:
    task = parse_argv(["-m", "implement issue #369"])
    assert task.message == "implement issue #369"
    assert task.task_id.startswith("cli-")
    assert task.issue_ref is None
    assert task.agent_hint is None
    assert task.mode == "interactive"
    assert task.adapter_env == {}


def test_parse_argv_with_options() -> None:
    task = parse_argv([
        "-m", "design the solution",
        "--issue", "369",
        "--agent", "claude",
        "--task-id", "cli-custom-id",
        "--requester", "marcos",
    ])
    assert task.message == "design the solution"
    assert task.task_id == "cli-custom-id"
    assert task.issue_ref == 369
    assert task.agent_hint == "claude"
    assert task.requester == "marcos"


def test_parse_argv_with_auto_flag() -> None:
    task = parse_argv(["-m", "faz tudo", "--issue", "369", "--auto"])
    assert task.mode == "auto"
    assert task.issue_ref == 369


def test_parse_argv_requires_message() -> None:
    with pytest.raises(SystemExit):
        parse_argv([])
