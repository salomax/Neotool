#!/usr/bin/env python3
"""
verifier.py — Deterministic validation gate before code review.

Runs neotool validate (typecheck/lint/test) and supports impl retry loop.
"""
from __future__ import annotations

import logging
import subprocess
from dataclasses import dataclass
from pathlib import Path

log = logging.getLogger("verifier")

WORKSPACE = "/workspaces/invistus"
IMPL_RETRY_LIMIT = 3
MAX_OUTPUT_CHARS = 12000


@dataclass(frozen=True)
class VerifierResult:
    passed: bool
    exit_code: int
    output: str
    scopes: tuple[str, ...]


def detect_validation_scopes(workspace: str = WORKSPACE) -> list[str]:
    """Infer validate scopes from branch diff vs main."""
    try:
        result = subprocess.run(
            ["git", "diff", "--name-only", "main...HEAD"],
            capture_output=True,
            text=True,
            timeout=30,
            cwd=workspace,
        )
        if result.returncode != 0:
            result = subprocess.run(
                ["git", "diff", "--name-only", "HEAD"],
                capture_output=True,
                text=True,
                timeout=30,
                cwd=workspace,
            )
        files = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    except Exception as exc:
        log.warning("detect_validation_scopes error: %s", exc)
        files = []

    scopes: list[str] = []
    if any(f.startswith("web/") for f in files):
        scopes.append("--web")
    if any(f.startswith("mobile/") for f in files):
        scopes.append("--mobile")
    if any(f.startswith("service/kotlin/") for f in files):
        scopes.append("--service")
    if any(f.startswith(".devcontainer/") for f in files):
        if "--web" not in scopes:
            scopes.append("--web")
    if not scopes:
        scopes = ["--web"]
    return scopes


def _truncate_output(output: str) -> str:
    text = output.strip() or "(no verifier output)"
    if len(text) > MAX_OUTPUT_CHARS:
        return text[:MAX_OUTPUT_CHARS] + "\n...(truncated)"
    return text


def _run_validate_command(
    scopes: list[str],
    *,
    workspace: str,
    skip_coverage: bool,
) -> VerifierResult:
    """Run neotool validate for a single scope (or full validation when scopes is empty)."""
    cli = str(Path(workspace) / "scripts" / "cli" / "cli")
    cmd = ["bash", cli, "validate", *scopes]
    if skip_coverage:
        cmd.append("--skip-coverage")
    cmd.append("--no-parallel")

    log.info("verifier running: %s", " ".join(cmd))
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=3600,
            cwd=workspace,
        )
        output = _truncate_output((proc.stdout or "") + (proc.stderr or ""))
        exit_code = proc.returncode if proc.returncode is not None else 1
        passed = exit_code == 0
        log.info(
            "verifier scope=%s passed=%s exit=%s",
            scopes or ["full"],
            passed,
            exit_code,
        )
        return VerifierResult(
            passed=passed,
            exit_code=exit_code,
            output=output,
            scopes=tuple(scopes),
        )
    except subprocess.TimeoutExpired:
        return VerifierResult(
            passed=False,
            exit_code=-1,
            output="❌ Verifier timeout after 3600s",
            scopes=tuple(scopes),
        )
    except Exception as exc:
        log.exception("verifier error scopes=%s: %s", scopes, exc)
        return VerifierResult(
            passed=False,
            exit_code=1,
            output=f"❌ Verifier error: {exc}",
            scopes=tuple(scopes),
        )


def _merge_verifier_results(results: list[VerifierResult]) -> VerifierResult:
    """Combine per-scope runs into one result."""
    if not results:
        return VerifierResult(
            passed=False,
            exit_code=1,
            output="❌ Verifier produced no results",
            scopes=(),
        )
    if len(results) == 1:
        return results[0]

    passed = all(r.passed for r in results)
    exit_code = 0 if passed else max(r.exit_code for r in results)
    scopes = tuple(scope for r in results for scope in r.scopes)
    sections = []
    for result in results:
        label = ", ".join(result.scopes) if result.scopes else "full"
        sections.append(f"=== scope: {label} (exit {result.exit_code}) ===\n{result.output}")
    output = _truncate_output("\n\n".join(sections))
    return VerifierResult(
        passed=passed,
        exit_code=exit_code,
        output=output,
        scopes=scopes,
    )


def run_verifier(*, workspace: str = WORKSPACE, skip_coverage: bool = True) -> VerifierResult:
    """Run neotool validate for detected scopes.

    validate.sh accepts only one of --web/--mobile/--service per invocation,
    so multi-layer diffs run one validate call per scope and merge results.
    """
    scopes = detect_validation_scopes(workspace)
    if len(scopes) <= 1:
        return _run_validate_command(scopes, workspace=workspace, skip_coverage=skip_coverage)

    log.info("verifier multi-scope diff — running sequentially: %s", scopes)
    results = [
        _run_validate_command([scope], workspace=workspace, skip_coverage=skip_coverage)
        for scope in scopes
    ]
    return _merge_verifier_results(results)


def build_impl_retry_prompt(verifier: VerifierResult, issue_number: int | None) -> str:
    ref = f"issue #{issue_number}" if issue_number else "the current task"
    return (
        f"The deterministic verifier failed for {ref}. "
        f"Fix the issues below and re-implement.\n\n"
        f"Scopes: {', '.join(verifier.scopes)}\n\n"
        f"--- Verifier output ---\n{verifier.output}"
    )
