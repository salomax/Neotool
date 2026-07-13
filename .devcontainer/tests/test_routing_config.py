"""Tests for check_config intent and agent-default model resolution."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import router
from agents.backend import BackendResult


def test_classify_check_config_via_llm(monkeypatch) -> None:
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")

    def fake_gemini(message: str, state: dict) -> dict:
        return {
            "intent": "check_config",
            "agent": None,
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }

    with patch.object(router, "_classify_gemini", side_effect=fake_gemini):
        result = router.classify("quais são os agentes e models por stage?", {})
    assert result["intent"] == "check_config"


def test_classify_check_config_english_via_llm(monkeypatch) -> None:
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")

    with patch.object(
        router,
        "_classify_gemini",
        return_value={
            "intent": "check_config",
            "agent": None,
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        },
    ):
        result = router.classify("show routing config", {})
    assert result["intent"] == "check_config"


def test_route_check_config_returns_summary() -> None:
    with patch("router.classify") as mock_classify:
        mock_classify.return_value = {
            "intent": "check_config",
            "agent": None,
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
        output = router.route("check-config", "thread-cfg", clean_message=False)

    assert output is not None
    assert "Orchestrator" in output
    assert "stage_impl" in output
    assert "gemini" in output.lower()


def test_dispatch_stage_impl_uses_codex_agent_default_model() -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="ok", exit_code=0, elapsed_ms=100,
    )

    with patch.object(router, "_setup_impl_branch"):
        router.dispatch(
            {"intent": "stage_impl", "agent": None, "model": None, "issue_number": None},
            prompt="implement",
            thread="t-impl",
            backend=mock_backend,
        )

    assert mock_backend.invoke.call_args.kwargs["agent"] == "codex"
    assert mock_backend.invoke.call_args.kwargs["model"] == "gpt-5.4"


def test_dispatch_agent_override_uses_that_agent_default_model() -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="ok", exit_code=0, elapsed_ms=100,
    )

    with patch.object(router, "_setup_impl_branch"):
        router.dispatch(
            # stage_design defaults to claude/sonnet; user forces codex without model
            {"intent": "stage_design", "agent": "codex", "model": None},
            prompt="design",
            thread="t-override",
            backend=mock_backend,
        )

    assert mock_backend.invoke.call_args.kwargs["agent"] == "codex"
    assert mock_backend.invoke.call_args.kwargs["model"] == "gpt-5.4"


def test_classify_gemini_uses_orchestrator_model(monkeypatch) -> None:
    from agents.routing import OrchestratorConfig

    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")
    captured: dict = {}

    class _Resp:
        def read(self):
            return b'{"candidates":[{"content":{"parts":[{"text":"{\\"intent\\":\\"question\\",\\"agent\\":null,\\"model\\":null,\\"issue_number\\":null,\\"fast_track\\":false,\\"stages\\":[],\\"needs_confirmation\\":false,\\"ambiguity_reason\\":null}"}]}}]}'

    def fake_urlopen(req, timeout=10):
        captured["url"] = req.full_url
        return _Resp()

    with patch.object(
        router,
        "get_orchestrator_config",
        return_value=OrchestratorConfig(
            provider="gemini",
            classifier_model="gemini-custom-flash",
            planner_model="gemini-2.5-flash-lite",
        ),
    ), patch("router.urllib.request.urlopen", side_effect=fake_urlopen):
        result = router._classify_gemini("hello", {})

    assert result["intent"] == "question"
    assert "gemini-custom-flash" in captured["url"]
