#!/usr/bin/env python3
"""
cli_adapter.py — CLI entry point for dev-agent tasks.

Powers: neotool dev agent -m "<message>"
"""
from __future__ import annotations

import argparse
import getpass
import os
import sys
import uuid
from pathlib import Path

# Ensure .devcontainer is on sys.path when invoked directly
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from core import Task, handle_task


def parse_argv(argv: list[str] | None = None) -> Task:
    parser = argparse.ArgumentParser(
        description="Run a dev-agent task from the CLI",
    )
    parser.add_argument(
        "-m", "--message",
        required=True,
        help="Task message (same as a Slack message to @dev-agent)",
    )
    parser.add_argument(
        "--issue",
        type=int,
        default=None,
        help="GitHub issue number (optional)",
    )
    parser.add_argument(
        "--agent",
        choices=("claude", "codex", "cursor"),
        default=None,
        help="Agent hint (optional)",
    )
    parser.add_argument(
        "--task-id",
        default=None,
        help="Override task ID (default: cli-<uuid>)",
    )
    parser.add_argument(
        "--auto",
        action="store_true",
        help="Run autonomously (skip plan approval; circuit-breakers still apply)",
    )
    parser.add_argument(
        "--requester",
        default=None,
        help="Requester identifier (default: $USER)",
    )

    args = parser.parse_args(argv)
    task_id = args.task_id or f"cli-{uuid.uuid4()}"
    requester = args.requester or os.environ.get("USER") or getpass.getuser()

    return Task(
        message=args.message,
        task_id=task_id,
        issue_ref=args.issue,
        agent_hint=args.agent,
        mode="auto" if args.auto else "interactive",
        requester=requester,
        adapter_env={},
    )


def run(argv: list[str] | None = None) -> int:
    task = parse_argv(argv)

    print(f"task_id: {task.task_id}", file=sys.stderr, flush=True)

    def on_progress(status: str) -> None:
        print(f"⏳ {status}", flush=True)

    def on_result(output: str | None, meta: dict) -> None:
        if output is None:
            return
        text = output.strip() or "_(no response)_"
        intent = meta.get("intent", "")
        if intent.startswith("stage_"):
            stage = intent.replace("stage_", "")
            print(f"\n✅ {stage} complete\n", flush=True)
        print(text, flush=True)

    handle_task(
        task,
        on_progress=on_progress,
        on_result=on_result,
        clean_message=False,
    )
    return 0


def main() -> int:
    return run()


if __name__ == "__main__":
    raise SystemExit(main())
