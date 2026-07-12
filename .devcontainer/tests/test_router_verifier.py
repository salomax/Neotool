"""Integration tests for verifier gate + impl retry in router."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import router
from agents.backend import BackendResult
from verifier import VerifierResult


def _fail_verifier() -> VerifierResult:
    return VerifierResult(
        passed=False,
        exit_code=1,
        output="lint failed",
        scopes=("--web",),
    )


def _pass_verifier() -> VerifierResult:
    return VerifierResult(
        passed=True,
        exit_code=0,
        output="ok",
        scopes=("--web",),
    )


@patch("router._dispatch_stage_with_finalize")
@patch("router.run_verifier")
def test_review_runs_verifier_then_review_on_pass(
    mock_verifier,
    mock_dispatch,
) -> None:
    mock_verifier.return_value = _pass_verifier()
    mock_dispatch.return_value = "review output"

    output = router._dispatch_review_with_verifier(
        {"intent": "stage_review", "issue_number": 369},
        "review prompt",
        "thread-v1",
        {},
        issue_number=369,
        verifier_fn=mock_verifier,
    )

    assert output == "review output"
    mock_verifier.assert_called_once()
    mock_dispatch.assert_called_once()
    assert mock_dispatch.call_args.kwargs["intent"] == "stage_review"


@patch("router._dispatch_stage_with_finalize")
@patch("router.run_verifier")
def test_review_retries_impl_then_passes(
    mock_verifier,
    mock_dispatch,
) -> None:
    mock_verifier.side_effect = [_fail_verifier(), _pass_verifier()]
    mock_dispatch.side_effect = ["impl fix", "review output"]

    with patch("router.load_state", return_value={"impl_retry_count": 0}), patch(
        "router.save_state"
    ):
        output = router._dispatch_review_with_verifier(
            {"intent": "stage_review", "issue_number": 369},
            "review prompt",
            "thread-v2",
            {},
            issue_number=369,
            verifier_fn=mock_verifier,
        )

    assert output == "review output"
    assert mock_verifier.call_count == 2
    assert mock_dispatch.call_count == 2
    assert mock_dispatch.call_args_list[0].kwargs["intent"] == "stage_impl"
    assert mock_dispatch.call_args_list[1].kwargs["intent"] == "stage_review"


@patch("router._dispatch_stage_with_finalize")
@patch("router.run_verifier")
def test_review_escalates_after_max_impl_retries(
    mock_verifier,
    mock_dispatch,
) -> None:
    mock_verifier.return_value = _fail_verifier()
    mock_dispatch.return_value = "impl attempt"

    with patch("router.load_state", return_value={"impl_retry_count": 0}), patch(
        "router.save_state"
    ):
        output = router._dispatch_review_with_verifier(
            {"intent": "stage_review", "issue_number": 369},
            "review prompt",
            "thread-v3",
            {},
            issue_number=369,
            verifier_fn=mock_verifier,
        )

    assert output.startswith("⏸️")
    assert "3 impl retries" in output
    assert mock_verifier.call_count == 4  # initial + 3 retries
    assert mock_dispatch.call_count == 3


@patch("router.apply_post_stage_circuit_breaker")
@patch("router.load_state")
@patch("router.save_state")
@patch("router._write_stage_checkpoint")
def test_finalize_stage_run_passes_prior_validate_count(
    mock_write,
    mock_save,
    mock_load,
    mock_breaker,
) -> None:
    from circuit_breaker import PauseDecision

    mock_load.return_value = {"validate_fail_count": 1}
    mock_breaker.return_value = (PauseDecision(pause=False), {"validate_fail_count": 2})
    result = BackendResult(output="fail", exit_code=1, elapsed_ms=1)

    pause = router._finalize_stage_run(
        "thread-f",
        {"intent": "stage_validate", "issue_number": 369},
        {},
        issue_number=369,
        intent="stage_validate",
        result=result,
    )

    assert pause is None
    mock_breaker.assert_called_once()
    passed_state = mock_breaker.call_args.kwargs["state"]
    assert passed_state["validate_fail_count"] == 1
