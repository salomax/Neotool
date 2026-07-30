#!/usr/bin/env python3
"""Deterministic documentation audit for the Invistus repository."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import subprocess
from collections import Counter
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import unquote


SCHEMA_VERSION = 1
MARKDOWN_LINK_RE = re.compile(r"(?<!!)\[[^\]]*]\(([^)]+)\)")
EXTERNAL_SCHEMES = ("http://", "https://", "mailto:", "tel:", "data:")


def run_git(root: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )


def count_words(files: Iterable[Path]) -> int:
    paths = list(files)
    if not paths:
        return 0
    env = os.environ.copy()
    env["LC_ALL"] = "C"
    result = subprocess.run(
        ["wc", "-w", *[str(path) for path in paths]],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        check=True,
    )
    last_line = result.stdout.strip().splitlines()[-1]
    return int(last_line.split()[0])


def has_frontmatter(text: str) -> bool:
    if not text.startswith("---\n"):
        return False
    return "\n---\n" in text[4:]


def normalize_link_target(raw: str) -> str | None:
    target = raw.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    if " " in target and not target.startswith(EXTERNAL_SCHEMES):
        target = target.split()[0]
    target = unquote(target.split("#", 1)[0].split("?", 1)[0])
    if not target or target.startswith(("#", *EXTERNAL_SCHEMES)):
        return None
    return target


def resolve_link(root: Path, source: Path, raw: str) -> Path | None:
    target = normalize_link_target(raw)
    if target is None:
        return None
    if target.startswith("/"):
        return (root / target.lstrip("/")).resolve(strict=False)
    return (source.parent / target).resolve(strict=False)


def collect_links(root: Path, files: list[Path]) -> tuple[list[dict[str, Any]], Counter[str]]:
    links: list[dict[str, Any]] = []
    incoming: Counter[str] = Counter()
    for source in files:
        text = source.read_text(encoding="utf-8", errors="replace")
        for match in MARKDOWN_LINK_RE.finditer(text):
            raw = match.group(1)
            resolved = resolve_link(root, source, raw)
            if resolved is None:
                continue
            try:
                resolved_relative = resolved.relative_to(root).as_posix()
            except ValueError:
                resolved_relative = str(resolved)
            exists = resolved.exists()
            source_relative = source.relative_to(root).as_posix()
            record = {
                "source": source_relative,
                "line": text.count("\n", 0, match.start()) + 1,
                "target": raw,
                "resolved": resolved_relative,
                "exists": exists,
            }
            links.append(record)
            if exists and resolved.is_file() and resolved.suffix.lower() == ".md":
                incoming[resolved_relative] += 1
    return links, incoming


def last_commit_date(root: Path, relative_path: str) -> str | None:
    tracked = run_git(root, "ls-files", "--error-unmatch", "--", relative_path)
    if tracked.returncode != 0:
        return None
    result = run_git(
        root,
        "log",
        "-1",
        "--follow",
        "--format=%cs",
        "--",
        relative_path,
    )
    value = result.stdout.strip()
    if not value:
        value = run_git(
            root,
            "log",
            "-1",
            "--format=%cs",
            "--",
            relative_path,
        ).stdout.strip()
    return value or None


def collect_history(
    root: Path,
    files: list[Path],
    as_of: date,
    *,
    enabled: bool,
) -> tuple[list[dict[str, Any]], list[str]]:
    stale: list[dict[str, Any]] = []
    uncommitted: list[str] = []
    if not enabled:
        return stale, [path.relative_to(root).as_posix() for path in files]
    for path in files:
        relative = path.relative_to(root).as_posix()
        committed = last_commit_date(root, relative)
        if committed is None:
            uncommitted.append(relative)
            continue
        committed_date = date.fromisoformat(committed)
        age_days = (as_of - committed_date).days
        if age_days > 180:
            stale.append(
                {
                    "path": relative,
                    "last_commit": committed,
                    "age_days": age_days,
                }
            )
    stale.sort(key=lambda item: item["path"])
    return stale, sorted(uncommitted)


def audit_repository(
    root: Path,
    *,
    as_of: date,
    git_history: bool = True,
) -> dict[str, Any]:
    root = root.resolve()
    docs = root / "docs"
    if not docs.is_dir():
        raise ValueError(f"documentation directory not found: {docs}")

    files = sorted(docs.rglob("*.md"))
    relative_files = [path.relative_to(root).as_posix() for path in files]
    texts = {
        path: path.read_text(encoding="utf-8", errors="replace")
        for path in files
    }
    word_count = count_words(files)
    links, incoming = collect_links(root, files)
    broken_links = [link for link in links if not link["exists"]]
    with_frontmatter = sorted(
        path.relative_to(root).as_posix()
        for path, text in texts.items()
        if has_frontmatter(text)
    )
    without_frontmatter = sorted(set(relative_files) - set(with_frontmatter))
    stale, uncommitted = collect_history(
        root,
        files,
        as_of,
        enabled=git_history,
    )
    orphans = sorted(
        relative
        for relative in relative_files
        if incoming[relative] == 0
    )
    basename_counts = Counter(path.name for path in files)
    duplicate_basenames = [
        {
            "basename": basename,
            "count": count,
            "paths": sorted(
                path.relative_to(root).as_posix()
                for path in files
                if path.name == basename
            ),
        }
        for basename, count in sorted(basename_counts.items())
        if count > 1
    ]

    return {
        "schema_version": SCHEMA_VERSION,
        "as_of": as_of.isoformat(),
        "documentation_root": "docs",
        "markdown": {
            "files": len(files),
            "words": word_count,
            "estimated_tokens": math.ceil(word_count * 1.35),
            "readme_files": sum(path.name == "README.md" for path in files),
        },
        "links": {
            "internal": len(links),
            "broken": len(broken_links),
            "broken_items": broken_links,
        },
        "frontmatter": {
            "present": len(with_frontmatter),
            "missing": len(without_frontmatter),
            "coverage_percent": round(
                (len(with_frontmatter) / len(files) * 100) if files else 100.0,
                2,
            ),
            "missing_files": without_frontmatter,
        },
        "history": {
            "stale_over_180_days": len(stale),
            "stale_items": stale,
            "uncommitted": len(uncommitted),
            "uncommitted_files": uncommitted,
        },
        "orphans": {
            "count": len(orphans),
            "files": orphans,
        },
        "duplicate_basenames": {
            "names": len(duplicate_basenames),
            "files": sum(item["count"] for item in duplicate_basenames),
            "items": duplicate_basenames,
        },
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--as-of",
        type=date.fromisoformat,
        default=date.today(),
        help="Fixed date used for staleness calculations (YYYY-MM-DD)",
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument("--pretty", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    payload = audit_repository(args.root, as_of=args.as_of)
    rendered = json.dumps(
        payload,
        ensure_ascii=False,
        indent=2 if args.pretty else None,
        sort_keys=True,
    )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    else:
        print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
