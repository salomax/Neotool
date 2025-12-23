"""
Unit and integration tests for extract completion check.

Tests cover:
- Completion check logic (pure functions)
- Database operations (mocked and real)
- Marking ifdata_load_control as extracted
- Idempotency
- Error handling
- Edge cases (no tasks, all terminal, mixed statuses)
"""

from unittest.mock import MagicMock, patch

import pytest

import psycopg2
from tasks.extract_completion import (
    check_extract_completion,
    check_extract_completion_task,
    mark_load_control_extracted,
)

# ============================================================================
# Unit Tests - Pure Functions (No Database)
# ============================================================================


class TestCheckExtractCompletion:
    """Test check_extract_completion function."""

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
        logger.info = MagicMock()
        logger.warning = MagicMock()
        return logger

    def test_all_tasks_terminal_success(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when all tasks are SUCCESS."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: all tasks are SUCCESS
        mock_cursor.fetchall.return_value = [
            ("SUCCESS", 8),
        ]

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result["is_complete"] is True
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 8
        assert result["non_terminal_tasks"] == 0
        assert result["status_breakdown"] == {"SUCCESS": 8}

        # Verify SQL query
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "SELECT" in sql
        assert "status" in sql
        assert "COUNT(*)" in sql
        assert "FROM financial_data.ifdata_extract_task" in sql
        assert "WHERE ano_mes = %s AND nome_relatorio = %s" in sql

    def test_all_tasks_terminal_empty(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when all tasks are EMPTY."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: all tasks are EMPTY
        mock_cursor.fetchall.return_value = [
            ("EMPTY", 4),
        ]

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result["is_complete"] is True
        assert result["total_tasks"] == 4
        assert result["terminal_tasks"] == 4
        assert result["non_terminal_tasks"] == 0
        assert result["status_breakdown"] == {"EMPTY": 4}

    def test_mixed_terminal_statuses(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when tasks have mixed terminal statuses."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: mix of SUCCESS and EMPTY (both terminal)
        mock_cursor.fetchall.return_value = [
            ("SUCCESS", 6),
            ("EMPTY", 2),
        ]

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result["is_complete"] is True
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 8
        assert result["non_terminal_tasks"] == 0
        assert result["status_breakdown"] == {"SUCCESS": 6, "EMPTY": 2}

    def test_pending_tasks_not_complete(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when some tasks are PENDING."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: mix of terminal and non-terminal
        mock_cursor.fetchall.return_value = [
            ("SUCCESS", 6),
            ("PENDING", 2),
        ]

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result["is_complete"] is False
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 6
        assert result["non_terminal_tasks"] == 2
        assert result["status_breakdown"] == {"SUCCESS": 6, "PENDING": 2}

    def test_running_tasks_not_complete(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when some tasks are RUNNING."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: mix of terminal and RUNNING
        mock_cursor.fetchall.return_value = [
            ("SUCCESS", 4),
            ("EMPTY", 2),
            ("RUNNING", 2),
        ]

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result["is_complete"] is False
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 6
        assert result["non_terminal_tasks"] == 2
        assert result["status_breakdown"] == {"SUCCESS": 4, "EMPTY": 2, "RUNNING": 2}

    def test_error_tasks_not_complete(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when some tasks are ERROR."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: mix of terminal and ERROR
        mock_cursor.fetchall.return_value = [
            ("SUCCESS", 6),
            ("ERROR", 2),
        ]

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result["is_complete"] is False
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 6
        assert result["non_terminal_tasks"] == 2
        assert result["status_breakdown"] == {"SUCCESS": 6, "ERROR": 2}

    def test_no_tasks_not_complete(self, mock_conn, mock_cursor, mock_logger):
        """Test completion check when no tasks exist."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock query result: no tasks
        mock_cursor.fetchall.return_value = []

        result = check_extract_completion(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        # No tasks means not complete (can't mark as extracted if no tasks exist)
        assert result["is_complete"] is False
        assert result["total_tasks"] == 0
        assert result["terminal_tasks"] == 0
        assert result["non_terminal_tasks"] == 0
        assert result["status_breakdown"] == {}


class TestMarkLoadControlExtracted:
    """Test mark_load_control_extracted function."""

    @pytest.fixture
    def mock_conn(self):
        """Mock database connection."""
        return MagicMock()

    @pytest.fixture
    def mock_cursor(self):
        """Mock database cursor."""
        cursor = MagicMock()
        cursor.rowcount = 1  # Default: row was inserted/updated
        return cursor

    @pytest.fixture
    def mock_logger(self):
        """Mock logger."""
        logger = MagicMock()
        logger.info = MagicMock()
        logger.warning = MagicMock()
        return logger

    def test_mark_extracted_new_record(self, mock_conn, mock_cursor, mock_logger):
        """Test marking extracted when record doesn't exist (INSERT)."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        mock_cursor.rowcount = 1  # Row was inserted

        result = mark_load_control_extracted(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result is True

        # Verify SQL query
        mock_cursor.execute.assert_called_once()
        sql = mock_cursor.execute.call_args[0][0]
        assert "INSERT INTO financial_data.ifdata_load_control" in sql
        assert "ON CONFLICT" in sql
        assert "DO UPDATE SET" in sql
        assert "extracted = TRUE" in sql
        assert "extracted_at" in sql

        # Verify parameters
        params = mock_cursor.execute.call_args[0][1]
        assert params[0] == ano_mes
        assert params[1] == nome_relatorio

        # Verify logging
        mock_logger.info.assert_called_once()
        assert (
            "Marked ifdata_load_control as extracted"
            in mock_logger.info.call_args[0][0]
        )

    def test_mark_extracted_existing_record(self, mock_conn, mock_cursor, mock_logger):
        """Test marking extracted when record exists (UPDATE)."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        mock_cursor.rowcount = 1  # Row was updated

        result = mark_load_control_extracted(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result is True
        mock_logger.info.assert_called_once()

    def test_mark_extracted_no_rows_updated(self, mock_conn, mock_cursor, mock_logger):
        """Test marking extracted when no rows are updated."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        mock_cursor.rowcount = 0  # No rows updated

        result = mark_load_control_extracted(
            mock_conn, mock_cursor, ano_mes, nome_relatorio, mock_logger
        )

        assert result is False
        mock_logger.warning.assert_called_once()
        assert "No rows updated" in mock_logger.warning.call_args[0][0]


# ============================================================================
# Unit Tests - Prefect Task (Mocked Database)
# ============================================================================


class TestCheckExtractCompletionTask:
    """Test check_extract_completion_task Prefect task."""

    @patch("tasks.extract_completion.get_run_logger")
    @patch("tasks.extract_completion.get_db_connection")
    def test_successful_completion_all_terminal(
        self, mock_get_db_conn, mock_get_logger
    ):
        """Test successful completion check when all tasks are terminal."""
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

        # Mock completion check: all tasks terminal
        mock_cursor.fetchall.side_effect = [
            [("SUCCESS", 8)],  # First call: check_extract_completion
        ]
        mock_cursor.rowcount = 1  # Second call: mark_load_control_extracted

        # Use .fn() to bypass Prefect decorator overhead for faster unit tests
        result = check_extract_completion_task.fn(ano_mes, nome_relatorio)

        assert result["is_complete"] is True
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 8
        assert result["non_terminal_tasks"] == 0
        assert result["marked_extracted"] is True

        # Verify commit was called
        mock_conn.commit.assert_called_once()
        mock_conn.close.assert_called_once()

    @patch("tasks.extract_completion.get_run_logger")
    @patch("tasks.extract_completion.get_db_connection")
    def test_not_complete_pending_tasks(self, mock_get_db_conn, mock_get_logger):
        """Test completion check when some tasks are PENDING."""
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

        # Mock completion check: some tasks pending
        mock_cursor.fetchall.return_value = [
            ("SUCCESS", 6),
            ("PENDING", 2),
        ]

        # Use .fn() to bypass Prefect decorator overhead for faster unit tests
        result = check_extract_completion_task.fn(ano_mes, nome_relatorio)

        assert result["is_complete"] is False
        assert result["total_tasks"] == 8
        assert result["terminal_tasks"] == 6
        assert result["non_terminal_tasks"] == 2
        assert result["marked_extracted"] is False  # Should not mark if not complete

        # Verify mark_load_control_extracted was NOT called (no execute for UPDATE)
        # Only one execute call (for check_extract_completion)
        assert mock_cursor.execute.call_count == 1

        # Verify commit was called
        mock_conn.commit.assert_called_once()
        mock_conn.close.assert_called_once()

    @patch("tasks.extract_completion.get_run_logger")
    @patch("tasks.extract_completion.get_db_connection")
    def test_invalid_ano_mes_format(self, mock_get_db_conn, mock_get_logger):
        """Test that invalid ano_mes format raises ValueError."""
        ano_mes = "invalid"
        nome_relatorio = "Report 1"

        # Mock logger - set up before function is called
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Validation happens before get_db_connection is called, so we patch it
        # to verify it's never called (confirms validation happens first)
        # Use .fn() to bypass Prefect decorator overhead for faster unit tests
        with pytest.raises(ValueError) as exc_info:
            check_extract_completion_task.fn(ano_mes, nome_relatorio)

        assert "Invalid ano_mes format" in str(exc_info.value)
        assert "Expected YYYYMM format" in str(exc_info.value)

        # Verify get_db_connection was never called (validation failed first)
        # This confirms the test is truly unit-testing validation logic
        mock_get_db_conn.assert_not_called()

    @patch("tasks.extract_completion.get_run_logger")
    @patch("tasks.extract_completion.get_db_connection")
    def test_empty_nome_relatorio(self, mock_get_db_conn, mock_get_logger):
        """Test that empty nome_relatorio raises ValueError."""
        ano_mes = "202403"
        nome_relatorio = ""

        # Mock logger - set up before function is called
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Validation happens before get_db_connection is called, so we patch it
        # to verify it's never called (confirms validation happens first)
        # Use .fn() to bypass Prefect decorator overhead for faster unit tests
        with pytest.raises(ValueError) as exc_info:
            check_extract_completion_task.fn(ano_mes, nome_relatorio)

        assert "nome_relatorio cannot be empty" in str(exc_info.value)

        # Verify get_db_connection was never called (validation failed first)
        # This confirms the test is truly unit-testing validation logic
        mock_get_db_conn.assert_not_called()

    @patch("tasks.extract_completion.get_run_logger")
    @patch("tasks.extract_completion.get_db_connection")
    def test_database_error_handling(self, mock_get_db_conn, mock_get_logger):
        """Test that database errors are properly handled."""
        ano_mes = "202403"
        nome_relatorio = "Report 1"

        # Mock logger
        mock_logger = MagicMock()
        mock_get_logger.return_value = mock_logger

        # Mock database connection that raises error
        mock_conn = MagicMock()
        mock_get_db_conn.return_value = mock_conn
        mock_conn.cursor.side_effect = psycopg2.Error("Database connection failed")

        # Use .fn() to bypass Prefect decorator overhead for faster unit tests
        with pytest.raises(psycopg2.Error):
            check_extract_completion_task.fn(ano_mes, nome_relatorio)

        # Verify rollback was called
        mock_conn.rollback.assert_called_once()
        mock_conn.close.assert_called_once()

        # Verify error was logged
        mock_logger.exception.assert_called_once()
        assert "Database error" in mock_logger.exception.call_args[0][0]


# ============================================================================
# Integration Tests (Testcontainers)
# ============================================================================


@pytest.mark.integration
class TestExtractCompletionIntegration:
    """Integration tests for extract completion with testcontainers database."""

    @pytest.fixture
    def clean_tables(self, migrated_database):
        """Clean test tables before and after test."""
        with migrated_database.cursor() as cur:
            cur.execute("DELETE FROM financial_data.ifdata_extract_task")
            cur.execute("DELETE FROM financial_data.ifdata_load_control")
            migrated_database.commit()

        yield

        with migrated_database.cursor() as cur:
            cur.execute("DELETE FROM financial_data.ifdata_extract_task")
            cur.execute("DELETE FROM financial_data.ifdata_load_control")
            migrated_database.commit()

    def test_integration_all_terminal_mark_extracted(
        self, migrated_database, clean_tables
    ):
        """Integration test: all tasks terminal -> mark as extracted."""
        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # Create extract tasks with terminal statuses
        with migrated_database.cursor() as cur:
            # Create 4 tasks (one per tipo) all with SUCCESS status
            for tipo in range(1, 5):
                cur.execute(
                    """
                    INSERT INTO financial_data.ifdata_extract_task
                        (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
                         page_skip, page_top, status)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                    (ano_mes, tipo, nome_relatorio, "1", 0, 1000, "SUCCESS"),
                )

            migrated_database.commit()

        # Check completion
        with migrated_database.cursor() as cur:
            from unittest.mock import MagicMock

            from tasks.extract_completion import (
                check_extract_completion,
                mark_load_control_extracted,
            )

            mock_logger = MagicMock()

            result = check_extract_completion(
                migrated_database, cur, ano_mes, nome_relatorio, mock_logger
            )

            assert result["is_complete"] is True
            assert result["total_tasks"] == 4
            assert result["terminal_tasks"] == 4
            assert result["non_terminal_tasks"] == 0

            # Mark as extracted
            marked = mark_load_control_extracted(
                migrated_database, cur, ano_mes, nome_relatorio, mock_logger
            )
            assert marked is True

            migrated_database.commit()

        # Verify ifdata_load_control was created and marked as extracted
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT extracted, extracted_at
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )

            row = cur.fetchone()
            assert row is not None
            assert row[0] is True  # extracted = TRUE
            assert row[1] is not None  # extracted_at is set

    def test_integration_idempotency(self, migrated_database, clean_tables):
        """Integration test: marking extracted multiple times is idempotent."""
        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # Create extract tasks with terminal statuses
        with migrated_database.cursor() as cur:
            for tipo in range(1, 5):
                cur.execute(
                    """
                    INSERT INTO financial_data.ifdata_extract_task
                        (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
                         page_skip, page_top, status)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                    (ano_mes, tipo, nome_relatorio, "1", 0, 1000, "SUCCESS"),
                )

            migrated_database.commit()

        # Mark as extracted first time
        with migrated_database.cursor() as cur:
            from unittest.mock import MagicMock

            from tasks.extract_completion import mark_load_control_extracted

            mock_logger = MagicMock()

            marked1 = mark_load_control_extracted(
                migrated_database, cur, ano_mes, nome_relatorio, mock_logger
            )
            assert marked1 is True

            migrated_database.commit()

        # Get first extracted_at timestamp
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT extracted_at
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            first_extracted_at = cur.fetchone()[0]

        # Mark as extracted second time (idempotent)
        with migrated_database.cursor() as cur:
            from unittest.mock import MagicMock

            from tasks.extract_completion import mark_load_control_extracted

            mock_logger = MagicMock()

            marked2 = mark_load_control_extracted(
                migrated_database, cur, ano_mes, nome_relatorio, mock_logger
            )
            assert marked2 is True  # Still returns True (row was updated)

            migrated_database.commit()

        # Verify extracted_at was NOT changed (preserved from first call)
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT extracted_at
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s AND nome_relatorio = %s
            """,
                (ano_mes, nome_relatorio),
            )
            second_extracted_at = cur.fetchone()[0]

            # extracted_at should be preserved (not updated on second call)
            assert second_extracted_at == first_extracted_at

    def test_integration_mixed_statuses_not_complete(
        self, migrated_database, clean_tables
    ):
        """Integration test: mixed statuses -> not complete."""
        ano_mes = "202403"
        nome_relatorio = "Test Report"

        # Create extract tasks with mixed statuses
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_extract_task
                    (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
                     page_skip, page_top, status)
                VALUES
                    (%s, 1, %s, '1', 0, 1000, 'SUCCESS'),
                    (%s, 2, %s, '1', 0, 1000, 'SUCCESS'),
                    (%s, 3, %s, '1', 0, 1000, 'PENDING'),
                    (%s, 4, %s, '1', 0, 1000, 'RUNNING')
            """,
                (
                    ano_mes,
                    nome_relatorio,
                    ano_mes,
                    nome_relatorio,
                    ano_mes,
                    nome_relatorio,
                    ano_mes,
                    nome_relatorio,
                ),
            )

            migrated_database.commit()

        # Check completion
        with migrated_database.cursor() as cur:
            from unittest.mock import MagicMock

            from tasks.extract_completion import check_extract_completion

            mock_logger = MagicMock()

            result = check_extract_completion(
                migrated_database, cur, ano_mes, nome_relatorio, mock_logger
            )

            assert result["is_complete"] is False
            assert result["total_tasks"] == 4
            assert result["terminal_tasks"] == 2  # 2 SUCCESS
            assert result["non_terminal_tasks"] == 2  # 1 PENDING + 1 RUNNING
            assert result["status_breakdown"]["SUCCESS"] == 2
            assert result["status_breakdown"]["PENDING"] == 1
            assert result["status_breakdown"]["RUNNING"] == 1
