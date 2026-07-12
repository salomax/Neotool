#!/usr/bin/env python3
"""
checkpoint.py — Structured GitHub issue checkpoints for dev-agent resume.

Checkpoints are stored as HTML comments in issue comments:
  <!-- neotool-checkpoint:{...json...} -->
"""
from __future__ import annotations

import json
import logging
import re
import subprocess
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Any

log = logging.getLogger("checkpoint")

WORKSPACE = "/workspaces/invistus"
CHECKPOINT_MARKER = "<!-- neotool-checkpoint:"
CHECKPOINT_RE = re.compile(
    r"<!--\s*neotool-checkpoint:(\{.*?\})\s*-->",
    re.DOTALL,
)
# Legacy/alternate marker from solution design
LEGACY_CHECKPOINT_RE = re.compile(
    r"<!--\s*checkpoint:\s*(\{.*?\})\s*-->",
    re.DOTALL,
)

CHECKPOINT_VERSION = 1
VALID_STATUSES = frozenset({"in_progress", "completed", "failed"})


@dataclass(frozen=True)
class Checkpoint:
    """Structured checkpoint persisted on a GitHub issue."""

    stage: str
    status: str
    task_id: str
    timestamp: str
    issue_number: int
    output_summary: str = ""
    v: int = CHECKPOINT_VERSION

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Checkpoint | None:
        try:
            stage = str(data["stage"])
            status = str(data["status"])
            if status not in VALID_STATUSES:
                return None
            return cls(
                v=int(data.get("v", CHECKPOINT_VERSION)),
                stage=stage,
                status=status,
                task_id=str(data["task_id"]),
                timestamp=str(data["timestamp"]),
                issue_number=int(data["issue_number"]),
                output_summary=str(data.get("output_summary") or ""),
            )
        except (KeyError, TypeError, ValueError):
            return None


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_checkpoint_from_comment(body: str) -> Checkpoint | None:
    """Extract checkpoint JSON from an issue comment body."""
    if not body:
        return None
    for pattern in (CHECKPOINT_RE, LEGACY_CHECKPOINT_RE):
        match = pattern.search(body)
        if not match:
            continue
        try:
            data = json.loads(match.group(1))
        except json.JSONDecodeError:
            log.warning("malformed checkpoint JSON in comment")
            continue
        cp = Checkpoint.from_dict(data)
        if cp:
            return cp
    return None


def format_checkpoint_comment(checkpoint: Checkpoint) -> str:
    """Build issue comment body with hidden checkpoint JSON."""
    payload = json.dumps(checkpoint.to_dict(), ensure_ascii=False, separators=(",", ":"))
    visible = (
        f"_dev-agent checkpoint: `{checkpoint.stage}` "
        f"({checkpoint.status}) · task `{checkpoint.task_id}`_"
    )
    return f"{CHECKPOINT_MARKER}{payload} -->\n{visible}"


def write_checkpoint(
    issue_number: int,
    *,
    stage: str,
    status: str,
    task_id: str,
    output_summary: str = "",
    workspace: str = WORKSPACE,
) -> bool:
    """Post a structured checkpoint comment on a GitHub issue."""
    checkpoint = Checkpoint(
        stage=stage,
        status=status,
        task_id=task_id,
        timestamp=utc_now_iso(),
        issue_number=issue_number,
        output_summary=(output_summary or "")[:500],
    )
    body = format_checkpoint_comment(checkpoint)
    try:
        result = subprocess.run(
            ["gh", "issue", "comment", str(issue_number), "-b", body],
            capture_output=True,
            text=True,
            timeout=30,
            cwd=workspace,
        )
        if result.returncode != 0:
            log.warning(
                "write_checkpoint failed issue=%s: %s",
                issue_number,
                result.stderr[:200],
            )
            return False
        log.info(
            "checkpoint written issue=%s stage=%s status=%s task=%s",
            issue_number,
            stage,
            status,
            task_id,
        )
        return True
    except Exception as exc:
        log.warning("write_checkpoint error issue=%s: %s", issue_number, exc)
        return False


def fetch_last_checkpoint(
    issue_number: int,
    *,
    workspace: str = WORKSPACE,
) -> Checkpoint | None:
    """Return the most recent checkpoint comment on an issue, if any."""
    try:
        result = subprocess.run(
            [
                "gh",
                "issue",
                "view",
                str(issue_number),
                "--json",
                "comments",
            ],
            capture_output=True,
            text=True,
            timeout=30,
            cwd=workspace,
        )
        if result.returncode != 0:
            log.warning(
                "fetch_last_checkpoint failed issue=%s: %s",
                issue_number,
                result.stderr[:200],
            )
            return None
        data = json.loads(result.stdout)
    except Exception as exc:
        log.warning("fetch_last_checkpoint error issue=%s: %s", issue_number, exc)
        return None

    comments = data.get("comments") or []
    for comment in reversed(comments):
        body = comment.get("body") or ""
        cp = parse_checkpoint_from_comment(body)
        if cp:
            return cp
    return None


def stage_index(stage: str, stages: list[str]) -> int | None:
    try:
        return stages.index(stage)
    except ValueError:
        return None


def current_stage_from_checkpoint(checkpoint: Checkpoint, stages: list[str]) -> int | None:
    """Map checkpoint to pipeline index for resume."""
    idx = stage_index(checkpoint.stage, stages)
    if idx is None:
        return None
    if checkpoint.status == "completed":
        return min(idx + 1, len(stages))
    if checkpoint.status == "in_progress":
        return idx
    if checkpoint.status == "failed":
        return idx
    return None
