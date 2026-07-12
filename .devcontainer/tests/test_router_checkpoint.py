"""Integration tests for checkpoint resume in router.route()."""
from __future__ import annotations

from unittest.mock import patch

import router
from agents.backend import BackendResult
from checkpoint import Checkpoint


def _design_completed_checkpoint() -> Checkpoint:
    return Checkpoint(
        stage="design",
        status="completed",
        task_id="cli-resume-1",
        timestamp="2026-07-12T21:00:00Z",
        issue_number=369,
        output_summary="analysis posted",
    )


def _success_result(output: str = "ok") -> BackendResult:
    return BackendResult(output=output, exit_code=0, elapsed_ms=100)


def _failed_result(output: str = "❌ Timeout — agente excedeu 30 minutos.") -> BackendResult:
    return BackendResult(output=output, exit_code=-1, elapsed_ms=1_800_000)


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_route_bumps_repeated_design_to_backlog(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_fetch.return_value = _design_completed_checkpoint()
    mock_classify.return_value = {
        "intent": "stage_design",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _success_result("backlog output")

    output = router.route(
        "design issue #369",
        "cli-resume-1",
        clean_message=False,
    )

    assert output == "backlog output"
    dispatch_clf = mock_dispatch_result.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_backlog"
    assert mock_write.call_count == 2
    assert mock_write.call_args_list[0].kwargs["status"] == "in_progress"
    assert mock_write.call_args_list[1].kwargs["status"] == "completed"


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
    mock_fetch.return_value = _design_completed_checkpoint()
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

    prompt = mock_dispatch_result.call_args.args[1]
    assert "/neotool-backlog-gen" in prompt
    assert "Issue: #369" in prompt


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_route_in_progress_design_stays_on_design(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_fetch.return_value = Checkpoint(
        stage="design",
        status="in_progress",
        task_id="cli-resume-3",
        timestamp="2026-07-12T21:00:00Z",
        issue_number=369,
    )
    mock_classify.return_value = {
        "intent": "stage_design",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _success_result("design retry")

    router.route("design issue #369", "cli-resume-3", clean_message=False)

    dispatch_clf = mock_dispatch_result.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_design"


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
@patch("router._apply_stage_state")
def test_route_writes_failed_checkpoint_on_dispatch_failure(
    mock_apply_stage,
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_fetch.return_value = None
    mock_classify.return_value = {
        "intent": "stage_design",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _failed_result()

    output = router.route("design issue #369", "cli-fail-1", clean_message=False)

    assert "Timeout" in (output or "")
    assert mock_write.call_count == 2
    assert mock_write.call_args_list[1].kwargs["status"] == "failed"
    mock_apply_stage.assert_not_called()


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_route_does_not_bump_after_failed_design_checkpoint(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_fetch.return_value = Checkpoint(
        stage="design",
        status="failed",
        task_id="cli-fail-2",
        timestamp="2026-07-12T21:00:00Z",
        issue_number=369,
        output_summary="timeout",
    )
    mock_classify.return_value = {
        "intent": "stage_design",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch_result.return_value = _success_result("design retry")

    router.route("design issue #369", "cli-fail-2", clean_message=False)

    dispatch_clf = mock_dispatch_result.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_design"


@patch("router._sync_state_from_checkpoint")
@patch("router.load_state")
def test_route_syncs_checkpoint_when_issue_in_message(
    mock_load,
    mock_sync,
) -> None:
    mock_load.return_value = {}
    synced = {"current_stage": 2, "issue_number": 369, "last_checkpoint": {"stage": "design"}}
    mock_sync.return_value = synced

    with patch("router.classify") as mock_classify, patch("router.dispatch_result") as mock_dispatch_result:
        mock_classify.return_value = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
        mock_dispatch_result.return_value = _success_result("ok")
        router.route("what is in issue #369?", "thread-checkpoint-sync", clean_message=False)

    assert mock_sync.call_count >= 1
    assert mock_sync.call_args.args[1] == 369
