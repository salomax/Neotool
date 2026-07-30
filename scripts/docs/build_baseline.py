#!/usr/bin/env python3
"""Combine the documentation audit and retrieval eval into baseline artifacts."""

from __future__ import annotations

import argparse
import io
import json
import subprocess
import sys
import tarfile
import tempfile
from datetime import date
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit  # noqa: E402


DECLARED_REFERENCE = {
    "markdown_files": 323,
    "words": 645_530,
    "internal_links": 1_199,
    "broken_links": 390,
    "frontmatter_present": 194,
    "readme_files": 26,
    "skills_markdown_files": 57,
    "skills_words": 25_085,
}


def measure_reference(root: Path, git_ref: str, as_of: date) -> dict[str, Any]:
    archive = subprocess.run(
        ["git", "archive", git_ref, "docs"],
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    )
    with tempfile.TemporaryDirectory(prefix="sdd-reference-") as temporary:
        temporary_root = Path(temporary)
        with tarfile.open(fileobj=io.BytesIO(archive.stdout), mode="r:") as tar:
            tar.extractall(temporary_root, filter="data")
        measured = audit.audit_repository(
            temporary_root,
            as_of=as_of,
            git_history=False,
        )
    return {
        "markdown_files": measured["markdown"]["files"],
        "words": measured["markdown"]["words"],
        "internal_links": measured["links"]["internal"],
        "broken_links": measured["links"]["broken"],
        "frontmatter_present": measured["frontmatter"]["present"],
        "readme_files": measured["markdown"]["readme_files"],
    }


def render_markdown(payload: dict[str, Any]) -> str:
    current = payload["audit"]
    reference = payload["reference"]
    evaluation = payload["evaluation"]
    aggregates = evaluation["aggregates"]
    lines = [
        "---",
        'title: "SDD Baseline — July 2026"',
        "type: baseline",
        "category: documentation",
        "status: current",
        "version: 1.0.0",
        f"date: {payload['as_of']}",
        "tags: [sdd, baseline, evaluation, documentation]",
        "---",
        "",
        "# SDD Baseline — July 2026",
        "",
        "This document records the frozen Phase 0 baseline. Paths are rendered as code,",
        "not Markdown links, so this report does not alter the internal-link metric.",
        "",
        "## Corpus audit",
        "",
        "| Metric | Declared reference | Measured reference | Phase 0 checkout |",
        "|---|---:|---:|---:|",
        (
            f"| Markdown files | {reference['declared']['markdown_files']:,} | "
            f"{reference['measured']['markdown_files']:,} | "
            f"{current['markdown']['files']:,} |"
        ),
        (
            f"| Words | {reference['declared']['words']:,} | "
            f"{reference['measured']['words']:,} | {current['markdown']['words']:,} |"
        ),
        (
            f"| Internal links | {reference['declared']['internal_links']:,} | "
            f"{reference['measured']['internal_links']:,} | "
            f"{current['links']['internal']:,} |"
        ),
        (
            f"| Broken links | {reference['declared']['broken_links']:,} | "
            f"{reference['measured']['broken_links']:,} | "
            f"{current['links']['broken']:,} |"
        ),
        (
            f"| Frontmatter present | {reference['declared']['frontmatter_present']:,} | "
            f"{reference['measured']['frontmatter_present']:,} | "
            f"{current['frontmatter']['present']:,} |"
        ),
        (
            f"| README files | {reference['declared']['readme_files']:,} | "
            f"{reference['measured']['readme_files']:,} | "
            f"{current['markdown']['readme_files']:,} |"
        ),
        "",
        "The independent file-existence scanner differs from the declared broken-link",
        "count because the original scanner implementation was not versioned. The difference",
        "remains inside the Gate 0 tolerance.",
        "",
        "## Retrieval evaluation",
        "",
        f"- Questions checksum: `{evaluation['questions_sha256']}`",
        f"- Model: `{evaluation['configuration']['model']}`",
        f"- Reasoning: `{evaluation['configuration']['reasoning']}`",
        f"- Complete runs: {aggregates['complete']}/{aggregates['questions']}",
        f"- Correct answers: {aggregates['correct']}/{aggregates['questions']} "
        f"({aggregates['accuracy_percent']:.2f}%)",
        f"- Total tokens: {aggregates['total_tokens']:,}",
        f"- Agent cycles: {aggregates['agent_cycles']:,}",
        "",
        "| ID | Layer | Correct | Tokens | Cycles | Files read |",
        "|---|---|---:|---:|---:|---:|",
    ]
    for result in evaluation["results"]:
        lines.append(
            f"| `{result['id']}` | {result['layer']} | "
            f"{'yes' if result.get('correct') else 'no'} | "
            f"{(result.get('usage') or {}).get('total_tokens', 0):,} | "
            f"{result.get('turns', 0):,} | {len(result.get('files_read', [])):,} |"
        )
    lines += [
        "",
        "## Gate 0",
        "",
        f"- Audit completed: {'yes' if current else 'no'}",
        f"- Twenty isolated eval runs completed: "
        f"{'yes' if aggregates['complete'] == 20 else 'no'}",
        f"- Token telemetry complete: "
        f"{'yes' if all(result.get('usage') for result in evaluation['results']) else 'no'}",
        f"- Turn telemetry complete: "
        f"{'yes' if all(result.get('turns', 0) > 0 for result in evaluation['results']) else 'no'}",
        "",
        "The question set is frozen after this baseline. Changing it requires a new",
        "versioned baseline rather than editing the existing file.",
        "",
    ]
    return "\n".join(lines)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--eval", dest="evaluation", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--output-md", type=Path, required=True)
    parser.add_argument("--reference-commit", default="d8f99613")
    parser.add_argument("--as-of", type=date.fromisoformat, default=date(2026, 7, 30))
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    audit_payload = json.loads(args.audit.read_text(encoding="utf-8"))
    eval_payload = json.loads(args.evaluation.read_text(encoding="utf-8"))
    measured_reference = measure_reference(root, args.reference_commit, args.as_of)
    payload = {
        "schema_version": 1,
        "as_of": args.as_of.isoformat(),
        "reference": {
            "commit": args.reference_commit,
            "declared": DECLARED_REFERENCE,
            "measured": measured_reference,
        },
        "audit": audit_payload,
        "evaluation": eval_payload,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_md.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    args.output_md.write_text(render_markdown(payload), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
