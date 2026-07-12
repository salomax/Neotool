#!/usr/bin/env python3
"""
circuit_breaker.py — Pause conditions for autonomous dev-agent execution.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from agents.backend import BackendResult
from planner import Plan

VALIDATE_FAIL_LIMIT = 3
BLOCKER_RE = re.compile(r"blocker\s*:\s*true", re.IGNORECASE)


@dataclass(frozen=True)
class PauseDecision:
    pause: bool
    reason: str | None = None


def should_pause_for_plan(plan: Plan, *, mode: str) -> PauseDecision:
    """Decide whether to pause before executing a plan."""
    if plan.open_questions:
        return PauseDecision(
            pause=True,
            reason="Open questions must be resolved before autonomous execution.",
        )
    if not plan.autonomous_eligible:
        return PauseDecision(
            pause=True,
            reason="This task needs human approval before running autonomously.",
        )
    if mode != "auto":
        return PauseDecision(
            pause=True,
            reason="Interactive mode — approve the plan to continue.",
        )
    return PauseDecision(pause=False)


def detect_blocker(output: str) -> bool:
    return bool(BLOCKER_RE.search(output or ""))


def apply_post_stage_circuit_breaker(
    *,
    stage: str,
    result: BackendResult,
    state: dict,
) -> tuple[PauseDecision, dict[str, Any]]:
    """Evaluate circuit-breakers and return state updates to persist."""
    updates: dict[str, Any] = {}

    if detect_blocker(result.output):
        return PauseDecision(
            pause=True,
            reason=f"Stage `{stage}` reported blocker:true — human input required.",
        ), updates

    if stage == "validate":
        current = int(state.get("validate_fail_count", 0))
        if result.exit_code == 0:
            if current:
                updates["validate_fail_count"] = 0
        else:
            new_count = current + 1
            updates["validate_fail_count"] = new_count
            if new_count >= VALIDATE_FAIL_LIMIT:
                return PauseDecision(
                    pause=True,
                    reason=(
                        f"Validate failed {new_count} times consecutively "
                        f"(limit {VALIDATE_FAIL_LIMIT})."
                    ),
                ), updates

    return PauseDecision(pause=False), updates
