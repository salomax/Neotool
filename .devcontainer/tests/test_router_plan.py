"""Integration tests for planner + auto mode in router.route()."""
from __future__ import annotations

from unittest.mock import patch

import router
from planner import Plan


def _eligible_plan() -> Plan:
    return Plan(
        summary="Run design then backlog",
        stages=["design", "backlog"],
        open_questions=[],
        autonomous_eligible=True,
        issue_number=369,
    )


@patch("router.generate_plan")
@patch("router.classify")
def test_fast_track_interactive_pauses_for_plan_approval(
    mock_classify,
    mock_generate,
) -> None:
    mock_classify.return_value = {
        "intent": "fast_track",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": True,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_generate.return_value = _eligible_plan()

    output = router.route(
        "faz tudo issue #369",
        "cli-plan-1",
        clean_message=False,
        mode="interactive",
    )

    assert output is not None
    assert "Pipeline plan" in output
    assert "sim" in output.lower()


@patch("router.execute_plan")
@patch("router.generate_plan")
@patch("router.classify")
def test_fast_track_auto_executes_without_pause(
    mock_classify,
    mock_generate,
    mock_execute,
) -> None:
    mock_classify.return_value = {
        "intent": "fast_track",
        "agent": None,
        "model": None,
        "issue_number": 369,
        "fast_track": True,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_generate.return_value = _eligible_plan()
    mock_execute.return_value = "pipeline done"

    output = router.route(
        "faz tudo issue #369",
        "cli-plan-2",
        clean_message=False,
        mode="auto",
    )

    assert output == "pipeline done"
    mock_execute.assert_called_once()


@patch("router.generate_plan")
@patch("router.classify")
def test_fast_track_auto_pauses_on_open_questions(
    mock_classify,
    mock_generate,
) -> None:
    mock_classify.return_value = {
        "intent": "fast_track",
        "agent": None,
        "model": None,
        "issue_number": None,
        "fast_track": True,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_generate.return_value = Plan(
        summary="Need info",
        stages=["design"],
        open_questions=["Which issue?"],
        autonomous_eligible=False,
    )

    output = router.route(
        "faz tudo",
        "cli-plan-3",
        clean_message=False,
        mode="auto",
    )

    assert output is not None
    assert "Open questions" in output


@patch("router.execute_plan")
@patch("router.load_state")
@patch("router.classify")
def test_pending_plan_advance_executes_plan(
    mock_classify,
    mock_load,
    mock_execute,
) -> None:
    mock_load.return_value = {
        "pending_plan": {
            "plan": _eligible_plan().to_dict(),
            "next_stage_index": 0,
        },
    }
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
    mock_execute.return_value = "executed after approval"

    output = router.route("sim", "cli-plan-4", clean_message=False)

    assert output == "executed after approval"
    mock_execute.assert_called_once()
