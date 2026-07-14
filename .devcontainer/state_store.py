#!/usr/bin/env python3
"""
state_store.py — Durable, transactional state management for dev-agent orchestrator.

Replaces the /tmp JSON read-modify-write with SQLite for atomicity.
Supports per-thread advisory locks to prevent concurrent stage runs.
"""

import json
import logging
import os
import sqlite3
import threading
from pathlib import Path
from typing import Dict, Optional

log = logging.getLogger("state_store")

# Database path (can be overridden via NEOTOOL_STATE_DB env var)
_STATE_DB = os.environ.get(
    "NEOTOOL_STATE_DB",
    "/tmp/bridge-sessions/state.db"
)

# Per-thread locks: dict of thread_key -> threading.Lock
_THREAD_LOCKS: Dict[str, threading.Lock] = {}
_THREAD_LOCKS_GUARD = threading.Lock()


def _init_db(db_path: str) -> None:
    """Initialize the SQLite database with schema if it doesn't exist."""
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    try:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        conn.execute("""
            CREATE TABLE IF NOT EXISTS state (
                thread_key TEXT PRIMARY KEY,
                state_json TEXT NOT NULL,
                updated_at REAL NOT NULL
            )
        """)
        conn.commit()
    finally:
        conn.close()


def get_thread_lock(thread: str) -> threading.Lock:
    """Return a per-thread lock for serializing stage runs within this process."""
    global _THREAD_LOCKS
    with _THREAD_LOCKS_GUARD:
        if thread not in _THREAD_LOCKS:
            _THREAD_LOCKS[thread] = threading.Lock()
        return _THREAD_LOCKS[thread]


def load_state(thread: str) -> dict:
    """Load state for a thread from the database."""
    _init_db(_STATE_DB)
    try:
        conn = sqlite3.connect(_STATE_DB)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        cursor = conn.execute(
            "SELECT state_json FROM state WHERE thread_key = ?",
            (thread,)
        )
        row = cursor.fetchone()
        conn.close()
        if row:
            return json.loads(row[0]) or {}
        return {}
    except Exception as e:
        log.warning("load_state error for thread=%s: %s", thread, e)
        return {}


def update_state(thread: str, updates: dict) -> dict:
    """
    Atomically merge updates into thread state.
    
    None values delete keys (matching current save_state semantics).
    Returns the merged state after update.
    """
    _init_db(_STATE_DB)
    try:
        conn = sqlite3.connect(_STATE_DB)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        
        # Use BEGIN IMMEDIATE to get an exclusive lock
        conn.execute("BEGIN IMMEDIATE")
        try:
            # Load current state
            cursor = conn.execute(
                "SELECT state_json FROM state WHERE thread_key = ?",
                (thread,)
            )
            row = cursor.fetchone()
            state = json.loads(row[0]) if row else {}
            
            # Apply updates (None = delete key)
            for k, v in updates.items():
                if v is None:
                    state.pop(k, None)
                else:
                    state[k] = v
            
            # Write back
            state_json = json.dumps(state, indent=2)
            import time
            updated_at = time.time()
            
            if row:
                conn.execute(
                    "UPDATE state SET state_json = ?, updated_at = ? WHERE thread_key = ?",
                    (state_json, updated_at, thread)
                )
            else:
                conn.execute(
                    "INSERT INTO state (thread_key, state_json, updated_at) VALUES (?, ?, ?)",
                    (thread, state_json, updated_at)
                )
            
            conn.commit()
            return state
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
    except Exception as e:
        log.warning("update_state error for thread=%s: %s", thread, e)
        # On error, fall back to current state
        return load_state(thread)


def delete_thread_state(thread: str) -> None:
    """Delete all state for a thread."""
    _init_db(_STATE_DB)
    try:
        conn = sqlite3.connect(_STATE_DB)
        conn.execute("DELETE FROM state WHERE thread_key = ?", (thread,))
        conn.commit()
        conn.close()
    except Exception as e:
        log.warning("delete_thread_state error for thread=%s: %s", thread, e)


def clear_all_state() -> None:
    """Clear all state (mainly for testing). WARNING: destructive."""
    _init_db(_STATE_DB)
    try:
        conn = sqlite3.connect(_STATE_DB)
        conn.execute("DELETE FROM state")
        conn.commit()
        conn.close()
        log.info("state store cleared")
    except Exception as e:
        log.warning("clear_all_state error: %s", e)


def set_state_db_path(path: str) -> None:
    """Override the state database path. Call before other operations."""
    global _STATE_DB
    _STATE_DB = path
