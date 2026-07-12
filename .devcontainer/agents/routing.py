#!/usr/bin/env python3
"""
routing.py — Load stage → agent/model/backend table from routing.yaml.
"""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

log = logging.getLogger("routing")

ROUTING_FILE = Path(__file__).resolve().parent / "routing.yaml"

DEFAULT_AGENT_MODELS: dict[str, str] = {
    "claude": "claude-sonnet-4-6",
    "codex": "gpt-5.4",
    "cursor": "auto",
}

DEFAULT_ORCHESTRATOR: dict[str, Any] = {
    "provider": "gemini",
    "classifier": {"model": "gemini-2.5-flash-lite"},
    "planner": {"model": "gemini-2.5-flash-lite"},
}

# Fallback when routing.yaml is missing or PyYAML is unavailable.
BUILTIN_ROUTING: dict[str, Any] = {
    "defaults": {"agent": "claude", "backend": "cli"},
    "agents": {
        "claude": {"default_model": "claude-sonnet-4-6"},
        "codex": {"default_model": "gpt-5.4"},
        "cursor": {"default_model": "auto"},
    },
    "orchestrator": dict(DEFAULT_ORCHESTRATOR),
    "stages": {
        "question": {"agent": "claude"},
        "stage_issue": {"agent": "claude", "model": "claude-haiku-4-5"},
        "stage_design": {"agent": "claude", "model": "claude-sonnet-4-6"},
        "stage_backlog": {"agent": "claude", "model": "claude-haiku-4-5"},
        "stage_impl": {"agent": "codex"},
        "stage_validate": {"agent": "cursor", "model": "auto"},
        "stage_review": {"agent": "claude", "model": "claude-sonnet-4-6"},
        "stage_pr": {"agent": "cursor", "model": "auto"},
        "fast_track": {"agent": "claude", "model": "claude-sonnet-4-6"},
    },
}


@dataclass(frozen=True)
class RouteConfig:
    agent: str
    model: str | None
    backend: str


@dataclass(frozen=True)
class OrchestratorConfig:
    provider: str = "gemini"
    classifier_model: str = "gemini-2.5-flash-lite"
    planner_model: str = "gemini-2.5-flash-lite"


@dataclass(frozen=True)
class RoutingTable:
    stages: dict[str, RouteConfig]
    agent_defaults: dict[str, str] = field(default_factory=lambda: dict(DEFAULT_AGENT_MODELS))
    orchestrator: OrchestratorConfig = field(default_factory=OrchestratorConfig)
    default_agent: str = "claude"
    default_backend: str = "cli"
    default_model: str | None = None


def _parse_yaml(text: str) -> dict[str, Any]:
    try:
        import yaml
    except ImportError as exc:
        raise RuntimeError(
            "PyYAML is required to load .devcontainer/agents/routing.yaml. "
            "On DevPod: sudo apt-get update && sudo apt-get install -y python3-yaml"
        ) from exc
    data = yaml.safe_load(text)
    if not isinstance(data, dict):
        raise ValueError("routing.yaml root must be a mapping")
    return data


def _load_raw_routing(path: Path) -> dict[str, Any]:
    if not path.is_file():
        log.warning("routing file not found: %s — using built-in table", path)
        return BUILTIN_ROUTING
    return _parse_yaml(path.read_text(encoding="utf-8"))


def _parse_agent_defaults(raw: dict[str, Any]) -> dict[str, str]:
    agents = dict(DEFAULT_AGENT_MODELS)
    for name, cfg in (raw.get("agents") or {}).items():
        if not isinstance(cfg, dict):
            continue
        model = cfg.get("default_model")
        if model:
            agents[str(name)] = str(model)
    return agents


def _parse_orchestrator(raw: dict[str, Any]) -> OrchestratorConfig:
    orch = raw.get("orchestrator") or {}
    if not isinstance(orch, dict):
        orch = {}
    classifier = orch.get("classifier") or {}
    planner = orch.get("planner") or {}
    if not isinstance(classifier, dict):
        classifier = {}
    if not isinstance(planner, dict):
        planner = {}

    provider = str(
        os.environ.get("NEOTOOL_ORCHESTRATOR_PROVIDER")
        or orch.get("provider")
        or DEFAULT_ORCHESTRATOR["provider"]
    )
    classifier_model = str(
        os.environ.get("NEOTOOL_CLASSIFIER_MODEL")
        or classifier.get("model")
        or DEFAULT_ORCHESTRATOR["classifier"]["model"]
    )
    planner_model = str(
        os.environ.get("NEOTOOL_PLANNER_MODEL")
        or planner.get("model")
        or DEFAULT_ORCHESTRATOR["planner"]["model"]
    )
    return OrchestratorConfig(
        provider=provider,
        classifier_model=classifier_model,
        planner_model=planner_model,
    )


def _normalize_table(raw: dict[str, Any]) -> RoutingTable:
    defaults = raw.get("defaults") or {}
    default_agent = str(defaults.get("agent", "claude"))
    default_backend = str(defaults.get("backend", "cli"))
    default_model = defaults.get("model")
    agent_defaults = _parse_agent_defaults(raw)
    orchestrator = _parse_orchestrator(raw)
    stages_raw = raw.get("stages") or {}

    stages: dict[str, RouteConfig] = {}
    for intent, cfg in stages_raw.items():
        if cfg is None:
            cfg = {}
        if not isinstance(cfg, dict):
            raise ValueError(f"routing stage {intent!r} must be a mapping")
        agent = str(cfg.get("agent", default_agent))
        model = cfg.get("model")
        if model is None:
            model = agent_defaults.get(agent)
        stages[intent] = RouteConfig(
            agent=agent,
            model=str(model) if model is not None else None,
            backend=str(cfg.get("backend", default_backend)),
        )
    return RoutingTable(
        stages=stages,
        agent_defaults=agent_defaults,
        orchestrator=orchestrator,
        default_agent=default_agent,
        default_backend=default_backend,
        default_model=str(default_model) if default_model is not None else None,
    )


def load_routing_table(path: Path | None = None) -> RoutingTable:
    """Load routing.yaml; fall back to BUILTIN_ROUTING only when the file is missing."""
    routing_path = path or ROUTING_FILE
    try:
        raw = _load_raw_routing(routing_path)
    except RuntimeError:
        raise
    except Exception as exc:
        log.warning("failed to load routing from %s: %s — using built-in table", routing_path, exc)
        raw = BUILTIN_ROUTING
    return _normalize_table(raw)


def resolve_route(intent: str, table: RoutingTable | None = None) -> RouteConfig:
    """Return routing config for intent; unknown intents fall back to table defaults."""
    routing = table if table is not None else load_routing_table()
    if intent in routing.stages:
        return routing.stages[intent]
    agent = routing.default_agent
    model = routing.default_model or routing.agent_defaults.get(agent)
    return RouteConfig(
        agent=agent,
        model=model,
        backend=routing.default_backend,
    )


def resolve_agent_default_model(agent: str, table: RoutingTable | None = None) -> str | None:
    """Return default model for an agent from the routing table."""
    routing = table if table is not None else load_routing_table()
    return routing.agent_defaults.get(agent)


def normalize_agent_override(agent: str | None) -> str | None:
    """Treat null-like classifier values as no user override."""
    if agent is None:
        return None
    if not isinstance(agent, str):
        return None
    cleaned = agent.strip().lower()
    if not cleaned or cleaned in {"null", "none", "undefined", "nil"}:
        return None
    return agent.strip()


def get_orchestrator_config(table: RoutingTable | None = None) -> OrchestratorConfig:
    routing = table if table is not None else load_routing_table()
    return routing.orchestrator


def format_routing_summary(table: RoutingTable | None = None) -> str:
    """Human-readable routing + orchestrator config for check_config answers."""
    routing = table if table is not None else load_routing_table()
    orch = routing.orchestrator
    lines = [
        "📋 *Dev-agent routing config*",
        "",
        "*Orchestrator*",
        f"• provider: `{orch.provider}`",
        f"• classifier: `{orch.classifier_model}`",
        f"• planner: `{orch.planner_model}`",
        "",
        "*Agent defaults*",
    ]
    for agent in sorted(routing.agent_defaults):
        lines.append(f"• `{agent}` → `{routing.agent_defaults[agent]}`")
    lines += ["", "*Stages*"]
    for intent in sorted(routing.stages):
        cfg = routing.stages[intent]
        model = cfg.model or "(none)"
        lines.append(f"• `{intent}` → `{cfg.agent}` / `{model}` ({cfg.backend})")
    lines += [
        "",
        f"_defaults: agent=`{routing.default_agent}` backend=`{routing.default_backend}`_",
        "_Override per message: `@codex …`, `model claude-haiku-4-5 …`_",
        "_Override orchestrator via env: `NEOTOOL_CLASSIFIER_MODEL`, "
        "`NEOTOOL_PLANNER_MODEL`, `NEOTOOL_ORCHESTRATOR_PROVIDER`_",
    ]
    return "\n".join(lines)


_ROUTING_TABLE: RoutingTable | None = None


def get_routing_table(reload: bool = False) -> RoutingTable:
    global _ROUTING_TABLE
    if _ROUTING_TABLE is None or reload:
        _ROUTING_TABLE = load_routing_table()
    return _ROUTING_TABLE
