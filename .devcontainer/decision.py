#!/usr/bin/env python3
"""
decision.py — Pure decision functions for routing classification to actions.

Contains no I/O, no state_store access, no network calls.
Encodes the logic: "given classification and state, what should happen?"
"""
from __future__ import annotations

import logging
from dataclasses import dataclass

log = logging.getLogger("decision")

# Import intent and stage mappings
from state_machine import INTENT_TO_STAGE, STAGE_TO_INTENT


@dataclass(frozen=True)
class Action:
    """Immutable action descriptor produced by next_action() pure function."""
    kind: str  # e.g. "dispatch_stage", "dispatch_review", "confirm", "check_usage", "check_config", "summarize", "fast_track"
    intent: str | None
    issue_number: int | None
    needs_confirmation: bool
    ambiguity_reason: str | None


def next_action(state: dict, classification: dict) -> Action:
    """
    Pure decision function: given state and classification, determine the action.
    
    No I/O, no subprocess, no network, no state_store, no GitHub calls.
    Only inspects input dicts and returns an Action.
    
    Mirrors the branching logic in route() AFTER pending-state handling.
    """
    intent = classification.get("intent", "question")
    issue_number = classification.get("issue_number") or state.get("issue_number")
    needs_confirmation = classification.get("needs_confirmation", False)
    ambiguity_reason = classification.get("ambiguity_reason")
    
    # Short-circuit actions that don't dispatch
    if intent == "check_usage":
        return Action(
            kind="check_usage",
            intent=intent,
            issue_number=None,
            needs_confirmation=False,
            ambiguity_reason=None,
        )
    
    if intent == "check_config":
        return Action(
            kind="check_config",
            intent=intent,
            issue_number=None,
            needs_confirmation=False,
            ambiguity_reason=None,
        )
    
    if intent == "summarize":
        return Action(
            kind="summarize",
            intent=intent,
            issue_number=issue_number,
            needs_confirmation=False,
            ambiguity_reason=None,
        )
    
    if intent == "fast_track":
        return Action(
            kind="fast_track",
            intent=intent,
            issue_number=issue_number,
            needs_confirmation=False,
            ambiguity_reason=None,
        )
    
    # If needs confirmation, don't dispatch yet
    if needs_confirmation:
        return Action(
            kind="confirm",
            intent=intent,
            issue_number=issue_number,
            needs_confirmation=True,
            ambiguity_reason=ambiguity_reason,
        )
    
    # Dispatch decision: review uses verifier, everything else uses finalize
    if intent == "stage_review":
        return Action(
            kind="dispatch_review",
            intent=intent,
            issue_number=issue_number,
            needs_confirmation=False,
            ambiguity_reason=None,
        )
    
    # Default: dispatch as stage
    return Action(
        kind="dispatch_stage",
        intent=intent,
        issue_number=issue_number,
        needs_confirmation=False,
        ambiguity_reason=None,
    )
