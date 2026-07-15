"""Tests for the Slack mrkdwn formatting helpers in slack_format.py."""
from __future__ import annotations

from slack_format import (
    _format_elapsed,
    _format_elapsed_padded,
    format_pipeline_log,
    format_progress_header,
    format_stage_result,
)


# ── format_stage_result: completion header ──────────────────────────────────


def test_completion_header_has_status_icon_agent_model_elapsed() -> None:
    text = format_stage_result(
        "Resposta do agente",
        {
            "intent": "stage_design",
            "agent": "claude",
            "model": "claude-sonnet-4-6",
            "issue_number": 390,
            "status": "completed",
            "elapsed_ms": 134_000,  # 2m14s
        },
    )
    header = text.splitlines()[0]
    assert "✅" in header
    assert "design concluído" in header
    assert "`claude`" in header
    assert "`claude-sonnet-4-6`" in header
    assert "issue #390" in header
    assert "2m14s" in header


def test_completion_footer_has_divider_before_next_stage() -> None:
    text = format_stage_result(
        "Resposta",
        {"intent": "stage_design", "agent": "claude", "status": "completed"},
    )
    assert "──────────\n→ próximo: `backlog`" in text
    assert "responda `sim` para continuar" in text


def test_elapsed_omitted_gracefully_when_absent() -> None:
    text = format_stage_result(
        "Resposta",
        {"intent": "stage_design", "agent": "claude", "status": "completed"},
    )
    header = text.splitlines()[0]
    # No stray separators / no crash when elapsed_ms is missing.
    assert "None" not in header
    assert not header.endswith("·")


# ── format_stage_result: blocked / failed headers must differ from success ──


def test_blocked_header_differs_from_success() -> None:
    text = format_stage_result(
        "Preciso do número da issue para continuar.",
        {
            "intent": "stage_impl",
            "agent": "codex",
            "model": "gpt-5.4",
            "issue_number": 390,
            "status": "blocked",
        },
    )
    header = text.splitlines()[0]
    assert "⛔" in header
    assert "bloqueado" in header
    assert "concluído" not in text
    assert "próximo" not in text


def test_failed_header_differs_from_success() -> None:
    text = format_stage_result(
        "Erro ao executar.",
        {"intent": "stage_pr", "agent": "cursor", "status": "failed"},
    )
    header = text.splitlines()[0]
    assert "❌" in header
    assert "falhou" in header
    assert "concluído" not in text
    assert "próximo" not in text
    assert "tentar de novo" in text


def test_blocked_and_completed_headers_are_distinct() -> None:
    blocked = format_stage_result(
        "Preciso do número da issue.",
        {"intent": "stage_impl", "agent": "codex", "status": "blocked"},
    )
    completed = format_stage_result(
        "Feito.",
        {"intent": "stage_impl", "agent": "codex", "status": "completed"},
    )
    assert blocked.splitlines()[0] != completed.splitlines()[0]


# ── elapsed helpers ───────────────────────────────────────────────────────


def test_format_elapsed_under_a_minute() -> None:
    assert _format_elapsed(45_000) == "45s"


def test_format_elapsed_over_a_minute() -> None:
    assert _format_elapsed(134_000) == "2m14s"


def test_format_elapsed_none_is_empty() -> None:
    assert _format_elapsed(None) == ""


def test_format_elapsed_padded_always_shows_minutes() -> None:
    assert _format_elapsed_padded(8_000) == "0m08s"
    assert _format_elapsed_padded(62_000) == "1m02s"


# ── format_progress_header (START state) ────────────────────────────────────


def test_progress_header_shows_agent_model_issue_and_timer() -> None:
    text = format_progress_header(
        {
            "intent": "stage_design",
            "agent": "claude",
            "model": "claude-sonnet-4-6",
            "issue_number": 390,
            "label": "Design de solução",
        },
        134,
    )
    lines = text.splitlines()
    assert lines[0].startswith("▶️")
    assert "design de solução" in lines[0]
    assert "`claude`" in lines[0] and "`claude-sonnet-4-6`" in lines[0]
    assert "issue #390" in lines[0]
    assert "02:14" in lines[1]
    assert "trabalhando" in lines[1].lower()


def test_progress_header_without_model_shows_only_agent() -> None:
    text = format_progress_header({"agent": "claude", "label": "question"}, 0)
    header = text.splitlines()[0]
    assert "`claude`" in header
    assert "/" not in header.split("·")[1] if "·" in header else True


# ── format_pipeline_log ──────────────────────────────────────────────────────


def test_pipeline_log_renders_one_row_per_stage_with_agent_model_status_duration() -> None:
    rows = [
        {"stage": "issue", "agent": "claude", "model": "haiku", "status": "completed", "elapsed_ms": 8_000},
        {"stage": "design", "agent": "claude", "model": "sonnet", "status": "completed", "elapsed_ms": 62_000},
        {
            "stage": "impl",
            "agent": "codex",
            "model": "gpt-5.4",
            "status": "completed",
            "elapsed_ms": 148_000,
            "link_label": "comentário",
            "link": "github.com/invistus/invistus/issues/390#issuecomment-1",
        },
        {
            "stage": "pr",
            "agent": "cursor",
            "model": "auto",
            "status": "completed",
            "elapsed_ms": 47_000,
            "link_label": "PR #391",
            "link": "github.com/invistus/invistus/pull/391",
        },
    ]
    text = format_pipeline_log(rows, issue_number=390)

    assert "🚀" in text and "Pipeline" in text
    assert "issue #390" in text
    assert text.count("✅") == 5  # 4 stage rows + terminal line
    assert "issue" in text and "design" in text and "impl" in text and "pr" in text
    assert "`claude`/`haiku`" in text
    assert "0m08s" in text and "1m02s" in text and "2m28s" in text and "0m47s" in text
    assert "comentário: github.com/invistus/invistus/issues/390#issuecomment-1" in text
    assert "PR #391: github.com/invistus/invistus/pull/391" in text
    assert "pipeline concluído" in text


def test_pipeline_log_paused_line_when_blocked_mid_run() -> None:
    rows = [
        {"stage": "issue", "agent": "claude", "model": "haiku", "status": "completed", "elapsed_ms": 8_000},
        {"stage": "impl", "agent": "codex", "model": "gpt-5.4", "status": "blocked", "elapsed_ms": 5_000},
    ]
    text = format_pipeline_log(rows, issue_number=390, paused="Implementação bloqueada — envie #N.")

    assert "⏸️" in text
    assert "bloqueada" in text
    assert "pipeline concluído" not in text
    # Stages completed so far are still listed.
    assert "issue" in text
    assert "impl" in text


def test_pipeline_log_elapsed_missing_does_not_crash() -> None:
    rows = [{"stage": "issue", "agent": "claude", "status": "completed", "elapsed_ms": None}]
    text = format_pipeline_log(rows, issue_number=1)
    assert "issue" in text
    assert "None" not in text


# ── fast_track bypass: pipeline output isn't re-wrapped ─────────────────────


def test_format_stage_result_passes_pipeline_output_through_unchanged() -> None:
    pipeline_text = format_pipeline_log(
        [{"stage": "issue", "agent": "claude", "status": "completed", "elapsed_ms": 1000}],
        issue_number=390,
    )
    wrapped = format_stage_result(
        pipeline_text,
        {"intent": "fast_track", "status": "completed", "issue_number": 390},
    )
    assert wrapped == pipeline_text
