#!/usr/bin/env python3
"""Run the frozen SDD retrieval evaluation with isolated Codex sessions."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, Iterable


SCHEMA_VERSION = 1
DEFAULT_MODEL = "gpt-5.4"
DEFAULT_REASONING = "medium"
TOOL_EVENT_TYPES = {
    "command_execution",
    "mcp_tool_call",
    "file_search",
    "web_search",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_questions(path: Path, root: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    questions = payload.get("questions")
    if payload.get("version") != 1 or not isinstance(questions, list):
        raise ValueError("questions file must contain version 1 and a questions list")
    if len(questions) != 20:
        raise ValueError(f"expected exactly 20 questions, found {len(questions)}")
    ids = [item.get("id") for item in questions]
    if len(set(ids)) != len(ids) or any(not item for item in ids):
        raise ValueError("question IDs must be present and unique")
    for question in questions:
        for expected in question.get("expected_files", []):
            if not (root / expected).exists():
                raise ValueError(
                    f"{question['id']}: expected file does not exist: {expected}"
                )
    return payload


def normalize_path(raw: str, root: Path) -> str:
    value = raw.strip().replace("\\", "/")
    root_text = root.resolve().as_posix()
    candidate = Path(value)
    if candidate.is_absolute():
        value = candidate.resolve(strict=False).as_posix()
    if value.startswith(root_text + "/"):
        value = value[len(root_text) + 1 :]
    if value.startswith("./"):
        value = value[2:]
    return value.lstrip("/")


def normalize_paths(values: Iterable[str], root: Path) -> list[str]:
    return sorted(
        {
            normalized
            for value in values
            if isinstance(value, str)
            for normalized in [normalize_path(value, root)]
            if normalized
        }
    )


def _walk_dicts(value: Any) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        yield value
        for nested in value.values():
            yield from _walk_dicts(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from _walk_dicts(nested)


def parse_usage(events: list[dict[str, Any]]) -> dict[str, int] | None:
    candidates: list[dict[str, int]] = []
    for event in events:
        for item in _walk_dicts(event):
            if "input_tokens" not in item or "output_tokens" not in item:
                continue
            candidates.append(
                {
                    "input_tokens": int(item.get("input_tokens") or 0),
                    "cached_input_tokens": int(item.get("cached_input_tokens") or 0),
                    "output_tokens": int(item.get("output_tokens") or 0),
                    "reasoning_output_tokens": int(
                        item.get("reasoning_output_tokens") or 0
                    ),
                }
            )
    nonzero = [
        candidate
        for candidate in candidates
        if candidate["input_tokens"] or candidate["output_tokens"]
    ]
    if not nonzero:
        return None
    usage = max(
        nonzero,
        key=lambda item: item["input_tokens"] + item["output_tokens"],
    )
    usage["total_tokens"] = usage["input_tokens"] + usage["output_tokens"]
    return usage


def extract_session_id(events: list[dict[str, Any]]) -> str | None:
    for event in events:
        for item in _walk_dicts(event):
            for key in ("thread_id", "session_id"):
                value = item.get(key)
                if isinstance(value, str) and value:
                    return value
    return None


def rollout_metrics(
    session_id: str | None,
) -> tuple[dict[str, int] | None, int | None]:
    if not session_id:
        return None, None
    codex_home = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
    sessions = codex_home / "sessions"
    if not sessions.is_dir():
        return None, None
    matches = list(sessions.glob(f"**/rollout-*-{session_id}.jsonl"))
    if not matches:
        return None, None
    path = max(matches, key=lambda candidate: candidate.stat().st_mtime)
    events: list[dict[str, Any]] = []
    cycles = 0
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("type") != "event_msg":
            continue
        payload = event.get("payload") or {}
        if payload.get("type") != "token_count":
            continue
        info = payload.get("info") or {}
        last_turn = info.get("last_token_usage")
        if isinstance(last_turn, dict) and (
            int(last_turn.get("input_tokens") or 0)
            or int(last_turn.get("output_tokens") or 0)
        ):
            cycles += 1
        block = info.get("total_token_usage") or info.get("last_token_usage")
        if isinstance(block, dict):
            events.append({"usage": block})
    return parse_usage(events), cycles or None


def count_agent_cycles(events: list[dict[str, Any]]) -> int:
    reasoning_items = 0
    for event in events:
        item = event.get("item")
        if (
            event.get("type") in {"item.started", "item.completed"}
            and isinstance(item, dict)
            and item.get("type") == "reasoning"
            and event.get("type") == "item.completed"
        ):
            reasoning_items += 1
    if reasoning_items:
        return reasoning_items
    completed_turns = sum(event.get("type") == "turn.completed" for event in events)
    return completed_turns or 1


def tracked_files(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "ls-files"],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    )
    return sorted(line for line in result.stdout.splitlines() if line)


def observed_files(
    events: list[dict[str, Any]],
    known_files: list[str],
) -> list[str]:
    tool_blobs: list[str] = []
    for event in events:
        event_blob = json.dumps(event, ensure_ascii=False)
        if any(tool_type in event_blob for tool_type in TOOL_EVENT_TYPES):
            tool_blobs.append(event_blob)
    blob = "\n".join(tool_blobs)
    return [path for path in known_files if path in blob]


def forbidden_match(cited: str, forbidden: str) -> bool:
    if forbidden.endswith("/"):
        return cited.startswith(forbidden)
    return cited == forbidden


def compute_score(
    cited_files: Iterable[str],
    expected_files: Iterable[str],
    forbidden_files: Iterable[str],
) -> dict[str, Any]:
    cited = set(cited_files)
    expected = list(expected_files)
    forbidden = list(forbidden_files)
    missing = sorted(path for path in expected if path not in cited)
    violations = sorted(
        path
        for path in cited
        if any(forbidden_match(path, denied) for denied in forbidden)
    )
    return {
        "correct": not missing and not violations,
        "missing_expected_files": missing,
        "forbidden_files_cited": violations,
    }


def parse_event_stream(raw: str) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for line in raw.splitlines():
        if not line.strip():
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return events


def load_existing_results(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    return {
        result["id"]: result
        for result in payload.get("results", [])
        if result.get("status") == "complete"
    }


def write_results(
    path: Path,
    *,
    model: str,
    reasoning: str,
    questions_path: Path,
    results: list[dict[str, Any]],
) -> None:
    correct = sum(result.get("correct") is True for result in results)
    complete = sum(result.get("status") == "complete" for result in results)
    totals = {
        "questions": len(results),
        "complete": complete,
        "correct": correct,
        "accuracy_percent": round(correct / len(results) * 100, 2)
        if results
        else 0.0,
        "input_tokens": sum(
            (result.get("usage") or {}).get("input_tokens", 0)
            for result in results
        ),
        "cached_input_tokens": sum(
            (result.get("usage") or {}).get("cached_input_tokens", 0)
            for result in results
        ),
        "output_tokens": sum(
            (result.get("usage") or {}).get("output_tokens", 0)
            for result in results
        ),
        "reasoning_output_tokens": sum(
            (result.get("usage") or {}).get("reasoning_output_tokens", 0)
            for result in results
        ),
        "total_tokens": sum(
            (result.get("usage") or {}).get("total_tokens", 0)
            for result in results
        ),
        "agent_cycles": sum(result.get("turns", 0) for result in results),
    }
    payload = {
        "schema_version": SCHEMA_VERSION,
        "questions_sha256": sha256_file(questions_path),
        "configuration": {
            "model": model,
            "reasoning": reasoning,
            "execution": "sequential-isolated",
            "sandbox": "read-only",
            "turn_definition": "completed reasoning cycles, falling back to completed Codex turns",
        },
        "aggregates": totals,
        "results": results,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        "w",
        encoding="utf-8",
        dir=path.parent,
        delete=False,
    ) as temporary:
        json.dump(payload, temporary, ensure_ascii=False, indent=2, sort_keys=True)
        temporary.write("\n")
        temporary_path = Path(temporary.name)
    temporary_path.replace(path)


def build_prompt(question: dict[str, Any]) -> str:
    return f"""Answer this repository question using only evidence from the current checkout:

{question["question"]}

Rules:
- Work read-only. Do not edit, create, delete, stage, commit, or format files.
- Use repository-relative paths.
- Inspect the minimum code and documentation needed for an accurate answer.
- The final structured response must contain:
  - answer: a concise answer in Portuguese;
  - cited_files: every repository file used as evidence in the answer;
  - files_read: every repository file whose content you inspected.
- Do not cite a path merely because it appeared in this prompt or another document.
"""


def run_question(
    root: Path,
    question: dict[str, Any],
    *,
    model: str,
    reasoning: str,
    schema_path: Path,
    known_files: list[str],
    timeout_seconds: int,
) -> dict[str, Any]:
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="sdd-eval-") as temporary:
        final_path = Path(temporary) / "final.json"
        command = [
            "codex",
            "exec",
            "--json",
            "--sandbox",
            "read-only",
            "--model",
            model,
            "-c",
            f'model_reasoning_effort="{reasoning}"',
            "--output-schema",
            str(schema_path),
            "--output-last-message",
            str(final_path),
            "--cd",
            str(root),
            build_prompt(question),
        ]
        process = subprocess.run(
            command,
            cwd=root,
            text=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout_seconds,
            check=False,
        )
        events = parse_event_stream(process.stdout)
        session_id = extract_session_id(events)
        rollout_usage, rollout_cycles = rollout_metrics(session_id)
        usage = parse_usage(events) or rollout_usage
        error: str | None = None
        response: dict[str, Any] | None = None
        if process.returncode != 0:
            error = process.stderr.strip() or f"codex exited with {process.returncode}"
        elif not final_path.exists():
            error = "Codex did not write the structured final response"
        else:
            try:
                response = json.loads(final_path.read_text(encoding="utf-8"))
            except json.JSONDecodeError as exc:
                error = f"invalid structured final response: {exc}"
        if usage is None:
            error = f"{error}; telemetry unavailable" if error else "telemetry unavailable"

        cited = normalize_paths(
            (response or {}).get("cited_files", []),
            root,
        )
        reported = normalize_paths(
            (response or {}).get("files_read", []),
            root,
        )
        observed = observed_files(events, known_files)
        score = compute_score(
            cited,
            question.get("expected_files", []),
            question.get("forbidden_files", []),
        )
        turns = rollout_cycles or count_agent_cycles(events)
        status = "complete" if error is None and turns > 0 else "invalid"
        return {
            "id": question["id"],
            "layer": question["layer"],
            "question": question["question"],
            "status": status,
            "error": error,
            "answer": (response or {}).get("answer", ""),
            "cited_files": cited,
            "reported_files_read": reported,
            "observed_files": observed,
            "files_read": reported,
            "expected_files": question.get("expected_files", []),
            "forbidden_files": question.get("forbidden_files", []),
            **score,
            "usage": usage,
            "turns": turns,
            "latency_ms": round((time.monotonic() - started) * 1000),
            "session_id": session_id,
        }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=script_dir.parents[2])
    parser.add_argument("--questions", type=Path, default=script_dir / "questions.yaml")
    parser.add_argument(
        "--schema",
        type=Path,
        default=script_dir / "response.schema.json",
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--reasoning", default=DEFAULT_REASONING)
    parser.add_argument("--timeout-seconds", type=int, default=900)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--only", action="append", default=[])
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    questions_payload = load_questions(args.questions, root)
    existing = load_existing_results(args.output) if args.resume else {}
    known_files = tracked_files(root)
    requested = set(args.only)
    results: list[dict[str, Any]] = []
    for index, question in enumerate(questions_payload["questions"], start=1):
        if requested and question["id"] not in requested:
            continue
        if question["id"] in existing:
            result = existing[question["id"]]
            print(f"[{index:02d}/20] {question['id']}: resumed", flush=True)
        else:
            print(f"[{index:02d}/20] {question['id']}: running", flush=True)
            try:
                result = run_question(
                    root,
                    question,
                    model=args.model,
                    reasoning=args.reasoning,
                    schema_path=args.schema.resolve(),
                    known_files=known_files,
                    timeout_seconds=args.timeout_seconds,
                )
            except subprocess.TimeoutExpired:
                result = {
                    "id": question["id"],
                    "layer": question["layer"],
                    "question": question["question"],
                    "status": "invalid",
                    "error": f"timed out after {args.timeout_seconds}s",
                    "correct": False,
                    "usage": None,
                    "turns": 0,
                }
            print(
                f"[{index:02d}/20] {question['id']}: "
                f"{result['status']}, correct={result.get('correct')}",
                flush=True,
            )
        results.append(result)
        write_results(
            args.output,
            model=args.model,
            reasoning=args.reasoning,
            questions_path=args.questions,
            results=results,
        )
    invalid = [result for result in results if result.get("status") != "complete"]
    return 2 if invalid else 0


if __name__ == "__main__":
    raise SystemExit(main())
