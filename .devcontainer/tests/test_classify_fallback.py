"""Tests for rule-based classification fallback when Gemini is unavailable."""
from __future__ import annotations

from unittest.mock import patch

import router


def test_classify_fallback_on_gemini_exception() -> None:
    """When Gemini raises, classify should use rules instead."""
    with patch.object(router, "_classify_gemini", side_effect=Exception("API error")):
        result = router.classify("implemente o backlog", {})
        # "implemente" should be classified as implementation (stage_impl)
        assert result["intent"] == "stage_impl"
        assert result["needs_confirmation"] is False


def test_classify_rules_explicit_agent_codex() -> None:
    """@codex override should be extracted."""
    result = router._classify_rules("@codex faça a revisão da issue #376", {})
    assert result["intent"] == "stage_review"
    assert result["agent"] == "codex"
    assert result["issue_number"] == 376
    assert result["needs_confirmation"] is False


def test_classify_rules_explicit_agent_claude() -> None:
    """use claude should set agent."""
    result = router._classify_rules("use claude para revisar o código", {})
    assert result["agent"] == "claude"
    assert result["needs_confirmation"] is False


def test_classify_rules_explicit_agent_cursor() -> None:
    """@cursor should be recognized."""
    result = router._classify_rules("@cursor implemente a feature", {})
    assert result["agent"] == "cursor"
    assert result["intent"] == "stage_impl"


def test_classify_rules_model_override() -> None:
    """Model overrides (model X, gpt-X, sonnet-X) should be extracted."""
    result1 = router._classify_rules("model claude-sonnet-4-6 implemente", {})
    assert result1["model"] == "claude-sonnet-4-6"
    
    result2 = router._classify_rules("sonnet-4 design da solução", {})
    assert result2["model"] == "4"


def test_classify_rules_check_usage() -> None:
    """Check usage patterns should be recognized."""
    result1 = router._classify_rules("check-usage", {})
    assert result1["intent"] == "check_usage"
    
    result2 = router._classify_rules("quanto gastei", {})
    assert result2["intent"] == "check_usage"
    
    result3 = router._classify_rules("token usage", {})
    assert result3["intent"] == "check_usage"


def test_classify_rules_check_config() -> None:
    """Check config patterns should be recognized."""
    result1 = router._classify_rules("config", {})
    assert result1["intent"] == "check_config"
    
    result2 = router._classify_rules("quais agentes estão configurados", {})
    assert result2["intent"] == "check_config"


def test_classify_rules_fast_track() -> None:
    """Fast track patterns should set fast_track=true."""
    result1 = router._classify_rules("de uma vez issue #42", {})
    assert result1["intent"] == "fast_track"
    assert result1["fast_track"] is True
    assert result1["issue_number"] == 42
    
    result2 = router._classify_rules("tudo junto", {})
    assert result2["intent"] == "fast_track"


def test_classify_rules_stage_issue() -> None:
    """Issue creation patterns should be recognized."""
    result1 = router._classify_rules("abra uma issue", {})
    assert result1["intent"] == "stage_issue"
    
    result2 = router._classify_rules("crie uma nova issue", {})
    assert result2["intent"] == "stage_issue"
    
    result3 = router._classify_rules("open an issue", {})
    assert result3["intent"] == "stage_issue"


def test_classify_rules_stage_design() -> None:
    """Design patterns should be recognized."""
    result1 = router._classify_rules("design da solução", {})
    assert result1["intent"] == "stage_design"
    
    result2 = router._classify_rules("análise técnica", {})
    assert result2["intent"] == "stage_design"
    
    result3 = router._classify_rules("qual o impacto", {})
    assert result3["intent"] == "stage_design"


def test_classify_rules_stage_backlog() -> None:
    """Backlog patterns should be recognized."""
    result1 = router._classify_rules("backlog", {})
    assert result1["intent"] == "stage_backlog"
    
    result2 = router._classify_rules("quebra em tarefas", {})
    assert result2["intent"] == "stage_backlog"
    
    result3 = router._classify_rules("gera as tasks", {})
    assert result3["intent"] == "stage_backlog"


def test_classify_rules_stage_impl() -> None:
    """Implementation patterns should be recognized."""
    result1 = router._classify_rules("implemente o backlog", {})
    assert result1["intent"] == "stage_impl"
    
    result2 = router._classify_rules("escreva o código", {})
    assert result2["intent"] == "stage_impl"


def test_classify_rules_stage_review() -> None:
    """Review patterns should be recognized."""
    result1 = router._classify_rules("faça a revisão da issue #376", {})
    assert result1["intent"] == "stage_review"
    assert result1["issue_number"] == 376
    
    result2 = router._classify_rules("revise o código", {})
    assert result2["intent"] == "stage_review"


def test_classify_rules_stage_validate() -> None:
    """Validation patterns should be recognized."""
    result1 = router._classify_rules("rode os testes", {})
    assert result1["intent"] == "stage_validate"
    
    result2 = router._classify_rules("lint", {})
    assert result2["intent"] == "stage_validate"


def test_classify_rules_stage_pr() -> None:
    """PR patterns should be recognized."""
    result1 = router._classify_rules("faz o commit", {})
    assert result1["intent"] == "stage_pr"
    
    result2 = router._classify_rules("abra o PR", {})
    assert result2["intent"] == "stage_pr"


def test_classify_rules_default_question() -> None:
    """Unknown patterns should default to question."""
    result = router._classify_rules("o que você acha?", {})
    assert result["intent"] == "question"


def test_classify_rules_extracts_issue_number() -> None:
    """Issue numbers should be extracted from various formats."""
    result1 = router._classify_rules("faça a revisão #42", {})
    assert result1["issue_number"] == 42
    
    result2 = router._classify_rules("issue 376 design", {})
    assert result2["issue_number"] == 376


def test_classify_rules_always_sets_needs_confirmation_false() -> None:
    """Fallback should never set needs_confirmation=true."""
    examples = [
        "@codex implemente",
        "design",
        "pode criar?",
        "hmm maybe design?",
        "anything ambiguous",
    ]
    for msg in examples:
        result = router._classify_rules(msg, {})
        assert result["needs_confirmation"] is False, f"Failed for: {msg}"


def test_classify_rules_returns_proper_shape() -> None:
    """Fallback should return all required fields."""
    result = router._classify_rules("teste", {})
    required_keys = {
        "intent", "agent", "model", "issue_number", 
        "fast_track", "stages", "needs_confirmation", "ambiguity_reason"
    }
    assert set(result.keys()) == required_keys


def test_classify_no_exception_when_no_api_key() -> None:
    """classify() should not raise when GOOGLE_API_KEY is unset."""
    # Remove GOOGLE_API_KEY if it exists
    import os
    old_key = os.environ.pop("GOOGLE_API_KEY", None)
    try:
        result = router.classify("implemente", {})
        assert result["intent"] == "stage_impl"
        assert result["needs_confirmation"] is False
    finally:
        if old_key is not None:
            os.environ["GOOGLE_API_KEY"] = old_key
