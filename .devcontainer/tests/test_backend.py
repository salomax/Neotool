"""Unit tests for CLIBackend.invoke()."""
from __future__ import annotations

import subprocess
from unittest.mock import patch

import pytest

from agents.backend import CLIBackend, BackendResult


class _FakeStream:
    def __init__(self, lines: list[str]) -> None:
        self._lines = list(lines)
        self._closed = False

    def readline(self) -> str:
        if self._lines:
            return self._lines.pop(0)
        return ""

    def close(self) -> None:
        self._closed = True


class _FakeProcess:
    def __init__(
        self,
        *,
        stdout_lines: list[str],
        stderr_lines: list[str] | None = None,
        returncode: int = 0,
        timeout: bool = False,
    ) -> None:
        self.stdout = _FakeStream(stdout_lines)
        self.stderr = _FakeStream(stderr_lines or [])
        self.returncode = returncode
        self._timeout = timeout
        self._killed = False
        self._wait_calls = 0

    def wait(self, timeout: float | None = None) -> int:
        self._wait_calls += 1
        if self._timeout and not self._killed:
            raise subprocess.TimeoutExpired(cmd=["fake"], timeout=timeout or 0)
        return self.returncode if self.returncode is not None else 0

    def kill(self) -> None:
        self._killed = True
        self.returncode = -9


@pytest.fixture
def backend(tmp_path) -> CLIBackend:
    agents_bin = tmp_path / "agents"
    agents_bin.mkdir()
    for name in ("claude-dev", "codex-dev", "cursor-dev"):
        (agents_bin / name).write_text("#!/bin/sh\necho ok\n")
    return CLIBackend(agents_bin=agents_bin, workspace=str(tmp_path))


def test_invoke_success(backend: CLIBackend) -> None:
    fake = _FakeProcess(stdout_lines=["hello\n", "world\n"], returncode=0)

    with patch("agents.backend.subprocess.Popen", return_value=fake):
        result = backend.invoke(
            "do something",
            agent="claude",
            model="sonnet",
            thread="thread-1",
        )

    assert isinstance(result, BackendResult)
    assert result.output == "hello\nworld"
    assert result.exit_code == 0
    assert result.elapsed_ms >= 0
    assert result.tokens_in is None
    assert result.tokens_out is None


def test_invoke_nonzero_exit_uses_stderr(backend: CLIBackend) -> None:
    fake = _FakeProcess(
        stdout_lines=[],
        stderr_lines=["error detail\n"],
        returncode=2,
    )

    with patch("agents.backend.subprocess.Popen", return_value=fake):
        result = backend.invoke(
            "fail",
            agent="codex",
            model=None,
            thread="thread-2",
        )

    assert result.output == "error detail"
    assert result.exit_code == 2


def test_invoke_nonzero_exit_empty_output_fallback(backend: CLIBackend) -> None:
    fake = _FakeProcess(stdout_lines=[], stderr_lines=[], returncode=3)

    with patch("agents.backend.subprocess.Popen", return_value=fake):
        result = backend.invoke(
            "fail silently",
            agent="cursor",
            model=None,
            thread="thread-3",
        )

    assert result.output == "_(sem resposta do agente — exit 3)_"
    assert result.exit_code == 3


def test_invoke_zero_exit_empty_output_fallback(backend: CLIBackend) -> None:
    fake = _FakeProcess(stdout_lines=[], stderr_lines=[], returncode=0)

    with patch("agents.backend.subprocess.Popen", return_value=fake):
        result = backend.invoke(
            "silent success",
            agent="claude",
            model=None,
            thread="thread-zero",
        )

    assert result.output == "_(sem resposta do agente — exit 0)_"
    assert result.exit_code == 0


def test_invoke_timeout(backend: CLIBackend) -> None:
    fake = _FakeProcess(stdout_lines=[], timeout=True)

    with patch("agents.backend.subprocess.Popen", return_value=fake):
        result = backend.invoke(
            "slow task",
            agent="claude",
            model=None,
            thread="thread-4",
            timeout=60,
        )

    assert result.exit_code == -1
    assert "Timeout" in result.output
    assert "1 minutos" in result.output
    assert fake._killed is True


def test_invoke_passes_model_via_env_not_argv(backend: CLIBackend) -> None:
    captured_cmd: list[str] = []
    captured_env: dict[str, str] = {}

    def _popen(cmd, env=None, **kwargs):
        captured_cmd[:] = list(cmd)
        captured_env.update(env or {})
        return _FakeProcess(stdout_lines=["ok\n"], returncode=0)

    with patch("agents.backend.subprocess.Popen", side_effect=_popen):
        backend.invoke(
            "Please implement X",
            agent="claude",
            model="claude-haiku-4-5",
            thread="t",
        )

    assert captured_cmd == [str(backend.agents_bin / "claude-dev"), "Please implement X"]
    assert captured_env["NEOTOOL_MODEL"] == "claude-haiku-4-5"
    assert "--model" not in captured_cmd


def test_invoke_clears_stale_neotool_model_when_unset(backend: CLIBackend) -> None:
    captured_env: dict[str, str] = {}

    def _popen(cmd, env=None, **kwargs):
        captured_env.update(env or {})
        return _FakeProcess(stdout_lines=["ok\n"], returncode=0)

    with patch("agents.backend.subprocess.Popen", side_effect=_popen):
        with patch.dict("os.environ", {"NEOTOOL_MODEL": "stale-model"}, clear=False):
            backend.invoke("task", agent="claude", model=None, thread="t")

    assert "NEOTOOL_MODEL" not in captured_env


def test_invoke_wrapper_receives_intact_prompt_via_subprocess(tmp_path) -> None:
    agents_bin = tmp_path / "agents"
    agents_bin.mkdir()
    for name in ("claude-dev", "codex-dev", "cursor-dev"):
        script = agents_bin / name
        script.write_text('#!/bin/sh\necho "MODEL=${NEOTOOL_MODEL:-} PROMPT=$*"\n')
        script.chmod(0o755)

    backend = CLIBackend(agents_bin=agents_bin, workspace=str(tmp_path))
    result = backend.invoke(
        "Run tests, lint, typecheck and report results.",
        agent="cursor",
        model="auto",
        thread="wrapper-test",
    )

    assert result.exit_code == 0
    assert "MODEL=auto" in result.output
    assert "PROMPT=Run tests, lint, typecheck and report results." in result.output
    assert "--model" not in result.output


def test_invoke_writes_progress_log(backend: CLIBackend, tmp_path) -> None:
    log_path = tmp_path / "progress.log"
    fake = _FakeProcess(stdout_lines=["line1\n", "line2\n"], returncode=0)

    with patch("agents.backend.subprocess.Popen", return_value=fake):
        backend.invoke(
            "task",
            agent="claude",
            model=None,
            thread="thread-5",
            progress_log=str(log_path),
        )

    assert log_path.read_text() == "line1\nline2\n"


def test_invoke_merges_env_and_thread(backend: CLIBackend) -> None:
    captured_env: dict[str, str] = {}

    def _popen(cmd, env=None, **kwargs):
        captured_env.update(env or {})
        return _FakeProcess(stdout_lines=["ok\n"], returncode=0)

    with patch("agents.backend.subprocess.Popen", side_effect=_popen):
        backend.invoke(
            "task",
            agent="claude",
            model=None,
            thread="my-thread",
            env={"SLACK_TOKEN": "x-token"},
        )

    assert captured_env["NEOTOOL_THREAD"] == "my-thread"
    assert captured_env["SLACK_TOKEN"] == "x-token"


def test_invoke_prepends_agents_bin_to_path(backend: CLIBackend) -> None:
    captured_env: dict[str, str] = {}

    def _popen(cmd, env=None, **kwargs):
        captured_env.update(env or {})
        return _FakeProcess(stdout_lines=["ok\n"], returncode=0)

    with patch("agents.backend.subprocess.Popen", side_effect=_popen):
        backend.invoke(
            "task",
            agent="claude",
            model=None,
            thread="thread-path",
        )

    agents_bin = str(backend.agents_bin)
    assert captured_env["PATH"].startswith(f"{agents_bin}:")
