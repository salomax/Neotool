"""Unit tests for agents/routing.py."""
from __future__ import annotations

from pathlib import Path

from agents.routing import (
    BUILTIN_ROUTING,
    OrchestratorConfig,
    RouteConfig,
    format_routing_summary,
    get_orchestrator_config,
    load_routing_table,
    resolve_agent_default_model,
    resolve_route,
)


def test_load_routing_table_from_yaml(tmp_path: Path) -> None:
    yaml_path = tmp_path / "routing.yaml"
    yaml_path.write_text(
        (Path(__file__).resolve().parent.parent / "agents" / "routing.yaml").read_text(),
        encoding="utf-8",
    )
    table = load_routing_table(yaml_path)

    assert table.stages["stage_issue"] == RouteConfig(
        agent="claude", model="claude-haiku-4-5", backend="cli",
    )
    assert table.stages["stage_backlog"] == RouteConfig(
        agent="claude", model="claude-haiku-4-5", backend="cli",
    )
    # stage_impl has no model in YAML → agents.codex.default_model
    assert table.stages["stage_impl"] == RouteConfig(
        agent="codex", model="gpt-5.4", backend="cli",
    )
    assert table.stages["question"] == RouteConfig(
        agent="claude", model="claude-sonnet-4-6", backend="cli",
    )
    assert table.stages["stage_pr"] == RouteConfig(
        agent="cursor", model="auto", backend="cli",
    )
    assert table.agent_defaults["claude"] == "claude-sonnet-4-6"
    assert table.orchestrator.classifier_model == "gemini-2.5-flash-lite"
    assert table.orchestrator.planner_model == "gemini-2.5-flash-lite"


def test_resolve_route_unknown_intent_uses_table_defaults(tmp_path: Path) -> None:
    yaml_path = tmp_path / "routing.yaml"
    yaml_path.write_text(
        """
defaults:
  agent: codex
  backend: cli
  model: gpt-custom
agents:
  codex:
    default_model: gpt-5.4
stages:
  stage_issue:
    agent: claude
    model: claude-haiku-4-5
""",
        encoding="utf-8",
    )
    table = load_routing_table(yaml_path)
    cfg = resolve_route("advance", table)
    assert cfg.agent == "codex"
    assert cfg.model == "gpt-custom"
    assert cfg.backend == "cli"


def test_resolve_route_unknown_intent_falls_back_to_agent_default(tmp_path: Path) -> None:
    yaml_path = tmp_path / "routing.yaml"
    yaml_path.write_text(
        """
defaults:
  agent: claude
  backend: cli
agents:
  claude:
    default_model: claude-sonnet-4-6
stages: {}
""",
        encoding="utf-8",
    )
    table = load_routing_table(yaml_path)
    cfg = resolve_route("advance", table)
    assert cfg.agent == "claude"
    assert cfg.model == "claude-sonnet-4-6"


def test_resolve_route_user_agent_override_not_applied_here() -> None:
    table = load_routing_table()
    cfg = resolve_route("stage_impl", table)
    assert cfg.agent == "codex"
    assert cfg.model == "gpt-5.4"


def test_load_routing_table_missing_file_uses_builtin(tmp_path: Path) -> None:
    table = load_routing_table(tmp_path / "missing.yaml")
    assert table.stages["stage_design"].model == BUILTIN_ROUTING["stages"]["stage_design"]["model"]


def test_load_routing_table_custom_stage(tmp_path: Path) -> None:
    yaml_path = tmp_path / "routing.yaml"
    yaml_path.write_text(
        """
defaults:
  agent: claude
  backend: cli
agents:
  claude:
    default_model: claude-sonnet-4-6
stages:
  stage_issue:
    agent: claude
    model: custom-haiku
    backend: cli
""",
        encoding="utf-8",
    )
    table = load_routing_table(yaml_path)
    assert table.stages["stage_issue"].model == "custom-haiku"


def test_orchestrator_env_overrides(tmp_path: Path, monkeypatch) -> None:
    yaml_path = tmp_path / "routing.yaml"
    yaml_path.write_text(
        """
defaults:
  agent: claude
  backend: cli
orchestrator:
  provider: gemini
  classifier:
    model: gemini-2.5-flash-lite
  planner:
    model: gemini-2.5-flash-lite
stages: {}
""",
        encoding="utf-8",
    )
    monkeypatch.setenv("NEOTOOL_CLASSIFIER_MODEL", "gemini-2.0-flash")
    monkeypatch.setenv("NEOTOOL_PLANNER_MODEL", "gemini-2.5-pro")
    monkeypatch.setenv("NEOTOOL_ORCHESTRATOR_PROVIDER", "gemini")
    table = load_routing_table(yaml_path)
    assert table.orchestrator == OrchestratorConfig(
        provider="gemini",
        classifier_model="gemini-2.0-flash",
        planner_model="gemini-2.5-pro",
    )
    assert get_orchestrator_config(table).classifier_model == "gemini-2.0-flash"


def test_format_routing_summary_includes_stages_and_orchestrator() -> None:
    summary = format_routing_summary()
    assert "Orchestrator" in summary
    assert "gemini-2.5-flash-lite" in summary
    assert "stage_impl" in summary
    assert "Agent defaults" in summary
    assert "codex" in summary


def test_resolve_agent_default_model() -> None:
    assert resolve_agent_default_model("codex") == "gpt-5.4"
    assert resolve_agent_default_model("unknown-agent") is None
