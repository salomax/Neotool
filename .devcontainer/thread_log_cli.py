#!/usr/bin/env python3
"""CLI for dev-agent thread log inspection."""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

from thread_log import aggregate_cost, list_tasks, read_entries, task_dir


def _cmd_logs(task_id: str) -> int:
    entries = read_entries(task_id)
    if not entries:
        print(f"No thread log found for task: {task_id}", file=sys.stderr)
        return 1
    for entry in entries:
        stage = entry.get("stage", "?")
        print(
            f"[{entry.get('ts', '?')}] {stage} "
            f"{entry.get('direction', '?')} "
            f"{entry.get('agent', '?')}/{entry.get('model', '')} "
            f"latency={entry.get('latency_ms')}ms "
            f"tokens={entry.get('tokens_in') or 0}/{entry.get('tokens_out') or 0}"
        )
        content = entry.get("content") or ""
        if content:
            print(content)
            if not content.endswith("\n"):
                print()
    return 0


def _cmd_tail(task_id: str, follow: bool, interval: float) -> int:
    base = task_dir(task_id)
    if not base.is_dir():
        print(f"No thread log found for task: {task_id}", file=sys.stderr)
        return 1

    seen: dict[Path, int] = {}
    if follow:
        # Start at EOF — only show lines appended after tail -f was invoked.
        for path in sorted(base.glob("*.jsonl")):
            seen[path] = path.stat().st_size

    def _emit_new() -> None:
        for path in sorted(base.glob("*.jsonl")):
            offset = seen.get(path, 0)
            with path.open(encoding="utf-8") as fh:
                fh.seek(offset)
                chunk = fh.read()
                if chunk:
                    sys.stdout.write(chunk)
                    sys.stdout.flush()
                seen[path] = fh.tell()

    _emit_new()
    if not follow:
        return 0

    try:
        while True:
            time.sleep(interval)
            _emit_new()
    except KeyboardInterrupt:
        print()
        return 0


def _cmd_cost(task_id: str) -> int:
    summary = aggregate_cost(task_id)
    if not summary["stages"]:
        print(f"No thread log found for task: {task_id}", file=sys.stderr)
        return 1
    print(f"Task: {summary['task_id']}")
    print("Stage breakdown:")
    for stage, counts in sorted(summary["stages"].items()):
        print(
            f"  {stage}: tokens_in={counts['tokens_in']} "
            f"tokens_out={counts['tokens_out']}"
        )
    total = summary["total"]
    print(
        f"Total: tokens_in={total['tokens_in']} tokens_out={total['tokens_out']}"
    )
    return 0


def _cmd_list() -> int:
    tasks = list_tasks()
    if not tasks:
        print("No tasks found.")
        return 0
    for item in tasks:
        print(
            f"{item['task_id']}\t{item['last_modified']}\t{item['last_stage']}"
        )
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Dev-agent thread log CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    logs_p = sub.add_parser("logs", help="Show all log entries for a task")
    logs_p.add_argument("task_id")

    tail_p = sub.add_parser("tail", help="Tail log entries for a task")
    tail_p.add_argument("task_id")
    tail_p.add_argument(
        "-f", "--follow",
        action="store_true",
        help="Follow appended lines (Ctrl-C to exit)",
    )
    tail_p.add_argument(
        "--interval",
        type=float,
        default=1.0,
        help="Poll interval when following (seconds)",
    )

    cost_p = sub.add_parser("cost", help="Show token cost breakdown for a task")
    cost_p.add_argument("task_id")

    sub.add_parser("list", help="List known tasks")

    args = parser.parse_args(argv)

    if args.command == "logs":
        return _cmd_logs(args.task_id)
    if args.command == "tail":
        return _cmd_tail(args.task_id, args.follow, args.interval)
    if args.command == "cost":
        return _cmd_cost(args.task_id)
    if args.command == "list":
        return _cmd_list()
    parser.error(f"unknown command: {args.command}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
