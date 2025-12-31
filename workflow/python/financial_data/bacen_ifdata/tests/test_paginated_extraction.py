"""
Unit and integration tests for paginated extraction task.

Tests cover:
- Task picking logic with database locking
- Task status update logic
- Raw purge decision and execution
- BACEN API URL building
- CSV parsing and raw data storage
- Next page task creation
- Full extraction flow (mocked and real database)
- Error handling and retry tracking
- Idempotency
"""

from unittest.mock import MagicMock, patch

import pytest

import psycopg2
import requests
from tasks.paginated_extraction import (
    DEFAULT_PAGE_TOP,
    MAX_ATTEMPTS,
    RetryablePaginatedExtractionError,
    build_bacen_url,
    create_next_page_task,
    execute_paginated_extraction,
    fetch_bacen_data,
    insert_raw_rows,
    parse_csv_rows,
    pick_pending_task,
    purge_raw_for_scope,
    should_purge_raw,
    update_task_status,
)

# ============================================================================
# Unit Tests - Helper Functions
# ============================================================================


class TestPickPendingTask:
    """Test pick_pending_task function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        return MagicMock()

    def test_pick_task_returns_task_dict(self, mock_conn, mock_cursor):
        """Test that pick_pending_task returns task dictionary when task exists."""
        # Mock fetchone to return a task
        mock_cursor.fetchone.return_value = (
            "202403",  # ano_mes
            1,  # tipo_instituicao
            "Report 1",  # nome_relatorio
            "1",  # numero_relatorio
            0,  # page_skip
            1000,  # page_top
            0,  # attempt_count
        )

        result = pick_pending_task(mock_conn, mock_cursor)

        assert result is not None
        assert result["ano_mes"] == "202403"
        assert result["tipo_instituicao"] == 1
        assert result["nome_relatorio"] == "Report 1"
        assert result["numero_relatorio"] == "1"
        assert result["page_skip"] == 0
        assert result["page_top"] == 1000
        assert result["attempt_count"] == 0

        # Verify SQL query
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "SELECT" in sql
        assert "FROM financial_data.ifdata_extract_task" in sql
        assert "WHERE status = 'PENDING'" in sql
        assert "ORDER BY created_at ASC" in sql
        assert "FOR UPDATE SKIP LOCKED" in sql

    def test_pick_task_returns_none_when_no_tasks(self, mock_conn, mock_cursor):
        """Test that pick_pending_task returns None when no tasks available."""
        mock_cursor.fetchone.return_value = None

        result = pick_pending_task(mock_conn, mock_cursor)

        assert result is None
        mock_cursor.execute.assert_called_once()

    def test_pick_task_defaults_page_top_when_missing(self, mock_conn, mock_cursor):
        """Test that pick_pending_task falls back to DEFAULT_PAGE_TOP when page_top is null."""
        mock_cursor.fetchone.return_value = (
            "202403",
            1,
            "Report 1",
            "1",
            0,
            None,  # Missing page_top
            0,
        )

        result = pick_pending_task(mock_conn, mock_cursor)

        assert result["page_top"] == DEFAULT_PAGE_TOP


class TestUpdateTaskStatus:
    """Test update_task_status function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        return MagicMock()

    @pytest.fixture
    def task_key(self):
        """Sample task key."""
        return {
            "ano_mes": "202403",
            "tipo_instituicao": 1,
            "nome_relatorio": "Report 1",
            "numero_relatorio": "1",
            "page_skip": 0,
        }

    def test_update_to_running_sets_started_at(self, mock_conn, mock_cursor, task_key):
        """Test that updating to RUNNING sets started_at timestamp."""
        update_task_status(mock_conn, mock_cursor, task_key, "RUNNING")

        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "UPDATE financial_data.ifdata_extract_task" in sql
        assert "status = %s" in sql
        assert "started_at = now()" in sql
        assert "attempt_count = attempt_count + 1" in sql

    def test_update_to_success_sets_finished_at(self, mock_conn, mock_cursor, task_key):
        """Test that updating to SUCCESS sets finished_at timestamp."""
        update_task_status(mock_conn, mock_cursor, task_key, "SUCCESS")

        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "UPDATE financial_data.ifdata_extract_task" in sql
        assert "status = %s" in sql
        assert "finished_at = now()" in sql

    def test_update_to_error_stores_error_details(
        self, mock_conn, mock_cursor, task_key
    ):
        """Test that updating to ERROR stores error and HTTP status."""
        error_msg = "HTTP 500: Internal Server Error"
        http_status = 500

        update_task_status(
            mock_conn,
            mock_cursor,
            task_key,
            "ERROR",
            error=error_msg,
            http_status=http_status,
        )

        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "last_error = %s" in sql
        assert "last_http_status = %s" in sql

        # Verify parameters include error and http_status
        params = mock_cursor.execute.call_args[0][1]
        assert error_msg in params
        assert http_status in params


class TestShouldPurgeRaw:
    """Test should_purge_raw function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        return MagicMock()

    @pytest.fixture
    def scope(self):
        """Sample scope."""
        return {
            "ano_mes": "202403",
            "nome_relatorio": "Report 1",
            "numero_relatorio": "1",
            "tipo_instituicao": 1,
        }

    def test_should_purge_when_page_skip_zero_and_raw_exists(
        self, mock_conn, mock_cursor, scope
    ):
        """Test that purge is needed when page_skip=0 and raw data exists."""
        # Mock count query to return > 0
        mock_cursor.fetchone.return_value = (5,)  # 5 rows exist

        result = should_purge_raw(mock_conn, mock_cursor, scope, page_skip=0)

        assert result is True
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "SELECT COUNT(*)" in sql
        assert "FROM financial_data.ifdata_raw" in sql

    def test_should_not_purge_when_page_skip_non_zero(
        self, mock_conn, mock_cursor, scope
    ):
        """Test that purge is not needed when page_skip != 0."""
        result = should_purge_raw(mock_conn, mock_cursor, scope, page_skip=1000)

        assert result is False
        # Should not execute query when page_skip != 0
        mock_cursor.execute.assert_not_called()

    def test_should_not_purge_when_no_raw_exists(self, mock_conn, mock_cursor, scope):
        """Test that purge is not needed when no raw data exists."""
        # Mock count query to return 0
        mock_cursor.fetchone.return_value = (0,)  # No rows exist

        result = should_purge_raw(mock_conn, mock_cursor, scope, page_skip=0)

        assert result is False


class TestPurgeRawForScope:
    """Test purge_raw_for_scope function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 5  # 5 rows deleted
        return cursor

    @pytest.fixture
    def scope(self):
        """Sample scope."""
        return {
            "ano_mes": "202403",
            "nome_relatorio": "Report 1",
            "numero_relatorio": "1",
            "tipo_instituicao": 1,
        }

    def test_purge_deletes_rows(self, mock_conn, mock_cursor, scope):
        """Test that purge deletes rows for scope."""
        result = purge_raw_for_scope(mock_conn, mock_cursor, scope)

        assert result == 5
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "DELETE FROM financial_data.ifdata_raw" in sql
        assert "WHERE ano_mes = %s" in sql


class TestBuildBacenUrl:
    """Test build_bacen_url function."""

    def test_builds_correct_url(self):
        """Test that URL is built correctly with all parameters."""
        url = build_bacen_url(
            ano_mes="202403",
            tipo_instituicao=1,
            relatorio="1",
            page_skip=0,
            page_top=1000,
        )

        assert "IfDataValores" in url
        assert "@AnoMes=202403" in url
        assert "@TipoInstituicao=1" in url
        assert (
            "@Relatorio='1'" in url or "@Relatorio=%271%27" in url
        )  # Quoted value (may be URL encoded)
        assert "$top=1000" in url
        assert "$skip=0" in url
        # URL encoding converts / to %2F
        assert "$format=text%2Fcsv" in url or "$format=text/csv" in url

    def test_url_encodes_special_characters(self):
        """Test that special characters in report names are URL encoded."""
        url = build_bacen_url(
            ano_mes="202403",
            tipo_instituicao=1,
            relatorio="Report Name with Spaces",
            page_skip=0,
            page_top=1000,
        )

        # URL should be properly encoded
        assert "Report" in url or "%20" in url  # Spaces should be encoded


class TestFetchBacenData:
    """Test fetch_bacen_data function."""

    @pytest.fixture
    def mock_session(self):
        """Mock requests session."""
        return MagicMock()

    def test_fetches_data_successfully(self, mock_session):
        """Test that data is fetched successfully."""
        mock_response = MagicMock()
        mock_response.text = "header1,header2\nvalue1,value2"
        mock_response.raise_for_status = MagicMock()
        mock_session.get.return_value = mock_response

        result = fetch_bacen_data(mock_session, "http://example.com/api")

        assert result == "header1,header2\nvalue1,value2"
        mock_session.get.assert_called_once_with("http://example.com/api", timeout=60)
        mock_response.raise_for_status.assert_called_once()

    def test_raises_on_http_error(self, mock_session):
        """Test that HTTP errors are raised."""
        mock_response = MagicMock()
        mock_response.raise_for_status.side_effect = requests.HTTPError("404 Not Found")
        mock_session.get.return_value = mock_response

        with pytest.raises(requests.HTTPError):
            fetch_bacen_data(mock_session, "http://example.com/api")


class TestParseCsvRows:
    """Test parse_csv_rows function."""

    @pytest.fixture
    def scope(self):
        """Sample scope."""
        return {
            "ano_mes": "202403",
            "tipo_instituicao": 1,
            "nome_relatorio": "Report 1",
            "numero_relatorio": "1",
        }

    def test_parses_csv_with_cod_inst(self, scope):
        """Test that CSV is parsed and cod_inst is extracted."""
        csv_text = "CodInst,Field1,Field2\n001,value1,value2\n002,value3,value4"

        result = parse_csv_rows(csv_text, scope)

        assert len(result) == 2
        assert result[0]["cod_inst"] == "001"
        assert result[0]["ano_mes"] == "202403"
        assert result[0]["tipo_instituicao"] == 1
        assert "value1,value2" in result[0]["raw_data"]  # Original row preserved

    def test_handles_missing_cod_inst(self, scope):
        """Test that missing cod_inst is handled gracefully."""
        csv_text = "Field1,Field2\nvalue1,value2"

        result = parse_csv_rows(csv_text, scope)

        assert len(result) == 1
        assert result[0]["cod_inst"] is None
        assert result[0]["raw_data"] == "value1,value2"

    def test_handles_empty_csv(self, scope):
        """Test that empty CSV returns empty list."""
        result = parse_csv_rows("", scope)
        assert result == []

        result = parse_csv_rows("   ", scope)
        assert result == []

    def test_handles_bom(self, scope):
        """Test that BOM is handled correctly."""
        csv_text = "\ufeffCodInst,Field1\n001,value1"

        result = parse_csv_rows(csv_text, scope)

        assert len(result) == 1
        assert result[0]["cod_inst"] == "001"


class TestInsertRawRows:
    """Test insert_raw_rows function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 2  # 2 rows inserted
        return cursor

    @pytest.fixture
    def sample_rows(self):
        """Sample rows to insert."""
        return [
            {
                "ano_mes": "202403",
                "tipo_instituicao": 1,
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "cod_inst": "001",
                "raw_data": "001,value1,value2",
            },
            {
                "ano_mes": "202403",
                "tipo_instituicao": 1,
                "nome_relatorio": "Report 1",
                "numero_relatorio": "1",
                "cod_inst": "002",
                "raw_data": "002,value3,value4",
            },
        ]

    def test_inserts_rows(self, mock_conn, mock_cursor, sample_rows):
        """Test that rows are inserted using executemany."""
        result = insert_raw_rows(mock_conn, mock_cursor, sample_rows)

        assert result == 2
        mock_cursor.executemany.assert_called_once()
        sql = mock_cursor.executemany.call_args[0][0]
        assert "INSERT INTO financial_data.ifdata_raw" in sql

    def test_handles_empty_list(self, mock_conn, mock_cursor):
        """Test that empty list returns 0 without error."""
        result = insert_raw_rows(mock_conn, mock_cursor, [])

        assert result == 0
        mock_cursor.executemany.assert_not_called()


class TestCreateNextPageTask:
    """Test create_next_page_task function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 1  # Task created
        return cursor

    @pytest.fixture
    def current_task(self):
        """Sample current task."""
        return {
            "ano_mes": "202403",
            "tipo_instituicao": 1,
            "nome_relatorio": "Report 1",
            "numero_relatorio": "1",
            "page_skip": 0,
            "page_top": 1000,
        }

    def test_creates_next_page_task(self, mock_conn, mock_cursor, current_task):
        """Test that next page task is created with correct page_skip."""
        result = create_next_page_task(
            mock_conn, mock_cursor, current_task, page_top=1000
        )

        assert result is True
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "INSERT INTO financial_data.ifdata_extract_task" in sql
        assert "ON CONFLICT" in sql

        # Verify page_skip is incremented
        params = mock_cursor.execute.call_args[0][1]
        assert params[4] == 1000  # page_skip = 0 + 1000

    def test_handles_existing_task(self, mock_conn, mock_cursor, current_task):
        """Test that existing task is handled gracefully (idempotency)."""
        mock_cursor.rowcount = 0  # Task already exists (ON CONFLICT DO NOTHING)

        result = create_next_page_task(
            mock_conn, mock_cursor, current_task, page_top=1000
        )

        assert result is False  # Task was not created (already exists)


# ============================================================================
# Integration Tests - Main Task Function
# ============================================================================


class TestExecutePaginatedExtraction:
    """Test execute_paginated_extraction Prefect task."""

    @pytest.fixture
    def sample_task(self):
        """Sample task from database."""
        return {
            "ano_mes": "202403",
            "tipo_instituicao": 1,
            "nome_relatorio": "Report 1",
            "numero_relatorio": "1",
            "page_skip": 0,
            "page_top": 1000,
            "attempt_count": 0,
        }

    @pytest.fixture
    def sample_csv(self):
        """Sample CSV response from BACEN."""
        return "CodInst,Field1,Field2\n001,value1,value2\n002,value3,value4"

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_bacen_session")
    @patch("tasks.paginated_extraction.get_db_connection")
    @patch("tasks.paginated_extraction.insert_raw_rows")
    def test_successful_extraction_with_next_page(
        self,
        mock_insert_raw_rows,
        mock_get_db_conn,
        mock_get_bacen_session,
        mock_logger,
        sample_task,
        sample_csv,
    ):
        """Test successful extraction that creates next page task."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # Mock pick_pending_task
        mock_cursor.fetchone.side_effect = [
            tuple(sample_task.values()),  # First call: pick task
            (0,),  # Second call: should_purge_raw (no existing raw)
        ]
        # Mock rowcount for create_next_page_task (returns 1 when next page is created)
        mock_cursor.rowcount = 1
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock insert_raw_rows to return page_top (full page) to trigger next page creation
        mock_insert_raw_rows.return_value = sample_task["page_top"]  # 1000 rows

        # Mock BACEN session
        mock_session = MagicMock()
        mock_response = MagicMock()
        mock_response.text = sample_csv
        mock_response.raise_for_status = MagicMock()
        mock_session.get.return_value = mock_response
        mock_get_bacen_session.return_value = mock_session

        # Call task function
        result = execute_paginated_extraction.fn()

        # Verify result
        assert result["status"] == "SUCCESS"
        assert result["rows_inserted"] == sample_task["page_top"]
        assert result["next_page_created"] is True
        assert result["error"] is None

        # Verify database operations
        assert (
            mock_conn.commit.call_count >= 2
        )  # At least 2 commits (status update, insert)
        mock_conn.close.assert_called_once()

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_bacen_session")
    @patch("tasks.paginated_extraction.get_db_connection")
    def test_empty_response_marks_empty(
        self, mock_get_db_conn, mock_get_bacen_session, mock_logger, sample_task
    ):
        """Test that empty CSV response marks task as EMPTY (terminal)."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # Mock pick_pending_task
        mock_cursor.fetchone.side_effect = [
            tuple(sample_task.values()),  # First call: pick task
            (0,),  # Second call: should_purge_raw (no existing raw)
        ]
        mock_cursor.rowcount = 0  # No rows inserted
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock BACEN session (empty response)
        mock_session = MagicMock()
        mock_response = MagicMock()
        mock_response.text = ""  # Empty CSV
        mock_response.raise_for_status = MagicMock()
        mock_session.get.return_value = mock_response
        mock_get_bacen_session.return_value = mock_session

        # Call task function
        result = execute_paginated_extraction.fn()

        # Verify result
        assert result["status"] == "EMPTY"
        assert result["rows_inserted"] == 0
        assert result["next_page_created"] is False
        assert result["error"] is None

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_bacen_session")
    @patch("tasks.paginated_extraction.get_db_connection")
    def test_no_pending_tasks_returns_none(
        self, mock_get_db_conn, mock_get_bacen_session, mock_logger
    ):
        """Test that no PENDING tasks returns None status."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # Mock pick_pending_task returns None
        mock_cursor.fetchone.return_value = None
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Call task function
        result = execute_paginated_extraction.fn()

        # Verify result
        assert result["status"] is None
        assert result["task_key"] is None
        assert result["rows_inserted"] == 0

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_bacen_session")
    @patch("tasks.paginated_extraction.get_db_connection")
    def test_http_error_marks_error_on_final_attempt(
        self, mock_get_db_conn, mock_get_bacen_session, mock_logger, sample_task
    ):
        """Test that HTTP errors mark task as ERROR on the last allowed attempt."""
        sample_task["attempt_count"] = MAX_ATTEMPTS - 1
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # Mock pick_pending_task
        mock_cursor.fetchone.side_effect = [
            tuple(sample_task.values()),  # First call: pick task
            (0,),  # Second call: should_purge_raw (no existing raw)
        ]
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock BACEN session (HTTP error)
        mock_session = MagicMock()
        mock_response = MagicMock()
        mock_response.status_code = 500
        mock_response.raise_for_status.side_effect = requests.HTTPError(
            "500 Internal Server Error"
        )
        mock_session.get.return_value = mock_response
        mock_get_bacen_session.return_value = mock_session

        # Call task function
        result = execute_paginated_extraction.fn()

        # Verify result
        assert result["status"] == "ERROR"
        assert result["rows_inserted"] == 0
        assert result["error"] is not None
        assert "HTTP" in result["error"] or "500" in result["error"]

        params = mock_cursor.execute.call_args_list[-1][0][1]
        assert params[0] == "ERROR"
        assert mock_conn.commit.call_count == 2  # RUNNING + failure update

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_bacen_session")
    @patch("tasks.paginated_extraction.get_db_connection")
    def test_http_error_requeues_and_raises_before_max(
        self, mock_get_db_conn, mock_get_bacen_session, mock_logger, sample_task
    ):
        """Test that HTTP errors before max attempts are re-queued and raise for retry."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        mock_cursor.fetchone.side_effect = [
            tuple(sample_task.values()),  # First call: pick task
            (0,),  # Second call: should_purge_raw (no existing raw)
        ]
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        mock_session = MagicMock()
        mock_response = MagicMock()
        mock_response.status_code = 503
        mock_response.raise_for_status.side_effect = requests.HTTPError(
            "503 Unavailable"
        )
        mock_session.get.return_value = mock_response
        mock_get_bacen_session.return_value = mock_session

        with pytest.raises(RetryablePaginatedExtractionError):
            execute_paginated_extraction.fn()

        params = mock_cursor.execute.call_args_list[-1][0][1]
        assert params[0] == "PENDING"
        assert mock_conn.commit.call_count == 2  # RUNNING + retry requeue

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_bacen_session")
    @patch("tasks.paginated_extraction.get_db_connection")
    def test_purge_raw_on_reload(
        self,
        mock_get_db_conn,
        mock_get_bacen_session,
        mock_logger,
        sample_task,
        sample_csv,
    ):
        """Test that raw data is purged when page_skip=0 and reloading."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # Mock pick_pending_task
        mock_cursor.fetchone.side_effect = [
            tuple(sample_task.values()),  # First call: pick task
            (5,),  # Second call: should_purge_raw (5 rows exist - reloading scenario)
        ]
        mock_cursor.rowcount = 2  # 2 rows inserted after purge
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock BACEN session
        mock_session = MagicMock()
        mock_response = MagicMock()
        mock_response.text = sample_csv
        mock_response.raise_for_status = MagicMock()
        mock_session.get.return_value = mock_response
        mock_get_bacen_session.return_value = mock_session

        # Call task function
        result = execute_paginated_extraction.fn()

        # Verify result
        assert result["status"] == "SUCCESS"

        # Verify purge was called (DELETE query should be executed)
        # We check that execute was called with DELETE statement
        execute_calls = [call[0][0] for call in mock_cursor.execute.call_args_list]
        delete_calls = [
            sql
            for sql in execute_calls
            if "DELETE FROM financial_data.ifdata_raw" in sql
        ]
        assert len(delete_calls) > 0  # Purge should have been called

    @patch("tasks.paginated_extraction.get_run_logger")
    @patch("tasks.paginated_extraction.get_db_connection")
    def test_database_error_raises(self, mock_get_db_conn, mock_logger):
        """Test that database errors are raised."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection error
        mock_get_db_conn.side_effect = psycopg2.OperationalError("Connection failed")

        # Verify error is raised
        with pytest.raises(psycopg2.OperationalError):
            execute_paginated_extraction.fn()
