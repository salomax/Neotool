"""
Unit and integration tests for report catalog fetching task.

Tests cover:
- CSV parsing logic (pure functions)
- API integration (mocked)
- Error handling
- Edge cases (empty catalog, missing columns)
"""

from unittest.mock import MagicMock, Mock, patch

import pytest

import requests
from tasks.report_catalog_fetch import (
    MAX_REPORTS,
    fetch_catalog_csv,
    fetch_report_catalog,
    parse_csv_to_reports,
)

# ============================================================================
# Unit Tests - Pure Functions (No Network)
# ============================================================================


class TestParseCsvToReports:
    """Test parse_csv_to_reports function."""

    def test_parse_valid_csv(self):
        """Test parsing valid CSV with headers."""
        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
Relatรณrio 2,2
Relatรณrio 3,3"""

        reports = parse_csv_to_reports(csv_content)

        assert len(reports) == 3
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[0]["numero_relatorio"] == "1"
        assert reports[1]["nome_relatorio"] == "Relatรณrio 2"
        assert reports[1]["numero_relatorio"] == "2"
        assert reports[2]["nome_relatorio"] == "Relatรณrio 3"
        assert reports[2]["numero_relatorio"] == "3"

    def test_parse_csv_with_whitespace(self):
        """Test parsing CSV with whitespace in values."""
        csv_content = """NomeRelatorio,NumeroRelatorio
  Relatรณrio 1  ,  1
Relatรณrio 2,2"""

        reports = parse_csv_to_reports(csv_content)

        assert len(reports) == 2
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"  # Trimmed
        assert reports[0]["numero_relatorio"] == "1"  # Trimmed
        assert reports[1]["nome_relatorio"] == "Relatรณrio 2"
        assert reports[1]["numero_relatorio"] == "2"

    def test_parse_csv_case_insensitive_columns(self):
        """Test that CSV parsing handles case-insensitive column names."""
        csv_content = """nomerelatorio,numerorelatorio
Relatรณrio 1,1
Relatรณrio 2,2"""

        reports = parse_csv_to_reports(csv_content)

        # Should still map correctly (case-insensitive matching)
        assert len(reports) == 2
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[0]["numero_relatorio"] == "1"

    def test_parse_csv_with_bom_header(self):
        """Test that CSV with BOM in header is handled."""
        csv_content = "\ufeffNomeRelatorio,NumeroRelatorio\nRelatรณrio 1,1"

        reports = parse_csv_to_reports(csv_content)

        assert len(reports) == 1
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[0]["numero_relatorio"] == "1"

    def test_parse_empty_csv(self):
        """Test that empty CSV raises ValueError."""
        with pytest.raises(ValueError, match="CSV content is empty"):
            parse_csv_to_reports("")

        with pytest.raises(ValueError, match="CSV content is empty"):
            parse_csv_to_reports("   ")

    def test_parse_csv_no_headers(self):
        """Test that CSV without headers raises ValueError."""
        csv_content = """Relatรณrio 1,1
Relatรณrio 2,2"""

        # DictReader will use first row as headers, but we check for required columns
        with pytest.raises(ValueError, match="CSV missing required columns"):
            parse_csv_to_reports(csv_content)

    def test_parse_csv_missing_required_columns(self):
        """Test that missing required columns raises ValueError."""
        csv_content = """NomeRelatorio
Relatรณrio 1"""

        with pytest.raises(ValueError, match="CSV missing required columns"):
            parse_csv_to_reports(csv_content)

    def test_parse_csv_skips_rows_with_missing_fields(self):
        """Test that rows with missing required fields are skipped."""
        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
,2
Relatรณrio 3,
Relatรณrio 4,4"""

        mock_logger = MagicMock()
        reports = parse_csv_to_reports(csv_content, logger=mock_logger)

        # Should only have 2 reports (skipped rows 2 and 3)
        assert len(reports) == 2
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[0]["numero_relatorio"] == "1"
        assert reports[1]["nome_relatorio"] == "Relatรณrio 4"
        assert reports[1]["numero_relatorio"] == "4"

        # Should log warnings for skipped rows
        assert mock_logger.warning.call_count >= 2
        warning_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any("Skipped row" in str(call) for call in warning_calls)
        assert any(
            "missing required fields" in str(call).lower() for call in warning_calls
        )

    def test_parse_csv_logs_skipped_rows(self):
        """Test that skipped rows are logged when logger is provided."""
        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
,2
Relatรณrio 3,3"""

        mock_logger = MagicMock()
        reports = parse_csv_to_reports(csv_content, logger=mock_logger)

        assert len(reports) == 2

        # Should log warning for skipped row
        assert mock_logger.warning.call_count >= 1
        warning_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any("Skipped row" in str(call) for call in warning_calls)
        assert any(
            "missing required fields" in str(call).lower() for call in warning_calls
        )

    def test_parse_csv_with_empty_values(self):
        """Test parsing CSV with empty values (should be skipped)."""
        csv_content = """NomeRelatorio,NumeroRelatorio
,1
Relatรณrio 2,
Relatรณrio 3,3"""

        mock_logger = MagicMock()
        reports = parse_csv_to_reports(csv_content, logger=mock_logger)

        # Should only have 1 report (rows 1 and 2 are skipped)
        assert len(reports) == 1
        assert reports[0]["nome_relatorio"] == "Relatรณrio 3"
        assert reports[0]["numero_relatorio"] == "3"

    def test_parse_csv_single_report(self):
        """Test parsing CSV with single report."""
        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio ร�nico,42"""

        reports = parse_csv_to_reports(csv_content)

        assert len(reports) == 1
        assert reports[0]["nome_relatorio"] == "Relatรณrio ร�nico"
        assert reports[0]["numero_relatorio"] == "42"

    def test_parse_csv_with_special_characters(self):
        """Test parsing CSV with special characters in report names."""
        # Note: CSV with commas in values should be quoted
        # Using raw string to avoid quote escaping issues
        csv_content = (
            "NomeRelatorio,NumeroRelatorio\n"
            "Relatรณrio com Acentos: รงรฃo,1\n"
            '"Relatรณrio com ""Aspas""",2\n'
            '"Relatรณrio com, Vรญrgula",3'
        )

        reports = parse_csv_to_reports(csv_content)

        assert len(reports) == 3
        assert reports[0]["nome_relatorio"] == "Relatรณrio com Acentos: รงรฃo"
        # CSV reader will unescape quotes: "" becomes "
        assert reports[1]["nome_relatorio"] == 'Relatรณrio com "Aspas"'
        assert reports[2]["nome_relatorio"] == "Relatรณrio com, Vรญrgula"

    def test_parse_csv_with_extra_columns(self):
        """Test parsing CSV with extra columns (should ignore them)."""
        csv_content = """NomeRelatorio,NumeroRelatorio,ExtraColumn
Relatรณrio 1,1,Extra Value 1
Relatรณrio 2,2,Extra Value 2"""

        reports = parse_csv_to_reports(csv_content)

        assert len(reports) == 2
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[0]["numero_relatorio"] == "1"
        assert "ExtraColumn" not in reports[0]  # Extra column not included
        assert reports[1]["nome_relatorio"] == "Relatรณrio 2"
        assert reports[1]["numero_relatorio"] == "2"

    def test_parse_csv_deduplicates_natural_key(self):
        """Test that duplicate natural keys are skipped."""
        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
Relatรณrio 1,1
Relatรณrio 2,2"""

        mock_logger = MagicMock()
        reports = parse_csv_to_reports(csv_content, logger=mock_logger)

        assert len(reports) == 2
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[1]["nome_relatorio"] == "Relatรณrio 2"
        warning_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any(
            "duplicate catalog row" in str(call).lower() for call in warning_calls
        )


# ============================================================================
# Unit Tests - API Integration (Mocked)
# ============================================================================


class TestFetchCatalogCsv:
    """Test fetch_catalog_csv function with mocked requests."""

    @patch("tasks.report_catalog_fetch.get_bacen_session")
    def test_successful_fetch(self, mock_get_session):
        """Test successful CSV fetch."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.text = (
            "NomeRelatorio,NumeroRelatorio\nRelatรณrio 1,1\nRelatรณrio 2,2"
        )
        mock_response.raise_for_status = Mock()
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        csv_content = fetch_catalog_csv()

        assert (
            csv_content
            == "NomeRelatorio,NumeroRelatorio\nRelatรณrio 1,1\nRelatรณrio 2,2"
        )

        # Verify API call parameters
        mock_session.get.assert_called_once()
        call_args = mock_session.get.call_args
        # URL is first positional argument
        url = call_args[0][0]
        assert url.startswith(
            "https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata/ListaDeRelatorio()"
        )
        # Check that query string contains expected parameters
        # Note: build_bacen_query_string URL-encodes values, so commas become %2C
        assert "$format=text%2Fcsv" in url or "$format=text/csv" in url
        assert (
            "$select=NomeRelatorio%2CNumeroRelatorio" in url
        )  # Comma is URL-encoded as %2C
        assert f"$top={MAX_REPORTS}" in url
        # timeout is a keyword argument
        assert call_args[1]["timeout"] == 30

        mock_response.raise_for_status.assert_called_once()
        mock_session.close.assert_called_once()

    @patch("tasks.report_catalog_fetch.get_bacen_session")
    def test_fetch_empty_result(self, mock_get_session):
        """Test fetch when API returns empty result."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.text = ""
        mock_response.raise_for_status = Mock()
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        csv_content = fetch_catalog_csv()

        assert csv_content == ""
        mock_session.get.assert_called_once()
        mock_session.close.assert_called_once()

    @patch("tasks.report_catalog_fetch.get_bacen_session")
    def test_fetch_with_whitespace(self, mock_get_session):
        """Test fetch when API returns CSV with leading/trailing whitespace."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.text = "  NomeRelatorio,NumeroRelatorio\nRelatรณrio 1,1  \n"
        mock_response.raise_for_status = Mock()
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        csv_content = fetch_catalog_csv()

        # Should strip whitespace
        assert csv_content == "NomeRelatorio,NumeroRelatorio\nRelatรณrio 1,1"
        mock_session.close.assert_called_once()

    @patch("tasks.report_catalog_fetch.get_bacen_session")
    def test_http_error(self, mock_get_session):
        """Test HTTP error handling."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.raise_for_status.side_effect = requests.HTTPError("404 Not Found")
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        with pytest.raises(requests.HTTPError):
            fetch_catalog_csv()

        # Session should be closed even on error
        mock_session.close.assert_called_once()

    @patch("tasks.report_catalog_fetch.get_bacen_session")
    def test_network_error(self, mock_get_session):
        """Test network error handling."""
        mock_session = MagicMock()
        mock_session.get.side_effect = requests.ConnectionError("Connection failed")
        mock_get_session.return_value = mock_session

        with pytest.raises(requests.ConnectionError):
            fetch_catalog_csv()

        # Session should be closed even on error
        mock_session.close.assert_called_once()

    @patch("tasks.report_catalog_fetch.get_bacen_session")
    def test_timeout_error(self, mock_get_session):
        """Test timeout error handling."""
        mock_session = MagicMock()
        mock_session.get.side_effect = requests.Timeout("Request timeout")
        mock_get_session.return_value = mock_session

        with pytest.raises(requests.Timeout):
            fetch_catalog_csv()

        # Session should be closed even on error
        mock_session.close.assert_called_once()


# ============================================================================
# Integration Tests - Full Task (Mocked)
# ============================================================================


class TestFetchReportCatalog:
    """Test fetch_report_catalog Prefect task with mocked dependencies."""

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_successful_fetch(self, mock_get_logger, mock_fetch_csv):
        """Test successful catalog fetch.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
Relatรณrio 2,2
Relatรณrio 3,3"""
        mock_fetch_csv.return_value = csv_content

        reports = fetch_report_catalog.fn()

        assert len(reports) == 3
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[0]["numero_relatorio"] == "1"
        assert reports[1]["nome_relatorio"] == "Relatรณrio 2"
        assert reports[1]["numero_relatorio"] == "2"
        assert reports[2]["nome_relatorio"] == "Relatรณrio 3"
        assert reports[2]["numero_relatorio"] == "3"

        # Verify logging
        mock_logger.info.assert_any_call("Fetching report catalog CSV from BACEN API")
        mock_logger.info.assert_any_call("Parsing CSV to reports")
        mock_logger.info.assert_any_call("Fetched 3 reports from catalog")

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_empty_catalog(self, mock_get_logger, mock_fetch_csv):
        """Test handling of empty catalog.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        csv_content = "NomeRelatorio,NumeroRelatorio"
        mock_fetch_csv.return_value = csv_content

        reports = fetch_report_catalog.fn()

        assert len(reports) == 0

        # Verify warning is logged
        mock_logger.warning.assert_called_with("Catalog is empty - no reports found")

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_http_error_propagates(self, mock_get_logger, mock_fetch_csv):
        """Test that HTTP errors are properly logged and propagated.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        mock_fetch_csv.side_effect = requests.HTTPError("404 Not Found")

        with pytest.raises(requests.HTTPError):
            fetch_report_catalog.fn()

        # Verify error is logged
        mock_logger.exception.assert_called()
        error_calls = [str(call) for call in mock_logger.exception.call_args_list]
        assert any("HTTP error" in str(call) for call in error_calls)

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_invalid_csv_format(self, mock_get_logger, mock_fetch_csv):
        """Test handling of invalid CSV format.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Invalid CSV (missing required columns)
        csv_content = "InvalidColumn,AnotherColumn\nValue1,Value2"
        mock_fetch_csv.return_value = csv_content

        with pytest.raises(ValueError, match="CSV missing required columns"):
            fetch_report_catalog.fn()

        # Verify error is logged
        mock_logger.exception.assert_called()
        error_calls = [str(call) for call in mock_logger.exception.call_args_list]
        assert any("Invalid data format" in str(call) for call in error_calls)

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_empty_csv_content(self, mock_get_logger, mock_fetch_csv):
        """Test handling of empty CSV content.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        mock_fetch_csv.return_value = ""

        with pytest.raises(ValueError, match="CSV content is empty"):
            fetch_report_catalog.fn()

        # Verify error is logged
        mock_logger.exception.assert_called()
        error_calls = [str(call) for call in mock_logger.exception.call_args_list]
        assert any("Invalid data format" in str(call) for call in error_calls)

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_unexpected_error(self, mock_get_logger, mock_fetch_csv):
        """Test handling of unexpected errors.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        mock_fetch_csv.side_effect = Exception("Unexpected error")

        with pytest.raises(Exception, match="Unexpected error"):
            fetch_report_catalog.fn()

        # Verify error is logged
        mock_logger.exception.assert_called()
        error_calls = [str(call) for call in mock_logger.exception.call_args_list]
        assert any("Unexpected error" in str(call) for call in error_calls)

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_csv_with_skipped_rows(self, mock_get_logger, mock_fetch_csv):
        """Test handling of CSV with some skipped rows.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
,2
Relatรณrio 3,3"""
        mock_fetch_csv.return_value = csv_content

        reports = fetch_report_catalog.fn()

        # Should only have 2 reports (row 2 is skipped)
        assert len(reports) == 2
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[1]["nome_relatorio"] == "Relatรณrio 3"

        # Verify warning is logged for skipped row
        mock_logger.warning.assert_called()
        warning_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any("Skipped row" in str(call) for call in warning_calls)

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_large_catalog(self, mock_get_logger, mock_fetch_csv):
        """Test handling of large catalog (up to MAX_REPORTS).

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Generate CSV with many reports
        reports_data = "\n".join([f"Relatรณrio {i},{i}" for i in range(1, 51)])
        csv_content = f"NomeRelatorio,NumeroRelatorio\n{reports_data}"
        mock_fetch_csv.return_value = csv_content

        reports = fetch_report_catalog.fn()

        assert len(reports) == 50
        assert reports[0]["nome_relatorio"] == "Relatรณrio 1"
        assert reports[49]["nome_relatorio"] == "Relatรณrio 50"

        # Verify logging includes count
        mock_logger.info.assert_any_call("Fetched 50 reports from catalog")

    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.report_catalog_fetch.get_run_logger")
    def test_catalog_with_duplicate_rows(self, mock_get_logger, mock_fetch_csv):
        """Test that duplicate catalog rows are skipped with logging.

        Note: Uses .fn() to bypass Prefects retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        csv_content = """NomeRelatorio,NumeroRelatorio
Relatรณrio 1,1
Relatรณrio 1,1
Relatรณrio 2,2"""
        mock_fetch_csv.return_value = csv_content

        reports = fetch_report_catalog.fn()

        assert len(reports) == 2
        assert reports[0]["numero_relatorio"] == "1"
        assert reports[1]["numero_relatorio"] == "2"
        warning_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any(
            "duplicate catalog row" in str(call).lower() for call in warning_calls
        )
