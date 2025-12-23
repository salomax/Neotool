"""
Unit and integration tests for extract task creation.

Tests cover:
- Batch task creation logic (pure functions)
- Database operations (mocked and real)
- Idempotency (ON CONFLICT DO NOTHING)
- Error handling
- Edge cases (empty reports, invalid ano_mes, missing fields)
"""

from unittest.mock import MagicMock, PropertyMock, patch

import pytest

import psycopg2
from tasks.extract_task_creation import (
    DEFAULT_PAGE_SKIP,
    DEFAULT_PAGE_TOP,
    DEFAULT_STATUS,
    TIPO_INSTITUICAO_MIN,
    create_extract_tasks,
    create_extract_tasks_batch,
)

# ============================================================================
# Unit Tests - Pure Functions (No Database)
# ============================================================================


class TestCreateExtractTasksBatch:
    """Test create_extract_tasks_batch function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 1  # Default: row was inserted
        return cursor

    @pytest.fixture
    def mock_logger(self):
        """Mock logger."""
        logger = MagicMock()
        logger.debug = MagicMock()
        logger.warning = MagicMock()
        logger.info = MagicMock()
        return logger

    @pytest.fixture
    def sample_reports(self):
        """Sample reports for testing."""
        return [
            {"nome_relatorio": "Report 1", "numero_relatorio": "1"},
            {"nome_relatorio": "Report 2", "numero_relatorio": "2"},
        ]

    def test_create_tasks_for_all_tipos(
        self, mock_conn, mock_cursor, mock_logger, sample_reports
    ):
        """Test that tasks are created for all TipoInstituicao values (1-4)."""
        ano_mes = "202403"

        # Reset rowcount for each call (simulate successful inserts)
        mock_cursor.rowcount = 1

        stats = create_extract_tasks_batch(
            mock_conn, mock_cursor, ano_mes, sample_reports, mock_logger
        )

        # Should create 2 reports * 4 tipos = 8 tasks
        assert stats["total"] == 8
        assert stats["created"] == 8
        assert stats["existing"] == 0

        # Verify execute was called 8 times (one per task)
        assert mock_cursor.execute.call_count == 8

        # Verify all combinations were created
        execute_calls = [call[0][0] for call in mock_cursor.execute.call_args_list]
        assert all(
            "INSERT INTO financial_data.ifdata_extract_task" in sql
            for sql in execute_calls
        )

    def test_idempotency_existing_tasks(
        self, mock_conn, mock_cursor, mock_logger, sample_reports
    ):
        """Test that existing tasks are detected via rowcount=0 (ON CONFLICT)."""
        ano_mes = "202403"

        # Simulate some tasks already exist (rowcount=0 means conflict/already exists)
        call_count = [0]

        def rowcount_side_effect():
            call_count[0] += 1
            # First 4 tasks are new (created), next 4 already exist
            return 1 if call_count[0] <= 4 else 0

        # Use PropertyMock to simulate changing rowcount
        type(mock_cursor).rowcount = PropertyMock(side_effect=rowcount_side_effect)

        stats = create_extract_tasks_batch(
            mock_conn, mock_cursor, ano_mes, sample_reports, mock_logger
        )

        # Should have 8 total, 4 created, 4 existing
        assert stats["total"] == 8
        assert stats["created"] == 4
        assert stats["existing"] == 4

    def test_skip_reports_with_missing_fields(
        self, mock_conn, mock_cursor, mock_logger
    ):
        """Test that reports with missing required fields are skipped."""
        ano_mes = "202403"
        reports = [
            {"nome_relatorio": "Report 1", "numero_relatorio": "1"},  # Valid
            {"nome_relatorio": "", "numero_relatorio": "2"},  # Missing nome_relatorio
            {"nome_relatorio": "Report 3"},  # Missing numero_relatorio
            {"nome_relatorio": "Report 4", "numero_relatorio": "4"},  # Valid
        ]

        mock_cursor.rowcount = 1

        stats = create_extract_tasks_batch(
            mock_conn, mock_cursor, ano_mes, reports, mock_logger
        )

        # Should only process 2 valid reports * 4 tipos = 8 tasks
        assert stats["total"] == 8
        assert stats["created"] == 8

        # Should log warnings for skipped reports
        assert mock_logger.warning.call_count == 2

        # Should not execute for skipped reports
        # 2 valid reports * 4 tipos = 8 execute calls
        assert mock_cursor.execute.call_count == 8

    def test_empty_reports_list(self, mock_conn, mock_cursor, mock_logger):
        """Test that empty reports list creates no tasks."""
        ano_mes = "202403"
        reports = []

        stats = create_extract_tasks_batch(
            mock_conn, mock_cursor, ano_mes, reports, mock_logger
        )

        assert stats["total"] == 0
        assert stats["created"] == 0
        assert stats["existing"] == 0
        assert mock_cursor.execute.call_count == 0

    def test_sql_parameters_correct(self, mock_conn, mock_cursor, mock_logger):
        """Test that SQL parameters are passed correctly."""
        ano_mes = "202403"
        reports = [{"nome_relatorio": "Report 1", "numero_relatorio": "1"}]

        mock_cursor.rowcount = 1

        create_extract_tasks_batch(
            mock_conn, mock_cursor, ano_mes, reports, mock_logger
        )

        # Check first execute call (first task)
        first_call = mock_cursor.execute.call_args_list[0]
        sql = first_call[0][0]
        params = first_call[0][1]

        # Verify SQL contains ON CONFLICT
        assert "ON CONFLICT" in sql
        assert "DO NOTHING" in sql

        # Verify parameters
        assert params[0] == ano_mes
        assert params[1] == TIPO_INSTITUICAO_MIN  # First tipo
        assert params[2] == "Report 1"
        assert params[3] == "1"
        assert params[4] == DEFAULT_PAGE_SKIP
        assert params[5] == DEFAULT_PAGE_TOP
        assert params[6] == DEFAULT_STATUS


# ============================================================================
# Unit Tests - Prefect Task (Mocked Database)
# ============================================================================


class TestCreateExtractTasks:
    """Test create_extract_tasks Prefect task."""

    @pytest.fixture
    def sample_reports(self):
        """Sample reports for testing."""
        return [
            {"nome_relatorio": "Report 1", "numero_relatorio": "1"},
            {"nome_relatorio": "Report 2", "numero_relatorio": "2"},
        ]

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_successful_task_creation(
        self, mock_get_db_conn, mock_logger, sample_reports
    ):
        """Test successful creation of extract tasks."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.rowcount = 1  # All inserts succeed
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Call task function (using .fn() to bypass Prefect decorator)
        result = create_extract_tasks.fn("202403", sample_reports)

        # Verify result
        assert result["total"] == 8  # 2 reports * 4 tipos
        assert result["created"] == 8
        assert result["existing"] == 0

        # Verify database operations
        mock_get_db_conn.assert_called_once()
        mock_conn.cursor.assert_called_once()
        assert mock_cursor.execute.call_count == 8
        mock_conn.commit.assert_called_once()
        mock_conn.close.assert_called_once()

        # Verify logging
        mock_log.info.assert_called()
        assert any(
            "Creating extract tasks" in str(call)
            for call in mock_log.info.call_args_list
        )
        assert any(
            "Extract task creation complete" in str(call)
            for call in mock_log.info.call_args_list
        )

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_idempotency_on_retry(self, mock_get_db_conn, mock_logger, sample_reports):
        """Test that task is idempotent when called multiple times."""
        # Mock logger
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Create separate mock connections for each call
        # First call: all tasks created (rowcount=1)
        mock_conn1 = MagicMock()
        mock_cursor1 = MagicMock()
        mock_cursor1.__enter__.return_value = mock_cursor1
        mock_cursor1.__exit__.return_value = None
        mock_cursor1.rowcount = 1  # All inserts succeed
        mock_conn1.cursor.return_value = mock_cursor1

        # Second call: all tasks exist (rowcount=0)
        mock_conn2 = MagicMock()
        mock_cursor2 = MagicMock()
        mock_cursor2.__enter__.return_value = mock_cursor2
        mock_cursor2.__exit__.return_value = None
        mock_cursor2.rowcount = 0  # All conflicts (already exist)
        mock_conn2.cursor.return_value = mock_cursor2

        # Return different connections for each call
        mock_get_db_conn.side_effect = [mock_conn1, mock_conn2]

        # First call
        result1 = create_extract_tasks.fn("202403", sample_reports)
        assert result1["created"] == 8
        assert result1["existing"] == 0

        # Second call (should be idempotent)
        result2 = create_extract_tasks.fn("202403", sample_reports)
        assert result2["created"] == 0
        assert result2["existing"] == 8
        assert result2["total"] == 8

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_invalid_ano_mes_format(
        self, mock_get_db_conn, mock_logger, sample_reports
    ):
        """Test that invalid ano_mes format raises ValueError."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Test various invalid formats
        invalid_formats = ["", "2024", "2024031", "20240", "abc123", None]

        for invalid_ano_mes in invalid_formats:
            if invalid_ano_mes is None:
                # Skip None as it will fail the len() check differently
                continue

            with pytest.raises(ValueError, match="Invalid ano_mes format"):
                create_extract_tasks.fn(invalid_ano_mes, sample_reports)

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_empty_reports_list(self, mock_get_db_conn, mock_logger):
        """Test that empty reports list returns zero stats without error."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        result = create_extract_tasks.fn("202403", [])

        assert result["total"] == 0
        assert result["created"] == 0
        assert result["existing"] == 0

        # Should log warning about empty list
        mock_log.warning.assert_called()
        assert any(
            "Empty reports list" in str(call)
            for call in mock_log.warning.call_args_list
        )

        # Should not connect to database
        mock_get_db_conn.assert_not_called()

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_database_error_raises(self, mock_get_db_conn, mock_logger, sample_reports):
        """Test that database connection errors are logged and raised."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Simulate database connection failure
        mock_get_db_conn.side_effect = psycopg2.OperationalError("Connection failed")

        # Verify error is raised
        with pytest.raises(psycopg2.OperationalError, match="Connection failed"):
            create_extract_tasks.fn("202403", sample_reports)

        # Should log the database error even when connection creation fails
        assert mock_log.exception.called
        assert any(
            "Database error while creating extract tasks" in str(call)
            for call in mock_log.exception.call_args_list
        )

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_transaction_rollback_on_error(
        self, mock_get_db_conn, mock_logger, sample_reports
    ):
        """Test that transaction is rolled back on error during batch insert."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None

        # Simulate error during execute
        mock_cursor.execute.side_effect = psycopg2.DatabaseError("Database error")
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Verify error is raised
        with pytest.raises(psycopg2.DatabaseError, match="Database error"):
            create_extract_tasks.fn("202403", sample_reports)

        # Should rollback on error
        mock_conn.rollback.assert_called_once()
        # Should not commit on error
        mock_conn.commit.assert_not_called()
        # Should still close connection
        mock_conn.close.assert_called_once()

    @patch("tasks.extract_task_creation.create_extract_tasks_batch")
    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_unexpected_error_rolls_back_and_logs(
        self, mock_get_db_conn, mock_logger, mock_batch, sample_reports
    ):
        """Test that unexpected errors trigger rollback and logging."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Simulate unexpected error inside batch creation
        mock_batch.side_effect = RuntimeError("boom")

        with pytest.raises(RuntimeError, match="boom"):
            create_extract_tasks.fn("202403", sample_reports)

        mock_conn.rollback.assert_called_once()
        mock_conn.commit.assert_not_called()
        mock_conn.close.assert_called_once()
        assert mock_log.exception.called
        assert any(
            "Unexpected error while creating extract tasks" in str(call)
            for call in mock_log.exception.call_args_list
        )

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_all_tipo_instituicao_values(
        self, mock_get_db_conn, mock_logger, sample_reports
    ):
        """Test that tasks are created for all TipoInstituicao values (1-4)."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.rowcount = 1
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        create_extract_tasks.fn("202403", sample_reports)

        # Verify execute was called with all tipo values
        execute_calls = mock_cursor.execute.call_args_list

        # Extract tipo_instituicao values from parameters
        tipo_values = set()
        for call in execute_calls:
            params = call[0][1]  # Second argument is params tuple
            tipo_instituicao = params[1]  # tipo_instituicao is second param
            tipo_values.add(tipo_instituicao)

        # Should have all tipos 1-4
        assert tipo_values == {1, 2, 3, 4}

    @patch("tasks.extract_task_creation.get_run_logger")
    @patch("tasks.extract_task_creation.get_db_connection")
    def test_correct_default_values(self, mock_get_db_conn, mock_logger):
        """Test that tasks are created with correct default values."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Mock database connection
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.rowcount = 1
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        reports = [{"nome_relatorio": "Report 1", "numero_relatorio": "1"}]
        create_extract_tasks.fn("202403", reports)

        # Check first execute call parameters
        first_call = mock_cursor.execute.call_args_list[0]
        params = first_call[0][1]

        # Verify default values
        assert params[4] == DEFAULT_PAGE_SKIP  # page_skip
        assert params[5] == DEFAULT_PAGE_TOP  # page_top
        assert params[6] == DEFAULT_STATUS  # status


# ============================================================================
# Integration Tests - Testcontainers
# ============================================================================


@pytest.mark.integration
class TestCreateExtractTasksIntegration:
    """Integration tests with testcontainers database."""

    @pytest.fixture
    def clean_extract_tasks(self, migrated_database):
        """Clean ifdata_extract_task table before and after test."""
        with migrated_database.cursor() as cur:
            # Clean before
            cur.execute(
                "DELETE FROM financial_data.ifdata_extract_task WHERE ano_mes = '202403'"
            )
            migrated_database.commit()

        yield

        # Clean after
        with migrated_database.cursor() as cur:
            cur.execute(
                "DELETE FROM financial_data.ifdata_extract_task WHERE ano_mes = '202403'"
            )
            migrated_database.commit()

    @patch("tasks.extract_task_creation.get_db_connection")
    @patch("tasks.extract_task_creation.get_run_logger")
    def test_create_tasks_in_database(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_extract_tasks,
    ):
        """Test that tasks are actually created in database."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        reports = [
            {"nome_relatorio": "Test Report 1", "numero_relatorio": "1"},
            {"nome_relatorio": "Test Report 2", "numero_relatorio": "2"},
        ]

        # Call task function
        result = create_extract_tasks.fn("202403", reports)

        # Verify statistics
        assert result["total"] == 8  # 2 reports * 4 tipos
        assert result["created"] == 8
        assert result["existing"] == 0

        # Verify tasks exist in database
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.ifdata_extract_task
                WHERE ano_mes = '202403'
                """
            )
            count = cur.fetchone()[0]
            assert count == 8

            # Verify task details
            cur.execute(
                """
                SELECT ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
                       page_skip, page_top, status
                FROM financial_data.ifdata_extract_task
                WHERE ano_mes = '202403'
                ORDER BY tipo_instituicao, nome_relatorio
                """
            )
            tasks = cur.fetchall()

            assert len(tasks) == 8

            # Verify all tipos are present
            tipos = {task[1] for task in tasks}
            assert tipos == {1, 2, 3, 4}

            # Verify default values
            for task in tasks:
                assert task[4] == DEFAULT_PAGE_SKIP  # page_skip
                assert task[5] == DEFAULT_PAGE_TOP  # page_top
                assert task[6] == DEFAULT_STATUS  # status

    @patch("tasks.extract_task_creation.get_db_connection")
    @patch("tasks.extract_task_creation.get_run_logger")
    def test_idempotency_in_database(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_extract_tasks,
    ):
        """Test idempotency by calling task twice."""
        mock_log = MagicMock()
        mock_logger.return_value = mock_log

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        reports = [
            {"nome_relatorio": "Test Report 1", "numero_relatorio": "1"},
        ]

        # First call
        result1 = create_extract_tasks.fn("202403", reports)
        assert result1["created"] == 4  # 1 report * 4 tipos
        assert result1["existing"] == 0

        # Second call (should be idempotent)
        result2 = create_extract_tasks.fn("202403", reports)
        assert result2["created"] == 0
        assert result2["existing"] == 4
        assert result2["total"] == 4

        # Verify only 4 tasks exist (no duplicates)
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.ifdata_extract_task
                WHERE ano_mes = '202403'
                """
            )
            count = cur.fetchone()[0]
            assert count == 4
