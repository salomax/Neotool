"""Tests for advance remapping and thread summary."""
from __future__ import annotations

from unittest.mock import patch

import router
from agents.backend import BackendResult


def _success_result(output: str = "ok") -> BackendResult:
    return BackendResult(
        output=output,
        exit_code=0,
        elapsed_ms=100,
        tokens_in=10,
        tokens_out=20,
    )


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
@patch("router.load_state")
def test_advance_after_backlog_runs_impl(
    mock_load,
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_load.return_value = {"issue_number": 42, "current_stage": 3}
    mock_fetch.return_value = None
    mock_classify.return_value = {
        "intent": "advance",
        "agent": None,
        "model": None,
        "issue_number": 42,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _success_result("impl output")

    router.route("sim", "cli-advance-impl", clean_message=False)

    dispatch_clf = mock_dispatch_result.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_impl"
    agent, model, _ = router._resolve_dispatch_targets(dispatch_clf)
    assert agent == "codex"


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
@patch("router.load_state")
def test_advance_after_validate_runs_stage_pr_with_cursor_routing(
    mock_load,
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_load.return_value = {"issue_number": 42, "current_stage": 6}
    mock_fetch.return_value = None
    mock_classify.return_value = {
        "intent": "advance",
        "agent": None,
        "model": None,
        "issue_number": 42,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _success_result("pr output")

    router.route("sim", "cli-advance-pr", clean_message=False)

    dispatch_clf = mock_dispatch_result.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_pr"
    agent, model, _ = router._resolve_dispatch_targets(dispatch_clf)
    assert agent == "cursor"
    assert model == "auto"


@patch("router.write_thread_summary")
@patch("router.classify")
@patch("router.load_state")
def test_advance_after_pr_triggers_summarize(
    mock_load,
    mock_classify,
    mock_summary,
) -> None:
    mock_load.return_value = {"issue_number": 42, "current_stage": 7}
    mock_classify.return_value = {
        "intent": "advance",
        "agent": None,
        "model": None,
        "issue_number": 42,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_summary.return_value = "📊 summary"

    output = router.route("sim", "cli-advance-done", clean_message=False)

    assert output == "📊 summary"
    mock_summary.assert_called_once_with("cli-advance-done")


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
@patch("router.load_state")
def test_route_advance_after_design_checkpoint_runs_backlog(
    mock_load,
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_load.return_value = {"issue_number": 369, "current_stage": 2}
    mock_fetch.return_value = None
    mock_classify.return_value = {
        "intent": "advance",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _success_result("backlog from advance")

    router.route("sim", "cli-resume-2", clean_message=False)

    dispatch_clf = mock_dispatch_result.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_backlog"
    prompt = mock_dispatch_result.call_args.args[1]
    assert "/neotool-backlog-gen" in prompt
    assert "Issue: #369" in prompt
