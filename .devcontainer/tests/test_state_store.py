"""Tests for state_store transactional state management."""

import tempfile
from pathlib import Path

import pytest
import state_store


@pytest.fixture
def isolated_state_store():
    """Provide an isolated SQLite state store for testing."""
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = str(Path(tmpdir) / "test.db")
        state_store.set_state_db_path(db_path)
        state_store.clear_all_state()
        yield db_path


def test_load_empty_state(isolated_state_store):
    """Loading non-existent thread returns empty dict."""
    state = state_store.load_state("test-thread-1")
    assert state == {}


def test_update_state_creates_new(isolated_state_store):
    """Updating new thread creates it."""
    state = state_store.update_state("test-thread-1", {"key": "value"})
    assert state == {"key": "value"}


def test_update_state_merges(isolated_state_store):
    """Updating existing thread merges changes."""
    state_store.update_state("test-thread-1", {"a": 1, "b": 2})
    state = state_store.update_state("test-thread-1", {"b": 3, "c": 4})
    assert state == {"a": 1, "b": 3, "c": 4}


def test_update_state_deletes_with_none(isolated_state_store):
    """Setting a value to None deletes the key."""
    state_store.update_state("test-thread-1", {"a": 1, "b": 2})
    state = state_store.update_state("test-thread-1", {"b": None})
    assert state == {"a": 1}


def test_load_after_update(isolated_state_store):
    """State persists across load() calls."""
    state_store.update_state("test-thread-1", {"issue": 123, "stage": "impl"})
    loaded = state_store.load_state("test-thread-1")
    assert loaded == {"issue": 123, "stage": "impl"}


def test_thread_lock_serializes_access(isolated_state_store):
    """get_thread_lock returns consistent lock for same thread."""
    lock1 = state_store.get_thread_lock("test-thread")
    lock2 = state_store.get_thread_lock("test-thread")
    assert lock1 is lock2


def test_different_threads_have_different_locks(isolated_state_store):
    """Different threads get different locks."""
    lock1 = state_store.get_thread_lock("thread-1")
    lock2 = state_store.get_thread_lock("thread-2")
    assert lock1 is not lock2


def test_delete_thread_state(isolated_state_store):
    """delete_thread_state removes all state for a thread."""
    state_store.update_state("test-thread", {"data": "value"})
    state_store.delete_thread_state("test-thread")
    assert state_store.load_state("test-thread") == {}
