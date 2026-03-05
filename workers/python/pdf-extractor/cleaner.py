from __future__ import annotations

import re
from typing import Iterable

BOILERPLATE_PATTERNS = [
    re.compile(r"acumule\s+pontos", re.IGNORECASE),
    re.compile(r"central\s+de\s+atendimento", re.IGNORECASE),
    re.compile(r"\bsac\b", re.IGNORECASE),
    re.compile(r"ouvidoria", re.IGNORECASE),
    re.compile(r"endere[cç]o", re.IGNORECASE),
    re.compile(r"fale\s+conosco", re.IGNORECASE),
]

_WS_RE = re.compile(r"\s+")


def clean_extracted_text(text: str, max_chars: int = 12_000) -> str:
    """Clean extraction output while preserving table regions."""
    if not text:
        return ""

    lines = text.splitlines()
    lines = _remove_boilerplate(lines)
    lines = _normalize_lines(lines)
    lines = _collapse_blank_lines(lines)
    lines = _truncate_preserving_tables(lines, max_chars=max_chars)

    return "\n".join(lines).strip()


def _remove_boilerplate(lines: Iterable[str]) -> list[str]:
    result: list[str] = []
    for raw_line in lines:
        line = raw_line.strip()
        if line and any(p.search(line) for p in BOILERPLATE_PATTERNS):
            continue
        result.append(raw_line)
    return result


def _normalize_lines(lines: Iterable[str]) -> list[str]:
    result: list[str] = []
    for line in lines:
        stripped = line.rstrip()
        if stripped.strip().startswith("|"):
            result.append(_normalize_table_line(stripped))
        else:
            result.append(_WS_RE.sub(" ", stripped).strip())
    return result


def _normalize_table_line(line: str) -> str:
    pieces = [segment.strip() for segment in line.split("|")]
    if pieces and pieces[0] == "":
        pieces = pieces[1:]
    if pieces and pieces[-1] == "":
        pieces = pieces[:-1]
    normalized = ["---" if part == "" else _WS_RE.sub(" ", part) for part in pieces]
    return "| " + " | ".join(normalized) + " |"


def _collapse_blank_lines(lines: Iterable[str]) -> list[str]:
    result: list[str] = []
    previous_blank = False
    for line in lines:
        is_blank = line.strip() == ""
        if is_blank and previous_blank:
            continue
        result.append(line)
        previous_blank = is_blank
    return result


def _truncate_preserving_tables(lines: list[str], max_chars: int) -> list[str]:
    if _text_length(lines) <= max_chars:
        return lines

    keep = [True] * len(lines)
    for idx in range(len(lines) - 1, -1, -1):
        if _text_length([line for i, line in enumerate(lines) if keep[i]]) <= max_chars:
            break
        if lines[idx].strip().startswith("|"):
            continue
        keep[idx] = False

    truncated = [line for i, line in enumerate(lines) if keep[i]]
    # If tables alone exceed max_chars, keep them intact (do not trim table rows).
    return truncated


def _text_length(lines: list[str]) -> int:
    if not lines:
        return 0
    return sum(len(line) for line in lines) + (len(lines) - 1)
