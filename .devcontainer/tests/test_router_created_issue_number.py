"""Tests for capturing a freshly-created issue number from stage_issue output.

Bug: when a user asks to create a NEW issue (intent stage_issue) without
giving a number, the created issue's number only ever shows up in the
agent's own output (issue URL / "#N") -- nothing persisted it into thread
state, so later stages (e.g. stage_impl's `Issue: #N` prompt line) never
saw it. router.extract_created_issue_number() + the capture step in
router._finalize_stage_run() close that gap.
"""
from __future__ import annotations

from unittest.mock import patch

import router
from agents.backend import BackendResult


# -- extract_created_issue_number: unit tests ----------------------------------

def test_extract_created_issue_number_from_github_url() -> None:
    output = (
        "Issue created successfully!\n"
        "https://github.com/invistus/invistus/issues/390\n"
    )
    assert router.extract_created_issue_number(output) == 390


def test_extract_created_issue_number_from_created_issue_phrase() -> None:
    assert router.extract_created_issue_number("created issue #390") == 390


def test_extract_created_issue_number_from_criada_phrase() -> None:
    assert router.extract_created_issue_number("issue #390 criada com sucesso") == 390


def test_extract_created_issue_number_from_created_en_phrase() -> None:
    assert router.extract_created_issue_number("issue #390 created") == 390


def test_extract_created_issue_number_from_leading_hash() -> None:
    assert router.extract_created_issue_number("#390\n\nTitle: some bug") == 390


def test_extract_created_issue_number_none_for_pull_url() -> None:
    output = "Opened PR: https://github.com/invistus/invistus/pull/391"
    assert router.extract_created_issue_number(output) is None


def test_extract_created_issue_number_none_for_empty() -> None:
    assert router.extract_created_issue_number("") is None
    assert router.extract_created_issue_number(None) is None


def test_extract_created_issue_number_none_for_no_match() -> None:
    assert router.extract_created_issue_number("all done, nothing to see here") is None


def test_extract_created_issue_number_prefers_issue_url_over_pr_url() -> None:
    output = (
        "Created https://github.com/invistus/invistus/issues/390\n"
        "Related PR (do not use): https://github.com/invistus/invistus/pull/391\n"
    )
    assert router.extract_created_issue_number(output) == 390

    # Order swapped -- PR URL appears first in the text, issue URL after.
    output_swapped = (
        "Related PR (do not use): https://github.com/invistus/invistus/pull/391\n"
        "Created https://github.com/invistus/invistus/issues/390\n"
    )
    assert router.extract_created_issue_number(output_swapped) == 390


# -- Integration: stage_issue dispatch fills the gap ---------------------------

def _stage_issue_classification() -> dict:
    return {
        "intent": "stage_issue",
        "agent": None,
        "model": None,
        "issue_number": None,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_stage_issue_captures_created_number_into_state(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_fetch.return_value = None
    mock_classify.return_value = _stage_issue_classification()
    mock_dispatch_result.return_value = BackendResult(
        output=(
            "Issue created:\n"
            "https://github.com/invistus/invistus/issues/390"
        ),
        exit_code=0,
        elapsed_ms=100,
    )

    thread = "thread-created-issue-1"
    router.route("abra uma issue para o bug X", thread, clean_message=False)

    state = router.load_state(thread)
    assert state.get("issue_number") == 390

    # Terminal checkpoint for the issue stage must carry the captured number.
    assert mock_write.call_count == 1
    kwargs = mock_write.call_args.kwargs
    assert kwargs["status"] == "completed"
    assert mock_write.call_args.args[0] == 390 or kwargs.get("issue_number") == 390


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_stage_issue_does_not_override_known_issue_number(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    """Regression: when the issue number is already known (classifier or user
    typed it), the captured-from-output path must not run/override it."""
    mock_fetch.return_value = None
    clf = _stage_issue_classification()
    clf["issue_number"] = 42
    mock_classify.return_value = clf
    mock_dispatch_result.return_value = BackendResult(
        output=(
            "Enriched issue.\n"
            "https://github.com/invistus/invistus/issues/999"
        ),
        exit_code=0,
        elapsed_ms=100,
    )

    thread = "thread-created-issue-2"
    router.route("abra uma issue #42", thread, clean_message=False)

    state = router.load_state(thread)
    assert state.get("issue_number") == 42


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_stage_issue_no_capture_when_output_has_no_number(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    mock_fetch.return_value = None
    mock_classify.return_value = _stage_issue_classification()
    mock_dispatch_result.return_value = BackendResult(
        output="Something went wrong trying to create the issue.",
        exit_code=0,
        elapsed_ms=100,
    )

    thread = "thread-created-issue-3"
    router.route("abra uma issue para o bug Y", thread, clean_message=False)

    state = router.load_state(thread)
    assert state.get("issue_number") is None


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint")
@patch("router.classify")
def test_stage_issue_no_capture_on_failure(
    mock_classify,
    mock_fetch,
    mock_write,
    mock_dispatch_result,
) -> None:
    """Only capture on successful completion, never on blocked/failed runs."""
    mock_fetch.return_value = None
    mock_classify.return_value = _stage_issue_classification()
    mock_dispatch_result.return_value = BackendResult(
        output="https://github.com/invistus/invistus/issues/500",
        exit_code=1,
        elapsed_ms=100,
    )

    thread = "thread-created-issue-4"
    router.route("abra uma issue para o bug Z", thread, clean_message=False)

    state = router.load_state(thread)
    assert state.get("issue_number") is None
