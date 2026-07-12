"""Unit tests for verifier.py."""
from __future__ import annotations

import subprocess
from pathlib import Path
from unittest.mock import patch

import pytest

from verifier import (
    IMPL_RETRY_LIMIT,
    VerifierResult,
    _merge_verifier_results,
    build_impl_retry_prompt,
    detect_validation_scopes,
    run_verifier,
)


def test_detect_validation_scopes_web() -> None:
    with patch("verifier.subprocess.run") as mock_run:
        mock_run.return_value.returncode = 0
        mock_run.return_value.stdout = "web/src/foo.ts\n"
        scopes = detect_validation_scopes("/tmp/ws")
    assert scopes == ["--web"]


def test_detect_validation_scopes_multi_layer() -> None:
    with patch("verifier.subprocess.run") as mock_run:
        mock_run.return_value.returncode = 0
        mock_run.return_value.stdout = "web/a.ts\nservice/kotlin/app/Foo.kt\n"
        scopes = detect_validation_scopes("/tmp/ws")
    assert scopes == ["--web", "--service"]


def test_build_impl_retry_prompt_includes_output() -> None:
    verifier = VerifierResult(
        passed=False,
        exit_code=1,
        output="lint error on line 1",
        scopes=("--web",),
    )
    prompt = build_impl_retry_prompt(verifier, 369)
    assert "issue #369" in prompt
    assert "lint error on line 1" in prompt


@patch("verifier.subprocess.run")
def test_run_verifier_success(mock_run) -> None:
    mock_run.return_value.returncode = 0
    mock_run.return_value.stdout = "all good"
    mock_run.return_value.stderr = ""
    with patch("verifier.detect_validation_scopes", return_value=["--web"]):
        result = run_verifier(workspace="/tmp/ws")
    assert result.passed is True
    assert result.exit_code == 0
    assert mock_run.call_count == 1


@patch("verifier.subprocess.run")
def test_run_verifier_failure(mock_run) -> None:
    mock_run.return_value.returncode = 1
    mock_run.return_value.stdout = ""
    mock_run.return_value.stderr = "typecheck failed"
    with patch("verifier.detect_validation_scopes", return_value=["--web"]):
        result = run_verifier(workspace="/tmp/ws")
    assert result.passed is False
    assert "typecheck failed" in result.output


@patch("verifier._run_validate_command")
def test_run_verifier_multi_scope_runs_separate_commands(mock_run_scope) -> None:
    mock_run_scope.side_effect = [
        VerifierResult(passed=True, exit_code=0, output="web ok", scopes=("--web",)),
        VerifierResult(passed=True, exit_code=0, output="service ok", scopes=("--service",)),
    ]
    with patch("verifier.detect_validation_scopes", return_value=["--web", "--service"]):
        result = run_verifier(workspace="/tmp/ws")

    assert result.passed is True
    assert mock_run_scope.call_count == 2
    mock_run_scope.assert_any_call(["--web"], workspace="/tmp/ws", skip_coverage=True)
    mock_run_scope.assert_any_call(["--service"], workspace="/tmp/ws", skip_coverage=True)
    assert "web ok" in result.output
    assert "service ok" in result.output


def test_merge_verifier_results_fails_if_any_scope_fails() -> None:
    merged = _merge_verifier_results([
        VerifierResult(passed=True, exit_code=0, output="ok", scopes=("--web",)),
        VerifierResult(passed=False, exit_code=1, output="fail", scopes=("--service",)),
    ])
    assert merged.passed is False
    assert merged.exit_code == 1
    assert merged.scopes == ("--web", "--service")


def test_validate_cli_rejects_combined_scope_flags() -> None:
    """Real validate.sh rejects --web + --service in one invocation."""
    repo_root = Path(__file__).resolve().parents[2]
    cli = repo_root / "scripts" / "cli" / "cli"
    if not cli.exists():
        pytest.skip("neotool cli not found")

    result = subprocess.run(
        ["bash", str(cli), "validate", "--web", "--service", "--skip-coverage"],
        capture_output=True,
        text=True,
        cwd=str(repo_root),
    )
    combined = (result.stdout or "") + (result.stderr or "")
    assert result.returncode != 0
    assert "cannot be used together" in combined


def test_impl_retry_limit_constant() -> None:
    assert IMPL_RETRY_LIMIT == 3
