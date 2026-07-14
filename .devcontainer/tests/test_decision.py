"""Unit tests for pure decision logic."""
from __future__ import annotations

from decision import Action, next_action


def test_check_usage_returns_check_usage_action() -> None:
    """check_usage intent returns a check_usage action."""
    action = next_action({}, {"intent": "check_usage"})
    assert action.kind == "check_usage"
    assert action.intent == "check_usage"
    assert action.issue_number is None
    assert action.needs_confirmation is False


def test_check_config_returns_check_config_action() -> None:
    """check_config intent returns a check_config action."""
    action = next_action({}, {"intent": "check_config"})
    assert action.kind == "check_config"
    assert action.intent == "check_config"
    assert action.issue_number is None


def test_summarize_returns_summarize_action() -> None:
    """summarize intent returns a summarize action."""
    action = next_action({"issue_number": 42}, {"intent": "summarize"})
    assert action.kind == "summarize"
    assert action.intent == "summarize"
    assert action.issue_number == 42


def test_fast_track_returns_fast_track_action() -> None:
    """fast_track intent returns a fast_track action."""
    action = next_action({}, {"intent": "fast_track", "issue_number": 99})
    assert action.kind == "fast_track"
    assert action.intent == "fast_track"
    assert action.issue_number == 99


def test_needs_confirmation_returns_confirm_action() -> None:
    """When needs_confirmation=true, returns confirm action without dispatching."""
    action = next_action(
        {"issue_number": 50},
        {
            "intent": "stage_impl",
            "issue_number": 50,
            "needs_confirmation": True,
            "ambiguity_reason": "Could be impl or backlog",
        }
    )
    assert action.kind == "confirm"
    assert action.intent == "stage_impl"
    assert action.issue_number == 50
    assert action.needs_confirmation is True
    assert action.ambiguity_reason == "Could be impl or backlog"


def test_stage_review_returns_dispatch_review_action() -> None:
    """stage_review intent returns dispatch_review action (uses verifier)."""
    action = next_action(
        {},
        {"intent": "stage_review", "issue_number": 42}
    )
    assert action.kind == "dispatch_review"
    assert action.intent == "stage_review"
    assert action.issue_number == 42
    assert action.needs_confirmation is False


def test_stage_impl_returns_dispatch_stage_action() -> None:
    """stage_impl intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "stage_impl", "issue_number": 42})
    assert action.kind == "dispatch_stage"
    assert action.intent == "stage_impl"
    assert action.issue_number == 42


def test_stage_issue_returns_dispatch_stage_action() -> None:
    """stage_issue intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "stage_issue"})
    assert action.kind == "dispatch_stage"
    assert action.intent == "stage_issue"


def test_stage_design_returns_dispatch_stage_action() -> None:
    """stage_design intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "stage_design", "issue_number": 100})
    assert action.kind == "dispatch_stage"
    assert action.issue_number == 100


def test_stage_backlog_returns_dispatch_stage_action() -> None:
    """stage_backlog intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "stage_backlog"})
    assert action.kind == "dispatch_stage"


def test_stage_validate_returns_dispatch_stage_action() -> None:
    """stage_validate intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "stage_validate"})
    assert action.kind == "dispatch_stage"


def test_stage_pr_returns_dispatch_stage_action() -> None:
    """stage_pr intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "stage_pr", "issue_number": 42})
    assert action.kind == "dispatch_stage"
    assert action.issue_number == 42


def test_question_returns_dispatch_stage_action() -> None:
    """question intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "question"})
    assert action.kind == "dispatch_stage"
    assert action.intent == "question"


def test_advance_returns_dispatch_stage_action() -> None:
    """advance intent returns dispatch_stage action."""
    action = next_action({}, {"intent": "advance"})
    assert action.kind == "dispatch_stage"
    assert action.intent == "advance"


def test_issue_number_from_classification_takes_precedence() -> None:
    """Issue number from classification takes precedence over state."""
    action = next_action(
        {"issue_number": 100},
        {"intent": "stage_impl", "issue_number": 42}
    )
    assert action.issue_number == 42


def test_issue_number_from_state_when_not_in_classification() -> None:
    """Issue number falls back to state when not in classification."""
    action = next_action(
        {"issue_number": 100},
        {"intent": "stage_impl"}
    )
    assert action.issue_number == 100


def test_action_is_frozen() -> None:
    """Action is immutable (frozen dataclass)."""
    action = next_action({}, {"intent": "question"})
    try:
        action.kind = "modified"
        assert False, "Should not be able to modify frozen dataclass"
    except (AttributeError, TypeError):
        pass  # Expected


def test_multiple_decision_calls_are_deterministic() -> None:
    """Multiple calls with same inputs produce identical outputs."""
    state = {"issue_number": 42, "current_stage": 2}
    classification = {"intent": "stage_design", "needs_confirmation": False}
    
    action1 = next_action(state, classification)
    action2 = next_action(state, classification)
    action3 = next_action(state, classification)
    
    assert action1 == action2 == action3


def test_no_side_effects_on_inputs() -> None:
    """next_action does not modify input dicts."""
    state = {"issue_number": 42}
    classification = {"intent": "stage_impl"}
    
    state_copy = dict(state)
    classification_copy = dict(classification)
    
    next_action(state, classification)
    
    assert state == state_copy
    assert classification == classification_copy


def test_default_intent_when_missing() -> None:
    """When intent is missing, defaults to question."""
    action = next_action({}, {})
    assert action.intent == "question"
    assert action.kind == "dispatch_stage"
