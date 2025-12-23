"""
Unit and integration tests for registry ingestion task.

Tests cover:
- Hash computation logic (pure functions)
- CSV parsing and normalization
- Database upsert logic (mocked and real)
- API integration (mocked)
- Change detection (NEW/CHANGED/UNCHANGED)
"""

from unittest.mock import MagicMock, Mock, patch

import pytest

import psycopg2
import requests
from tasks.registry_ingestion import (
    compute_source_hash,
    fetch_registry_csv,
    ingest_registry,
    normalize_institution_fields,
    parse_csv_to_institutions,
    upsert_institution,
)

# ============================================================================
# Unit Tests - Pure Functions (No Database/Network)
# ============================================================================


class TestNormalizeInstitutionFields:
    """Test normalize_institution_fields function."""

    def test_trim_strings(self):
        """Test that strings are trimmed."""
        inst = {
            "bacen_cod_inst": "  001  ",
            "nome_instituicao": "  Test Bank  ",
            "situacao": "A",
        }
        normalized = normalize_institution_fields(inst)
        assert normalized["bacen_cod_inst"] == "001"
        assert normalized["nome_instituicao"] == "Test Bank"
        assert normalized["situacao"] == "A"

    def test_empty_string_to_none(self):
        """Test that empty strings are converted to None."""
        inst = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "",
            "tipo_consolidado_bancario": "   ",  # Only whitespace
            "situacao": "A",
        }
        normalized = normalize_institution_fields(inst)
        assert normalized["bacen_cod_inst"] == "001"
        assert normalized["nome_instituicao"] is None
        assert normalized["tipo_consolidado_bancario"] is None
        assert normalized["situacao"] == "A"

    def test_literal_null_string_to_none(self):
        """Test that literal 'null' strings (any casing) become None."""
        inst = {
            "segmento_tb": "null",
            "atividade": "NuLL",
            "cod_conglomerado_financeiro": " Null ",
            "cnpj_instituicao_lider": "12345678000190",
        }
        normalized = normalize_institution_fields(inst)
        assert normalized["segmento_tb"] is None
        assert normalized["atividade"] is None
        assert normalized["cod_conglomerado_financeiro"] is None
        assert normalized["cnpj_instituicao_lider"] == "12345678000190"

    def test_none_handling(self):
        """Test that None values are preserved."""
        inst = {
            "bacen_cod_inst": "001",
            "nome_instituicao": None,
            "situacao": "A",
        }
        normalized = normalize_institution_fields(inst)
        assert normalized["bacen_cod_inst"] == "001"
        assert normalized["nome_instituicao"] is None
        assert normalized["situacao"] == "A"

    def test_non_string_values(self):
        """Test that non-string values are preserved."""
        inst = {
            "bacen_cod_inst": "001",
            "some_number": 123,
            "some_bool": True,
        }
        normalized = normalize_institution_fields(inst)
        assert normalized["bacen_cod_inst"] == "001"
        assert normalized["some_number"] == 123
        assert normalized["some_bool"] is True


class TestComputeSourceHash:
    """Test compute_source_hash function."""

    def test_deterministic_output(self):
        """Test that same input produces same hash."""
        inst1 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "situacao": "A",
        }
        inst2 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "situacao": "A",
        }

        hash1 = compute_source_hash(inst1)
        hash2 = compute_source_hash(inst2)

        assert hash1 == hash2
        assert len(hash1) == 64  # SHA-256 hex is 64 characters

    def test_different_inputs_different_hashes(self):
        """Test that different inputs produce different hashes."""
        inst1 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "situacao": "A",
        }
        inst2 = {
            "bacen_cod_inst": "002",  # Different code
            "nome_instituicao": "Test Bank",
            "situacao": "A",
        }

        hash1 = compute_source_hash(inst1)
        hash2 = compute_source_hash(inst2)

        assert hash1 != hash2

    def test_hash_includes_all_hash_fields(self):
        """Test that hash includes all required fields."""
        inst = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "situacao": "A",
            "segmento_tb": "Segment",
            "atividade": "Activity",
            "tipo_consolidado_bancario": "b1",
            "tipo_consolidacao": "C",
            "tipo_controle": "1",
            "uf": "SP",
            "municipio": "São Paulo",
            "cod_conglomerado_financeiro": "CF001",
            "cod_conglomerado_prudencial": "CP001",
            "cnpj_instituicao_lider": "12345678000190",
        }

        # Normalize first
        normalized = normalize_institution_fields(inst)
        hash1 = compute_source_hash(normalized)

        # Change one field
        inst2 = inst.copy()
        inst2["nome_instituicao"] = "Different Bank"
        normalized2 = normalize_institution_fields(inst2)
        hash2 = compute_source_hash(normalized2)

        assert hash1 != hash2

    def test_hash_ignores_extra_fields(self):
        """Test that hash only includes HASH_FIELDS, ignores extra fields."""
        inst1 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "extra_field": "ignored",
        }
        inst2 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "different_extra": "also ignored",
        }

        hash1 = compute_source_hash(inst1)
        hash2 = compute_source_hash(inst2)

        assert hash1 == hash2  # Extra fields don't affect hash

    def test_hash_with_none_values(self):
        """Test that None values are handled correctly in hash."""
        inst1 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": None,
            "situacao": "A",
        }
        inst2 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "",  # Empty string normalized to None
            "situacao": "A",
        }

        normalized1 = normalize_institution_fields(inst1)
        normalized2 = normalize_institution_fields(inst2)

        hash1 = compute_source_hash(normalized1)
        hash2 = compute_source_hash(normalized2)

        assert hash1 == hash2  # None and empty string should produce same hash

    def test_hash_treats_literal_null_as_none(self):
        """Test that literal 'null' string normalizes to None and hashes equal."""
        inst1 = {
            "bacen_cod_inst": "001",
            "segmento_tb": None,
        }
        inst2 = {
            "bacen_cod_inst": "001",
            "segmento_tb": "null",
        }

        normalized1 = normalize_institution_fields(inst1)
        normalized2 = normalize_institution_fields(inst2)

        hash1 = compute_source_hash(normalized1)
        hash2 = compute_source_hash(normalized2)

        assert normalized2["segmento_tb"] is None
        assert hash1 == hash2

    def test_hash_sorted_keys(self):
        """Test that hash uses sorted keys (deterministic JSON)."""
        # Create two dicts with same values but different key order
        # (Python dicts preserve insertion order, but JSON sorts keys)
        inst1 = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test",
            "situacao": "A",
        }
        inst2 = {
            "situacao": "A",
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test",
        }

        hash1 = compute_source_hash(inst1)
        hash2 = compute_source_hash(inst2)

        assert hash1 == hash2  # Should be same regardless of key order


class TestParseCsvToInstitutions:
    """Test parse_csv_to_institutions function."""

    def test_parse_valid_csv(self):
        """Test parsing valid CSV with headers."""
        csv_content = """CodInst,NomeInstituicao,TCB,TD,TC,Situacao
001,Test Bank,b1,C,1,A
002,Another Bank,B2,I,2,A"""

        institutions, skipped_count = parse_csv_to_institutions(csv_content)

        assert len(institutions) == 2
        assert skipped_count == 0
        assert institutions[0]["bacen_cod_inst"] == "001"
        assert institutions[0]["nome_instituicao"] == "Test Bank"
        assert institutions[0]["tipo_consolidado_bancario"] == "b1"
        assert institutions[0]["tipo_consolidacao"] == "C"
        assert institutions[0]["tipo_controle"] == "1"
        assert institutions[0]["situacao"] == "A"

    def test_parse_csv_with_missing_columns(self):
        """Test parsing CSV with missing optional columns."""
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A"""

        institutions, skipped_count = parse_csv_to_institutions(csv_content)

        assert len(institutions) == 1
        assert skipped_count == 0
        assert institutions[0]["bacen_cod_inst"] == "001"
        assert institutions[0]["nome_instituicao"] == "Test Bank"
        assert institutions[0].get("tipo_consolidado_bancario") is None  # Missing column
        assert institutions[0].get("tipo_consolidacao") is None  # Missing column
        assert institutions[0].get("tipo_controle") is None  # Missing column

    def test_parse_csv_with_empty_values(self):
        """Test parsing CSV with empty values."""
        csv_content = """CodInst,NomeInstituicao,TCB,Situacao
001,Test Bank,,A
002,,,A"""

        institutions, skipped_count = parse_csv_to_institutions(csv_content)

        assert len(institutions) == 2
        assert skipped_count == 0
        assert (
            institutions[0]["tipo_consolidado_bancario"] == ""
        )  # Empty string (will be normalized later)
        assert institutions[1]["nome_instituicao"] == ""  # Empty string

    def test_parse_csv_case_insensitive(self):
        """Test that CSV parsing handles case-insensitive column names."""
        csv_content = """codinst,nomeinstituicao,situacao
001,Test Bank,A"""

        institutions, skipped_count = parse_csv_to_institutions(csv_content)

        # Should still map correctly (case-insensitive matching)
        assert len(institutions) == 1
        assert skipped_count == 0
        assert institutions[0].get("bacen_cod_inst") is not None

    def test_parse_empty_csv(self):
        """Test that empty CSV raises ValueError."""
        with pytest.raises(ValueError, match="CSV content is empty"):
            parse_csv_to_institutions("")

        with pytest.raises(ValueError, match="CSV content is empty"):
            parse_csv_to_institutions("   ")

    def test_parse_csv_no_headers(self):
        """Test that CSV without proper headers is handled gracefully."""
        csv_content = """001,Test Bank,A
002,Another Bank,A"""

        # DictReader will use first row as headers, so only the second row
        # is processed as data. Since the first row becomes headers, we only
        # process 1 data row (the second row), which will be skipped.
        institutions, skipped_count = parse_csv_to_institutions(csv_content)

        # Should return empty list since no valid CodInst found
        assert len(institutions) == 0
        assert (
            skipped_count == 1
        )  # Only 1 data row processed (first row becomes header)

    def test_parse_csv_skips_missing_cod_inst(self):
        """Test that rows without CodInst are skipped and counted."""
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A
,Missing Code,A
002,Another Bank,A"""

        institutions, skipped_count = parse_csv_to_institutions(csv_content)

        # Should only have 2 institutions (skipped the one without CodInst)
        assert len(institutions) == 2
        assert skipped_count == 1
        assert institutions[0]["bacen_cod_inst"] == "001"
        assert institutions[1]["bacen_cod_inst"] == "002"

    def test_parse_csv_logs_skipped_rows(self):
        """Test that skipped rows are logged when logger is provided."""
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A
,Missing Code,A
002,Another Bank,A"""

        mock_logger = MagicMock()
        institutions, skipped_count = parse_csv_to_institutions(
            csv_content, logger=mock_logger
        )

        assert len(institutions) == 2
        assert skipped_count == 1

        # Should log warning for skipped row
        assert mock_logger.warning.call_count >= 1
        # Check that warning includes row number and column info
        warning_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any("Skipped row" in str(call) for call in warning_calls)
        assert any(
            "missing bacen_cod_inst" in str(call).lower() for call in warning_calls
        )

    def test_parse_csv_logs_skip_rate_summary(self):
        """Test that skip rate summary is logged when logger is provided."""
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A
,Missing Code,A
,Another Missing,A
002,Another Bank,A"""

        mock_logger = MagicMock()
        institutions, skipped_count = parse_csv_to_institutions(
            csv_content, logger=mock_logger
        )

        assert len(institutions) == 2
        assert skipped_count == 2

        # Should log summary with skip rate
        summary_calls = [str(call) for call in mock_logger.warning.call_args_list]
        assert any(
            "Skipped" in str(call) and "%" in str(call) for call in summary_calls
        )


# ============================================================================
# Unit Tests - API Integration (Mocked)
# ============================================================================


class TestFetchRegistryCsv:
    """Test fetch_registry_csv function with mocked requests."""

    @patch("tasks.registry_ingestion.get_bacen_session")
    def test_successful_fetch_single_page(self, mock_get_session):
        """Test successful CSV fetch with single page (less than PAGE_SIZE)."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.text = "CodInst,NomeInstituicao\n001,Test Bank\n002,Another Bank"
        mock_response.raise_for_status = Mock()
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        csv_content = fetch_registry_csv("202403")

        assert csv_content == "CodInst,NomeInstituicao\n001,Test Bank\n002,Another Bank"
        # Should only make one request (less than PAGE_SIZE results)
        assert mock_session.get.call_count == 1
        mock_response.raise_for_status.assert_called_once()
        mock_session.close.assert_called_once()

    @patch("tasks.registry_ingestion.get_bacen_session")
    def test_successful_fetch_multiple_pages(self, mock_get_session):
        """Test successful CSV fetch with pagination (multiple pages)."""
        from tasks.registry_ingestion import PAGE_SIZE

        # First page: exactly PAGE_SIZE rows
        first_page_data = "\n".join(
            [f"{i:03d},Bank {i}" for i in range(1, PAGE_SIZE + 1)]
        )
        first_page_csv = f"CodInst,NomeInstituicao\n{first_page_data}"

        # Second page: fewer than PAGE_SIZE rows (last page)
        second_page_data = "\n".join(
            [f"{i:03d},Bank {i}" for i in range(PAGE_SIZE + 1, PAGE_SIZE + 5)]
        )
        second_page_csv = f"CodInst,NomeInstituicao\n{second_page_data}"

        mock_response1 = Mock()
        mock_response1.text = first_page_csv
        mock_response1.raise_for_status = Mock()

        mock_response2 = Mock()
        mock_response2.text = second_page_csv
        mock_response2.raise_for_status = Mock()

        mock_session = MagicMock()
        mock_session.get.side_effect = [mock_response1, mock_response2]
        mock_get_session.return_value = mock_session

        csv_content = fetch_registry_csv("202403")

        # Should combine header + all data rows
        lines = csv_content.splitlines()
        assert lines[0] == "CodInst,NomeInstituicao"
        # Should have PAGE_SIZE + 4 data rows (from first and second pages)
        assert len(lines) == 1 + PAGE_SIZE + 4

        # Should make 2 requests (first page, second page - stops when second page
        # has fewer than PAGE_SIZE rows, doesn't make third call)
        assert mock_session.get.call_count == 2

        # Verify pagination parameters
        calls = mock_session.get.call_args_list
        # First call: skip=0 (check URL contains $skip=0)
        first_url = calls[0][0][0]  # First positional argument is URL
        assert "$skip=0" in first_url or "skip=0" in first_url
        # Second call: skip=PAGE_SIZE (check URL contains $skip=PAGE_SIZE)
        second_url = calls[1][0][0]  # First positional argument is URL
        assert f"$skip={PAGE_SIZE}" in second_url or f"skip={PAGE_SIZE}" in second_url
        mock_session.close.assert_called_once()

    @patch("tasks.registry_ingestion.get_bacen_session")
    def test_fetch_empty_result(self, mock_get_session):
        """Test fetch when API returns empty result."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.text = ""
        mock_response.raise_for_status = Mock()
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        csv_content = fetch_registry_csv("202403")

        assert csv_content == ""
        mock_session.get.assert_called_once()
        mock_session.close.assert_called_once()

    @patch("tasks.registry_ingestion.get_bacen_session")
    def test_fetch_exact_page_size_stops_correctly(self, mock_get_session):
        """Test that exact PAGE_SIZE on first page triggers second page check."""
        from tasks.registry_ingestion import PAGE_SIZE

        # First page: exactly PAGE_SIZE rows
        first_page_data = "\n".join(
            [f"{i:03d},Bank {i}" for i in range(1, PAGE_SIZE + 1)]
        )
        first_page_csv = f"CodInst,NomeInstituicao\n{first_page_data}"

        # Second page: empty (no more results)
        second_page_csv = ""

        mock_response1 = Mock()
        mock_response1.text = first_page_csv
        mock_response1.raise_for_status = Mock()

        mock_response2 = Mock()
        mock_response2.text = second_page_csv
        mock_response2.raise_for_status = Mock()

        mock_session = MagicMock()
        mock_session.get.side_effect = [mock_response1, mock_response2]
        mock_get_session.return_value = mock_session

        csv_content = fetch_registry_csv("202403")

        # Should have header + PAGE_SIZE data rows
        lines = csv_content.splitlines()
        assert lines[0] == "CodInst,NomeInstituicao"
        assert len(lines) == 1 + PAGE_SIZE

        # Should make 2 requests (first page, second empty page to detect end)
        assert mock_session.get.call_count == 2
        mock_session.close.assert_called_once()

    @patch("tasks.registry_ingestion.get_bacen_session")
    def test_http_error(self, mock_get_session):
        """Test HTTP error handling."""
        mock_session = MagicMock()
        mock_response = Mock()
        mock_response.raise_for_status.side_effect = requests.HTTPError("404 Not Found")
        mock_session.get.return_value = mock_response
        mock_get_session.return_value = mock_session

        with pytest.raises(requests.HTTPError):
            fetch_registry_csv("202403")

        # Session should be closed even on error
        mock_session.close.assert_called_once()

    def test_invalid_ano_mes_format(self):
        """Test that invalid ano_mes format raises ValueError."""
        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            fetch_registry_csv("2024")

        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            fetch_registry_csv("2024031")

        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            fetch_registry_csv("abc123")

    @patch("tasks.registry_ingestion.get_bacen_session")
    def test_retry_on_transient_error(self, mock_get_session):
        """Test that retry mechanism handles transient errors.

        Note: When mocking session.get(), the urllib3 retry mechanism is bypassed
        because we're directly controlling the mock responses. The actual retry
        mechanism works at the HTTPAdapter level in urllib3, which retries before
        raise_for_status() is called. This test verifies that errors are properly
        raised when retries are exhausted.
        """
        mock_session = MagicMock()

        # Mock response that raises HTTPError on raise_for_status()
        error_response = Mock()
        error_response.raise_for_status.side_effect = requests.HTTPError(
            "503 Service Unavailable"
        )
        error_response.status_code = 503

        mock_session.get.return_value = error_response
        mock_get_session.return_value = mock_session

        # Should raise HTTPError when retries are exhausted
        with pytest.raises(requests.HTTPError, match="503 Service Unavailable"):
            fetch_registry_csv("202403")

        # Should have made at least one call
        assert mock_session.get.call_count >= 1
        mock_session.close.assert_called_once()


# ============================================================================
# Integration Tests - Database Upsert Logic (Mocked)
# ============================================================================


class TestUpsertInstitution:
    """Test upsert_institution function with mocked database."""

    def test_new_institution_insert(self):
        """Test inserting new institution (NEW -> CREATED)."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = None  # Not found

        inst = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "situacao": "A",
            "source_hash": "abc123" * 10,  # 60 chars, will be 64 in real usage
        }

        result = upsert_institution(mock_conn, mock_cursor, inst, "202403")

        assert result == "CREATED"
        mock_cursor.execute.assert_called()
        assert "INSERT INTO" in str(mock_cursor.execute.call_args)
        # Function no longer commits - caller manages transaction
        mock_conn.commit.assert_not_called()

    def test_changed_institution_update(self):
        """Test updating changed institution (CHANGED -> UPDATED)."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        # Existing institution with different hash
        mock_cursor.fetchone.return_value = {"source_hash": "oldhash" * 10}

        inst = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank Updated",
            "situacao": "A",
            "source_hash": "newhash" * 10,
        }

        result = upsert_institution(mock_conn, mock_cursor, inst, "202403")

        assert result == "UPDATED"
        mock_cursor.execute.assert_called()
        assert "UPDATE" in str(mock_cursor.execute.call_args)
        # Function no longer commits - caller manages transaction
        mock_conn.commit.assert_not_called()

    def test_unchanged_institution_noop(self):
        """Test unchanged institution (UNCHANGED -> None)."""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        # Existing institution with same hash
        same_hash = "samehash" * 10
        mock_cursor.fetchone.return_value = {"source_hash": same_hash}

        inst = {
            "bacen_cod_inst": "001",
            "nome_instituicao": "Test Bank",
            "situacao": "A",
            "source_hash": same_hash,
        }

        result = upsert_institution(mock_conn, mock_cursor, inst, "202403")

        assert result is None
        # Should only execute SELECT, not INSERT or UPDATE
        assert mock_cursor.execute.call_count == 1
        assert "SELECT" in str(mock_cursor.execute.call_args)
        mock_conn.commit.assert_not_called()


# ============================================================================
# Integration Tests - Full Flow (Mocked)
# ============================================================================


class TestIngestRegistry:
    """Test ingest_registry task with mocked dependencies."""

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_successful_ingestion_new_institutions(
        self, mock_fetch, mock_get_db_conn, mock_logger
    ):
        """Test successful ingestion with new institutions."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock CSV fetch
        csv_content = """CodInst,NomeInstituicao,TCB,TD,TC,Situacao
001,Test Bank,b1,C,1,A
002,Another Bank,B2,I,2,A"""
        mock_fetch.return_value = csv_content

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = None  # All new institutions
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        result = ingest_registry("202403")

        # Should return 2 new institutions
        assert len(result) == 2
        assert all(item["change_type"] == "CREATED" for item in result)
        assert result[0]["bacen_cod_inst"] == "001"
        assert result[1]["bacen_cod_inst"] == "002"

        # Should commit once at the end (single transaction)
        assert mock_conn.commit.call_count == 1
        # Should not rollback on success
        mock_conn.rollback.assert_not_called()

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_ingestion_mixed_changes(self, mock_fetch, mock_get_db_conn, mock_logger):
        """Test ingestion with mixed NEW/CHANGED/UNCHANGED institutions."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock CSV fetch
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A
002,Changed Bank,A
003,Unchanged Bank,A"""
        mock_fetch.return_value = csv_content

        # Mock database: return different hashes for different institutions
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        call_count = [0]

        def fetchone_side_effect():
            call_count[0] += 1
            if call_count[0] == 1:
                return None  # 001: NEW
            elif call_count[0] == 2:
                # 002: CHANGED (different hash)
                return {"source_hash": "different_hash" * 10}
            else:
                # 003: UNCHANGED (same hash - need to compute actual hash)
                # For this test, we'll use a mock that returns same hash
                inst = {
                    "bacen_cod_inst": "003",
                    "nome_instituicao": "Unchanged Bank",
                    "situacao": "A",
                }
                normalized = normalize_institution_fields(inst)
                same_hash = compute_source_hash(normalized)
                return {"source_hash": same_hash}

        mock_cursor.fetchone.side_effect = fetchone_side_effect
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        result = ingest_registry("202403")

        # Should return 2 institutions (NEW and CHANGED, not UNCHANGED)
        assert len(result) == 2
        assert result[0]["change_type"] == "CREATED"
        assert result[1]["change_type"] == "UPDATED"

        # Should commit once at the end (single transaction)
        assert mock_conn.commit.call_count == 1
        # Should not rollback on success
        mock_conn.rollback.assert_not_called()

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_http_error_propagates(self, mock_fetch, mock_logger):
        """Test that HTTP errors are propagated.

        Note: Uses .fn() to bypass Prefect's retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger.return_value = MagicMock()
        mock_fetch.side_effect = requests.HTTPError("404 Not Found")

        with pytest.raises(requests.HTTPError):
            ingest_registry.fn("202403")

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_database_error_propagates(self, mock_fetch, mock_get_db_conn, mock_logger):
        """Test that database errors are propagated.

        Note: Uses .fn() to bypass Prefect's retry mechanism (retries=3, retry_delay_seconds=60)
        which would otherwise make this test very slow.
        """
        mock_logger.return_value = MagicMock()
        mock_fetch.return_value = "CodInst,NomeInstituicao\n001,Test Bank"
        mock_get_db_conn.side_effect = psycopg2.OperationalError("Connection failed")

        with pytest.raises(psycopg2.OperationalError):
            ingest_registry.fn("202403")

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_transaction_rollback_on_error(
        self, mock_fetch, mock_get_db_conn, mock_logger
    ):
        """Test that transaction is rolled back on error during batch upsert."""
        mock_logger.return_value = MagicMock()

        # Mock CSV fetch
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A
002,Another Bank,A"""
        mock_fetch.return_value = csv_content

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # First fetchone succeeds (for first institution), second fails
        call_count = [0]

        def fetchone_side_effect():
            call_count[0] += 1
            if call_count[0] == 1:
                return None  # First institution: NEW
            else:
                # Second institution: raise error
                raise psycopg2.DatabaseError("Database error during upsert")

        mock_cursor.fetchone.side_effect = fetchone_side_effect
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Use .fn() to bypass Prefect's retry mechanism for fast test execution
        with pytest.raises(psycopg2.DatabaseError):
            ingest_registry.fn("202403")

        # Should rollback on error
        mock_conn.rollback.assert_called_once()
        # Should not commit on error
        mock_conn.commit.assert_not_called()

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_high_skip_rate_raises_error(
        self, mock_fetch, mock_get_db_conn, mock_logger
    ):
        """Test that high skip rate raises ValueError to catch schema shifts."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock CSV with high skip rate (>10%): 3 valid, 2 skipped = 40% skip rate (2/5)
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Test Bank,A
,Missing 1,A
002,Another Bank,A
,Missing 2,A
003,Third Bank,A"""
        mock_fetch.return_value = csv_content

        # Mock database connection (won't be reached due to skip rate error)
        mock_conn = MagicMock()
        mock_get_db_conn.return_value = mock_conn

        # Use .fn() to bypass Prefect's retry mechanism for fast test execution
        with pytest.raises(ValueError, match="High skip rate detected"):
            ingest_registry.fn("202403")

        # Should log error about high skip rate
        # Check that error was called at least once
        assert mock_log.error.call_count >= 1

        # Extract error messages from call arguments
        # call_args_list contains tuples of (args, kwargs)
        error_messages = []
        for call in mock_log.error.call_args_list:
            args, _kwargs = call
            # Extract the first positional argument (the error message string)
            if args:
                error_messages.append(str(args[0]))

        # Combine all error messages into a single string for checking
        all_error_text = " ".join(error_messages)

        # Verify error message contains expected content
        assert "High skip rate" in all_error_text
        assert "40.0%" in all_error_text or "40%" in all_error_text

        # Should not reach database operations
        mock_conn.cursor.assert_not_called()

    @patch("tasks.registry_ingestion.get_run_logger")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    def test_low_skip_rate_passes(self, mock_fetch, mock_get_db_conn, mock_logger):
        """Test that low skip rate (<10%) allows ingestion to proceed."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock CSV with low skip rate: 10 valid, 1 skipped = 9.1% skip rate (<10%)
        csv_content = """CodInst,NomeInstituicao,Situacao
001,Bank 1,A
002,Bank 2,A
003,Bank 3,A
004,Bank 4,A
005,Bank 5,A
006,Bank 6,A
007,Bank 7,A
008,Bank 8,A
009,Bank 9,A
,Missing,A
010,Bank 10,A"""
        mock_fetch.return_value = csv_content

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = None  # All new institutions
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        result = ingest_registry("202403")

        # Should proceed and return 10 institutions (1 skipped)
        assert len(result) == 10
        # Should log warning about skipped rows but not error
        warning_calls = [str(call) for call in mock_log.warning.call_args_list]
        assert any("Skipped" in str(call) for call in warning_calls)
        # Should not raise error
        error_calls = [str(call) for call in mock_log.error.call_args_list]
        assert not any("High skip rate" in str(call) for call in error_calls)
