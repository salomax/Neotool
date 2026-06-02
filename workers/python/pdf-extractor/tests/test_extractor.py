from __future__ import annotations

from unittest.mock import patch

import pytest
from pdfminer.pdfdocument import PDFPasswordIncorrect

from extractor import ExtractionError, PdfPasswordRequiredError, extract_pdf_document


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


@patch("extractor.pdfplumber.open")
def test_extract_pdf_document_includes_multiple_card_sections(mock_open):
    pages = [
        _FakePage(
            tables=[
                [["Data", "Descricao", "Valor"], ["10/01", "CARTAO 1234", "100,00"]],
                [["Data", "Descricao", "Valor"], ["11/01", "CARTAO 5678", "200,00"]],
            ],
            text="Bradesco",
        )
    ]
    mock_open.return_value = _FakePdf(pages)

    result = extract_pdf_document(b"%PDF-1.7 fake")

    assert "CARTAO 1234" in result.extracted_text
    assert "CARTAO 5678" in result.extracted_text
    assert result.metadata.tables_found == 2


@patch("extractor.pdfplumber.open")
def test_empty_pdf_raises_extraction_error(mock_open):
    mock_open.return_value = _FakePdf([_FakePage(tables=[], text="")])

    with pytest.raises(ExtractionError):
        extract_pdf_document(b"%PDF-1.7 empty")


@patch("extractor.pdfplumber.open")
def test_extract_pdf_document_forwards_password(mock_open):
    mock_open.return_value = _FakePdf([_FakePage(tables=[], text="Nubank bill page")])

    extract_pdf_document(b"%PDF-1.7 fake", password="secret")

    _, kwargs = mock_open.call_args
    assert kwargs["password"] == "secret"


@patch("extractor.pdfplumber.open")
def test_extract_pdf_document_encrypted_without_password_raises_password_required(mock_open):
    mock_open.side_effect = PDFPasswordIncorrect("incorrect password")

    with pytest.raises(PdfPasswordRequiredError):
        extract_pdf_document(b"%PDF-1.7 fake")
