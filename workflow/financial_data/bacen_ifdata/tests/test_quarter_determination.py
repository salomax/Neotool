"""
Unit and integration tests for quarter determination task.

Tests cover:
- Quarter computation logic (pure functions)
- Publication window calculations
- Database query logic (mocked and real)
- Edge cases (year boundaries, leap years)
"""

from datetime import date, timedelta
from unittest.mock import MagicMock, Mock, patch

import pytest

import psycopg2
from tasks.quarter_determination import (
    compute_next_quarter,
    determine_target_quarter,
    get_publication_deadline,
    get_quarter_end_date,
    is_within_publication_window,
    parse_ano_mes,
)

# ============================================================================
# Unit Tests - Pure Functions (No Database)
# ============================================================================


class TestParseAnoMes:
    """Test parse_ano_mes function."""

    def test_valid_ano_mes(self):
        """Test parsing valid ano_mes strings."""
        assert parse_ano_mes("202403") == (2024, 3)
        assert parse_ano_mes("202406") == (2024, 6)
        assert parse_ano_mes("202409") == (2024, 9)
        assert parse_ano_mes("202412") == (2024, 12)
        assert parse_ano_mes("201603") == (2016, 3)
        assert parse_ano_mes("200001") == (2000, 1)
        assert parse_ano_mes("209912") == (2099, 12)

    def test_invalid_format(self):
        """Test parsing invalid format raises ValueError."""
        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            parse_ano_mes("2024")
        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            parse_ano_mes("2024031")
        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            parse_ano_mes("2024-03")
        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            parse_ano_mes("abc123")
        with pytest.raises(ValueError, match="Invalid ano_mes format"):
            parse_ano_mes("")

    def test_invalid_month(self):
        """Test parsing invalid month raises ValueError."""
        with pytest.raises(ValueError, match="Invalid month"):
            parse_ano_mes("202400")
        with pytest.raises(ValueError, match="Invalid month"):
            parse_ano_mes("202413")


class TestComputeNextQuarter:
    """Test compute_next_quarter function."""

    def test_quarterly_months(self):
        """Test computing next quarter for quarterly months."""
        assert compute_next_quarter("202403") == "202406"  # March -> June
        assert compute_next_quarter("202406") == "202409"  # June -> September
        assert compute_next_quarter("202409") == "202412"  # September -> December
        assert (
            compute_next_quarter("202412") == "202503"
        )  # December -> March (next year)

    def test_year_boundary(self):
        """Test year boundary crossing."""
        assert compute_next_quarter("202312") == "202403"  # Dec 2023 -> Mar 2024
        assert compute_next_quarter("199912") == "200003"  # Dec 1999 -> Mar 2000

    def test_non_quarterly_months(self):
        """Test that non-quarterly months are treated as previous quarter."""
        # January/February treated as previous December
        assert compute_next_quarter("202401") == "202403"  # Jan -> Mar (same year)
        assert compute_next_quarter("202402") == "202403"  # Feb -> Mar (same year)
        # April/May treated as previous March
        assert compute_next_quarter("202404") == "202406"  # Apr -> Jun
        assert compute_next_quarter("202405") == "202406"  # May -> Jun
        # July/August treated as previous June
        assert compute_next_quarter("202407") == "202409"  # Jul -> Sep
        assert compute_next_quarter("202408") == "202409"  # Aug -> Sep
        # October/November treated as previous September
        assert compute_next_quarter("202410") == "202412"  # Oct -> Dec
        assert compute_next_quarter("202411") == "202412"  # Nov -> Dec

    def test_historical_start(self):
        """Test with historical start date."""
        assert compute_next_quarter("201603") == "201606"  # Mar 2016 -> Jun 2016


class TestGetQuarterEndDate:
    """Test get_quarter_end_date function."""

    def test_quarter_end_dates(self):
        """Test quarter end dates."""
        assert get_quarter_end_date(2024, 3) == date(2024, 3, 31)  # Q1
        assert get_quarter_end_date(2024, 6) == date(2024, 6, 30)  # Q2
        assert get_quarter_end_date(2024, 9) == date(2024, 9, 30)  # Q3
        assert get_quarter_end_date(2024, 12) == date(2024, 12, 31)  # Q4

    def test_leap_year_february(self):
        """Test that leap year doesn't affect quarter end dates."""
        # Q1 ends March 31, not affected by February
        assert get_quarter_end_date(2024, 3) == date(2024, 3, 31)  # 2024 is leap year
        assert get_quarter_end_date(2023, 3) == date(
            2023, 3, 31
        )  # 2023 is not leap year

    def test_invalid_month(self):
        """Test invalid month raises ValueError."""
        with pytest.raises(ValueError, match="Invalid quarterly month"):
            get_quarter_end_date(2024, 1)
        with pytest.raises(ValueError, match="Invalid quarterly month"):
            get_quarter_end_date(2024, 13)


class TestGetPublicationDeadline:
    """Test get_publication_deadline function."""

    def test_q1_q3_deadlines(self):
        """Test Q1-Q3 deadlines (60 days)."""
        # March 2024: ends 2024-03-31, deadline 2024-05-30 (60 days)
        deadline = get_publication_deadline("202403")
        assert deadline == date(2024, 3, 31) + timedelta(days=60)
        assert deadline == date(2024, 5, 30)

        # June 2024: ends 2024-06-30, deadline 2024-08-29 (60 days)
        deadline = get_publication_deadline("202406")
        assert deadline == date(2024, 6, 30) + timedelta(days=60)
        assert deadline == date(2024, 8, 29)

        # September 2024: ends 2024-09-30, deadline 2024-11-29 (60 days)
        deadline = get_publication_deadline("202409")
        assert deadline == date(2024, 9, 30) + timedelta(days=60)
        assert deadline == date(2024, 11, 29)

    def test_q4_deadline(self):
        """Test Q4 deadline (90 days)."""
        # December 2024: ends 2024-12-31, deadline 2025-03-31 (90 days)
        deadline = get_publication_deadline("202412")
        assert deadline == date(2024, 12, 31) + timedelta(days=90)
        assert deadline == date(2025, 3, 31)

    def test_invalid_ano_mes(self):
        """Test invalid ano_mes raises ValueError."""
        with pytest.raises(ValueError, match="Invalid quarterly month"):
            get_publication_deadline("202401")


class TestIsWithinPublicationWindow:
    """Test is_within_publication_window function."""

    def test_before_deadline(self):
        """Test when today is before deadline."""
        # March 2024 deadline is 2024-05-30
        # Check on 2024-05-29 (before deadline)
        assert not is_within_publication_window("202403", date(2024, 5, 29))

    def test_on_deadline(self):
        """Test when today is on deadline."""
        # March 2024 deadline is 2024-05-30
        assert is_within_publication_window("202403", date(2024, 5, 30))

    def test_after_deadline(self):
        """Test when today is after deadline."""
        # March 2024 deadline is 2024-05-30
        # Check on 2024-05-31 (after deadline)
        assert is_within_publication_window("202403", date(2024, 5, 31))

    def test_default_today(self):
        """Test that function uses today() when date not provided."""
        # This test just verifies it doesn't crash
        # Actual result depends on when test runs
        result = is_within_publication_window("202403")
        assert isinstance(result, bool)


# ============================================================================
# Unit Tests - Database Query Logic (Mocked)
# ============================================================================


class TestDetermineTargetQuarterMocked:
    """Test determine_target_quarter with mocked database."""

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_no_records_historical_start(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test when no records exist, uses historical start."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: no records
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {"last_ano_mes": None}
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        # Historical start: 201603, next quarter: 201606
        # June 2016 deadline: June 30, 2016 + 60 days = August 29, 2016
        # Use date after deadline: September 1, 2016
        from datetime import date as real_date

        # Make the mock callable (for date() constructor) and mock today() method
        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(
            return_value=date(2016, 9, 1)
        )  # After June 2016 deadline

        result = determine_target_quarter()

        assert result == "201606"  # Next quarter after 201603
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_last_extracted_computes_next(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test computing next quarter from last extracted."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: last extracted is 202412
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {"last_ano_mes": "202412"}
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        # Last extracted: 202412, next quarter: 202503
        # March 2025 deadline: March 31, 2025 + 60 days = May 30, 2025
        # Use date after deadline: June 1, 2025
        from datetime import date as real_date

        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(
            return_value=date(2025, 6, 1)
        )  # After March 2025 deadline

        result = determine_target_quarter()

        assert result == "202503"  # Next quarter after 202412
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_publication_window_not_reached_returns_none(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test when publication window not reached, returns None."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: last extracted is 202403
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {"last_ano_mes": "202403"}
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        from datetime import date as real_date

        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(return_value=date(2024, 8, 1))  # Before deadline

        result = determine_target_quarter()

        assert result is None  # Should wait
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_publication_window_reached_returns_target(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test when publication window reached, returns target quarter."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: last extracted is 202403
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {"last_ano_mes": "202403"}
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        from datetime import date as real_date

        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(return_value=date(2024, 9, 1))  # After deadline

        result = determine_target_quarter()

        assert result == "202406"  # Next quarter after 202403
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_prioritizes_completed_over_promoted_and_extracted(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test that completed quarters are prioritized over promoted and extracted.

        This ensures we don't skip incomplete quarters when a more complete one exists.
        """
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: completed=202412, promoted=202409, extracted=202406
        # Should prioritize completed (202412)
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {
            "last_ano_mes": "202412"
        }  # COALESCE returns completed
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        # Last completed: 202412, next quarter: 202503
        # March 2025 deadline: March 31, 2025 + 60 days = May 30, 2025
        # Use date after deadline: June 1, 2025
        from datetime import date as real_date

        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(
            return_value=date(2025, 6, 1)
        )  # After March 2025 deadline

        result = determine_target_quarter()

        assert result == "202503"  # Next quarter after 202412 (completed)
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_prioritizes_promoted_over_extracted(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test that promoted quarters are prioritized over extracted when no completed exists.

        This ensures we don't skip promoted quarters that are waiting for completion.
        """
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: no completed, promoted=202409, extracted=202406
        # Should prioritize promoted (202409)
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {
            "last_ano_mes": "202409"
        }  # COALESCE returns promoted
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        # Last promoted: 202409, next quarter: 202412
        # December 2024 deadline: December 31, 2024 + 90 days = March 31, 2025
        # Use date after deadline: April 1, 2025
        from datetime import date as real_date

        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(
            return_value=date(2025, 4, 1)
        )  # After December 2024 deadline

        result = determine_target_quarter()

        assert result == "202412"  # Next quarter after 202409 (promoted)
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.date")
    def test_uses_extracted_when_no_promoted_or_completed(
        self, mock_date_class, mock_get_db_conn, mock_logger
    ):
        """Test that extracted quarters are used when no promoted or completed exists.

        This is the fallback case when only extraction has been done.
        """
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Mock database: no completed, no promoted, extracted=202406
        # Should use extracted (202406)
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__.return_value = mock_cursor
        mock_cursor.__exit__.return_value = None
        mock_cursor.fetchone.return_value = {
            "last_ano_mes": "202406"
        }  # COALESCE returns extracted
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_conn.return_value = mock_conn

        # Mock date class: preserve constructor, mock today()
        # Last extracted: 202406, next quarter: 202409
        # September 2024 deadline: September 30, 2024 + 60 days = November 29, 2024
        # Use date after deadline: December 1, 2024
        from datetime import date as real_date

        def date_side_effect(*args, **kwargs):
            if args or kwargs:
                return real_date(*args, **kwargs)
            raise TypeError("date() missing required argument 'year' (pos 1)")

        mock_date_class.side_effect = date_side_effect
        mock_date_class.today = Mock(
            return_value=date(2024, 12, 1)
        )  # After September 2024 deadline

        result = determine_target_quarter()

        assert result == "202409"  # Next quarter after 202406 (extracted)
        mock_conn.close.assert_called_once()

    @patch("tasks.quarter_determination.get_run_logger")
    @patch("tasks.quarter_determination.get_db_connection")
    def test_database_error_raises(self, mock_get_db_conn, mock_logger):
        """Test that database errors are properly raised and logged.

        This test is important because:
        1. It verifies error propagation (errors aren't silently swallowed)
        2. It ensures Prefect's retry mechanism works (errors must be raised for retries)
        3. It tests the error handling path in the code
        4. It verifies that errors are logged before being raised
        """
        # Mock logger with exception method
        mock_log = MagicMock()
        mock_log.exception = MagicMock()
        mock_logger.return_value = mock_log

        # Simulate database connection failure
        mock_get_db_conn.side_effect = psycopg2.OperationalError("Connection failed")

        # Verify error is raised
        with pytest.raises(psycopg2.OperationalError, match="Connection failed"):
            determine_target_quarter()

        # Verify error was logged before being raised
        # Check that exception was called at least once
        assert mock_log.exception.call_count >= 1
        # Check that at least one call contains "Database error" in the message
        assert any(
            "Database error" in str(call) for call in mock_log.exception.call_args_list
        )


# ============================================================================
# Integration Tests - Testcontainers
# ============================================================================


@pytest.mark.integration
class TestDetermineTargetQuarterIntegration:
    """Integration tests with testcontainers database."""

    @pytest.fixture
    def clean_load_control(self, migrated_database):
        """Clean ifdata_load_control table before and after test."""
        with migrated_database.cursor() as cur:
            # Clean before
            cur.execute("DELETE FROM financial_data.ifdata_load_control")
            migrated_database.commit()

        yield

        # Clean after
        with migrated_database.cursor() as cur:
            cur.execute("DELETE FROM financial_data.ifdata_load_control")
            migrated_database.commit()

    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.get_run_logger")
    def test_no_records_uses_historical_start(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_load_control,
    ):
        """Test that no records uses historical start."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        # Table is empty (cleaned by fixture)

        # Mock today to be after March 2016 deadline
        # Historical start: 201603, next quarter: 201606
        # June 2016 deadline: June 30, 2016 + 60 days = August 29, 2016
        # Use date after deadline: September 1, 2016
        with patch("tasks.quarter_determination.date") as mock_date_class:
            from datetime import date as real_date

            def date_side_effect(*args, **kwargs):
                if args or kwargs:
                    return real_date(*args, **kwargs)
                raise TypeError("date() missing required argument 'year' (pos 1)")

            mock_date_class.side_effect = date_side_effect
            mock_date_class.today = Mock(
                return_value=date(2016, 9, 1)
            )  # After June 2016 deadline

            result = determine_target_quarter()

            assert result == "201606"

    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.get_run_logger")
    def test_with_extracted_record(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_load_control,
    ):
        """Test with existing extracted record."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        # Insert test record
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202403', 'TestReport', TRUE, FALSE, FALSE)
            """
            )
            migrated_database.commit()

        # Mock today to be after June 2024 deadline
        with patch("tasks.quarter_determination.date") as mock_date_class:
            from datetime import date as real_date

            def date_side_effect(*args, **kwargs):
                if args or kwargs:
                    return real_date(*args, **kwargs)
                raise TypeError("date() missing required argument 'year' (pos 1)")

            mock_date_class.side_effect = date_side_effect
            mock_date_class.today = Mock(return_value=date(2024, 9, 1))

            result = determine_target_quarter()

            assert result == "202406"

    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.get_run_logger")
    def test_publication_window_waiting(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_load_control,
    ):
        """Test that publication window check works correctly."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        # Insert test record
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202403', 'TestReport', TRUE, FALSE, FALSE)
            """
            )
            migrated_database.commit()

        # Mock today to be before June 2024 deadline
        # June 2024 ends 2024-06-30, deadline is 2024-08-29
        with patch("tasks.quarter_determination.date") as mock_date_class:
            from datetime import date as real_date

            def date_side_effect(*args, **kwargs):
                if args or kwargs:
                    return real_date(*args, **kwargs)
                raise TypeError("date() missing required argument 'year' (pos 1)")

            mock_date_class.side_effect = date_side_effect
            mock_date_class.today = Mock(
                return_value=date(2024, 8, 1)
            )  # Before deadline

            result = determine_target_quarter()

            assert result is None  # Should wait

    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.get_run_logger")
    def test_prioritizes_completed_in_integration(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_load_control,
    ):
        """Integration test: Verify that completed quarters are prioritized."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        # Insert test records with different states
        with migrated_database.cursor() as cur:
            # Insert extracted only (202403)
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202403', 'TestReport1', TRUE, FALSE, FALSE)
            """
            )
            # Insert promoted but not completed (202406)
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202406', 'TestReport1', TRUE, TRUE, FALSE)
            """
            )
            # Insert completed (202409)
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202409', 'TestReport1', TRUE, TRUE, TRUE)
            """
            )
            migrated_database.commit()

        # Mock today to be after December 2024 deadline
        # Last completed: 202409, next quarter: 202412
        # December 2024 deadline: December 31, 2024 + 90 days = March 31, 2025
        with patch("tasks.quarter_determination.date") as mock_date_class:
            from datetime import date as real_date

            def date_side_effect(*args, **kwargs):
                if args or kwargs:
                    return real_date(*args, **kwargs)
                raise TypeError("date() missing required argument 'year' (pos 1)")

            mock_date_class.side_effect = date_side_effect
            mock_date_class.today = Mock(
                return_value=date(2025, 4, 1)
            )  # After December 2024 deadline

            result = determine_target_quarter()

            # Should use completed (202409) as base, so next is 202412
            assert result == "202412"

    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.get_run_logger")
    def test_prioritizes_promoted_over_extracted_in_integration(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_load_control,
    ):
        """Integration test: Verify that promoted quarters are prioritized over extracted."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        # Insert test records: extracted only and promoted
        with migrated_database.cursor() as cur:
            # Insert extracted only (202403)
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202403', 'TestReport1', TRUE, FALSE, FALSE)
            """
            )
            # Insert promoted but not completed (202406)
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202406', 'TestReport1', TRUE, TRUE, FALSE)
            """
            )
            migrated_database.commit()

        # Mock today to be after September 2024 deadline
        # Last promoted: 202406, next quarter: 202409
        # September 2024 deadline: September 30, 2024 + 60 days = November 29, 2024
        with patch("tasks.quarter_determination.date") as mock_date_class:
            from datetime import date as real_date

            def date_side_effect(*args, **kwargs):
                if args or kwargs:
                    return real_date(*args, **kwargs)
                raise TypeError("date() missing required argument 'year' (pos 1)")

            mock_date_class.side_effect = date_side_effect
            mock_date_class.today = Mock(
                return_value=date(2024, 12, 1)
            )  # After September 2024 deadline

            result = determine_target_quarter()

            # Should use promoted (202406) as base, so next is 202409
            assert result == "202409"

    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.quarter_determination.get_run_logger")
    def test_uses_extracted_when_only_extracted_exists_in_integration(
        self,
        mock_logger,
        mock_get_db_conn,
        db_connection_factory,
        migrated_database,
        clean_load_control,
    ):
        """Integration test: Verify that extracted is used when no promoted/completed exists."""
        # Mock logger
        mock_logger.return_value = MagicMock()

        # Patch get_db_connection to return a new connection from factory
        # This allows task functions to close their connections safely
        mock_get_db_conn.side_effect = db_connection_factory

        # Insert test record: extracted only
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.ifdata_load_control
                (ano_mes, nome_relatorio, extracted, promoted, completed)
                VALUES ('202403', 'TestReport1', TRUE, FALSE, FALSE)
            """
            )
            migrated_database.commit()

        # Mock today to be after June 2024 deadline
        # Last extracted: 202403, next quarter: 202406
        # June 2024 deadline: June 30, 2024 + 60 days = August 29, 2024
        with patch("tasks.quarter_determination.date") as mock_date_class:
            from datetime import date as real_date

            def date_side_effect(*args, **kwargs):
                if args or kwargs:
                    return real_date(*args, **kwargs)
                raise TypeError("date() missing required argument 'year' (pos 1)")

            mock_date_class.side_effect = date_side_effect
            mock_date_class.today = Mock(
                return_value=date(2024, 9, 1)
            )  # After June 2024 deadline

            result = determine_target_quarter()

            # Should use extracted (202403) as base, so next is 202406
            assert result == "202406"
