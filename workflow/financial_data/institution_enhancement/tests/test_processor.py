"""
Unit tests for domain extraction from emails and processor functions.

Tests cover:
- Extraction from emails array
- Null/empty handling
- Whitespace normalization
- Edge cases
- Parent CNPJ retrieval
- Message processing with mocked dependencies
"""

from unittest.mock import MagicMock, Mock, patch

import pytest
import psycopg2
from psycopg2.extras import Json

from tasks.message import InstitutionUpsertedMessage
from tasks.processor import (
    extract_domain_from_emails,
    get_parent_cnpj,
    process_message,
)
from tasks.db_connection import return_db_connection


# ============================================================================
# Unit Tests - extract_domain_from_emails
# ============================================================================


class TestExtractDomainFromEmails:
    """Test extract_domain_from_emails function."""

    def test_valid_domain_extraction_single_email(self):
        """Test extraction from single email with valid domain."""
        emails = [{"domain": "example.com"}]
        result = extract_domain_from_emails(emails)

        assert result == "example.com"
        assert isinstance(result, str)

    def test_valid_domain_extraction_multiple_emails_uses_first(self):
        """Test that extraction uses first email when multiple emails provided."""
        emails = [
            {"domain": "first.com"},
            {"domain": "second.com"},
            {"domain": "third.com"},
        ]
        result = extract_domain_from_emails(emails)

        assert result == "first.com"
        # Should use first email, not second or third

    def test_typical_domain_format(self):
        """Test extraction with typical domain formats."""
        test_cases = [
            "example.com",
            "subdomain.example.com",
            "example.co.uk",
            "example-site.com",
            "123example.com",
        ]

        for domain in test_cases:
            emails = [{"domain": domain}]
            result = extract_domain_from_emails(emails)
            assert result == domain, f"Failed for domain: {domain}"

    def test_empty_list_returns_none(self):
        """Test that empty list returns None."""
        emails = []
        result = extract_domain_from_emails(emails)

        assert result is None

    def test_none_domain_returns_none(self):
        """Test that None domain returns None."""
        emails = [{"domain": None}]
        result = extract_domain_from_emails(emails)

        assert result is None

    def test_empty_string_domain_returns_none(self):
        """Test that empty string domain returns None."""
        emails = [{"domain": ""}]
        result = extract_domain_from_emails(emails)

        assert result is None

    def test_whitespace_only_domain_returns_none(self):
        """Test that whitespace-only domain returns None."""
        test_cases = [" ", "  ", "\t", "\n", "\t\n", "   "]

        for whitespace in test_cases:
            emails = [{"domain": whitespace}]
            result = extract_domain_from_emails(emails)
            assert result is None, f"Should return None for whitespace-only: {repr(whitespace)}"

    def test_leading_whitespace_stripped(self):
        """Test that leading whitespace is stripped from domain."""
        emails = [{"domain": "  example.com"}]
        result = extract_domain_from_emails(emails)

        assert result == "example.com"
        assert result.startswith("example")  # No leading whitespace

    def test_trailing_whitespace_stripped(self):
        """Test that trailing whitespace is stripped from domain."""
        emails = [{"domain": "example.com  "}]
        result = extract_domain_from_emails(emails)

        assert result == "example.com"
        assert result.endswith("com")  # No trailing whitespace

    def test_both_leading_and_trailing_whitespace_stripped(self):
        """Test that both leading and trailing whitespace are stripped."""
        emails = [{"domain": "  example.com  "}]
        result = extract_domain_from_emails(emails)

        assert result == "example.com"
        assert result == "example.com"  # No whitespace on either side

    def test_tab_and_newline_whitespace_stripped(self):
        """Test that tab and newline characters are stripped."""
        emails = [{"domain": "\t\nexample.com\n\t"}]
        result = extract_domain_from_emails(emails)

        assert result == "example.com"

    def test_missing_domain_key_returns_none(self):
        """Test that missing 'domain' key returns None."""
        emails = [{"email": "test@example.com"}]  # Missing 'domain' key
        result = extract_domain_from_emails(emails)

        assert result is None

    def test_empty_dict_returns_none(self):
        """Test that empty dictionary returns None."""
        emails = [{}]
        result = extract_domain_from_emails(emails)

        assert result is None

    def test_multiple_emails_first_has_no_domain_returns_none(self):
        """Test that if first email has no domain, returns None even if others have domains."""
        emails = [
            {"email": "test@example.com"},  # First has no domain key
            {"domain": "second.com"},  # Second has domain
            {"domain": "third.com"},  # Third has domain
        ]
        result = extract_domain_from_emails(emails)

        assert result is None
        # Should use first email, which has no domain

    def test_multiple_emails_first_has_empty_domain_returns_none(self):
        """Test that if first email has empty domain, returns None even if others have domains."""
        emails = [
            {"domain": ""},  # First has empty domain
            {"domain": "second.com"},  # Second has domain
            {"domain": "third.com"},  # Third has domain
        ]
        result = extract_domain_from_emails(emails)

        assert result is None
        # Should use first email, which has empty domain

    def test_domain_with_internal_spaces_preserved(self):
        """Test that internal spaces in domain are preserved (though unlikely in real domains)."""
        # Note: Real domains shouldn't have internal spaces, but we test the behavior
        emails = [{"domain": "example .com"}]
        result = extract_domain_from_emails(emails)

        # strip() only removes leading/trailing, not internal spaces
        assert result == "example .com"

    def test_domain_with_mixed_whitespace(self):
        """Test domain with various whitespace characters."""
        emails = [{"domain": "  \t\n  example.com  \t\n  "}]
        result = extract_domain_from_emails(emails)

        assert result == "example.com"

    def test_single_whitespace_character_returns_none(self):
        """Test that single whitespace character returns None."""
        emails = [{"domain": " "}]
        result = extract_domain_from_emails(emails)

        assert result is None


# ============================================================================
# Unit Tests - get_parent_cnpj
# ============================================================================


class TestGetParentCnpj:
    """Test get_parent_cnpj function."""

    def test_uses_cnpj_instituicao_lider_when_available(self):
        """Test that cnpj_instituicao_lider is used when available."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
            cnpj_instituicao_lider="11222333000181",
        )

        result = get_parent_cnpj(message)

        assert result == "11222333000181"
        # Should use leader CNPJ, not construct from bacen_cod_inst

    def test_constructs_cnpj_from_bacen_cod_inst_when_no_leader(self):
        """Test that CNPJ is constructed from bacen_cod_inst when no leader."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
            cnpj_instituicao_lider=None,
        )

        result = get_parent_cnpj(message)

        assert result is not None
        assert len(result) == 14
        assert result.startswith("608725040001")
        # Should construct CNPJ from bacen_cod_inst

    def test_raises_value_error_when_construction_fails(self):
        """Test that ValueError is raised when CNPJ construction fails."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="",  # Empty string will fail construction
            change_type="CREATED",
            cnpj_instituicao_lider=None,
        )

        with pytest.raises(ValueError, match="Cannot construct CNPJ from bacen_cod_inst"):
            get_parent_cnpj(message)

    def test_raises_value_error_when_invalid_bacen_cod_inst(self):
        """Test that ValueError is raised for invalid bacen_cod_inst."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="ABC",  # Invalid, no digits
            change_type="CREATED",
            cnpj_instituicao_lider=None,
        )

        with pytest.raises(ValueError, match="Cannot construct CNPJ from bacen_cod_inst"):
            get_parent_cnpj(message)

    def test_prioritizes_leader_over_construction(self):
        """Test that leader CNPJ is prioritized even if bacen_cod_inst is valid."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
            cnpj_instituicao_lider="99999999999999",  # Different from constructed
        )

        result = get_parent_cnpj(message)

        assert result == "99999999999999"
        # Should use leader, not construct


# ============================================================================
# Unit Tests - process_message (with mocked dependencies)
# ============================================================================


class TestProcessMessage:
    """Test process_message function with mocked dependencies."""

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_successful_processing_with_domain_extraction(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test successful processing with domain extraction."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API response
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {
                "cnpj": "60872504000123",
                "name": "Test Institution",
                "emails": [{"domain": "example.com"}],
            },
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act
        result = process_message(message)

        # Assert
        assert result["success"] is True
        assert result["parent_cnpj"] is not None
        assert result["domain"] == "example.com"
        assert result["error"] is None

        # Verify database was called
        mock_cursor.execute.assert_called_once()
        mock_conn.commit.assert_called_once()
        # Connection is returned to pool, not closed (connection pooling)

        # Verify CNPJá API was called
        mock_query_cnpja.assert_called_once()

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_successful_processing_without_domain_404_from_api(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test successful processing without domain (404 from API)."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API 404 response
        mock_query_cnpja.return_value = {
            "found": False,
            "error": None,
            "data": None,
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act
        result = process_message(message)

        # Assert
        assert result["success"] is True
        assert result["parent_cnpj"] is not None
        assert result["domain"] is None  # No domain from 404
        assert result["error"] is None

        # Verify database was still called (with null domain)
        mock_cursor.execute.assert_called_once()
        mock_conn.commit.assert_called_once()

    @patch("tasks.processor.query_cnpja")
    def test_api_error_handling_returns_error_result(self, mock_query_cnpja):
        """Test API error handling returns error result."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API error
        mock_query_cnpja.return_value = {
            "found": None,
            "error": "Request timeout",
            "data": None,
        }

        # Act
        result = process_message(message)

        # Assert
        assert result["success"] is False
        assert result["parent_cnpj"] is not None
        assert result["domain"] is None
        assert result["error"] == "Request timeout"

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_database_connection_error_raises_exception(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test database connection error raises exception."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API success
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {"emails": [{"domain": "example.com"}]},
        }

        # Mock database connection error
        mock_get_db_connection.side_effect = psycopg2.OperationalError("Connection failed")

        # Act & Assert
        with pytest.raises(psycopg2.OperationalError, match="Connection failed"):
            process_message(message)

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_database_insert_error_raises_exception(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test database insert error raises exception."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API success
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {"emails": [{"domain": "example.com"}]},
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_cursor.execute.side_effect = psycopg2.DatabaseError("Database error")
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act & Assert
        with pytest.raises(psycopg2.DatabaseError, match="Database error"):
            process_message(message)

        # Verify rollback was called
        mock_conn.rollback.assert_called_once()
        # Connection is returned to pool, not closed (connection pooling)

    def test_message_validation_error_raises_value_error(self):
        """Test message validation error raises ValueError."""
        # Arrange: Invalid message (missing bacen_cod_inst)
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="",  # Invalid: empty
            change_type="CREATED",
        )

        # Act & Assert
        with pytest.raises(ValueError, match="bacen_cod_inst is required"):
            process_message(message)

    def test_invalid_change_type_raises_value_error(self):
        """Test invalid change_type raises ValueError."""
        # Arrange: Invalid change_type
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="INVALID",  # Invalid change_type
        )

        # Act & Assert
        with pytest.raises(ValueError, match="Invalid change_type"):
            process_message(message)

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_idempotency_on_conflict_updates_existing_record(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test that ON CONFLICT updates existing record (idempotency)."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API response
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {
                "emails": [{"domain": "example.com"}],
            },
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act
        result = process_message(message)

        # Assert
        assert result["success"] is True

        # Verify SQL includes ON CONFLICT clause
        execute_call = mock_cursor.execute.call_args[0][0]
        assert "ON CONFLICT" in execute_call.upper()
        assert "DO UPDATE SET" in execute_call.upper()

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_receita_data_stored_as_jsonb(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test that receita_data is stored as JSONB."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        receita_data = {
            "cnpj": "60872504000123",
            "name": "Test Institution",
            "emails": [{"domain": "example.com"}],
            "address": {"street": "Test Street"},
        }

        # Mock CNPJá API response
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": receita_data,
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act
        process_message(message)

        # Assert: Verify Json wrapper was used
        execute_call_args = mock_cursor.execute.call_args[0][1]
        receita_param = execute_call_args[5]  # 6th parameter (0-indexed: 5)

        assert isinstance(receita_param, Json)
        assert receita_param.adapted == receita_data

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_no_receita_data_stored_as_none(
        self, mock_query_cnpja, mock_get_db_connection
    ):
        """Test that None receita_data is stored as None."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API 404 response
        mock_query_cnpja.return_value = {
            "found": False,
            "error": None,
            "data": None,
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act
        process_message(message)

        # Assert: Verify None is passed for receita_data
        execute_call_args = mock_cursor.execute.call_args[0][1]
        receita_param = execute_call_args[5]  # 6th parameter

        assert receita_param is None

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    @patch("tasks.processor.return_db_connection")
    def test_database_close_called_in_finally_block(
        self, mock_return_db_connection, mock_query_cnpja, mock_get_db_connection
    ):
        """Test that database connection is returned to pool in finally block."""
        # Arrange
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Mock CNPJá API success
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {"emails": [{"domain": "example.com"}]},
        }

        # Mock database
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_cursor.__enter__ = Mock(return_value=mock_cursor)
        mock_cursor.__exit__ = Mock(return_value=None)
        mock_conn.cursor.return_value = mock_cursor
        mock_get_db_connection.return_value = mock_conn

        # Act
        process_message(message)

        # Assert: Connection should be returned to pool even on success
        mock_return_db_connection.assert_called_once_with(mock_conn)

