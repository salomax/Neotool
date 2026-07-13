"""Unit tests for planner.py."""
from __future__ import annotations

import json
from unittest.mock import patch

from planner import (
    NO_MERGE_NOTE,
    PIPELINE_STAGES,
    Plan,
    _fallback_plan,
    format_plan_message,
    parse_plan_response,
)


def test_parse_plan_response_valid() -> None:
    payload = {
        "summary": "Design and implement",
        "stages": ["design", "backlog", "impl"],
        "open_questions": [],
        "autonomous_eligible": True,
        "issue_number": 369,
    }
    plan = parse_plan_response(json.dumps(payload))
    assert plan is not None
    assert plan.summary == "Design and implement"
    assert plan.stages == ["design", "backlog", "impl"]
    assert plan.autonomous_eligible is True


def test_parse_plan_response_filters_invalid_stages() -> None:
    payload = {
        "summary": "x",
        "stages": ["design", "unknown", "impl"],
        "open_questions": [],
        "autonomous_eligible": True,
    }
    plan = parse_plan_response(json.dumps(payload))
    assert plan is not None
    assert plan.stages == ["design", "impl"]


def test_parse_plan_response_empty_stages_returns_none() -> None:
    plan = parse_plan_response(json.dumps({"summary": "x", "stages": []}))
    assert plan is None


def test_fallback_plan_without_issue_has_open_question() -> None:
    plan = _fallback_plan("faz tudo de uma vez", {})
    assert plan.open_questions
    assert plan.autonomous_eligible is False


def test_fallback_plan_resumes_from_current_stage() -> None:
    plan = _fallback_plan("continua", {"current_stage": 2, "issue_number": 369})
    assert plan.stages[0] == "backlog"
    assert plan.issue_number == 369


def test_pipeline_stages_includes_pr() -> None:
    assert PIPELINE_STAGES[-1] == "pr"
    assert "validate" in PIPELINE_STAGES


def test_parse_plan_response_accepts_pr_stage() -> None:
    plan = parse_plan_response(json.dumps({
        "summary": "Full pipeline",
        "stages": ["design", "impl", "pr"],
        "open_questions": [],
        "autonomous_eligible": True,
    }))
    assert plan is not None
    assert plan.stages == ["design", "impl", "pr"]


def test_fallback_plan_full_pipeline_ends_with_pr() -> None:
    plan = _fallback_plan("faz tudo issue #42", {"issue_number": 42})
    assert plan.stages[-1] == "pr"
    assert plan.stages[0] == "issue"


def test_format_plan_message_notes_no_merge_when_pr_in_plan() -> None:
    plan = Plan(
        summary="Full run",
        stages=["impl", "pr"],
        open_questions=[],
        autonomous_eligible=True,
    )
    text = format_plan_message(plan)
    assert NO_MERGE_NOTE in text


def test_format_plan_message_lists_stages() -> None:
    plan = Plan(
        summary="Run pipeline",
        stages=["design", "backlog"],
        open_questions=["Need scope?"],
        autonomous_eligible=False,
    )
    text = format_plan_message(plan, reason="Needs approval")
    assert "Pipeline plan" in text
    assert "`design`" in text
    assert "Need scope?" in text
    assert "Needs approval" in text


@patch("planner._call_gemini")
def test_generate_plan_uses_gemini(mock_gemini) -> None:
    from planner import generate_plan

    mock_gemini.return_value = json.dumps({
        "summary": "From Gemini",
        "stages": ["design"],
        "open_questions": [],
        "autonomous_eligible": True,
        "issue_number": 1,
    })
    plan = generate_plan("design issue #1", {"issue_number": 1})
    assert plan.summary == "From Gemini"
    assert plan.stages == ["design"]
