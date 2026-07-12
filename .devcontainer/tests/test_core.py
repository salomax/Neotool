"""Unit tests for core.handle_task()."""
from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from core import Task, handle_task, parse_task_mode


def test_parse_task_mode_explicit_auto() -> None:
    msg, mode = parse_task_mode("design issue #1", explicit_mode="auto")
    assert msg == "design issue #1"
    assert mode == "auto"


def test_parse_task_mode_slack_auto_prefix() -> None:
    msg, mode = parse_task_mode("@dev-agent auto faz tudo issue #369")
    assert msg == "faz tudo issue #369"
    assert mode == "auto"


def test_parse_task_mode_default_interactive() -> None:
    msg, mode = parse_task_mode("design issue #1")
    assert mode == "interactive"


def test_handle_task_calls_route_and_callbacks() -> None:
    route_fn = MagicMock(return_value="agent output")
    progress_events: list[str] = []
    result_events: list[tuple[str | None, dict]] = []
    meta_events: list[dict] = []

    task = Task(
        message="implement issue #42",
        task_id="cli-test-1",
        requester="tester",
        adapter_env={"SLACK_TOKEN": "tok"},
    )

    output = handle_task(
        task,
        on_progress=progress_events.append,
        on_result=lambda o, m: result_events.append((o, m)),
        meta_callback=meta_events.append,
        route_fn=route_fn,
        progress_interval=0,
        clean_message=False,
    )

    assert output == "agent output"
    route_fn.assert_called_once()
    args, kwargs = route_fn.call_args
    assert args[0] == "implement issue #42"
    assert args[1] == "cli-test-1"
    assert kwargs["adapter_env"] == {"SLACK_TOKEN": "tok"}
    assert kwargs["clean_message"] is False
    assert kwargs["mode"] == "interactive"
    assert len(result_events) == 1
    assert result_events[0][0] == "agent output"


def test_handle_task_applies_agent_hint() -> None:
    route_fn = MagicMock(return_value="ok")

    task = Task(
        message="review the code",
        task_id="cli-test-2",
        agent_hint="codex",
    )

    handle_task(task, route_fn=route_fn, clean_message=False)

    message = route_fn.call_args.args[0]
    assert message.startswith("@codex review the code")


def test_handle_task_appends_issue_ref() -> None:
    route_fn = MagicMock(return_value="ok")

    task = Task(
        message="generate backlog",
        task_id="cli-test-3",
        issue_ref=369,
    )

    handle_task(task, route_fn=route_fn, clean_message=False)

    message = route_fn.call_args.args[0]
    assert "Issue: #369" in message


def test_handle_task_meta_callback_fires_from_route() -> None:
    route_fn = MagicMock(return_value="done")

    def _route(message, thread, **kwargs):
        cb = kwargs.get("meta_callback")
        if cb:
            cb({"intent": "question", "agent": "claude", "model": "sonnet"})
        return "done"

    meta_events: list[dict] = []
    task = Task(message="hello", task_id="t1")

    handle_task(
        task,
        meta_callback=meta_events.append,
        route_fn=_route,
        clean_message=False,
    )

    assert meta_events[0]["intent"] == "question"


def test_handle_task_reraises_without_on_result_on_error() -> None:
    route_fn = MagicMock(side_effect=RuntimeError("boom"))
    result_events: list[tuple] = []
    task = Task(message="hello", task_id="t-err")

    with pytest.raises(RuntimeError, match="boom"):
        handle_task(
            task,
            on_result=lambda o, m: result_events.append((o, m)),
            route_fn=route_fn,
            clean_message=False,
        )

    assert result_events == []
