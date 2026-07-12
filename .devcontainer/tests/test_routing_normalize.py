"""Tests for agent override normalization."""
from __future__ import annotations

from agents.routing import normalize_agent_override


def test_normalize_agent_override_null_like_values() -> None:
    assert normalize_agent_override(None) is None
    assert normalize_agent_override("") is None
    assert normalize_agent_override("null") is None
    assert normalize_agent_override("NULL") is None
    assert normalize_agent_override("none") is None


def test_normalize_agent_override_keeps_real_agents() -> None:
    assert normalize_agent_override("claude") == "claude"
    assert normalize_agent_override(" codex ") == "codex"
