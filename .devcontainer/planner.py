#!/usr/bin/env python3
"""
planner.py — Claude-powered pipeline planner for autonomous dev-agent runs.
"""
from __future__ import annotations

import json
import logging
import os
import re
import subprocess
from dataclasses import dataclass, field
from typing import Any

log = logging.getLogger("planner")

PIPELINE_STAGES = ["issue", "design", "backlog", "impl", "review", "validate", "pr"]

NO_MERGE_NOTE = "Merge no GitHub é sempre decisão humana — nunca automatizar merge."

PLAN_PROMPT = """\
You are a dev pipeline planner. Given a user request and thread state, return ONLY valid JSON.

User request: {message}
Thread state: {state}
Issue context (if any):
{issue_context}

Return JSON:
{{
  "summary": "one-line plan summary",
  "stages": ["issue", "design", "backlog", "impl", "review", "validate", "pr"],
  "open_questions": ["question needing human input before proceeding, or empty list"],
  "autonomous_eligible": true or false,
  "issue_number": integer or null
}}

Rules:
- "stages" must be an ordered subset of {allowed_stages}, starting from the appropriate point.
- When the user asks for the full pipeline ("tudo", "todas as fases", "de uma vez"), include all stages through "pr".
- Stage "pr" means commit on the feature branch and open a pull request — NEVER merge (merge is always human on GitHub).
- Set "open_questions" when scope, issue number, or approach is unclear.
- "autonomous_eligible" is false when human judgment is required before coding.
- Prefer fewer stages when the user asks for a narrow task.
- If issue_number is known from context, include it.
"""


@dataclass
class Plan:
    summary: str
    stages: list[str]
    open_questions: list[str] = field(default_factory=list)
    autonomous_eligible: bool = False
    issue_number: int | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "summary": self.summary,
            "stages": list(self.stages),
            "open_questions": list(self.open_questions),
            "autonomous_eligible": self.autonomous_eligible,
            "issue_number": self.issue_number,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Plan | None:
        try:
            stages = [str(s) for s in data.get("stages") or []]
            stages = [s for s in stages if s in PIPELINE_STAGES]
            if not stages:
                return None
            questions = [str(q) for q in data.get("open_questions") or [] if str(q).strip()]
            return cls(
                summary=str(data.get("summary") or "Pipeline plan"),
                stages=stages,
                open_questions=questions,
                autonomous_eligible=bool(data.get("autonomous_eligible")),
                issue_number=int(data["issue_number"]) if data.get("issue_number") else None,
            )
        except (TypeError, ValueError, KeyError):
            return None


def parse_plan_response(text: str) -> Plan | None:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        return None
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    return Plan.from_dict(data)


def extract_issue_number_from_text(text: str) -> int | None:
    """Extract GitHub issue number from free text (#359, issue 359, github URL)."""
    if not text:
        return None
    url_match = re.search(r"github\.com/[^/]+/[^/]+/issues/(\d+)", text)
    if url_match:
        return int(url_match.group(1))
    hash_match = re.search(r"(?:issue\s*#?|#)(\d+)", text, re.I)
    if hash_match:
        return int(hash_match.group(1))
    return None


def resolve_plan_issue_number(plan: Plan, state: dict) -> int | None:
    """Resolve issue number from plan field, thread state, or plan summary text."""
    if plan.issue_number:
        return plan.issue_number
    state_number = state.get("issue_number")
    if state_number:
        return int(state_number)
    return extract_issue_number_from_text(plan.summary)


def normalize_plan_issue(plan: Plan, state: dict) -> Plan:
    """Ensure plan.issue_number is set when recoverable from state or summary."""
    resolved = resolve_plan_issue_number(plan, state)
    if resolved and resolved != plan.issue_number:
        return Plan(
            summary=plan.summary,
            stages=plan.stages,
            open_questions=plan.open_questions,
            autonomous_eligible=plan.autonomous_eligible,
            issue_number=resolved,
        )
    return plan


_PLAN_SCHEMA = json.dumps({
    "type": "object",
    "required": ["summary", "stages", "autonomous_eligible"],
    "properties": {
        "summary":             {"type": "string"},
        "stages":              {"type": "array", "items": {"type": "string", "enum": PIPELINE_STAGES}},
        "open_questions":      {"type": "array", "items": {"type": "string"}},
        "autonomous_eligible": {"type": "boolean"},
        "issue_number":        {"type": ["integer", "null"]},
    },
})


def _call_claude_planner(prompt: str) -> str:
    from agents.routing import get_orchestrator_config
    orch = get_orchestrator_config()
    model = orch.planner_model or "claude-haiku-4-5-20251001"

    result = subprocess.run(
        [
            "claude",
            "--model", model,
            "--json-schema", _PLAN_SCHEMA,
            "--output-format", "json",
            "-p", prompt,
            "--dangerously-skip-permissions",
        ],
        capture_output=True,
        text=True,
        timeout=30,
    )
    if result.returncode != 0:
        raise ValueError(f"claude planner exit {result.returncode}: {result.stderr[:200]}")

    for line in result.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            data = json.loads(line)
        except json.JSONDecodeError:
            continue
        if data.get("type") == "result":
            return data.get("result", "")

    raise ValueError(f"No result in claude planner output: {result.stdout[:300]}")


def generate_plan(
    message: str,
    state: dict,
    *,
    issue_context: str = "",
) -> Plan:
    """Generate a pipeline plan via Claude, with a deterministic fallback."""
    prompt = PLAN_PROMPT.format(
        message=message,
        state=json.dumps({k: v for k, v in state.items() if k in ("current_stage", "issue_number")}),
        issue_context=issue_context or "(none)",
        allowed_stages=json.dumps(PIPELINE_STAGES),
    )
    try:
        text = _call_claude_planner(prompt)
        plan = parse_plan_response(text)
        if plan:
            return normalize_plan_issue(plan, state)
        log.warning("planner: no valid JSON in claude response: %s", text[:200])
    except Exception as exc:
        log.warning("planner: claude planner failed: %s", exc)

    return _fallback_plan(message, state)


def _fallback_plan(message: str, state: dict) -> Plan:
    """Rule-based fallback when Gemini is unavailable."""
    issue_number = state.get("issue_number") or extract_issue_number_from_text(message)

    stages = list(PIPELINE_STAGES)
    start_idx = state.get("current_stage", 0)
    if isinstance(start_idx, int) and 0 <= start_idx < len(stages):
        stages = stages[start_idx:]

    open_questions: list[str] = []
    if not issue_number:
        open_questions.append("Which GitHub issue should this pipeline target?")

    return Plan(
        summary="Run pipeline stages sequentially",
        stages=stages,
        open_questions=open_questions,
        autonomous_eligible=not open_questions,
        issue_number=issue_number,
    )


def format_plan_message(plan: Plan, *, reason: str | None = None) -> str:
    lines = ["📋 *Pipeline plan*", "", plan.summary, "", "*Stages:*"]
    for idx, stage in enumerate(plan.stages, start=1):
        lines.append(f"{idx}. `{stage}`")
    if plan.open_questions:
        lines += ["", "*Open questions:*"]
        lines.extend(f"• {q}" for q in plan.open_questions)
    if reason:
        lines += ["", f"_{reason}_"]
    if "pr" in plan.stages:
        lines += ["", f"_{NO_MERGE_NOTE}_"]
    lines += ["", "Responda `sim` para executar o plano, ou descreva o que prefere."]
    return "\n".join(lines)


def stage_to_intent(stage: str) -> str:
    return f"stage_{stage}"
