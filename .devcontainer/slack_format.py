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

_MISSING_ISSUE_RE = re.compile(
    r"preciso do n[uú]mero da issue|"
    r"what is the issue number|"
    r"issue number is required|"
    r"informe no formato\s*#|"
    r"do not proceed without|"
    r"envie no formato\s*#",
    re.IGNORECASE,
)


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


def format_stage_result(output: str | None, meta: dict, *, max_chars: int = 3800) -> str:
    if output is None:
        return ""

    result = output.strip() or "_(sem resposta)_"
    if len(result) > max_chars:
        result = result[:max_chars] + "\n_(truncado)_"

    intent = meta.get("intent", "")
    agent = meta.get("agent", "")
    model = meta.get("model", "")
    issue = meta.get("issue_number")
    branch = meta.get("branch")
    status = resolve_stage_status(intent, result, meta.get("status"))

    header_parts = []
    if intent.startswith("stage_"):
        stage_short = intent.replace("stage_", "")
        emoji = _STAGE_EMOJI.get(intent, "✅")
        if status == "blocked":
            header_parts.append(f"{emoji} *{stage_short} bloqueado*")
        elif status == "failed":
            header_parts.append(f"{emoji} *{stage_short} falhou*")
        else:
            header_parts.append(f"{emoji} *{stage_short} concluído*")
    if issue:
        header_parts.append(f"issue #{issue}")
    if branch:
        header_parts.append(f"`{branch}`")
    elif agent:
        a = f"`{agent}`"
        if model:
            a += f" / `{model}`"
        header_parts.append(a)
    if branch and agent:
        a = f"`{agent}`"
        if model:
            a += f" / `{model}`"
        header_parts.append(a)

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
        footer = f"\n\n→ próximo: `{next_s}`  —  `sim` para continuar" if next_s else ""
    return f"{header}\n\n{result}{footer}" if header else result
