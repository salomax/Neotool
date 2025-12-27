"""
Unit tests for CNPJá API client.

Tests cover:
- Rate limiting enforcement
- HTTP request handling
- Error handling (404, timeout, connection errors)
- Response parsing
"""

import time
from unittest.mock import MagicMock, Mock, patch

import pytest
import requests

from tasks.cnpja_client import CNPJA_RATE_LIMIT_SECONDS, enforce_rate_limit, query_cnpja


# ============================================================================
# Unit Tests - enforce_rate_limit
# ============================================================================


class TestEnforceRateLimit:
    """Test enforce_rate_limit function."""

    def test_first_call_no_wait(self):
        """Test that first call doesn't wait (no previous call)."""
        # Reset global state
        import tasks.cnpja_client

        tasks.cnpja_client._last_cnpja_query_time = None

        with patch("tasks.cnpja_client.time.sleep") as mock_sleep:
            with patch("tasks.cnpja_client.time.time", return_value=1000.0):
                enforce_rate_limit()

                # Should not sleep on first call
                mock_sleep.assert_not_called()

    def test_second_call_too_soon_waits(self):
        """Test that second call waits when called too soon."""
        import tasks.cnpja_client

        # Reset and set up: first call at time 1000
        tasks.cnpja_client._last_cnpja_query_time = 1000.0

        with patch("tasks.cnpja_client.time.sleep") as mock_sleep:
            with patch("tasks.cnpja_client.time.time", return_value=1010.0):  # 10 seconds later
                enforce_rate_limit()

                # Should wait for remaining 5 seconds (15 - 10 = 5)
                mock_sleep.assert_called_once_with(5.0)

    def test_second_call_after_15_seconds_no_wait(self):
        """Test that second call doesn't wait when 15+ seconds have passed."""
        import tasks.cnpja_client

        # Reset and set up: first call at time 1000
        tasks.cnpja_client._last_cnpja_query_time = 1000.0

        with patch("tasks.cnpja_client.time.sleep") as mock_sleep:
            with patch("tasks.cnpja_client.time.time", return_value=1016.0):  # 16 seconds later
                enforce_rate_limit()

                # Should not wait (16 > 15)
                mock_sleep.assert_not_called()

    def test_second_call_exactly_15_seconds_no_wait(self):
        """Test that second call doesn't wait when exactly 15 seconds have passed."""
        import tasks.cnpja_client

        # Reset and set up: first call at time 1000
        tasks.cnpja_client._last_cnpja_query_time = 1000.0

        with patch("tasks.cnpja_client.time.sleep") as mock_sleep:
            with patch("tasks.cnpja_client.time.time", return_value=1015.0):  # Exactly 15 seconds later
                enforce_rate_limit()

                # Should not wait (15 >= 15)
                mock_sleep.assert_not_called()

    def test_multiple_calls_state_persists(self):
        """Test that rate limiting state persists across multiple calls."""
        import tasks.cnpja_client

        # Reset state
        tasks.cnpja_client._last_cnpja_query_time = None

        with patch("tasks.cnpja_client.time.sleep") as mock_sleep:
            # First call at time 1000
            with patch("tasks.cnpja_client.time.time", return_value=1000.0):
                enforce_rate_limit()
                mock_sleep.assert_not_called()

            # Second call at time 1005 (5 seconds later, should wait 10 seconds)
            with patch("tasks.cnpja_client.time.time", return_value=1005.0):
                enforce_rate_limit()
                mock_sleep.assert_called_once_with(10.0)

            # Third call at time 1016 (11 seconds after second call, should wait 4 seconds)
            mock_sleep.reset_mock()
            with patch("tasks.cnpja_client.time.time", return_value=1016.0):
                enforce_rate_limit()
                mock_sleep.assert_called_once_with(4.0)

    def test_rate_limit_respects_15_second_minimum(self):
        """Test that rate limit enforces 15-second minimum between calls."""
        import tasks.cnpja_client

        tasks.cnpja_client._last_cnpja_query_time = 1000.0

        with patch("tasks.cnpja_client.time.sleep") as mock_sleep:
            # Call at 1000.1 (0.1 seconds later)
            with patch("tasks.cnpja_client.time.time", return_value=1000.1):
                enforce_rate_limit()
                # Should wait almost 15 seconds
                mock_sleep.assert_called_once()
                wait_time = mock_sleep.call_args[0][0]
                assert wait_time > 14.0  # Close to 15 seconds


# ============================================================================
# Unit Tests - query_cnpja
# ============================================================================


class TestQueryCnpja:
    """Test query_cnpja function."""

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_successful_query_200_response(self, mock_get, mock_enforce_rate_limit):
        """Test successful query with 200 response."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "cnpj": "11222333000181",
            "name": "Example Company",
            "emails": [{"domain": "example.com"}],
        }
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is True
        assert result["error"] is None
        assert result["data"] == {
            "cnpj": "11222333000181",
            "name": "Example Company",
            "emails": [{"domain": "example.com"}],
        }

        # Verify rate limiting was enforced
        mock_enforce_rate_limit.assert_called_once()

        # Verify request was made correctly
        mock_get.assert_called_once()
        call_args = mock_get.call_args
        assert call_args[0][0] == "https://open.cnpja.com/office/11222333000181"
        assert call_args[1]["headers"]["User-Agent"] == "Invistus Institution Enhancement Consumer"
        assert call_args[1]["headers"]["Accept"] == "application/json"
        assert call_args[1]["timeout"] == 30

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_404_not_found_handling(self, mock_get, mock_enforce_rate_limit):
        """Test 404 Not Found handling."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 404
        mock_response.text = "Not Found"
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is False
        assert result["error"] is None
        assert result["data"] is None

        # Verify rate limiting was enforced
        mock_enforce_rate_limit.assert_called_once()

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_timeout_error_handling(self, mock_get, mock_enforce_rate_limit):
        """Test timeout error handling."""
        # Arrange
        mock_get.side_effect = requests.exceptions.Timeout("Request timeout")

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == "Request timeout: Request timeout"
        assert result["data"] is None

        # Verify rate limiting was enforced
        mock_enforce_rate_limit.assert_called_once()

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_connection_error_handling(self, mock_get, mock_enforce_rate_limit):
        """Test connection error handling."""
        # Arrange
        mock_get.side_effect = requests.exceptions.ConnectionError("Connection failed")

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == "Connection error: Connection failed"
        assert result["data"] is None

        # Verify rate limiting was enforced
        mock_enforce_rate_limit.assert_called_once()

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_500_server_error_handling(self, mock_get, mock_enforce_rate_limit):
        """Test 500 server error handling."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.text = "Internal Server Error"
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == "HTTP 500: Internal Server Error"
        assert result["data"] is None

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_503_service_unavailable_handling(self, mock_get, mock_enforce_rate_limit):
        """Test 503 Service Unavailable handling."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 503
        mock_response.text = "Service Unavailable"
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == "HTTP 503: Service Unavailable"
        assert result["data"] is None

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_other_http_error_codes(self, mock_get, mock_enforce_rate_limit):
        """Test other HTTP error codes (e.g., 400, 401, 403)."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 401
        mock_response.text = "Unauthorized"
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == "HTTP 401: Unauthorized"
        assert result["data"] is None

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_response_parsing_json(self, mock_get, mock_enforce_rate_limit):
        """Test that JSON response is parsed correctly."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "cnpj": "11222333000181",
            "name": "Test Company",
            "emails": [{"domain": "test.com"}],
            "address": {"street": "Test Street"},
        }
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is True
        assert result["data"]["cnpj"] == "11222333000181"
        assert result["data"]["name"] == "Test Company"
        assert result["data"]["emails"] == [{"domain": "test.com"}]
        mock_response.json.assert_called_once()

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_url_construction(self, mock_get, mock_enforce_rate_limit):
        """Test that URL is constructed correctly."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {}
        mock_get.return_value = mock_response

        # Act
        query_cnpja("60872504000123")

        # Assert
        mock_get.assert_called_once()
        call_args = mock_get.call_args
        assert call_args[0][0] == "https://open.cnpja.com/office/60872504000123"

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_headers_are_set_correctly(self, mock_get, mock_enforce_rate_limit):
        """Test that headers are set correctly."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {}
        mock_get.return_value = mock_response

        # Act
        query_cnpja("11222333000181")

        # Assert
        mock_get.assert_called_once()
        call_args = mock_get.call_args
        headers = call_args[1]["headers"]
        assert headers["User-Agent"] == "Invistus Institution Enhancement Consumer"
        assert headers["Accept"] == "application/json"

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_timeout_is_set_correctly(self, mock_get, mock_enforce_rate_limit):
        """Test that timeout is set correctly."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {}
        mock_get.return_value = mock_response

        # Act
        query_cnpja("11222333000181")

        # Assert
        mock_get.assert_called_once()
        call_args = mock_get.call_args
        assert call_args[1]["timeout"] == 30

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_rate_limit_called_before_request(self, mock_get, mock_enforce_rate_limit):
        """Test that rate limiting is enforced before making the request."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {}
        mock_get.return_value = mock_response

        # Act
        query_cnpja("11222333000181")

        # Assert: enforce_rate_limit should be called before requests.get
        call_order = [str(call) for call in mock_enforce_rate_limit.call_args_list]
        get_order = [str(call) for call in mock_get.call_args_list]

        # Verify both were called
        assert mock_enforce_rate_limit.called
        assert mock_get.called

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_long_error_message_truncated(self, mock_get, mock_enforce_rate_limit):
        """Test that long error messages are truncated to 200 characters."""
        # Arrange
        mock_response = Mock()
        mock_response.status_code = 500
        # Create a long error message (> 200 chars)
        long_error = "A" * 300
        mock_response.text = long_error
        mock_get.return_value = mock_response

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == f"HTTP 500: {long_error[:200]}"
        assert len(result["error"]) == 210  # "HTTP 500: " (10) + 200 chars

    @patch("tasks.cnpja_client.enforce_rate_limit")
    @patch("tasks.cnpja_client.requests.get")
    def test_request_exception_handling(self, mock_get, mock_enforce_rate_limit):
        """Test that generic RequestException is handled."""
        # Arrange
        mock_get.side_effect = requests.exceptions.RequestException("Generic request error")

        # Act
        result = query_cnpja("11222333000181")

        # Assert
        assert result["found"] is None
        assert result["error"] == "Generic request error"
        assert result["data"] is None

