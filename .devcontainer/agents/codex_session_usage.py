#!/usr/bin/env python3
"""
codex_session_usage.py — Read token usage from Codex CLI rollout JSONL files.

Codex persists session telemetry under $CODEX_HOME/sessions/YYYY/MM/DD/
rollout-*.jsonl. Each file contains event_msg/token_count entries with
last_token_usage (per turn) and total_token_usage (cumulative).

See: https://community.openai.com/t/how-can-we-clearly-see-token-usage-per-codex-conversation-task-especially-in-codex-web/1381431
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class CodexTokenUsage:
    input_tokens: int = 0
    output_tokens: int = 0
    cached_input_tokens: int = 0
    reasoning_output_tokens: int = 0
    source: str = "none"
    rollout_path: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def codex_home() -> Path:
    return Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))


def _usage_from_block(block: dict[str, Any] | None) -> CodexTokenUsage:
    if not block:
        return CodexTokenUsage()
    output = int(block.get("output_tokens") or 0)
    reasoning = int(block.get("reasoning_output_tokens") or 0)
    return CodexTokenUsage(
        input_tokens=int(block.get("input_tokens") or 0),
        output_tokens=output + reasoning,
        cached_input_tokens=int(block.get("cached_input_tokens") or 0),
        reasoning_output_tokens=reasoning,
    )


def read_session_id_from_rollout(path: Path) -> str | None:
    try:
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if obj.get("type") == "session_meta":
                    payload = obj.get("payload") or {}
                    session_id = payload.get("id")
                    if session_id:
                        return str(session_id)
    except OSError:
        return None
    return None


def find_rollout_for_session(
    session_id: str,
    *,
    home: Path | None = None,
) -> Path | None:
    """Locate rollout JSONL for a Codex session UUID."""
    root = home or codex_home()
    sessions_dir = root / "sessions"
    if not sessions_dir.is_dir():
        return None

    # Filename often ends with the session UUID.
    matches = list(sessions_dir.glob(f"**/rollout-*-{session_id}.jsonl"))
    if matches:
        return max(matches, key=lambda p: p.stat().st_mtime)

    # Fallback: scan session_meta.id in recent rollout files.
    candidates = sorted(
        sessions_dir.glob("**/rollout-*.jsonl"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    for path in candidates[:100]:
        if read_session_id_from_rollout(path) == session_id:
            return path
    return None


def find_latest_rollout(*, home: Path | None = None, modified_after: float | None = None) -> Path | None:
    root = home or codex_home()
    sessions_dir = root / "sessions"
    if not sessions_dir.is_dir():
        return None
    candidates = sorted(
        sessions_dir.glob("**/rollout-*.jsonl"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    for path in candidates:
        if modified_after is not None and path.stat().st_mtime < modified_after:
            continue
        return path
    return None


def parse_rollout_usage(path: Path) -> CodexTokenUsage:
    """Extract token usage from the last token_count event in a rollout file."""
    last_turn: dict[str, Any] | None = None
    last_total: dict[str, Any] | None = None
    try:
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if obj.get("type") != "event_msg":
                    continue
                payload = obj.get("payload") or {}
                if payload.get("type") != "token_count":
                    continue
                info = payload.get("info") or {}
                turn = info.get("last_token_usage")
                total = info.get("total_token_usage")
                if isinstance(turn, dict):
                    last_turn = turn
                if isinstance(total, dict):
                    last_total = total
    except OSError:
        return CodexTokenUsage(source="unreadable", rollout_path=str(path))

    if last_turn:
        usage = _usage_from_block(last_turn)
        return CodexTokenUsage(
            input_tokens=usage.input_tokens,
            output_tokens=usage.output_tokens,
            cached_input_tokens=usage.cached_input_tokens,
            reasoning_output_tokens=usage.reasoning_output_tokens,
            source="last_token_usage",
            rollout_path=str(path),
        )

    if last_total:
        usage = _usage_from_block(last_total)
        return CodexTokenUsage(
            input_tokens=usage.input_tokens,
            output_tokens=usage.output_tokens,
            cached_input_tokens=usage.cached_input_tokens,
            reasoning_output_tokens=usage.reasoning_output_tokens,
            source="total_token_usage",
            rollout_path=str(path),
        )

    return CodexTokenUsage(source="none", rollout_path=str(path))


def diff_usage(current: CodexTokenUsage, baseline: CodexTokenUsage) -> CodexTokenUsage:
    return CodexTokenUsage(
        input_tokens=max(0, current.input_tokens - baseline.input_tokens),
        output_tokens=max(0, current.output_tokens - baseline.output_tokens),
        cached_input_tokens=max(0, current.cached_input_tokens - baseline.cached_input_tokens),
        reasoning_output_tokens=max(
            0,
            current.reasoning_output_tokens - baseline.reasoning_output_tokens,
        ),
        source="delta_total_token_usage",
        rollout_path=current.rollout_path,
    )


def extract_session_tokens(
    session_id: str | None = None,
    *,
    baseline: CodexTokenUsage | None = None,
    modified_after: float | None = None,
    home: Path | None = None,
) -> CodexTokenUsage:
    path: Path | None = None
    if session_id:
        path = find_rollout_for_session(session_id, home=home)
    if path is None:
        path = find_latest_rollout(home=home, modified_after=modified_after)
    if path is None:
        return CodexTokenUsage(source="no_rollout")

    current = parse_rollout_usage(path)
    if (
        baseline is not None
        and current.source == "total_token_usage"
        and (current.input_tokens or current.output_tokens)
    ):
        return diff_usage(current, baseline)
    return current


def _parse_baseline_json(raw: str) -> CodexTokenUsage | None:
    if not raw.strip():
        return None
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return None
    return CodexTokenUsage(
        input_tokens=int(data.get("input_tokens") or 0),
        output_tokens=int(data.get("output_tokens") or 0),
        cached_input_tokens=int(data.get("cached_input_tokens") or 0),
        reasoning_output_tokens=int(data.get("reasoning_output_tokens") or 0),
        source=str(data.get("source") or "baseline"),
        rollout_path=data.get("rollout_path"),
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Read Codex session token usage from rollout JSONL")
    parser.add_argument("--session", help="Codex session UUID")
    parser.add_argument(
        "--latest-after",
        type=float,
        help="Pick newest rollout modified after this Unix timestamp",
    )
    parser.add_argument(
        "--baseline-json",
        help="JSON baseline totals for delta on resumed sessions",
    )
    parser.add_argument(
        "--baseline",
        action="store_true",
        help="With --session, print cumulative totals before a new turn",
    )
    args = parser.parse_args(argv)

    if args.baseline:
        if not args.session:
            print(json.dumps(CodexTokenUsage(source="missing_session").to_dict()))
            return 1
        path = find_rollout_for_session(args.session)
        if not path:
            print(json.dumps(CodexTokenUsage(source="no_rollout").to_dict()))
            return 0
        usage = parse_rollout_usage(path)
        # For baseline we want cumulative totals when available.
        if usage.source == "last_token_usage":
            # Re-read preferring total if we only got last turn.
            total = CodexTokenUsage(source="none", rollout_path=str(path))
            try:
                with path.open(encoding="utf-8") as fh:
                    last_total = None
                    for line in fh:
                        try:
                            obj = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        if obj.get("type") != "event_msg":
                            continue
                        payload = obj.get("payload") or {}
                        if payload.get("type") != "token_count":
                            continue
                        info = payload.get("info") or {}
                        if isinstance(info.get("total_token_usage"), dict):
                            last_total = info["total_token_usage"]
                if last_total:
                    block = _usage_from_block(last_total)
                    total = CodexTokenUsage(
                        input_tokens=block.input_tokens,
                        output_tokens=block.output_tokens,
                        cached_input_tokens=block.cached_input_tokens,
                        reasoning_output_tokens=block.reasoning_output_tokens,
                        source="total_token_usage",
                        rollout_path=str(path),
                    )
                    usage = total
            except OSError:
                pass
        print(json.dumps(usage.to_dict()))
        return 0

    baseline = _parse_baseline_json(args.baseline_json or "")
    usage = extract_session_tokens(
        args.session,
        baseline=baseline,
        modified_after=args.latest_after,
    )
    print(json.dumps(usage.to_dict()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
