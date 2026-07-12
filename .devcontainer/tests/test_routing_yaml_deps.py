"""Tests for routing.yaml availability via system PyYAML."""
from __future__ import annotations

import pytest

from agents.routing import ROUTING_FILE, load_routing_table


def test_routing_yaml_loads_from_repo_file() -> None:
    pytest.importorskip("yaml")
    assert ROUTING_FILE.is_file()
    table = load_routing_table(ROUTING_FILE)
    assert table.stages["stage_issue"].model == "claude-haiku-4-5"


def test_yaml_import_available() -> None:
    yaml = pytest.importorskip("yaml")
    assert yaml.safe_load("agent: claude") == {"agent": "claude"}
