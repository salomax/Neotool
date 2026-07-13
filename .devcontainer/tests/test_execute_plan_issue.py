"""Tests for issue number propagation in fast_track execute_plan."""
from __future__ import annotations

from unittest.mock import patch

from planner import Plan, normalize_plan_issue, resolve_plan_issue_number


def test_normalize_plan_issue_from_summary() -> None:
    plan = Plan(
        summary="Implement color fix for issue #359",
        stages=["impl"],
        issue_number=None,
    )
    normalized = normalize_plan_issue(plan, {})
    assert normalized.issue_number == 359


def test_resolve_plan_issue_number_prefers_explicit_field() -> None:
    plan = Plan(summary="other #999", stages=["impl"], issue_number=359)
    assert resolve_plan_issue_number(plan, {}) == 359


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.load_state")
def test_execute_plan_impl_prompt_includes_issue_from_summary(
    mock_load,
    mock_fetch,
    mock_write,
    mock_dispatch,
) -> None:
    import router
    from agents.backend import BackendResult

    mock_fetch.return_value = None
    mock_load.return_value = {}
    mock_dispatch.return_value = BackendResult(
        output="done", exit_code=0, elapsed_ms=1,
    )

    plan = Plan(
        summary="Implement MonthSummary color for issue #359",
        stages=["impl"],
        issue_number=None,
    )
    router.execute_plan(plan, "cli-issue-359", {})

    prompt = mock_dispatch.call_args.args[1]
    classification = mock_dispatch.call_args.args[0]
    assert classification["issue_number"] == 359
    assert "/neotool-implement" in prompt
    assert "Issue: #359" in prompt


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.save_state")
@patch("router.load_state")
def test_execute_plan_impl_blocked_output_pauses(
    mock_load,
    mock_save,
    mock_fetch,
    mock_write,
    mock_dispatch,
) -> None:
    import router
    from agents.backend import BackendResult
    from circuit_breaker import PauseDecision

    mock_fetch.return_value = None
    mock_load.return_value = {}
    blocked = (
        "Preciso do número da issue para seguir o fluxo correto dessa implementação."
    )
    mock_dispatch.return_value = BackendResult(
        output=blocked, exit_code=0, elapsed_ms=1,
    )

    plan = Plan(
        summary="Implement something",
        stages=["impl", "review"],
        issue_number=None,
    )
    output = router.execute_plan(plan, "cli-issue-blocked", {})

    assert "bloquead" in output.lower()
    pending_calls = [
        c.args[1] for c in mock_save.call_args_list
        if len(c.args) > 1 and isinstance(c.args[1], dict) and "pending_plan" in c.args[1]
    ]
    assert pending_calls
    assert pending_calls[-1]["pending_plan"]["next_stage_index"] == 0  # stay on impl
