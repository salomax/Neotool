"""Tests for state_machine stage progression logic."""

import pytest
import state_machine


def test_stage_index():
    """stage_index returns correct indices."""
    assert state_machine.stage_index("issue") == 0
    assert state_machine.stage_index("design") == 1
    assert state_machine.stage_index("impl") == 3
    assert state_machine.stage_index("pr") == 6
    assert state_machine.stage_index("unknown") is None


def test_next_stage_all_pending():
    """next_stage with all pending stages returns first stage."""
    state = {}
    assert state_machine.next_stage(state) == "issue"


def test_next_stage_skip_completed():
    """next_stage skips completed stages."""
    state = {
        "stage_status": {
            "issue": "completed",
            "design": "completed",
            "backlog": "pending",
        }
    }
    assert state_machine.next_stage(state) == "backlog"


def test_next_stage_all_completed():
    """next_stage returns None when all stages completed."""
    state = {
        "stage_status": {
            stage: "completed"
            for stage in state_machine.STAGES
        }
    }
    assert state_machine.next_stage(state) is None


def test_current_stage_index_empty():
    """current_stage_index with empty state is 0."""
    assert state_machine.current_stage_index({}) == 0


def test_current_stage_index_progression():
    """current_stage_index tracks progress through stages."""
    state = {}
    assert state_machine.current_stage_index(state) == 0
    
    state = {
        "stage_status": {"issue": "completed"}
    }
    assert state_machine.current_stage_index(state) == 1
    
    state = {
        "stage_status": {
            "issue": "completed",
            "design": "completed",
            "backlog": "completed",
        }
    }
    assert state_machine.current_stage_index(state) == 3


def test_current_stage_index_all_completed():
    """current_stage_index returns len(STAGES) when all done."""
    state = {
        "stage_status": {
            stage: "completed"
            for stage in state_machine.STAGES
        }
    }
    assert state_machine.current_stage_index(state) == len(state_machine.STAGES)


def test_mark_stage_status():
    """mark_stage_status updates a stage and derives current_stage."""
    state = {}
    updated = state_machine.mark_stage_status(state, "issue", "completed")
    assert updated["stage_status"]["issue"] == "completed"
    assert updated["current_stage"] == 1  # Next stage is design


def test_mark_stage_status_invalid_status():
    """mark_stage_status rejects invalid status."""
    state = {}
    with pytest.raises(ValueError):
        state_machine.mark_stage_status(state, "issue", "invalid_status")


def test_mark_stage_status_invalid_stage():
    """mark_stage_status rejects invalid stage."""
    state = {}
    with pytest.raises(ValueError):
        state_machine.mark_stage_status(state, "unknown_stage", "completed")


def test_init_stage_status():
    """init_stage_status initializes all stages as pending."""
    state = {}
    initialized = state_machine.init_stage_status(state)
    assert len(initialized["stage_status"]) == len(state_machine.STAGES)
    assert all(
        initialized["stage_status"][s] == "pending"
        for s in state_machine.STAGES
    )
    assert initialized["current_stage"] == 0


def test_intent_to_stage_mapping():
    """INTENT_TO_STAGE contains all stage intents."""
    assert state_machine.INTENT_TO_STAGE["stage_issue"] == "issue"
    assert state_machine.INTENT_TO_STAGE["stage_impl"] == "impl"
    assert len(state_machine.INTENT_TO_STAGE) == len(state_machine.STAGES)


def test_stage_to_intent_reverse_mapping():
    """STAGE_TO_INTENT is inverse of INTENT_TO_STAGE."""
    assert state_machine.STAGE_TO_INTENT["issue"] == "stage_issue"
    assert state_machine.STAGE_TO_INTENT["impl"] == "stage_impl"
    assert len(state_machine.STAGE_TO_INTENT) == len(state_machine.STAGES)
