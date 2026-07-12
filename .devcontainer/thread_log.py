#!/usr/bin/env python3
"""
thread_log.py — Append-only JSONL audit trail for dev-agent invocations.

One file per task+stage: ~/.neotool/dev-agent/threads/<task_id>/<stage>.jsonl
"""
from __future__ import annotations

import json
import logging
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping

from agents.backend import AgentBackend, BackendResult

log = logging.getLogger("thread_log")

DEFAULT_ROOT = Path.home() / ".neotool" / "dev-agent" / "threads"


def threads_root(root: Path | None = None) -> Path:
    return root if root is not None else DEFAULT_ROOT


def _safe_segment(value: str) -> str:
    return re.sub(r"[^\w.\-]", "_", value)


def task_dir(task_id: str, root: Path | None = None) -> Path:
    return threads_root(root) / _safe_segment(task_id)


def log_file(task_id: str, stage: str, root: Path | None = None) -> Path:
    return task_dir(task_id, root) / f"{_safe_segment(stage)}.jsonl"


def log_stage_for_intent(intent: str) -> str:
    """Map classifier intent to a thread-log stage key.

    Pipeline stages (stage_*) keep their name; conversational intents
    (question, advance, fast_track, …) bucket under ``question`` so cost/logs
    align with the STAGES model instead of one file per classifier label.
    """
    if intent.startswith("stage_"):
        return intent
    return "question"


def append_entry(
    task_id: str,
    stage: str,
    *,
    direction: str,
    agent: str,
    model: str,
    tokens_in: int | None = None,
    tokens_out: int | None = None,
    latency_ms: int | None = None,
    content: str,
    root: Path | None = None,
    ts: float | None = None,
) -> dict[str, Any]:
    """Append one JSONL record. Returns the written entry."""
    entry: dict[str, Any] = {
        "ts": datetime.fromtimestamp(ts or time.time(), tz=timezone.utc).isoformat(),
        "direction": direction,
        "agent": agent,
        "model": model,
        "tokens_in": tokens_in,
        "tokens_out": tokens_out,
        "latency_ms": latency_ms,
        "content": content,
    }
    path = log_file(task_id, stage, root)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(entry, ensure_ascii=False) + "\n")
    return entry


def read_entries(task_id: str, root: Path | None = None) -> list[dict[str, Any]]:
    """Read all entries for a task, ordered by file then line."""
    base = task_dir(task_id, root)
    if not base.is_dir():
        return []
    entries: list[dict[str, Any]] = []
    for path in sorted(base.glob("*.jsonl")):
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                except json.JSONDecodeError:
                    log.warning("skipping malformed JSONL line in %s", path)
                    continue
                record.setdefault("stage", path.stem)
                entries.append(record)
    return entries


def aggregate_cost(task_id: str, root: Path | None = None) -> dict[str, Any]:
    """Sum tokens_in/tokens_out per stage, per agent, and total for a task.

    Only ``direction=out`` records contribute (agent responses), avoiding double
    counting of prompt lines logged as ``direction=in``.
    """
    base = task_dir(task_id, root)
    stages: dict[str, dict[str, int]] = {}
    agents: dict[str, dict[str, int]] = {}
    total_in = 0
    total_out = 0
    had_out_without_tokens = False

    if not base.is_dir():
        return {
            "task_id": task_id,
            "stages": stages,
            "agents": agents,
            "total": {"tokens_in": 0, "tokens_out": 0},
            "note": None,
        }

    for path in sorted(base.glob("*.jsonl")):
        stage = path.stem
        if stage == "stage_summarize":
            continue
        stage_in = 0
        stage_out = 0
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if record.get("direction") != "out":
                    continue
                ti = record.get("tokens_in")
                to = record.get("tokens_out")
                if ti is None and to is None:
                    agent_name = record.get("agent") or "unknown"
                    if agent_name not in ("system", "unknown"):
                        had_out_without_tokens = True
                    ti = 0
                    to = 0
                stage_in += ti or 0
                stage_out += to or 0
                agent_name = record.get("agent") or "unknown"
                bucket = agents.setdefault(
                    agent_name,
                    {"tokens_in": 0, "tokens_out": 0, "calls": 0},
                )
                bucket["tokens_in"] += ti or 0
                bucket["tokens_out"] += to or 0
                bucket["calls"] += 1
        if stage_in or stage_out:
            stages[stage] = {"tokens_in": stage_in, "tokens_out": stage_out}
        total_in += stage_in
        total_out += stage_out

    note = None
    if had_out_without_tokens and total_in == 0 and total_out == 0:
        note = (
            "Token counts unavailable for some agent runs (Codex/Cursor may not "
            "report usage in exec mode). Claude runs should populate after wrapper update."
        )
    elif had_out_without_tokens:
        note = "Partial token data — some agents did not report usage."
    elif any(
        agent == "codex"
        for agent in agents
        if (agents[agent]["tokens_in"] or agents[agent]["tokens_out"])
    ):
        note = (
            "Codex token counts are read from ~/.codex/sessions rollout JSONL when available."
        )

    return {
        "task_id": task_id,
        "stages": stages,
        "agents": agents,
        "total": {"tokens_in": total_in, "tokens_out": total_out},
        "note": note,
    }


def format_thread_cost_summary(task_id: str, root: Path | None = None) -> str:
    """Human-readable Slack/Markdown summary of thread token usage."""
    data = aggregate_cost(task_id, root)
    lines = [
        "📊 *Thread token summary*",
        f"Task: `{data['task_id']}`",
        "",
        "*By stage*",
    ]
    if data["stages"]:
        for stage, counts in sorted(data["stages"].items()):
            lines.append(
                f"• `{stage}`: in={counts['tokens_in']:,} out={counts['tokens_out']:,}"
            )
    else:
        lines.append("• _(no outbound agent calls logged)_")

    if data.get("agents"):
        lines += ["", "*By agent*"]
        for agent, counts in sorted(data["agents"].items()):
            if agent == "system":
                continue
            lines.append(
                f"• `{agent}`: in={counts['tokens_in']:,} "
                f"out={counts['tokens_out']:,} ({counts['calls']} call(s))"
            )

    total = data["total"]
    lines += [
        "",
        f"*Total:* in={total['tokens_in']:,} out={total['tokens_out']:,}",
    ]
    if data.get("note"):
        lines.append(f"_{data['note']}_")
    return "\n".join(lines)


def write_thread_summary(task_id: str, root: Path | None = None) -> str:
    """Append a deterministic summarize record to the thread log."""
    summary = format_thread_cost_summary(task_id, root)
    append_entry(
        task_id,
        "stage_summarize",
        direction="out",
        agent="system",
        model="",
        latency_ms=0,
        content=summary,
        root=root,
    )
    return summary


def list_tasks(root: Path | None = None) -> list[dict[str, Any]]:
    """List task directories with last-modified timestamp."""
    base = threads_root(root)
    if not base.is_dir():
        return []

    tasks: list[dict[str, Any]] = []
    for path in sorted(base.iterdir()):
        if not path.is_dir():
            continue
        jsonl_files = list(path.glob("*.jsonl"))
        if not jsonl_files:
            continue
        last_mtime = max(f.stat().st_mtime for f in jsonl_files)
        last_stage = max(jsonl_files, key=lambda f: f.stat().st_mtime).stem
        tasks.append({
            "task_id": path.name,
            "last_modified": datetime.fromtimestamp(last_mtime, tz=timezone.utc).isoformat(),
            "last_stage": last_stage,
        })
    tasks.sort(key=lambda item: item["last_modified"], reverse=True)
    return tasks


class LoggingBackend:
    """AgentBackend wrapper that writes in/out JSONL lines per invocation."""

    def __init__(
        self,
        inner: AgentBackend,
        *,
        task_id: str,
        stage: str,
        root: Path | None = None,
    ) -> None:
        self._inner = inner
        self._task_id = task_id
        self._stage = stage
        self._root = root

    def _write(self, **fields: Any) -> None:
        append_entry(self._task_id, self._stage, root=self._root, **fields)

    def invoke(
        self,
        prompt: str,
        *,
        agent: str,
        model: str | None,
        thread: str,
        env: Mapping[str, str] | None = None,
        progress_log: str | None = None,
        timeout: int | None = None,
    ) -> BackendResult:
        try:
            self._write(
                direction="in",
                agent=agent,
                model=model or "",
                latency_ms=0,
                content=prompt,
            )
        except Exception as exc:
            log.warning(
                "thread log write failed (in) for task=%s stage=%s: %s",
                self._task_id,
                self._stage,
                exc,
            )

        result = self._inner.invoke(
            prompt,
            agent=agent,
            model=model,
            thread=thread,
            env=env,
            progress_log=progress_log,
            timeout=timeout,
        )

        try:
            self._write(
                direction="out",
                agent=agent,
                model=model or "",
                tokens_in=result.tokens_in,
                tokens_out=result.tokens_out,
                latency_ms=result.elapsed_ms,
                content=result.output,
            )
        except Exception as exc:
            log.warning(
                "thread log write failed (out) for task=%s stage=%s: %s",
                self._task_id,
                self._stage,
                exc,
            )
        return result
