"""Pytest configuration and fixtures for router tests."""

import tempfile
from pathlib import Path

import pytest


@pytest.fixture(autouse=True)
def temp_state_store(monkeypatch):
    """Use a temporary SQLite database for state in all tests."""
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = str(Path(tmpdir) / "test-state.db")
        import state_store
        monkeypatch.setattr(state_store, "_STATE_DB", db_path)
        # Also clear any existing state
        state_store.clear_all_state()
        yield db_path
