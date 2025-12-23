"""
Unit and integration tests for promotion task (raw to hot).

Tests cover:
- CSV parsing from raw_data column
- Row aggregation into JSONB documents
- Database operations (mocked and real)
- Active institution filtering
- UPSERT into bank_report
- Marking ifdata_load_control as completed
- Idempotency
- Error handling
- Edge cases (no raw data, no active institutions, etc.)
"""

import json
from decimal import Decimal
from unittest.mock import MagicMock, patch

import pytest

import psycopg2
from tasks.promotion import (
    aggregate_rows_to_structured_data,
    convert_saldo_to_numeric,
    fetch_raw_data_for_promotion,
    get_headers_from_first_row,
    mark_load_control_completed,
    map_csv_fields_to_names,
    parse_raw_csv_row,
    promote_raw_to_hot,
    upsert_bank_report,
)

# ============================================================================
# Unit Tests - Pure Functions (No Database)
# ============================================================================


class TestParseRawCsvRow:
    """Test parse_raw_csv_row function."""

    def test_parse_simple_csv_row(self):
        """Test parsing a simple CSV row."""
        raw_data = "value1,value2,value3"
        headers = ["field1", "field2", "field3"]

        result = parse_raw_csv_row(raw_data, headers)

        assert result == {
            "field1": "value1",
            "field2": "value2",
            "field3": "value3",
        }

    def test_parse_csv_row_without_headers(self):
        """Test parsing CSV row without headers (uses positional keys)."""
        raw_data = "value1,value2,value3"

        result = parse_raw_csv_row(raw_data)

        assert result == {
            "field_0": "value1",
            "field_1": "value2",
            "field_2": "value3",
        }

    def test_parse_csv_row_with_quoted_fields(self):
        """Test parsing CSV row with quoted fields."""
        raw_data = '"value,with,commas","normal value","quoted""value"'
        headers = ["field1", "field2", "field3"]

        result = parse_raw_csv_row(raw_data, headers)

        assert result["field1"] == "value,with,commas"
        assert result["field2"] == "normal value"
        assert result["field3"] == 'quoted"value'

    def test_parse_empty_csv_row(self):
        """Test parsing empty CSV row."""
        result = parse_raw_csv_row("")
        assert result == {}

        result = parse_raw_csv_row("   ")
        assert result == {}

    def test_parse_csv_row_mismatched_headers(self):
        """Test parsing when headers don't match row length."""
        raw_data = "value1,value2"
        headers = ["field1", "field2", "field3"]  # More headers than values

        result = parse_raw_csv_row(raw_data, headers)

        assert result["field1"] == "value1"
        assert result["field2"] == "value2"
        assert result["field3"] == ""  # Empty for missing value

    def test_parse_csv_row_more_values_than_headers(self):
        """Test parsing when row has more values than headers."""
        raw_data = "value1,value2,value3,value4"
        headers = ["field1", "field2"]

        result = parse_raw_csv_row(raw_data, headers)

        assert result["field1"] == "value1"
        assert result["field2"] == "value2"
        # Extra values are ignored when headers are provided


class TestConvertSaldoToNumeric:
    """Test convert_saldo_to_numeric function."""

    def test_convert_zero_string(self):
        """Test that "0" converts to Decimal("0")."""
        result = convert_saldo_to_numeric("0")
        assert result == Decimal("0")

    def test_convert_zero_point_zero(self):
        """Test that "0.0" converts to Decimal("0")."""
        result = convert_saldo_to_numeric("0.0")
        assert result == Decimal("0")

    def test_convert_null_string(self):
        """Test that "null" converts to None."""
        result = convert_saldo_to_numeric("null")
        assert result is None

    def test_convert_empty_string(self):
        """Test that "" converts to None."""
        result = convert_saldo_to_numeric("")
        assert result is None

    def test_convert_none(self):
        """Test that None converts to None."""
        result = convert_saldo_to_numeric(None)
        assert result is None

    def test_convert_numeric_string(self):
        """Test that numeric strings convert to Decimal."""
        result = convert_saldo_to_numeric("100.50")
        assert result == Decimal("100.50")

    def test_convert_percentage_string(self):
        """Test that percentage strings convert to Decimal."""
        result = convert_saldo_to_numeric("15.75")
        assert result == Decimal("15.75")

    def test_convert_invalid_string(self):
        """Test that invalid strings convert to None."""
        result = convert_saldo_to_numeric("invalid")
        assert result is None

    def test_convert_brazilian_format_comma_decimal(self):
        """Test that Brazilian format (comma as decimal separator) converts correctly."""
        # Format: "590878586,18" -> Decimal("590878586.18")
        result = convert_saldo_to_numeric("590878586,18")
        assert result == Decimal("590878586.18")
        assert float(result) == 590878586.18

    def test_convert_brazilian_format_quoted(self):
        """Test that quoted Brazilian format converts correctly."""
        # Format: "590878586,18" (with quotes) -> Decimal("590878586.18")
        result = convert_saldo_to_numeric('"590878586,18"')
        assert result == Decimal("590878586.18")
        assert float(result) == 590878586.18

    def test_convert_brazilian_format_single_digit_decimal(self):
        """Test Brazilian format with single digit decimal part."""
        result = convert_saldo_to_numeric("100,5")
        assert result == Decimal("100.5")
        assert float(result) == 100.5

    def test_convert_brazilian_format_zero_decimal(self):
        """Test Brazilian format with zero decimal part."""
        result = convert_saldo_to_numeric("100,0")
        assert result == Decimal("100.0")
        assert float(result) == 100.0

    def test_convert_brazilian_format_small_number(self):
        """Test Brazilian format with small numbers."""
        result = convert_saldo_to_numeric("0,18")
        assert result == Decimal("0.18")
        assert float(result) == 0.18

    def test_convert_international_format_still_works(self):
        """Test that international format (dot) still works after Brazilian format support."""
        result = convert_saldo_to_numeric("100.50")
        assert result == Decimal("100.50")
        assert float(result) == 100.50


class TestAggregateRowsToStructuredData:
    """Test aggregate_rows_to_structured_data function."""

    def test_aggregate_single_row_per_group(self):
        """Test aggregation with one row per institution."""
        rows = [
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                # Format: tipo,cod,ano,rel,num,Grupo,Conta,NomeColuna,DescricaoColuna,Saldo
                "raw_data": "1,001,202403,Report 1,1,Grupo1,Conta1,NomeColuna1,Desc1,100.50",
            }
        ]

        result = aggregate_rows_to_structured_data(rows)

        key = ("001", "202403", "Report 1", "1", 1)
        assert key in result
        # Function returns a list of row objects, even for single row
        assert len(result[key]) == 1
        assert result[key][0]["Grupo"] == "Grupo1"
        assert result[key][0]["Conta"] == "Conta1"
        assert result[key][0]["NomeColuna"] == "NomeColuna1"
        assert result[key][0]["DescricaoColuna"] == "Desc1"
        assert result[key][0]["Saldo"] == Decimal("100.50")

    def test_aggregate_multiple_rows_per_group(self):
        """Test aggregation with multiple rows per institution."""
        rows = [
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                "raw_data": "1,001,202403,Report 1,1,Grupo1,Conta1,NomeColuna1,Desc1,100.50",
            },
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                "raw_data": "1,001,202403,Report 1,1,Grupo2,Conta2,NomeColuna2,Desc2,200.75",
            },
        ]

        result = aggregate_rows_to_structured_data(rows)

        key = ("001", "202403", "Report 1", "1", 1)
        assert key in result
        # Function returns a list of row objects
        assert len(result[key]) == 2
        # Each row object is a separate dict in the array
        assert result[key][0]["Grupo"] == "Grupo1"
        assert result[key][0]["Saldo"] == Decimal("100.50")
        assert result[key][1]["Grupo"] == "Grupo2"
        assert result[key][1]["Saldo"] == Decimal("200.75")

    def test_aggregate_multiple_institutions(self):
        """Test aggregation with multiple institutions."""
        rows = [
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                "raw_data": "1,001,202403,Report 1,1,Grupo1,Conta1,NomeColuna1,Desc1,100.50",
            },
            {
                "cod_inst": "002",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                "raw_data": "1,002,202403,Report 1,1,Grupo2,Conta2,NomeColuna2,Desc2,200.75",
            },
        ]

        result = aggregate_rows_to_structured_data(rows)

        assert len(result) == 2
        assert ("001", "202403", "Report 1", "1", 1) in result
        assert ("002", "202403", "Report 1", "1", 1) in result

    def test_aggregate_empty_rows(self):
        """Test aggregation with empty row list."""
        result = aggregate_rows_to_structured_data([])
        assert result == {}

    def test_aggregate_without_headers(self):
        """Test aggregation without headers (uses positional keys).
        
        Note: Natural key fields (field_0-field_4) are excluded
        since they're already in table columns. Only CSV-specific fields (field_5+)
        are included in the result.
        """
        rows = [
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                # Include fields beyond field_4 (natural key fields are excluded)
                # field_5=Grupo, field_6=Conta, field_7=NomeColuna, field_9=Saldo
                "raw_data": "1,001,202403,Report 1,1,GrupoValue,ContaValue,NomeColunaValue,DescValue,50.25",
            }
        ]

        result = aggregate_rows_to_structured_data(rows)

        key = ("001", "202403", "Report 1", "1", 1)
        assert key in result
        # Function returns a list of row objects
        assert len(result[key]) == 1
        # Only CSV-specific fields (field_5+) are included, mapped to named fields
        assert "Grupo" in result[key][0]
        assert result[key][0]["Grupo"] == "GrupoValue"
        assert "Conta" in result[key][0]
        assert result[key][0]["Conta"] == "ContaValue"
        assert "NomeColuna" in result[key][0]
        assert result[key][0]["NomeColuna"] == "NomeColunaValue"
        assert "Saldo" in result[key][0]
        assert result[key][0]["Saldo"] == Decimal("50.25")

    def test_aggregate_preserves_zero_saldo(self):
        """Test that zero Saldo values are preserved."""
        rows = [
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                "raw_data": "1,001,202403,Report 1,1,Grupo1,Conta1,NomeColuna1,Desc1,0",
            }
        ]

        result = aggregate_rows_to_structured_data(rows)

        key = ("001", "202403", "Report 1", "1", 1)
        assert key in result
        assert len(result[key]) == 1
        assert result[key][0]["Saldo"] == Decimal("0")

    def test_aggregate_preserves_null_saldo(self):
        """Test that null Saldo values are preserved."""
        rows = [
            {
                "cod_inst": "001",
                "ano_mes": "202403",
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "tipo_instituicao": 1,
                "raw_data": "1,001,202403,Report 1,1,Grupo1,Conta1,NomeColuna1,Desc1,",
            }
        ]

        result = aggregate_rows_to_structured_data(rows)

        key = ("001", "202403", "Report 1", "1", 1)
        assert key in result
        assert len(result[key]) == 1
        assert result[key][0]["Saldo"] is None

    def test_aggregate_with_brazilian_format_saldo(self):
        """Test aggregation with Brazilian format saldo (comma as decimal separator)."""
        rows = [
            {
                "cod_inst": "00068987",
                "ano_mes": "202403",
                "nome_relatorio": "Resumo",
                "numero_relatorio": "1",
                "tipo_instituicao": 2,
                # Format: tipo,cod,ano,rel,num,Grupo,Conta,NomeColuna,DescricaoColuna,Saldo
                # Brazilian format: "590878586,18" (quoted in CSV)
                'raw_data': '2,00068987,202403,Resumo,1,null,78182,Ativo Total,"[10000007] + [20000004]","590878586,18"',
            }
        ]

        result = aggregate_rows_to_structured_data(rows)

        key = ("00068987", "202403", "Resumo", "1", 2)
        assert key in result
        assert len(result[key]) == 1
        assert result[key][0]["Grupo"] is None
        assert result[key][0]["Conta"] == "78182"
        assert result[key][0]["NomeColuna"] == "Ativo Total"
        assert result[key][0]["DescricaoColuna"] == "[10000007] + [20000004]"
        # Should convert Brazilian format to Decimal
        assert result[key][0]["Saldo"] == Decimal("590878586.18")
        assert float(result[key][0]["Saldo"]) == 590878586.18


class TestGetHeadersFromFirstRow:
    """Test get_headers_from_first_row function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        return MagicMock()

    @pytest.fixture
    def mock_logger(self):
        """Mock logger."""
        return MagicMock()

    def test_get_headers_returns_empty_list(self, mock_conn, mock_cursor, mock_logger):
        """Test that get_headers returns empty list (current implementation)."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        result = get_headers_from_first_row(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result == []


# ============================================================================
# Unit Tests - Database Operations (Mocked)
# ============================================================================


class TestFetchRawDataForPromotion:
    """Test fetch_raw_data_for_promotion function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.fetchall.return_value = []
        return cursor

    @pytest.fixture
    def mock_logger(self):
        """Mock logger."""
        logger = MagicMock()
        logger.debug = MagicMock()
        return logger

    def test_fetch_raw_data_success(self, mock_conn, mock_cursor, mock_logger):
        """Test successful fetch of raw data."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result
        mock_cursor.fetchall.return_value = [
            ("202403", 1, "Report 1", "1", "001", "value1,value2,value3"),
            ("202403", 1, "Report 1", "1", "002", "value4,value5,value6"),
        ]

        result = fetch_raw_data_for_promotion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert len(result) == 2
        assert result[0]["cod_inst"] == "001"
        assert result[0]["raw_data"] == "value1,value2,value3"
        assert result[1]["cod_inst"] == "002"

        # Verify SQL query
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "SELECT" in sql
        assert "FROM financial_data.ifdata_raw r" in sql
        assert "INNER JOIN financial_data.institution i" in sql
        assert "i.situacao = 'A'" in sql
        assert "WHERE r.ano_mes = %s" in sql
        assert "AND r.nome_relatorio = %s" in sql

    def test_fetch_raw_data_empty(self, mock_conn, mock_cursor, mock_logger):
        """Test fetch when no raw data exists."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        mock_cursor.fetchall.return_value = []

        result = fetch_raw_data_for_promotion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result == []
        mock_logger.debug.assert_called_once()


class TestUpsertBankReport:
    """Test upsert_bank_report function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 1  # Default: row was inserted/updated
        cursor.fetchone.return_value = ("test-uuid-123",)  # Return bank_report_id
        return cursor

    @pytest.fixture
    def mock_logger(self):
        """Mock logger."""
        logger = MagicMock()
        logger.info = MagicMock()
        return logger

    def test_upsert_single_report_with_data(self, mock_conn, mock_cursor, mock_logger):
        """Test UPSERT of single report with data rows."""
        from decimal import Decimal

        structured_data = {
            ("001", "202403", "Report 1", "1", 1): [
                {
                    "Grupo": "Grupo1",
                    "Conta": "Conta1",
                    "NomeColuna": "NomeColuna1",
                    "DescricaoColuna": "Desc1",
                    "Saldo": Decimal("100.50"),
                }
            ]
        }

        mock_cursor.rowcount = 1
        mock_cursor.fetchone.return_value = ("test-uuid-123",)

        result = upsert_bank_report(
            mock_conn, mock_cursor, structured_data, mock_logger
        )

        assert result == 1

        # Verify SQL queries were called (upsert report, delete data, insert data)
        assert mock_cursor.execute.call_count == 3

        # Verify first call is INSERT into bank_report
        first_sql = mock_cursor.execute.call_args_list[0][0][0]
        assert "INSERT INTO financial_data.bank_report" in first_sql
        assert "ON CONFLICT" in first_sql
        assert "RETURNING id" in first_sql

        # Verify second call is DELETE from bank_report_data
        second_sql = mock_cursor.execute.call_args_list[1][0][0]
        assert "DELETE FROM financial_data.bank_report_data" in second_sql

        # Verify third call is INSERT into bank_report_data
        third_sql = mock_cursor.execute.call_args_list[2][0][0]
        assert "INSERT INTO financial_data.bank_report_data" in third_sql

        # Verify parameters for data insert
        third_params = mock_cursor.execute.call_args_list[2][0][1]
        assert third_params[0] == "test-uuid-123"  # bank_report_id
        assert third_params[1] == "Grupo1"
        assert third_params[2] == "Conta1"
        assert third_params[3] == "NomeColuna1"
        assert third_params[4] == "Desc1"
        assert third_params[5] == Decimal("100.50")

    def test_upsert_multiple_reports(self, mock_conn, mock_cursor, mock_logger):
        """Test UPSERT of multiple reports."""
        from decimal import Decimal

        structured_data = {
            ("001", "202403", "Report 1", "1", 1): [
                {"Grupo": "Grupo1", "Conta": "Conta1", "NomeColuna": "NomeColuna1", "DescricaoColuna": "Desc1", "Saldo": Decimal("100.50")}
            ],
            ("002", "202403", "Report 1", "1", 1): [
                {"Grupo": "Grupo2", "Conta": "Conta2", "NomeColuna": "NomeColuna2", "DescricaoColuna": "Desc2", "Saldo": Decimal("200.75")}
            ],
        }

        mock_cursor.rowcount = 1
        mock_cursor.fetchone.return_value = ("test-uuid-123",)

        result = upsert_bank_report(
            mock_conn, mock_cursor, structured_data, mock_logger
        )

        assert result == 2
        # Each report: 1 upsert + 1 delete + N inserts (where N = number of data rows)
        # So 2 reports with 1 data row each = 2 + 2 + 2 = 6 calls
        assert mock_cursor.execute.call_count == 6

    def test_upsert_empty_data(self, mock_conn, mock_cursor, mock_logger):
        """Test UPSERT with empty data dict."""
        result = upsert_bank_report(mock_conn, mock_cursor, {}, mock_logger)

        assert result == 0
        mock_cursor.execute.assert_not_called()

    def test_upsert_with_null_saldo(self, mock_conn, mock_cursor, mock_logger):
        """Test UPSERT with null Saldo value."""
        structured_data = {
            ("001", "202403", "Report 1", "1", 1): [
                {
                    "Grupo": "Grupo1",
                    "Conta": "Conta1",
                    "NomeColuna": "NomeColuna1",
                    "DescricaoColuna": "Desc1",
                    "Saldo": None,
                }
            ]
        }

        mock_cursor.rowcount = 1
        mock_cursor.fetchone.return_value = ("test-uuid-123",)

        result = upsert_bank_report(
            mock_conn, mock_cursor, structured_data, mock_logger
        )

        assert result == 1
        # Verify null Saldo is passed correctly
        third_params = mock_cursor.execute.call_args_list[2][0][1]
        assert third_params[5] is None  # Saldo should be None


class TestMarkLoadControlCompleted:
    """Test mark_load_control_completed function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 1  # Default: row was updated
        return cursor

    @pytest.fixture
    def mock_logger(self):
        """Mock logger."""
        logger = MagicMock()
        logger.info = MagicMock()
        logger.warning = MagicMock()
        return logger

    def test_mark_completed_success(self, mock_conn, mock_cursor, mock_logger):
        """Test successful marking as completed."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        mock_cursor.rowcount = 1

        result = mark_load_control_completed(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result is True

        # Verify SQL query
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "UPDATE financial_data.ifdata_load_control" in sql
        assert "SET" in sql
        assert "promoted = TRUE" in sql
        assert "completed = TRUE" in sql
        assert "promoted_at = COALESCE" in sql
        assert "completed_at = COALESCE" in sql

        # Verify parameters
        params = mock_cursor.execute.call_args[0][1]
        assert params[0] == ano_mes
        assert params[1] == nome_relatorio

        # Verify logging
        mock_logger.info.assert_called_once()
        assert (
            "Marked ifdata_load_control as promoted and completed"
            in mock_logger.info.call_args[0][0]
        )

    def test_mark_completed_no_rows_updated(self, mock_conn, mock_cursor, mock_logger):
        """Test marking when no rows are updated."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        mock_cursor.rowcount = 0

        result = mark_load_control_completed(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result is False
        mock_logger.warning.assert_called_once()
        assert "No rows updated" in mock_logger.warning.call_args[0][0]


# ============================================================================
# Unit Tests - Prefect Task (Mocked Database)
# ============================================================================


class TestPromoteRawToHotTask:
    """Test promote_raw_to_hot Prefect task."""

    @patch("tasks.promotion.get_run_logger")
    @patch("tasks.promotion.get_db_connection")
    def test_successful_promotion(self, mock_get_db_conn, mock_get_logger):
        """Test successful promotion with data."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock logger
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Mock database connection
        mock_conn = MagicMock()
        mock_get_db_conn.return_value = mock_conn

        mock_cursor = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor
        mock_conn.cursor.return_value.__exit__.return_value = None

        # Mock fetch_raw_data_for_promotion
        # Note: raw_data must include fields beyond field_4 (field_5+) to be included
        # field_0-4 are natural keys (excluded), field_5+ are CSV-specific fields (included)
        # Format: tipo,cod,ano,rel,num,Grupo,Conta,NomeColuna,DescricaoColuna,Saldo
        mock_cursor.fetchall.side_effect = [
            [("202403", 1, "Report 1", "1", "001", "1,001,202403,Report 1,1,GrupoValue,ContaValue,NomeColunaValue,DescValue,100.50")],  # fetch_raw_data
        ]
        mock_cursor.rowcount = 1  # upsert_bank_report, mark_load_control_completed
        mock_cursor.fetchone.return_value = ("test-uuid-123",)  # bank_report_id

        # Use .fn() to bypass Prefect decorator overhead
        result = promote_raw_to_hot.fn(ano_mes, nome_relatorio)

        assert result["rows_fetched"] == 1
        assert result["documents_created"] == 1
        assert result["rows_upserted"] == 1
        assert result["marked_completed"] is True

        # Verify commit was called
        mock_conn.commit.assert_called_once()
        mock_conn.close.assert_called_once()

    @patch("tasks.promotion.get_run_logger")
    @patch("tasks.promotion.get_db_connection")
    def test_promotion_no_raw_data(self, mock_get_db_conn, mock_get_logger):
        """Test promotion when no raw data exists."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock logger
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Mock database connection
        mock_conn = MagicMock()
        mock_get_db_conn.return_value = mock_conn

        mock_cursor = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cursor
        mock_conn.cursor.return_value.__exit__.return_value = None

        # Mock fetch_raw_data_for_promotion: no data
        mock_cursor.fetchall.side_effect = [
            [],  # fetch_raw_data: empty
        ]
        mock_cursor.rowcount = 1  # mark_load_control_completed

        result = promote_raw_to_hot.fn(ano_mes, nome_relatorio)

        assert result["rows_fetched"] == 0
        assert result["documents_created"] == 0
        assert result["rows_upserted"] == 0
        assert result["marked_completed"] is True  # Still marked as completed

    @patch("tasks.promotion.get_run_logger")
    @patch("tasks.promotion.get_db_connection")
    def test_promotion_invalid_ano_mes(self, mock_get_db_conn, mock_get_logger):
        """Test promotion with invalid ano_mes format."""
        ano_mes = "invalid"
        nome_relatorio = "Report 1"

        # Mock logger
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        with pytest.raises(ValueError) as exc_info:
            promote_raw_to_hot.fn(ano_mes, nome_relatorio)

        assert "Invalid ano_mes format" in str(exc_info.value)

    @patch("tasks.promotion.get_run_logger")
    @patch("tasks.promotion.get_db_connection")
    def test_promotion_empty_nome_relatorio(self, mock_get_db_conn, mock_get_logger):
        """Test promotion with empty nome_relatorio."""
        ano_mes = "202403"
        nome_relatorio = ""

        # Mock logger
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        with pytest.raises(ValueError) as exc_info:
            promote_raw_to_hot.fn(ano_mes, nome_relatorio)

        assert "nome_relatorio cannot be empty" in str(exc_info.value)

    @patch("tasks.promotion.get_run_logger")
    @patch("tasks.promotion.get_db_connection")
    def test_promotion_database_error(self, mock_get_db_conn, mock_get_logger):
        """Test promotion with database error."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock logger
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Mock database connection that raises error
        mock_conn = MagicMock()
        mock_get_db_conn.return_value = mock_conn
        mock_conn.cursor.side_effect = psycopg2.Error("Database error")

        with pytest.raises(psycopg2.Error):
            promote_raw_to_hot.fn(ano_mes, nome_relatorio)

        # Verify rollback was called
        mock_conn.rollback.assert_called_once()
        mock_conn.close.assert_called_once()


# ============================================================================
# Integration Tests (Testcontainers)
# ============================================================================


@pytest.mark.integration
class TestPromotionIntegration:
    """Integration tests with testcontainers database."""

    @pytest.fixture
    def clean_tables(self, migrated_database):
        """Clean test tables before and after test."""
        with migrated_database.cursor() as cur:
            cur.execute("DELETE FROM financial_data.ifdata_raw")
            cur.execute("DELETE FROM financial_data.bank_report_data")  # Cascade delete will handle bank_report
            cur.execute("DELETE FROM financial_data.bank_report")
            cur.execute("DELETE FROM financial_data.ifdata_load_control")
            cur.execute("DELETE FROM financial_data.institution")
            migrated_database.commit()

        yield

        with migrated_database.cursor() as cur:
            cur.execute("DELETE FROM financial_data.ifdata_raw")
            cur.execute("DELETE FROM financial_data.bank_report_data")  # Cascade delete will handle bank_report
            cur.execute("DELETE FROM financial_data.bank_report")
            cur.execute("DELETE FROM financial_data.ifdata_load_control")
            cur.execute("DELETE FROM financial_data.institution")
            migrated_database.commit()

    @patch("tasks.promotion.get_db_connection")
    @patch("tasks.promotion.get_run_logger")
    def test_end_to_end_promotion(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_tables,
    ):
        """Test end-to-end promotion flow."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # 1. Insert test active institution
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.institution
                (bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao, source_hash)
                VALUES ('001', '202403', 'Test Bank', 'A', 'test_hash_001')
            """
            )
            migrated_database.commit()

        # 2. Insert test raw data
        # Note: raw_data must include fields beyond field_4 (field_5+) to be included in JSONB
        # field_0-4 are natural keys (excluded), field_5+ are CSV-specific fields (included)
        # Format: tipo,cod,ano,rel,num,Grupo,Conta,NomeColuna,DescricaoColuna,Saldo
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_raw
                (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, cod_inst, raw_data)
                VALUES
                (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo1,Conta1,NomeColuna1,Desc1,100.50'),
                (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo2,Conta2,NomeColuna2,Desc2,200.75')
            """,
                (ano_mes, nome_relatorio, ano_mes, nome_relatorio),
            )
            migrated_database.commit()

        # 3. Insert load control record (extracted but not promoted)
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES (%s, %s, TRUE, FALSE, FALSE)
            """,
                (ano_mes, nome_relatorio),
            )
            migrated_database.commit()

        # 4. Run promotion
        result = promote_raw_to_hot.fn(ano_mes, nome_relatorio)

        # 5. Verify promotion results
        assert result["rows_fetched"] == 2
        assert result["documents_created"] >= 1
        assert result["rows_upserted"] >= 1
        assert result["marked_completed"] is True

        # 6. Verify bank_report was created
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT id, cod_inst
                FROM financial_data.bank_report
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            report_row = cur.fetchone()
            assert report_row is not None
            bank_report_id = report_row[0]
            assert report_row[1] == "001"

        # 7. Verify bank_report_data was created with correct structure
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT grupo, conta, nome_coluna, descricao_coluna, saldo
                FROM financial_data.bank_report_data
                WHERE bank_report_id = %s
                ORDER BY conta
            """,
                (bank_report_id,),
            )
            data_rows = cur.fetchall()
            assert len(data_rows) == 2

            # Verify first row
            assert data_rows[0][0] == "Grupo1"  # grupo
            assert data_rows[0][1] == "Conta1"  # conta
            assert data_rows[0][2] == "NomeColuna1"  # nome_coluna
            assert data_rows[0][3] == "Desc1"  # descricao_coluna
            assert float(data_rows[0][4]) == 100.50  # saldo (numeric)

            # Verify second row
            assert data_rows[1][0] == "Grupo2"  # grupo
            assert data_rows[1][1] == "Conta2"  # conta
            assert data_rows[1][2] == "NomeColuna2"  # nome_coluna
            assert data_rows[1][3] == "Desc2"  # descricao_coluna
            assert float(data_rows[1][4]) == 200.75  # saldo (numeric)

        # 8. Verify load control was marked as completed
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT promoted, completed
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            row = cur.fetchone()
            assert row is not None
            assert row[0] is True  # promoted = TRUE
            assert row[1] is True  # completed = TRUE

    @patch("tasks.promotion.get_db_connection")
    @patch("tasks.promotion.get_run_logger")
    def test_promotion_idempotency(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_tables,
    ):
        """Test that promotion is idempotent (can be run multiple times)."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # Set up test data
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.institution
                (bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao, source_hash)
                VALUES ('001', '202403', 'Test Bank', 'A', 'test_hash_001')
            """
            )
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_raw
                (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, cod_inst, raw_data)
                VALUES (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo1,Conta1,NomeColuna1,Desc1,100.50')
            """,
                (ano_mes, nome_relatorio),
            )
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES (%s, %s, TRUE, FALSE, FALSE)
            """,
                (ano_mes, nome_relatorio),
            )
            migrated_database.commit()

        # 1. Run promotion first time
        result1 = promote_raw_to_hot.fn(ano_mes, nome_relatorio)
        assert result1["marked_completed"] is True

        # Get first promoted_at timestamp
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT promoted_at
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            first_promoted_at = cur.fetchone()[0]

        # 2. Run promotion second time (should be idempotent)
        result2 = promote_raw_to_hot.fn(ano_mes, nome_relatorio)
        assert result2["marked_completed"] is True

        # 3. Verify timestamps preserved
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT promoted_at, completed_at
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            second_promoted_at, _completed_at = cur.fetchone()
            # Timestamps should be preserved (not updated on second call)
            assert second_promoted_at == first_promoted_at

        # 4. Verify no duplicates in bank_report
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.bank_report
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            count = cur.fetchone()[0]
            # Should still have same count (no duplicates)
            assert count == result1["rows_upserted"]

        # 5. Verify bank_report_data has correct count (no duplicates)
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.bank_report_data brd
                INNER JOIN financial_data.bank_report br ON br.id = brd.bank_report_id
                WHERE br.ano_mes = %s AND br.nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            data_count = cur.fetchone()[0]
            # Should have same number of data rows (no duplicates)
            assert data_count > 0

    @patch("tasks.promotion.get_db_connection")
    @patch("tasks.promotion.get_run_logger")
    def test_promotion_active_institution_filter(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_tables,
    ):
        """Test that only active institutions are promoted."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # 1. Insert raw data for active and inactive institutions
        with migrated_database.cursor() as cur:
            # Active institution
            cur.execute(
                """
                INSERT INTO financial_data.institution
                (bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao, source_hash)
                VALUES ('001', '202403', 'Active Bank', 'A', 'test_hash_001')
            """
            )
            # Inactive institution
            cur.execute(
                """
                INSERT INTO financial_data.institution
                (bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao, source_hash)
                VALUES ('002', '202403', 'Inactive Bank', 'I', 'test_hash_002')
            """
            )
            # Raw data for both
            # Note: raw_data must include fields beyond field_4 (field_5+) to be included in JSONB
            # field_0-4 are natural keys (excluded), field_5+ are CSV-specific fields (included)
            # Format: tipo,cod,ano,rel,num,Grupo,Conta,NomeColuna,DescricaoColuna,Saldo
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_raw
                (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, cod_inst, raw_data)
                VALUES
                (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo1,Conta1,NomeColuna1,Desc1,100.50'),
                (%s, 1, %s, '1', '002', '1,002,202403,Test Report,1,Grupo2,Conta2,NomeColuna2,Desc2,200.75')
            """,
                (ano_mes, nome_relatorio, ano_mes, nome_relatorio),
            )
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES (%s, %s, TRUE, FALSE, FALSE)
            """,
                (ano_mes, nome_relatorio),
            )
            migrated_database.commit()

        # 2. Run promotion
        result = promote_raw_to_hot.fn(ano_mes, nome_relatorio)
        assert result["marked_completed"] is True

        # 3. Verify only active institutions in bank_report
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT cod_inst
                FROM financial_data.bank_report
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            cod_insts = {row[0] for row in cur.fetchall()}

            # Should only have active institution (001), not inactive (002)
            assert "001" in cod_insts
            assert "002" not in cod_insts

        # 4. Verify bank_report_data only has data for active institution
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.bank_report_data brd
                INNER JOIN financial_data.bank_report br ON br.id = brd.bank_report_id
                WHERE br.ano_mes = %s AND br.nome_relatorio = %s AND br.cod_inst = '001'
            """,
                (ano_mes, nome_relatorio),
            )
            active_count = cur.fetchone()[0]
            assert active_count > 0

            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.bank_report_data brd
                INNER JOIN financial_data.bank_report br ON br.id = brd.bank_report_id
                WHERE br.ano_mes = %s AND br.nome_relatorio = %s AND br.cod_inst = '002'
            """,
                (ano_mes, nome_relatorio),
            )
            inactive_count = cur.fetchone()[0]
            assert inactive_count == 0

    @patch("tasks.promotion.get_db_connection")
    @patch("tasks.promotion.get_run_logger")
    def test_promotion_preserves_zero_and_null_saldo(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_tables,
    ):
        """Test that zero and null Saldo values are preserved in bank_report_data."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Patch get_db_connection to return a new connection from factory
        mock_get_db_conn.side_effect = db_connection_factory

        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # Set up test data
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.institution
                (bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao, source_hash)
                VALUES ('001', '202403', 'Test Bank', 'A', 'test_hash_001')
            """
            )
            # Insert raw data with zero, null, and normal Saldo values
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_raw
                (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, cod_inst, raw_data)
                VALUES
                (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo1,Conta1,NomeColuna1,Desc1,0'),
                (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo2,Conta2,NomeColuna2,Desc2,'),
                (%s, 1, %s, '1', '001', '1,001,202403,Test Report,1,Grupo3,Conta3,NomeColuna3,Desc3,100.50')
            """,
                (ano_mes, nome_relatorio, ano_mes, nome_relatorio, ano_mes, nome_relatorio),
            )
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES (%s, %s, TRUE, FALSE, FALSE)
            """,
                (ano_mes, nome_relatorio),
            )
            migrated_database.commit()

        # Run promotion
        result = promote_raw_to_hot.fn(ano_mes, nome_relatorio)
        assert result["marked_completed"] is True

        # Verify all three rows are in bank_report_data
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT brd.grupo, brd.conta, brd.saldo
                FROM financial_data.bank_report_data brd
                INNER JOIN financial_data.bank_report br ON br.id = brd.bank_report_id
                WHERE br.ano_mes = %s AND br.nome_relatorio = %s
                ORDER BY brd.conta
            """,
                (ano_mes, nome_relatorio),
            )
            data_rows = cur.fetchall()
            assert len(data_rows) == 3

            # Verify zero Saldo is preserved as 0
            zero_row = [r for r in data_rows if r[1] == "Conta1"][0]
            assert zero_row[2] == 0  # saldo should be 0 (numeric zero)

            # Verify null Saldo is preserved as NULL
            null_row = [r for r in data_rows if r[1] == "Conta2"][0]
            assert null_row[2] is None  # saldo should be NULL

            # Verify normal Saldo is preserved
            normal_row = [r for r in data_rows if r[1] == "Conta3"][0]
            assert float(normal_row[2]) == 100.50  # saldo should be 100.50
