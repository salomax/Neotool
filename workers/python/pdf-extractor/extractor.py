from __future__ import annotations

import io
import re
import time

import pdfplumber

from schema import ExtractionMetadata, ExtractionResult

class ExtractionError(RuntimeError):
    pass

BANK_HINT_PATTERNS = [
    re.compile(r"nubank", re.IGNORECASE),
    re.compile(r"ita[uú]", re.IGNORECASE),
    re.compile(r"bradesco", re.IGNORECASE),
    re.compile(r"santander", re.IGNORECASE),
    re.compile(r"\binter\b", re.IGNORECASE),
    re.compile(r"c6\s*bank", re.IGNORECASE),
]


def extract_pdf_document(pdf_bytes: bytes) -> ExtractionResult:
    started = time.perf_counter()
    sections: list[str] = []
    tables_found = 0
    first_page_text = ""

    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        page_count = len(pdf.pages)
        for page_number, page in enumerate(pdf.pages, start=1):
            page_blocks: list[str] = []

            tables = page.extract_tables() or []
            for table in tables:
                markdown = _table_to_markdown(table)
                if markdown:
                    page_blocks.append(markdown)
                    tables_found += 1

            plain_text = (page.extract_text() or "").strip()
            if page_number == 1:
                first_page_text = plain_text
            if plain_text:
                page_blocks.append(plain_text)

            if page_blocks:
                sections.append(f"--- Page {page_number} ---\n" + "\n\n".join(page_blocks))

    extracted_text = "\n\n".join(sections).strip()
    if not extracted_text:
        raise ExtractionError("no_extractable_content")

    metadata = ExtractionMetadata(
        page_count=page_count,
        tables_found=tables_found,
        bank_name_hint=_detect_bank_hint(first_page_text),
        extraction_duration_ms=int((time.perf_counter() - started) * 1000),
    )
    return ExtractionResult(extracted_text=extracted_text, metadata=metadata)


def _table_to_markdown(table: list[list[object | None]]) -> str:
    rows: list[list[str]] = []
    for row in table:
        values = [_normalize_cell(cell) for cell in row]
        if any(cell for cell in values):
            rows.append(values)

    if not rows:
        return ""

    width = max(len(row) for row in rows)
    rows = [row + [""] * (width - len(row)) for row in rows]

    header = rows[0]
    separator = ["---"] * width
    body = rows[1:]

    markdown_lines = [
        _markdown_row(header),
        _markdown_row(separator),
    ]
    markdown_lines.extend(_markdown_row(row) for row in body)
    return "\n".join(markdown_lines)


def _normalize_cell(cell: object | None) -> str:
    if cell is None:
        return ""
    text = str(cell)
    return " ".join(text.split())


def _markdown_row(values: list[str]) -> str:
    return "| " + " | ".join(values) + " |"


def _detect_bank_hint(text: str) -> str | None:
    if not text:
        return None
    for pattern in BANK_HINT_PATTERNS:
        match = pattern.search(text)
        if match:
            token = match.group(0)
            return token.strip().title()
    return None
