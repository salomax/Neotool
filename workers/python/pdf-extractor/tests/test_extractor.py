from __future__ import annotations

from unittest.mock import patch

from extractor import extract_pdf_document


class _FakePage:
    def __init__(self, tables, text):
        self._tables = tables
        self._text = text

    def extract_tables(self):
        return self._tables

    def extract_text(self):
        return self._text


class _FakePdf:
    def __init__(self, pages):
        self.pages = pages

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


@patch("extractor.pdfplumber.open")
def test_extract_pdf_document_serializes_tables_and_text(mock_open):
    pages = [
        _FakePage(
            tables=[
                [["Data", "Descricao", "Valor"], ["15/01", "MERCADO EXTRA", "45,90"]]
            ],
            text="Nubank fatura de cartao",
        )
    ]
    mock_open.return_value = _FakePdf(pages)

    result = extract_pdf_document(b"%PDF-1.7 fake")

    assert "| Data | Descricao | Valor |" in result.extracted_text
    assert "MERCADO EXTRA" in result.extracted_text
    assert result.metadata.page_count == 1
    assert result.metadata.tables_found == 1
    assert result.metadata.bank_name_hint == "Nubank"


@patch("extractor.pdfplumber.open")
def test_extract_pdf_document_counts_multiple_pages(mock_open):
    pages = [
        _FakePage(tables=[], text="Itaú pagina 1"),
        _FakePage(tables=[[["A", "B"], ["1", "2"]]], text=""),
    ]
    mock_open.return_value = _FakePdf(pages)

    result = extract_pdf_document(b"%PDF-1.7 fake")

    assert result.metadata.page_count == 2
    assert result.metadata.tables_found == 1
    assert "--- Page 1 ---" in result.extracted_text
    assert "--- Page 2 ---" in result.extracted_text
