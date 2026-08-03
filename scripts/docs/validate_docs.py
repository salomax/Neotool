#!/usr/bin/env python3
"""Scoped mechanical validation for the current engineering docs corpus."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REQUIRED_FRONTMATTER = (
    "title",
    "type",
    "status",
    "updated",
    "owners",
    "source_of_truth",
    "load_when",
    "supersedes",
)

MARKDOWN_LINK_RE = re.compile(r"(?<!!)\[[^\]]*]\(([^)]+)\)")
EXTERNAL_SCHEMES = ("http://", "https://", "mailto:", "tel:", "data:")
WORD_CAP = 1200
INDEX_TYPES = {"index"}


def parse_frontmatter(text: str) -> dict[str, str] | None:
    if not text.startswith("---\n"):
        return None
    end = text.find("\n---\n", 4)
    if end < 0:
        return None
    block = text[4:end]
    keys: dict[str, str] = {}
    current: str | None = None
    for line in block.splitlines():
        if not line.strip():
            continue
        if re.match(r"^[a-zA-Z0-9_]+:", line) and not line.startswith(" "):
            key, _, value = line.partition(":")
            current = key.strip()
            keys[current] = value.strip()
        elif current and (line.startswith("  ") or line.startswith("- ")):
            keys[current] = (keys.get(current, "") + " " + line.strip()).strip()
    return keys


def normalize_link(raw: str) -> str | None:
    target = raw.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    if " " in target and not target.startswith(EXTERNAL_SCHEMES):
        target = target.split()[0]
    target = target.split("#", 1)[0].split("?", 1)[0]
    if not target or target.startswith(("#", *EXTERNAL_SCHEMES)):
        return None
    return target


def word_count(text: str) -> int:
    body = text
    if text.startswith("---\n"):
        end = text.find("\n---\n", 4)
        if end >= 0:
            body = text[end + 5 :]
    return len(re.findall(r"\S+", body))


def validate(root: Path, docs_root: Path, updated: str) -> list[str]:
    errors: list[str] = []
    files = sorted(docs_root.rglob("*.md"))
    if not files:
        return [f"no markdown files under {docs_root}"]

    for path in files:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(root)
        fm = parse_frontmatter(text)
        if fm is None:
            errors.append(f"{rel}: missing frontmatter")
            continue
        for key in REQUIRED_FRONTMATTER:
            if key not in fm:
                errors.append(f"{rel}: missing frontmatter key `{key}`")
        if fm.get("updated") != updated:
            errors.append(
                f"{rel}: updated={fm.get('updated')!r} expected {updated!r}"
            )
        words = word_count(text)
        doc_type = fm.get("type", "")
        if words > WORD_CAP and doc_type not in INDEX_TYPES:
            # runbooks with command tables are also allowed later; indexes exempt
            if doc_type != "runbook":
                errors.append(f"{rel}: {words} words exceeds {WORD_CAP}")

        for match in MARKDOWN_LINK_RE.finditer(text):
            target = normalize_link(match.group(1))
            if target is None:
                continue
            if target.startswith("/"):
                resolved = (root / target.lstrip("/")).resolve()
            else:
                resolved = (path.parent / target).resolve()
            try:
                resolved.relative_to(root.resolve())
            except ValueError:
                # outside repo is fine for absolute non-repo refs; still must exist
                pass
            if not resolved.exists():
                errors.append(f"{rel}: broken link -> {match.group(1)}")

        # Evidence paths listed in source_of_truth (best-effort)
        sot = fm.get("source_of_truth", "")
        for token in re.findall(r"[A-Za-z0-9_./-]+\.[A-Za-z0-9]+|[A-Za-z0-9_./-]+/", sot):
            candidate = token.rstrip("/")
            if candidate.startswith("docs/") or candidate.startswith("http"):
                continue
            # skip bare section dirs that may not exist yet as files
            p = root / candidate
            if candidate.endswith(".md") or candidate.endswith(".json") or candidate.endswith(".kts") or candidate.endswith(".yml") or candidate.endswith(".yaml") or candidate.endswith(".toml") or "/" in candidate:
                if not p.exists() and not (root / candidate.rstrip("/")).exists():
                    # Allow known top-level names without extension.
                    if candidate in {"infra", "AGENTS.md", "RTK.md", ".agents/skills"}:
                        if not (root / candidate).exists():
                            errors.append(f"{rel}: source_of_truth missing {candidate}")
                    elif any(candidate.endswith(ext) for ext in (".md", ".json", ".kts", ".yml", ".yaml", ".toml", ".py", ".sh")):
                        if not p.exists():
                            errors.append(f"{rel}: source_of_truth missing {candidate}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--docs", type=Path, default=Path("docs"))
    parser.add_argument("--updated", default="2026-08-03")
    args = parser.parse_args()
    root = args.root.resolve()
    docs_root = (root / args.docs).resolve() if not args.docs.is_absolute() else args.docs
    errors = validate(root, docs_root, args.updated)
    if errors:
        print(f"FAIL ({len(errors)} issues)")
        for err in errors:
            print(f" - {err}")
        return 1
    print("PASS")
    print(f" checked {len(list(docs_root.rglob('*.md')))} markdown files under {docs_root.relative_to(root)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
