"""Integration tests for the compact pipeline log produced by execute_plan()."""
from __future__ import annotations

from unittest.mock import patch

from agents.backend import BackendResult
from planner import Plan


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint", return_value=None)
@patch("router.save_state")
@patch("router.load_state", return_value={})
def test_execute_plan_renders_pipeline_log_with_link(
    mock_load,
    mock_save,
    mock_fetch,
    mock_write,
    mock_dispatch,
) -> None:
    import router

    mock_dispatch.side_effect = [
        BackendResult(
            output="Issue criada: https://github.com/invistus/invistus/issues/390",
            exit_code=0,
            elapsed_ms=8_000,
        ),
        BackendResult(
            output="PR aberto: https://github.com/invistus/invistus/pull/391",
            exit_code=0,
            elapsed_ms=47_000,
        ),
    ]

    plan = Plan(summary="issue #390", stages=["issue", "pr"], issue_number=390)
    output = router.execute_plan(plan, "cli-pipeline-1", {})

    assert "🚀" in output and "Pipeline" in output
    assert "issue #390" in output
    assert "issue" in output and "pr" in output
    assert "0m08s" in output and "0m47s" in output
    assert "PR #391" in output
    assert "pipeline concluído" in output
    # Full per-stage agent output must not be dumped into the collapsed balloon.
    assert "Issue criada" not in output


@patch("router.dispatch_result")
@patch("router.write_checkpoint")
@patch("router.fetch_last_checkpoint", return_value=None)
@patch("router.save_state")
@patch("router.load_state", return_value={})
def test_execute_plan_blocked_impl_pauses_with_log_and_reason(
    mock_load,
    mock_save,
    mock_fetch,
    mock_write,
    mock_dispatch,
) -> None:
    import router

    blocked = "Preciso do número da issue para seguir com a implementação."
    mock_dispatch.return_value = BackendResult(output=blocked, exit_code=0, elapsed_ms=5_000)

    plan = Plan(summary="Implement something", stages=["impl", "review"], issue_number=None)
    output = router.execute_plan(plan, "cli-pipeline-blocked", {})

    assert "🚀" in output and "Pipeline" in output
    assert "⏸️" in output
    assert "bloquead" in output.lower()
    assert "pipeline concluído" not in output


def test_notify_stage_outcome_includes_elapsed_ms() -> None:
    import router

    events: list[dict] = []
    router._notify_stage_outcome(
        events.append,
        intent="stage_design",
        issue_number=390,
        status="completed",
        elapsed_ms=134_000,
    )
    assert events[0]["elapsed_ms"] == 134_000


def test_notify_stage_outcome_omits_elapsed_ms_when_absent() -> None:
    import router

    events: list[dict] = []
    router._notify_stage_outcome(
        events.append,
        intent="stage_design",
        issue_number=390,
        status="completed",
    )
    assert "elapsed_ms" not in events[0]


def test_extract_stage_link_pr() -> None:
    import router

    label, link = router._extract_stage_link(
        "pr", "Aberto: https://github.com/invistus/invistus/pull/391"
    )
    assert label == "PR #391"
    assert "pull/391" in link


def test_extract_stage_link_impl_comment() -> None:
    import router

    label, link = router._extract_stage_link(
        "impl",
        "Comentei em https://github.com/invistus/invistus/issues/390#issuecomment-42",
    )
    assert label == "comentário"
    assert "issuecomment-42" in link


def test_extract_stage_link_none_when_absent() -> None:
    import router

    assert router._extract_stage_link("pr", "sem link aqui") == (None, None)
    assert router._extract_stage_link("design", "qualquer coisa") == (None, None)
