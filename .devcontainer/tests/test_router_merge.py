"""Tests for explicit merge (stage_merge) dispatch."""
from __future__ import annotations

from unittest.mock import patch

import router
from agents.backend import BackendResult


def _success_result(output: str = "merged") -> BackendResult:
    return BackendResult(output=output, exit_code=0, elapsed_ms=100)


@patch("router._append_thread_summary")
@patch("router._finalize_stage_run")
@patch("router.dispatch_result")
@patch("router.classify")
@patch("router.load_state")
def test_explicit_merge_dispatches_cursor(
    mock_load,
    mock_classify,
    mock_dispatch,
    mock_finalize,
    mock_summary,
) -> None:
    mock_load.return_value = {"issue_number": 359}
    mock_classify.return_value = {
        "intent": "stage_merge",
        "agent": None,
        "model": None,
        "issue_number": 359,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    mock_dispatch.return_value = _success_result("PR merged")
    mock_finalize.return_value = None
    mock_summary.return_value = "merged output\n\n---\nsummary"

    output = router.route("faça o merge", "cli-merge-1", clean_message=False)

    dispatch_clf = mock_dispatch.call_args.args[0]
    assert dispatch_clf["intent"] == "stage_merge"
    agent, model, _ = router._resolve_dispatch_targets(dispatch_clf)
    assert agent == "cursor"
    assert model == "auto"
    prompt = mock_dispatch.call_args.args[1]
    assert "gh pr merge" in prompt
    assert "explicitamente" in prompt.lower() or "explicit" in prompt.lower()
    assert output is not None
    mock_summary.assert_called_once()


def test_classify_merge_via_llm(monkeypatch) -> None:
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")
    with patch.object(
        router,
        "_classify_gemini",
        return_value={
            "intent": "stage_merge",
            "agent": None,
            "model": None,
            "issue_number": 359,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        },
    ):
        result = router.classify("faça o merge", {"issue_number": 359})
    assert result["intent"] == "stage_merge"


def test_fast_track_planner_stages_exclude_merge() -> None:
    from planner import PIPELINE_STAGES

    assert "pr" in PIPELINE_STAGES
    assert "merge" not in PIPELINE_STAGES
