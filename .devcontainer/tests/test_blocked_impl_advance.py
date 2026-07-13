"""Tests for blocked-impl status and awaiting-issue recovery."""
from __future__ import annotations

from unittest.mock import patch

import router
from agents.backend import BackendResult
from slack_format import format_stage_result

BLOCKED_OUTPUT = (
    "Preciso do número da issue para continuar com neotool-implement.\n\n"
    "Envie no formato #42."
)


def test_format_slack_result_blocked_does_not_offer_review() -> None:
    text = format_stage_result(
        BLOCKED_OUTPUT,
        {
            "intent": "stage_impl",
            "agent": "codex",
            "model": "gpt-5.4",
            "status": "blocked",
        },
    )
    assert "bloqueado" in text
    assert "concluído" not in text
    assert "próximo: `review`" not in text
    assert "#N" in text


def test_format_slack_result_detects_blocked_without_status() -> None:
    text = format_stage_result(
        BLOCKED_OUTPUT,
        {"intent": "stage_impl", "agent": "codex", "model": "gpt-5.4"},
    )
    assert "bloqueado" in text
    assert "próximo: `review`" not in text


@patch("router.save_state")
@patch("router.load_state")
def test_sim_while_awaiting_issue_does_not_rerun_impl(mock_load, mock_save) -> None:
    mock_load.return_value = {
        "awaiting_issue_number": True,
        "pending_retry_intent": "stage_impl",
        "current_stage": 3,
    }
    output = router.route("sim", "cli-await-sim", clean_message=False)
    assert output is not None
    assert "número da issue" in output.lower() or "bloqueado" in output.lower()


@patch("router._dispatch_stage_with_finalize")
@patch("router.build_prompt", return_value="impl prompt")
@patch("router.fetch_last_checkpoint", return_value=None)
@patch("router.save_state")
@patch("router.load_state")
def test_issue_number_reply_retries_impl(
    mock_load,
    mock_save,
    mock_fetch,
    mock_prompt,
    mock_dispatch,
) -> None:
    mock_load.return_value = {
        "awaiting_issue_number": True,
        "pending_retry_intent": "stage_impl",
        "current_stage": 3,
    }
    mock_dispatch.return_value = "impl ok"

    router.route("#376", "cli-await-retry", clean_message=False)

    mock_dispatch.assert_called_once()
    clf = mock_dispatch.call_args.args[0]
    assert clf["intent"] == "stage_impl"
    assert clf["issue_number"] == 376
    assert mock_dispatch.call_args.kwargs["issue_number"] == 376


@patch("router.write_checkpoint")
@patch("router.save_state")
@patch("router.load_state")
def test_finalize_blocked_impl_sets_awaiting_flag(
    mock_load,
    mock_save,
    mock_write,
) -> None:
    mock_load.return_value = {"current_stage": 3}
    result = BackendResult(output=BLOCKED_OUTPUT, exit_code=0, elapsed_ms=1)
    meta: list[dict] = []

    pause = router._finalize_stage_run(
        "cli-finalize-block",
        {"intent": "stage_impl", "issue_number": None},
        {"current_stage": 3},
        issue_number=None,
        intent="stage_impl",
        result=result,
        meta_callback=meta.append,
    )

    assert pause is not None and pause.pause
    saved = False
    for call in mock_save.call_args_list:
        updates = call.args[1] if len(call.args) > 1 else call.kwargs
        if isinstance(updates, dict) and updates.get("awaiting_issue_number") is True:
            saved = True
            break
    assert saved
    assert meta and meta[-1]["status"] == "blocked"
