"""Unit tests for planner orchestrator model wiring."""
from __future__ import annotations

from unittest.mock import patch

from agents.routing import OrchestratorConfig
from planner import _call_gemini


def test_planner_uses_orchestrator_planner_model(monkeypatch) -> None:
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")
    captured: dict = {}

    class _Resp:
        def read(self):
            return b'{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}'

    def fake_urlopen(req, timeout=15):
        captured["url"] = req.full_url
        return _Resp()

    with patch(
        "agents.routing.get_orchestrator_config",
        return_value=OrchestratorConfig(
            provider="gemini",
            classifier_model="gemini-2.5-flash-lite",
            planner_model="gemini-planner-custom",
        ),
    ), patch("planner.urllib.request.urlopen", side_effect=fake_urlopen):
        text = _call_gemini("plan this")

    assert text == "ok"
    assert "gemini-planner-custom" in captured["url"]
