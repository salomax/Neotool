"""Pure Slack result formatting for stage outcomes (no Slack SDK dependency)."""
from __future__ import annotations

import re

_STAGE_EMOJI = {
    "stage_issue": "📋",
    "stage_design": "🔍",
    "stage_backlog": "📝",
    "stage_impl": "⚙️",
    "stage_review": "🧐",
    "stage_validate": "🧪",
    "stage_pr": "🚀",
    "question": "💬",
}

_NEXT_STAGE = {
    "stage_issue": "design",
    "stage_design": "backlog",
    "stage_backlog": "impl",
    "stage_impl": "review",
    "stage_review": "validate",
    "stage_validate": "pr",
}

_STATUS_ICON = {
    "completed": "✅",
    "failed": "❌",
    "blocked": "⛔",
    "in_progress": "⏳",
}

_MISSING_ISSUE_RE = re.compile(
    r"preciso do n[uú]mero da issue|"
    r"what is the issue number|"
    r"issue number is required|"
    r"informe no formato\s*#|"
    r"do not proceed without|"
    r"envie no formato\s*#",
    re.IGNORECASE,
)

_DIVIDER = "──────────"


def resolve_stage_status(intent: str, output: str, meta_status: str | None) -> str:
    status = (meta_status or "completed").lower()
    if status in ("completed", "failed", "blocked"):
        if status == "completed" and intent == "stage_impl" and _MISSING_ISSUE_RE.search(output or ""):
            return "blocked"
        if status == "completed" and "⏸️" in (output or "") and "bloquead" in (output or "").lower():
            return "blocked"
        return status
    if intent == "stage_impl" and _MISSING_ISSUE_RE.search(output or ""):
        return "blocked"
    return "completed"


def _format_elapsed(elapsed_ms: int | float | None) -> str:
    """Render elapsed_ms compactly: 'Ys' under a minute, else 'XmYYs'."""
    if elapsed_ms is None:
        return ""
    try:
        total_secs = max(0, int(elapsed_ms) // 1000)
    except (TypeError, ValueError):
        return ""
    mins, secs = divmod(total_secs, 60)
    return f"{mins}m{secs:02d}s" if mins else f"{secs}s"


def _format_elapsed_padded(elapsed_ms: int | float | None) -> str:
    """Render elapsed_ms as 'XmYYs', always including minutes (pipeline rows)."""
    if elapsed_ms is None:
        return "–"
    try:
        total_secs = max(0, int(elapsed_ms) // 1000)
    except (TypeError, ValueError):
        return "–"
    mins, secs = divmod(total_secs, 60)
    return f"{mins}m{secs:02d}s"


def _format_agent_model(agent: str | None, model: str | None) -> str:
    if not agent:
        return ""
    text = f"`{agent}`"
    if model:
        text += f" / `{model}`"
    return text


def format_stage_result(output: str | None, meta: dict, *, max_chars: int = 3800) -> str:
    if output is None:
        return ""

    result = output.strip() or "_(sem resposta)_"
    if len(result) > max_chars:
        result = result[:max_chars] + "\n_(truncado)_"

    intent = meta.get("intent", "")

    if intent == "fast_track":
        # Pipeline output is already a fully-formatted log (see format_pipeline_log,
        # produced by router.execute_plan) — don't wrap it with a single-stage
        # header/footer.
        return result

    agent = meta.get("agent", "")
    model = meta.get("model", "")
    issue = meta.get("issue_number")
    branch = meta.get("branch")
    elapsed = _format_elapsed(meta.get("elapsed_ms"))
    status = resolve_stage_status(intent, result, meta.get("status"))

    header_parts = []
    if intent.startswith("stage_"):
        stage_short = intent.replace("stage_", "")
        if status == "blocked":
            header_parts.append(f"{_STATUS_ICON['blocked']} *{stage_short} bloqueado*")
        elif status == "failed":
            header_parts.append(f"{_STATUS_ICON['failed']} *{stage_short} falhou*")
        else:
            header_parts.append(f"{_STATUS_ICON['completed']} *{stage_short} concluído*")
    agent_model = _format_agent_model(agent, model)
    if agent_model:
        header_parts.append(agent_model)
    if issue:
        header_parts.append(f"issue #{issue}")
    if branch:
        header_parts.append(f"`{branch}`")
    if elapsed:
        header_parts.append(elapsed)

    header = "  ·  ".join(header_parts)
    if status == "blocked":
        footer = (
            "\n\nEnvie o número da issue (`#N`) para retentar "
            "— `sim` **não** avança de estágio."
        )
    elif status == "failed":
        footer = "\n\n`sim` para tentar de novo este estágio"
    else:
        next_s = _NEXT_STAGE.get(intent)
        footer = (
            f"\n\n{_DIVIDER}\n→ próximo: `{next_s}`  —  responda `sim` para continuar"
            if next_s else ""
        )
    return f"{header}\n\n{result}{footer}" if header else result


def format_progress_header(meta: dict, elapsed_secs: int) -> str:
    """Two-line 'start/progress' balloon: agent/model/issue header + timer.

    Posted immediately once dispatch meta is known (elapsed_secs=0), then
    refreshed on each progress tick with the live elapsed time.
    """
    agent = meta.get("agent", "")
    model = meta.get("model", "")
    issue = meta.get("issue_number")
    label = (meta.get("label") or "Trabalhando").lower()

    header_parts = [f"▶️ {label}"]
    agent_model = _format_agent_model(agent, model)
    if agent_model:
        header_parts.append(agent_model)
    if issue:
        header_parts.append(f"issue #{issue}")
    header = "  ·  ".join(header_parts)

    mins, secs = divmod(max(0, int(elapsed_secs)), 60)
    timer = f"⏳ trabalhando…  {mins:02d}:{secs:02d}"
    return f"{header}\n{timer}"


def _pipeline_row_line(row: dict) -> str:
    stage = row.get("stage", "")
    emoji = _STAGE_EMOJI.get(f"stage_{stage}", "•")
    agent = row.get("agent") or ""
    model = row.get("model") or ""
    if model:
        agent_model = f"`{agent}`/`{model}`"
    elif agent:
        agent_model = f"`{agent}`"
    else:
        agent_model = ""
    icon = _STATUS_ICON.get(row.get("status", "completed"), "✅")
    elapsed = _format_elapsed_padded(row.get("elapsed_ms"))

    line = f"{emoji} {stage:<8}·  {agent_model:<18}·  {icon}  {elapsed}"
    link = row.get("link")
    if link:
        label = row.get("link_label") or "link"
        line += f"  →  {label}: {link}"
    return line


def format_pipeline_log(
    rows: list[dict],
    *,
    issue_number: int | None = None,
    paused: str | None = None,
) -> str:
    """Compact per-stage log for a multi-stage (fast_track) pipeline run.

    Each row: {stage, agent, model, status, elapsed_ms, link_label?, link?}.
    """
    header = "🚀 *Pipeline*"
    if issue_number:
        header += f"  ·  issue #{issue_number}"

    lines = [header, ""]
    lines.extend(_pipeline_row_line(row) for row in rows)
    lines.append("")

    if paused:
        lines.append(f"⏸️ {paused}")
    else:
        all_ok = all(row.get("status") == "completed" for row in rows)
        lines.append("✅ *pipeline concluído*" if all_ok else "⛔ *pipeline interrompido*")

    return "\n".join(lines)
