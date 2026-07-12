"""Unit tests for thread_log_cli."""
from __future__ import annotations

from pathlib import Path

from thread_log import append_entry
from thread_log_cli import _cmd_tail, main


def test_cli_cost_prints_breakdown(capsys, tmp_path: Path, monkeypatch) -> None:
    root = tmp_path / "threads"
    append_entry(
        "cli-task", "question",
        direction="out", agent="claude", model="",
        tokens_in=10, tokens_out=20, latency_ms=1, content="x",
        root=root,
    )

    import thread_log_cli
    monkeypatch.setattr(thread_log_cli, "aggregate_cost", lambda task_id: {
        "task_id": task_id,
        "stages": {"question": {"tokens_in": 10, "tokens_out": 20}},
        "total": {"tokens_in": 10, "tokens_out": 20},
    })

    rc = main(["cost", "cli-task"])
    out = capsys.readouterr().out
    assert rc == 0
    assert "cli-task" in out
    assert "question" in out
    assert "tokens_in=10" in out


def test_cli_list_empty(capsys, monkeypatch) -> None:
    import thread_log_cli
    monkeypatch.setattr(thread_log_cli, "list_tasks", lambda: [])
    rc = main(["list"])
    out = capsys.readouterr().out
    assert rc == 0
    assert "No tasks found" in out


def test_tail_follow_starts_at_eof(capsys, tmp_path: Path, monkeypatch) -> None:
    import thread_log_cli
    root = tmp_path / "threads"
    append_entry(
        "tail-task", "question",
        direction="out", agent="claude", model="",
        latency_ms=1, content="old line\n", root=root,
    )
    monkeypatch.setattr(thread_log_cli, "task_dir", lambda task_id: root / task_id)

    def _interrupt_sleep(_interval: float) -> None:
        raise KeyboardInterrupt

    monkeypatch.setattr(thread_log_cli.time, "sleep", _interrupt_sleep)

    rc = _cmd_tail("tail-task", follow=True, interval=0.01)
    out = capsys.readouterr().out
    assert rc == 0
    assert "old line" not in out
