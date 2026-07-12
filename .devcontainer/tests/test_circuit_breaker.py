"""Unit tests for circuit_breaker.py."""
from __future__ import annotations

from agents.backend import BackendResult
from circuit_breaker import (
    apply_post_stage_circuit_breaker,
    detect_blocker,
    should_pause_for_plan,
)
from planner import Plan


def test_should_pause_for_plan_open_questions() -> None:
    plan = Plan(summary="x", stages=["design"], open_questions=["Which issue?"])
    decision = should_pause_for_plan(plan, mode="auto")
    assert decision.pause is True
    assert "Open questions" in (decision.reason or "")


def test_should_pause_for_plan_interactive_mode() -> None:
    plan = Plan(summary="x", stages=["design"], autonomous_eligible=True)
    decision = should_pause_for_plan(plan, mode="interactive")
    assert decision.pause is True
    assert "Interactive mode" in (decision.reason or "")


def test_should_pause_for_plan_auto_eligible() -> None:
    plan = Plan(summary="x", stages=["design"], autonomous_eligible=True)
    decision = should_pause_for_plan(plan, mode="auto")
    assert decision.pause is False


def test_detect_blocker_in_output() -> None:
    assert detect_blocker("All good blocker: true here") is True
    assert detect_blocker("All good") is False


def test_validate_failures_pause_on_third_attempt() -> None:
    result = BackendResult(output="tests failed", exit_code=1, elapsed_ms=100)
    state: dict = {}

    for attempt in range(1, 4):
        pause, updates = apply_post_stage_circuit_breaker(
            stage="validate",
            result=result,
            state=state,
        )
        state.update(updates)
        if attempt < 3:
            assert pause.pause is False, f"attempt {attempt} should not pause"
        else:
            assert pause.pause is True
            assert state["validate_fail_count"] == 3


def test_validate_success_resets_fail_count() -> None:
    result = BackendResult(output="ok", exit_code=0, elapsed_ms=1)
    pause, updates = apply_post_stage_circuit_breaker(
        stage="validate",
        result=result,
        state={"validate_fail_count": 2},
    )
    assert pause.pause is False
    assert updates["validate_fail_count"] == 0


def test_validate_failure_increments_once_per_call() -> None:
    result = BackendResult(output="fail", exit_code=1, elapsed_ms=1)
    pause, updates = apply_post_stage_circuit_breaker(
        stage="validate",
        result=result,
        state={"validate_fail_count": 1},
    )
    assert pause.pause is False
    assert updates["validate_fail_count"] == 2
