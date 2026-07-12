"""Unit tests for router.dispatch() backend delegation."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

import router
from agents.backend import BackendResult


@pytest.fixture
def classification() -> dict:
    return {
        "intent": "question",
        "agent": "codex",
        "model": "gpt-5",
        "issue_number": 369,
    }


def test_dispatch_delegates_to_backend(classification: dict) -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="agent response",
        exit_code=0,
        elapsed_ms=1500,
    )

    with patch.object(router, "_setup_impl_branch"):
        output = router.dispatch(
            classification,
            prompt="hello",
            thread="slack-thread-1",
            adapter_env={
                "SLACK_CHANNEL": "C123",
                "SLACK_TS": "1234.5678",
                "SLACK_TOKEN": "xoxb-test",
            },
            backend=mock_backend,
        )

    assert output == "agent response"
    mock_backend.invoke.assert_called_once()
    kwargs = mock_backend.invoke.call_args.kwargs
    assert mock_backend.invoke.call_args.args[0] == "hello"
    assert kwargs["agent"] == "codex"
    assert kwargs["model"] == "gpt-5"
    assert kwargs["thread"] == "slack-thread-1"
    assert kwargs["env"] == {
        "NEOTOOL_THREAD": "slack-thread-1",
        "SLACK_CHANNEL": "C123",
        "SLACK_TS": "1234.5678",
        "SLACK_TOKEN": "xoxb-test",
    }
    assert kwargs["timeout"] == 1800


def test_dispatch_resolves_default_agent_and_model(classification: dict) -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="ok",
        exit_code=0,
        elapsed_ms=100,
    )
    clf = {
        "intent": "stage_impl",
        "agent": None,
        "model": None,
        "issue_number": None,
    }

    with patch.object(router, "_setup_impl_branch"):
        router.dispatch(
            clf,
            prompt="implement",
            thread="t1",
            adapter_env={"SLACK_TOKEN": "tok"},
            backend=mock_backend,
        )

    kwargs = mock_backend.invoke.call_args.kwargs
    assert kwargs["agent"] == "codex"
    assert kwargs["model"] == "gpt-5.4"


def test_dispatch_stage_impl_sets_up_branch(classification: dict) -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="done",
        exit_code=0,
        elapsed_ms=200,
    )
    clf = {
        "intent": "stage_impl",
        "agent": "codex",
        "model": None,
        "issue_number": 42,
    }
    meta_events: list[dict] = []

    with patch.object(router, "_setup_impl_branch", return_value="feat/GH-42-slug") as setup:
        router.dispatch(
            clf,
            prompt="implement issue",
            thread="t2",
            adapter_env={"SLACK_TOKEN": "tok"},
            meta_callback=meta_events.append,
            backend=mock_backend,
        )

    setup.assert_called_once_with(42)
    assert meta_events[0]["branch"] == "feat/GH-42-slug"
    assert meta_events[0]["agent"] == "codex"


def test_dispatch_returns_timeout_message_from_backend() -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="❌ Timeout — agente excedeu 30 minutos.",
        exit_code=-1,
        elapsed_ms=1_800_000,
    )

    with patch.object(router, "_setup_impl_branch"):
        output = router.dispatch(
            {"intent": "question", "agent": "claude"},
            prompt="slow",
            thread="t3",
            backend=mock_backend,
        )

    assert "Timeout" in output


def test_dispatch_result_preserves_exit_code() -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="agent failed",
        exit_code=1,
        elapsed_ms=500,
    )

    with patch.object(router, "_setup_impl_branch"):
        result = router.dispatch_result(
            {"intent": "stage_design", "agent": "claude", "issue_number": 369},
            prompt="design",
            thread="t4",
            backend=mock_backend,
        )

    assert result.output == "agent failed"
    assert result.exit_code == 1


def test_checkpoint_status_from_result() -> None:
    assert router._checkpoint_status_from_result(
        BackendResult(output="ok", exit_code=0, elapsed_ms=1)
    ) == "completed"
    assert router._checkpoint_status_from_result(
        BackendResult(output="fail", exit_code=1, elapsed_ms=1)
    ) == "failed"
    assert router._checkpoint_status_from_result(
        BackendResult(output="timeout", exit_code=-1, elapsed_ms=1)
    ) == "failed"
