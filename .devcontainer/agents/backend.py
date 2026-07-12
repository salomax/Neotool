#!/usr/bin/env python3
"""
backend.py — AgentBackend protocol and CLIBackend implementation.

Abstracts subprocess invocation of claude-dev / codex-dev / cursor-dev wrappers
so router.dispatch() delegates to a swappable backend (APIBackend in phase 8).
"""
from __future__ import annotations

import logging
import os
import subprocess
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Protocol, runtime_checkable

log = logging.getLogger("backend")

WORKSPACE = "/workspaces/invistus"
DEFAULT_TIMEOUT = 1800


@dataclass(frozen=True)
class BackendResult:
    output: str
    exit_code: int
    elapsed_ms: int
    tokens_in: int | None = None
    tokens_out: int | None = None


@runtime_checkable
class AgentBackend(Protocol):
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
        ...


class CLIBackend:
    """Runs agent wrappers via subprocess (current v1 behavior)."""

    def __init__(
        self,
        *,
        agents_bin: str | Path | None = None,
        workspace: str = WORKSPACE,
        default_timeout: int = DEFAULT_TIMEOUT,
    ) -> None:
        self.agents_bin = Path(agents_bin) if agents_bin else Path(__file__).resolve().parent
        self.workspace = workspace
        self.default_timeout = default_timeout
        self._wrappers = {
            "claude": str(self.agents_bin / "claude-dev"),
            "codex": str(self.agents_bin / "codex-dev"),
            "cursor": str(self.agents_bin / "cursor-dev"),
        }

    def _build_cmd(self, agent: str, prompt: str) -> list[str]:
        wrapper = self._wrappers.get(agent, self._wrappers["claude"])
        return [wrapper, prompt]

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
        timeout_secs = timeout if timeout is not None else self.default_timeout
        cmd = self._build_cmd(agent, prompt)
        run_env = dict(os.environ)
        if env:
            run_env.update(env)
        run_env.setdefault("NEOTOOL_THREAD", thread)
        if model:
            run_env["NEOTOOL_MODEL"] = model
        else:
            run_env.pop("NEOTOOL_MODEL", None)
        run_env["PATH"] = f"{self.agents_bin}:{run_env.get('PATH', '')}"

        log.info(
            "CLIBackend.invoke agent=%s model=%s thread=%s timeout=%ds",
            agent,
            model,
            thread,
            timeout_secs,
        )
        log.info("running: %s", " ".join(str(x) for x in cmd[:2]) + " " + prompt[:80])

        t0 = time.time()
        timed_out = False

        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                cwd=self.workspace,
                env=run_env,
            )

            stdout_lines: list[str] = []
            stderr_lines: list[str] = []
            log_fh = open(progress_log, "w", buffering=1) if progress_log else None

            def _read_stdout() -> None:
                try:
                    assert proc.stdout is not None
                    for line in iter(proc.stdout.readline, ""):
                        stdout_lines.append(line)
                        if log_fh:
                            log_fh.write(line)
                            log_fh.flush()
                finally:
                    if proc.stdout:
                        proc.stdout.close()

            def _read_stderr() -> None:
                try:
                    assert proc.stderr is not None
                    for line in iter(proc.stderr.readline, ""):
                        stderr_lines.append(line)
                        log.debug("[agent stderr] %s", line.rstrip())
                finally:
                    if proc.stderr:
                        proc.stderr.close()

            t_out = threading.Thread(target=_read_stdout, daemon=True)
            t_err = threading.Thread(target=_read_stderr, daemon=True)
            t_out.start()
            t_err.start()

            try:
                proc.wait(timeout=timeout_secs)
            except subprocess.TimeoutExpired:
                timed_out = True
                proc.kill()
                proc.wait()
            finally:
                t_out.join(timeout=5)
                t_err.join(timeout=5)
                if log_fh:
                    log_fh.close()

            elapsed_ms = int((time.time() - t0) * 1000)

            if timed_out:
                log.error("timeout after %ds", timeout_secs)
                return BackendResult(
                    output=f"❌ Timeout — agente excedeu {timeout_secs // 60} minutos.",
                    exit_code=-1,
                    elapsed_ms=elapsed_ms,
                )

            output = "".join(stdout_lines).strip() or "".join(stderr_lines).strip()
            exit_code = proc.returncode if proc.returncode is not None else 1
            log.info(
                "agent done in %.1fs exit=%d chars=%d",
                elapsed_ms / 1000,
                exit_code,
                len(output),
            )
            if exit_code != 0:
                log.error("agent stderr (300): %s", "".join(stderr_lines)[:300])
            if not output:
                output = f"_(sem resposta do agente — exit {exit_code})_"

            return BackendResult(
                output=output,
                exit_code=exit_code,
                elapsed_ms=elapsed_ms,
            )

        except Exception as e:
            log.exception("CLIBackend.invoke error: %s", e)
            elapsed_ms = int((time.time() - t0) * 1000)
            return BackendResult(
                output=f"❌ Erro interno: {e}",
                exit_code=1,
                elapsed_ms=elapsed_ms,
            )


_default_backend: CLIBackend | None = None


def get_default_backend() -> CLIBackend:
    global _default_backend
    if _default_backend is None:
        _default_backend = CLIBackend()
    return _default_backend


def set_default_backend(backend: CLIBackend | None) -> None:
    global _default_backend
    _default_backend = backend
