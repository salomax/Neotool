"""Tests for Codex rollout JSONL token parsing."""
from __future__ import annotations

import json
from pathlib import Path

from agents.codex_session_usage import (
    extract_session_tokens,
    find_rollout_for_session,
    parse_rollout_usage,
)


def _write_rollout(path: Path, session_id: str, events: list[dict]) -> None:
    lines = [
        {
            "type": "session_meta",
            "payload": {"id": session_id, "timestamp": "2026-07-12T00:00:00Z"},
        },
        *events,
    ]
    path.write_text(
        "\n".join(json.dumps(line) for line in lines) + "\n",
        encoding="utf-8",
    )


def test_parse_rollout_last_turn_usage(tmp_path: Path) -> None:
    rollout = tmp_path / "rollout-2026-07-12T00-00-00Z-test.jsonl"
    _write_rollout(
        rollout,
        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        [
            {
                "type": "event_msg",
                "payload": {
                    "type": "token_count",
                    "info": {
                        "last_token_usage": {
                            "input_tokens": 1000,
                            "cached_input_tokens": 200,
                            "output_tokens": 300,
                            "reasoning_output_tokens": 50,
                        },
                        "total_token_usage": {
                            "input_tokens": 5000,
                            "output_tokens": 900,
                        },
                    },
                },
            }
        ],
    )

    usage = parse_rollout_usage(rollout)
    assert usage.input_tokens == 1000
    assert usage.output_tokens == 350  # output + reasoning
    assert usage.cached_input_tokens == 200
    assert usage.source == "last_token_usage"


def test_find_rollout_by_session_id(tmp_path: Path) -> None:
    session_id = "11111111-2222-3333-4444-555555555555"
    sessions = tmp_path / "sessions" / "2026" / "07" / "12"
    sessions.mkdir(parents=True)
    rollout = sessions / f"rollout-2026-07-12T01-00-00Z-{session_id}.jsonl"
    _write_rollout(
        rollout,
        session_id,
        [
            {
                "type": "event_msg",
                "payload": {
                    "type": "token_count",
                    "info": {
                        "last_token_usage": {
                            "input_tokens": 10,
                            "output_tokens": 5,
                        }
                    },
                },
            }
        ],
    )

    found = find_rollout_for_session(session_id, home=tmp_path)
    assert found == rollout

    usage = extract_session_tokens(session_id, home=tmp_path)
    assert usage.input_tokens == 10
    assert usage.output_tokens == 5
