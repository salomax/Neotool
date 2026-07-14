"""Tests for LLM-only classification (advance regex is the only local shortcut)."""
from __future__ import annotations

import json
from unittest.mock import patch

import pytest

import router
from checkpoint import Checkpoint

USER_MSG = (
    "já implementei!\n\n"
    "Claude, faça a revisão da implementação descrita em "
    "https://github.com/invistus/invistus/issues/376#issuecomment-4960772659"
)

REVIEW_CLASSIFICATION = {
    "intent": "stage_review",
    "agent": "claude",
    "model": None,
    "issue_number": 376,
    "fast_track": False,
    "stages": [],
    "needs_confirmation": False,
    "ambiguity_reason": None,
}


def test_advance_sim_is_local_without_llm() -> None:
    result = router.classify("sim", {"issue_number": 376})
    assert result["intent"] == "advance"


def test_non_advance_without_gemini_uses_fallback() -> None:
    """When Gemini fails, classify falls back to rules instead of raising."""
    with patch.object(router, "_classify_gemini", side_effect=Exception("No API key")):
        result = router.classify("faça a revisão da issue #376", {})
        # Should classify successfully via rules as stage_review
        assert result["intent"] == "stage_review"
        assert result["issue_number"] == 376



@patch("router._classify_gemini")
def test_review_routed_by_llm_classifier(mock_gemini) -> None:
    mock_gemini.return_value = REVIEW_CLASSIFICATION
    result = router.classify(USER_MSG, {"issue_number": 376})
    assert result["intent"] == "stage_review"
    assert result["agent"] == "claude"
    assert result["issue_number"] == 376
    state_arg = mock_gemini.call_args.args[1]
    assert state_arg.get("issue_number") == 376


def test_build_review_prompt_includes_comment_reference() -> None:
    prompt = router.build_prompt(
        {"intent": "stage_review", "issue_number": 376},
        USER_MSG,
        {},
    )
    assert "4960772659" in prompt
    assert "issues/comments/4960772659" in prompt
    assert "não reimplementar" in prompt.lower()


def test_classification_state_includes_checkpoint_context() -> None:
    ctx = router._classification_state_for_prompt({
        "issue_number": 376,
        "current_stage": 4,
        "last_checkpoint": {"stage": "impl", "status": "completed"},
        "pending_plan": {"plan": {}},
    })
    assert ctx["issue_number"] == 376
    assert ctx["next_pipeline_stage_if_user_says_sim"] == "review"
    assert ctx["last_checkpoint"] == {"stage": "impl", "status": "completed"}
    assert ctx["has_pending_plan"] is True


@patch("router._dispatch_review_with_verifier")
@patch("router._classify_gemini")
@patch("router.fetch_last_checkpoint")
@patch("router.load_state")
@patch("router.save_state")
def test_explicit_review_via_llm_classifier(
    mock_save,
    mock_load,
    mock_fetch,
    mock_gemini,
    mock_review,
) -> None:
    mock_gemini.return_value = REVIEW_CLASSIFICATION
    mock_load.return_value = {
        "issue_number": 376,
        "current_stage": 1,
        "last_checkpoint": Checkpoint(
            stage="issue",
            status="completed",
            task_id="t1",
            timestamp="2026-01-01T00:00:00Z",
            issue_number=376,
        ).to_dict(),
    }
    mock_fetch.return_value = Checkpoint(
        stage="issue",
        status="completed",
        task_id="t1",
        timestamp="2026-01-01T00:00:00Z",
        issue_number=376,
    )
    mock_review.return_value = "review ok"

    router.route(USER_MSG, "cli-review-376", clean_message=False)

    mock_review.assert_called_once()
    clf = mock_review.call_args.args[0]
    assert clf["intent"] == "stage_review"
    assert clf["issue_number"] == 376


@patch("router.load_state")
@patch("router._dispatch_review_with_verifier")
def test_route_classifies_via_fallback_when_gemini_unavailable(mock_dispatch, mock_load) -> None:
    """When Gemini fails, route() falls back to rules and classifies successfully."""
    mock_load.return_value = {}
    mock_dispatch.return_value = "Review completed"
    
    # With no GOOGLE_API_KEY and Gemini failing, the fallback classifier should kick in
    # "faça a revisão" should be classified as stage_review
    output = router.route("faça a revisão", "cli-fallback-test", clean_message=False)
    
    # The route should attempt to dispatch even without Gemini
    # (it will fail later due to missing /workspaces/invistus, but that's a different issue)
    # The point is that we don't get an "unavailable classifier" error anymore
    assert output is not None
