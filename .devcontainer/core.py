#!/usr/bin/env python3
"""
core.py — Adapter-agnostic dev-agent task orchestration.

Shared by Slack and CLI entry points. Adapters supply Task + callbacks;
this module owns progress ticking and the router.route() call.
"""
from __future__ import annotations

import logging
import re
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Optional

import router

log = logging.getLogger("core")

ProgressCallback = Callable[[str], None]
ResultCallback = Callable[[Optional[str], dict], None]
MetaCallback = Callable[[dict], None]

_AUTO_PREFIX_RE = re.compile(
    r"^(?:@dev-agent\s+)?auto(?:[:\s]+)(.+)$",
    re.IGNORECASE | re.DOTALL,
)


def parse_task_mode(message: str, explicit_mode: str | None = None) -> tuple[str, str]:
    """Return (message, mode). explicit_mode wins when set to 'auto'."""
    if explicit_mode == "auto":
        return message.strip(), "auto"
    match = _AUTO_PREFIX_RE.match(message.strip())
    if match:
        return match.group(1).strip(), "auto"
    return message.strip(), "interactive"


@dataclass
class Task:
    """Normalized task from any input adapter."""

    message: str
    task_id: str
    issue_ref: int | None = None
    agent_hint: str | None = None
    mode: str = "interactive"
    requester: str = "unknown"
    adapter_env: dict[str, str] = field(default_factory=dict)


def _compose_message(task: Task) -> str:
    """Apply optional agent hint prefix before routing."""
    message = task.message.strip()
    if task.agent_hint and task.agent_hint not in message.lower():
        return f"@{task.agent_hint} {message}"
    return message


def handle_task(
    task: Task,
    *,
    on_progress: ProgressCallback | None = None,
    on_result: ResultCallback | None = None,
    meta_callback: MetaCallback | None = None,
    route_fn: Callable = router.route,
    progress_interval: int = 30,
    clean_message: bool = True,
) -> str | None:
    """Run one dev-agent task: classify, dispatch, return router output."""
    meta: dict = {}
    dispatch_started = threading.Event()
    stop_progress = threading.Event()

    def _meta_wrapper(data: dict) -> None:
        meta.update(data)
        dispatch_started.set()
        if meta_callback:
            meta_callback(data)
        log.info(
            "  dispatch: intent=%s agent=%s model=%s",
            data.get("intent"),
            data.get("agent"),
            data.get("model"),
        )

    def _progress_updater() -> None:
        if not on_progress:
            return
        if not dispatch_started.wait(timeout=120):
            return
        t_start = time.time()
        while not stop_progress.wait(progress_interval):
            elapsed = int(time.time() - t_start)
            mins, secs = divmod(elapsed, 60)
            intent = meta.get("intent", "")
            agent = meta.get("agent", "")
            model = meta.get("model", "")
            label = meta.get("label", "Working")
            agent_str = f"{agent}/{model}" if model else agent
            parts = [label]
            if agent_str:
                parts.append(agent_str)
            parts.append(f"{mins:02d}:{secs:02d}")
            on_progress(" · ".join(parts))

    updater = threading.Thread(target=_progress_updater, daemon=True)
    updater.start()

    message = _compose_message(task)
    message, mode = parse_task_mode(message, task.mode if task.mode == "auto" else None)
    if task.issue_ref and f"#{task.issue_ref}" not in message and f"issue {task.issue_ref}" not in message.lower():
        message = f"{message}\n\nIssue: #{task.issue_ref}"

    log.info("▶ task_id=%s requester=%s mode=%s msg=%r", task.task_id, task.requester, mode, message[:120])

    output: str | None = None
    try:
        output = route_fn(
            message,
            task.task_id,
            adapter_env=task.adapter_env,
            meta_callback=_meta_wrapper,
            clean_message=clean_message,
            mode=mode,
        )
        log.info("◀ router output: %r", (output or "")[:200])
    except Exception as exc:
        log.exception("✗ handle_task error: %s", exc)
        raise
    finally:
        stop_progress.set()

    if on_result:
        on_result(output, meta)

    log.info("■ task_id=%s done", task.task_id)
    return output
