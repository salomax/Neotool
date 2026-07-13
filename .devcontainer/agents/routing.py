#!/usr/bin/env python3
"""
routing.py — Load stage → agent/model/backend table from routing.yaml.

routing.yaml is the single source of truth. Missing file, missing PyYAML, or
invalid content raises — there is no built-in routing fallback.
"""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

log = logging.getLogger("routing")

ROUTING_FILE = Path(__file__).resolve().parent / "routing.yaml"


@dataclass(frozen=True)
class RouteConfig:
    agent: str
    model: str | None
    backend: str


@dataclass(frozen=True)
class OrchestratorConfig:
    provider: str
    classifier_model: str
    planner_model: str


@dataclass(frozen=True)
class RoutingTable:
    stages: dict[str, RouteConfig]
    orchestrator: OrchestratorConfig
    agent_defaults: dict[str, str] = field(default_factory=dict)
    default_agent: str = ""
    default_backend: str = ""
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
        raise FileNotFoundError(
            f"routing config not found: {path}. "
            "Create agents/routing.yaml or set NEOTOOL_ROUTING_FILE."
        )
    try:
        return _parse_yaml(path.read_text(encoding="utf-8"))
    except RuntimeError:
        raise
    except Exception as exc:
        raise ValueError(f"failed to parse routing config {path}: {exc}") from exc


def _parse_agent_defaults(raw: dict[str, Any]) -> dict[str, str]:
    agents: dict[str, str] = {}
    for name, cfg in (raw.get("agents") or {}).items():
        if not isinstance(cfg, dict):
            continue
        model = cfg.get("default_model")
        if model:
            agents[str(name)] = str(model)
    return agents


def _parse_orchestrator(raw: dict[str, Any]) -> OrchestratorConfig:
    orch = raw.get("orchestrator")
    if not isinstance(orch, dict):
        raise ValueError("routing.yaml must define an orchestrator mapping")

    classifier = orch.get("classifier") or {}
    planner = orch.get("planner") or {}
    if not isinstance(classifier, dict):
        classifier = {}
    if not isinstance(planner, dict):
        planner = {}

    provider = os.environ.get("NEOTOOL_ORCHESTRATOR_PROVIDER") or orch.get("provider")
    classifier_model = (
        os.environ.get("NEOTOOL_CLASSIFIER_MODEL") or classifier.get("model")
    )
    planner_model = os.environ.get("NEOTOOL_PLANNER_MODEL") or planner.get("model")

    if not provider:
        raise ValueError("routing.yaml orchestrator.provider is required")
    if not classifier_model:
        raise ValueError(
            "routing.yaml orchestrator.classifier.model is required "
            "(or set NEOTOOL_CLASSIFIER_MODEL)"
        )
    if not planner_model:
        raise ValueError(
            "routing.yaml orchestrator.planner.model is required "
            "(or set NEOTOOL_PLANNER_MODEL)"
        )

    return OrchestratorConfig(
        provider=str(provider),
        classifier_model=str(classifier_model),
        planner_model=str(planner_model),
    )


def _normalize_table(raw: dict[str, Any]) -> RoutingTable:
    defaults = raw.get("defaults")
    if not isinstance(defaults, dict):
        raise ValueError("routing.yaml must define a defaults mapping")

    default_agent = defaults.get("agent")
    default_backend = defaults.get("backend")
    if not default_agent:
        raise ValueError("routing.yaml defaults.agent is required")
    if not default_backend:
        raise ValueError("routing.yaml defaults.backend is required")

    default_agent = str(default_agent)
    default_backend = str(default_backend)
    default_model = defaults.get("model")
    agent_defaults = _parse_agent_defaults(raw)
    orchestrator = _parse_orchestrator(raw)
    stages_raw = raw.get("stages")
    if not isinstance(stages_raw, dict):
        raise ValueError("routing.yaml must define a stages mapping")

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
    """Load routing.yaml. Raises if the file or PyYAML is unavailable."""
    routing_path = path or Path(
        os.environ.get("NEOTOOL_ROUTING_FILE", str(ROUTING_FILE))
    )
    raw = _load_raw_routing(routing_path)
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
