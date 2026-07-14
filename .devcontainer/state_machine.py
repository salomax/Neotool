#!/usr/bin/env python3
"""
state_machine.py — Pure decision functions for pipeline stage progression.

Eliminates the hardcoded stage_map and clarifies stage state semantics.
Stages are stored as explicit status per stage (pending/in_progress/completed/failed),
with a backward-compatible current_stage integer derived from them.
"""

from __future__ import annotations

import logging
from typing import Optional

log = logging.getLogger("state_machine")

# Canonical list of pipeline stages — SINGLE SOURCE OF TRUTH
STAGES = ["issue", "design", "backlog", "impl", "review", "validate", "pr"]

# Map from stage intent to canonical stage name
INTENT_TO_STAGE = {
    "stage_issue": "issue",
    "stage_design": "design",
    "stage_backlog": "backlog",
    "stage_impl": "impl",
    "stage_review": "review",
    "stage_validate": "validate",
    "stage_pr": "pr",
}

STAGE_TO_INTENT = {v: k for k, v in INTENT_TO_STAGE.items()}

# Valid stage status values
VALID_STATUSES = frozenset({"pending", "in_progress", "completed", "failed"})


def stage_index(stage: str) -> Optional[int]:
    """Return 0-based index of stage in STAGES, or None if not found."""
    try:
        return STAGES.index(stage)
    except ValueError:
        return None


def next_stage(state: dict) -> Optional[str]:
    """Determine the next stage to run based on current state.
    
    Returns the name of the first stage that is not completed, or None if all are done.
    """
    stage_status = state.get("stage_status", {})
    for stage in STAGES:
        status = stage_status.get(stage, "pending")
        if status != "completed":
            return stage
    return None


def current_stage_index(state: dict) -> int:
    """
    Backward-compatible: derive current_stage as an integer (0-based).
    
    - 0 = issue (first stage)
    - 1 = design
    - ...
    - 7 = after pr (all done)
    
    This is computed from stage_status for consistency.
    """
    stage_status = state.get("stage_status", {})
    # Find the first non-completed stage and return its index
    for i, stage in enumerate(STAGES):
        status = stage_status.get(stage, "pending")
        if status != "completed":
            return i
    # All stages completed
    return len(STAGES)


def mark_stage_status(
    state: dict,
    stage: str,
    status: str,
) -> dict:
    """Update the status of a single stage. Returns updated state."""
    if status not in VALID_STATUSES:
        raise ValueError(f"Invalid status {status}; must be one of {VALID_STATUSES}")
    if stage not in STAGES:
        raise ValueError(f"Invalid stage {stage}; must be one of {STAGES}")
    
    updated = dict(state)
    if "stage_status" not in updated:
        updated["stage_status"] = {}
    updated["stage_status"][stage] = status
    # Derive the backward-compatible current_stage integer
    updated["current_stage"] = current_stage_index(updated)
    return updated


def init_stage_status(state: dict) -> dict:
    """Ensure stage_status dict exists in state, initialized to all pending."""
    if "stage_status" not in state:
        state = dict(state)
        state["stage_status"] = {stage: "pending" for stage in STAGES}
        state["current_stage"] = 0
    return state
