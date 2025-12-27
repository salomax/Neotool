"""
Integration tests for institution enhancement consumer.

Tests cover:
- End-to-end flow with real Kafka (Testcontainers)
- End-to-end flow with real PostgreSQL (Testcontainers)
- Message processing with mocked CNPJá API
- DLQ flow
- Rate limiting enforcement
- Idempotency

These tests require Docker to be running.
"""

import json
import os
import time
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest
import psycopg2
from kafka import KafkaConsumer, KafkaProducer
from testcontainers.core.container import DockerContainer
from testcontainers.kafka import KafkaContainer

# Add parent directory to path for imports
import sys

sys.path.insert(0, str(Path(__file__).parent.parent))

from tasks.message import InstitutionUpsertedMessage
from tasks.processor import process_message

# Disable testcontainers Reaper to avoid Colima socket mount issues
os.environ.setdefault("TESTCONTAINERS_RYUK_DISABLED", "true")

# ============================================================================
# Fixtures
# ============================================================================


def _is_docker_available():
    """Check if Docker is available."""
    try:
        import docker

        # Detect Docker socket location (support Colima)
        if "DOCKER_HOST" not in os.environ:
            colima_socket = os.path.expanduser("~/.colima/default/docker.sock")
            if os.path.exists(colima_socket):
                os.environ["DOCKER_HOST"] = f"unix://{colima_socket}"
            elif os.path.exists("/var/run/docker.sock"):
                os.environ["DOCKER_HOST"] = "unix:///var/run/docker.sock"

        docker.from_env().ping()
        return True
    except (ImportError, Exception):
        return False


def _wait_for_postgres(container, username: str, password: str, database: str, timeout: float = 30):
    """Wait for PostgreSQL to be ready."""
    import time

    host = container.get_container_host_ip()
    port = container.get_exposed_port(5432)
    start_time = time.time()

    while time.time() - start_time < timeout:
        try:
            conn = psycopg2.connect(
                host=host,
                port=port,
                database=database,
                user=username,
                password=password,
                connect_timeout=2,
            )
            conn.close()
            return
        except psycopg2.OperationalError:
            time.sleep(0.5)

    pytest.fail(f"PostgreSQL container not ready after {timeout} seconds")


@pytest.fixture(scope="module")
def kafka_container():
    """Start Kafka testcontainer for integration tests."""
    if not _is_docker_available():
        pytest.skip("Docker is not available. These tests require Docker to be running.")

    try:
        with KafkaContainer() as kafka:
            yield kafka
    except Exception as e:
        pytest.skip(f"Failed to start Kafka container: {e}. Make sure Docker is running.")


@pytest.fixture(scope="module")
def kafka_bootstrap_servers(kafka_container):
    """Get Kafka bootstrap servers from container."""
    return kafka_container.get_bootstrap_server()


@pytest.fixture(scope="module")
def postgres_container():
    """Start PostgreSQL testcontainer for integration tests."""
    if not _is_docker_available():
        pytest.skip("Docker is not available. These tests require Docker to be running.")

    try:
        import docker
    except ImportError:
        pytest.skip("docker Python library not installed. Install: pip install docker")

    # Detect Docker socket location
    if "DOCKER_HOST" not in os.environ:
        colima_socket = os.path.expanduser("~/.colima/default/docker.sock")
        if os.path.exists(colima_socket):
            os.environ["DOCKER_HOST"] = f"unix://{colima_socket}"
        elif os.path.exists("/var/run/docker.sock"):
            os.environ["DOCKER_HOST"] = "unix:///var/run/docker.sock"

    username = "test"
    password = "test"
    database = "test"

    try:
        container = (
            DockerContainer("postgres:18-alpine")
            .with_env("POSTGRES_USER", username)
            .with_env("POSTGRES_PASSWORD", password)
            .with_env("POSTGRES_DB", database)
            .with_exposed_ports(5432)
        )
        started = False
        try:
            container.start()
            started = True
            _wait_for_postgres(container, username, password, database)

            container_info = SimpleNamespace(
                username=username,
                password=password,
                dbname=database,
                get_container_host_ip=container.get_container_host_ip,
                get_exposed_port=lambda port=5432: container.get_exposed_port(port),
            )

            yield container_info
        finally:
            if started:
                container.stop()
    except Exception as e:
        pytest.skip(f"Failed to start PostgreSQL container: {e}")


@pytest.fixture(scope="module")
def migrated_database(postgres_container):
    """Get database connection with schema created."""
    host = postgres_container.get_container_host_ip()
    port = postgres_container.get_exposed_port(5432)

    conn = psycopg2.connect(
        host=host,
        port=port,
        database=postgres_container.dbname,
        user=postgres_container.username,
        password=postgres_container.password,
    )

    # Create schema and table
    with conn.cursor() as cur:
        cur.execute("CREATE SCHEMA IF NOT EXISTS financial_data;")
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS financial_data.institution_enhancement (
                parent_cnpj TEXT NOT NULL PRIMARY KEY,
                domain TEXT,
                website TEXT,
                logo_storage_key TEXT,
                social_network JSONB,
                receita_data JSONB,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        """
        )
        conn.commit()

    yield conn

    conn.close()


@pytest.fixture
def clean_enhancement_table(postgres_container, migrated_database):
    """Clean enhancement table before and after test."""
    # Get a connection for cleanup (migrated_database might be closed by previous tests)
    cleanup_conn = _create_verification_connection(postgres_container)
    try:
        with cleanup_conn.cursor() as cur:
            cur.execute("DELETE FROM financial_data.institution_enhancement")
            cleanup_conn.commit()
    finally:
        cleanup_conn.close()

    yield

    # Clean up after test (create new connection since process_message may have closed migrated_database)
    cleanup_conn = _create_verification_connection(postgres_container)
    try:
        with cleanup_conn.cursor() as cur:
            cur.execute("DELETE FROM financial_data.institution_enhancement")
            cleanup_conn.commit()
    finally:
        cleanup_conn.close()


def _create_verification_connection(postgres_container):
    """Create a new database connection for verification after process_message closes the original."""
    host = postgres_container.get_container_host_ip()
    port = postgres_container.get_exposed_port(5432)
    return psycopg2.connect(
        host=host,
        port=port,
        database=postgres_container.dbname,
        user=postgres_container.username,
        password=postgres_container.password,
    )


# ============================================================================
# Integration Tests
# ============================================================================


@pytest.mark.integration
class TestEndToEndProcessing:
    """Test end-to-end message processing with real Kafka and PostgreSQL."""

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_successful_message_processing(
        self,
        mock_query_cnpja,
        mock_get_db_connection,
        kafka_bootstrap_servers,
        postgres_container,
        migrated_database,
        clean_enhancement_table,
    ):
        """Test successful message processing end-to-end."""
        # Arrange
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

        # Mock database connection (return new connection each time since process_message closes it)
        def get_fresh_connection():
            return _create_verification_connection(postgres_container)
        mock_get_db_connection.side_effect = get_fresh_connection

        # Create message
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Act
        result = process_message(message)

        # Assert
        assert result["success"] is True
        assert result["domain"] == "example.com"
        assert result["parent_cnpj"] is not None

        # Verify database record (create new connection since process_message closes it)
        verify_conn = _create_verification_connection(postgres_container)
        try:
            with verify_conn.cursor() as cur:
                cur.execute(
                    "SELECT domain, receita_data FROM financial_data.institution_enhancement WHERE parent_cnpj = %s",
                    (result["parent_cnpj"],),
                )
                row = cur.fetchone()
                assert row is not None
                assert row[0] == "example.com"
                assert row[1] is not None  # receita_data stored
        finally:
            verify_conn.close()

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_message_processing_404_from_api(
        self,
        mock_query_cnpja,
        mock_get_db_connection,
        postgres_container,
        migrated_database,
        clean_enhancement_table,
    ):
        """Test message processing when API returns 404."""
        # Arrange
        mock_query_cnpja.return_value = {
            "found": False,
            "error": None,
            "data": None,
        }

        mock_get_db_connection.return_value = migrated_database

        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Act
        result = process_message(message)

        # Assert
        assert result["success"] is True
        assert result["domain"] is None  # No domain from 404

        # Verify database record exists with null domain (create new connection since process_message closes it)
        verify_conn = _create_verification_connection(postgres_container)
        try:
            with verify_conn.cursor() as cur:
                cur.execute(
                    "SELECT domain FROM financial_data.institution_enhancement WHERE parent_cnpj = %s",
                    (result["parent_cnpj"],),
                )
                row = cur.fetchone()
                assert row is not None
                assert row[0] is None
        finally:
            verify_conn.close()

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.processor.query_cnpja")
    def test_idempotency_on_conflict_updates(
        self,
        mock_query_cnpja,
        mock_get_db_connection,
        postgres_container,
        migrated_database,
        clean_enhancement_table,
    ):
        """Test that processing same message twice updates existing record."""
        # Arrange
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {"emails": [{"domain": "first.com"}]},
        }

        # Mock database connection (return new connection each time since process_message closes it)
        def get_fresh_connection():
            return _create_verification_connection(postgres_container)
        mock_get_db_connection.side_effect = get_fresh_connection

        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # First processing
        result1 = process_message(message)
        parent_cnpj = result1["parent_cnpj"]

        # Change API response for second processing
        mock_query_cnpja.return_value = {
            "found": True,
            "error": None,
            "data": {"emails": [{"domain": "second.com"}]},
        }

        # Second processing (same message)
        result2 = process_message(message)

        # Assert: Should update existing record
        assert result2["success"] is True
        assert result2["parent_cnpj"] == parent_cnpj
        assert result2["domain"] == "second.com"  # Updated domain

        # Verify only one record exists (create new connection since process_message closes it)
        verify_conn = _create_verification_connection(postgres_container)
        try:
            with verify_conn.cursor() as cur:
                cur.execute("SELECT COUNT(*) FROM financial_data.institution_enhancement WHERE parent_cnpj = %s", (parent_cnpj,))
                count = cur.fetchone()[0]
                assert count == 1  # Only one record

                # Verify domain was updated
                cur.execute("SELECT domain FROM financial_data.institution_enhancement WHERE parent_cnpj = %s", (parent_cnpj,))
                domain = cur.fetchone()[0]
                assert domain == "second.com"
        finally:
            verify_conn.close()


@pytest.mark.integration
class TestKafkaIntegration:
    """Test Kafka integration with testcontainers."""

    def test_publish_and_consume_message(self, kafka_bootstrap_servers):
        """Test publishing and consuming message from Kafka."""
        # Arrange
        topic = "test-topic"
        test_message = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "occurred_at": "2024-01-01T00:00:00Z",
            "source": "test",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
        }

        # Create producer
        producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )

        # Create consumer
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_bootstrap_servers,
            auto_offset_reset="earliest",
            consumer_timeout_ms=5000,
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        )

        try:
            # Act: Publish message
            future = producer.send(topic, value=test_message)
            future.get(timeout=10)

            # Consume message
            messages = []
            for message in consumer:
                messages.append(message.value)
                if len(messages) >= 1:
                    break

            # Assert
            assert len(messages) == 1
            assert messages[0]["bacen_cod_inst"] == "60872504"
            assert messages[0]["change_type"] == "CREATED"
        finally:
            producer.close()
            consumer.close()


@pytest.mark.integration
class TestRateLimiting:
    """Test rate limiting enforcement."""

    @patch("tasks.processor.get_db_connection")
    @patch("tasks.cnpja_client.time.sleep")
    @patch("tasks.cnpja_client.time.time")
    @patch("tasks.cnpja_client.requests.get")
    def test_rate_limiting_enforced_between_calls(
        self,
        mock_requests_get,
        mock_time,
        mock_sleep,
        mock_get_db_connection,
        postgres_container,
        migrated_database,
        clean_enhancement_table,
    ):
        """Test that rate limiting is enforced between API calls."""
        # Reset global rate limiting state
        from tasks import cnpja_client
        cnpja_client._last_cnpja_query_time = None

        # Arrange: Mock HTTP response
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "cnpj": "60872504000123",
            "name": "Test Institution",
            "emails": [{"domain": "example.com"}],
        }
        mock_requests_get.return_value = mock_response

        # Mock database connection (return new connection each time since process_message closes it)
        def get_fresh_connection():
            return _create_verification_connection(postgres_container)
        mock_get_db_connection.side_effect = get_fresh_connection

        # Mock time to control rate limiting
        current_time = 1000.0
        mock_time.return_value = current_time

        message1 = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-1",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        message2 = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-2",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="12345678",
            change_type="CREATED",
        )

        # First call (at time 1000.0)
        process_message(message1)
        # After first call, _last_cnpja_query_time should be set to 1000.0

        # Second call 5 seconds later (at time 1005.0, should wait 10 seconds to reach 15s minimum)
        current_time = 1005.0
        mock_time.return_value = current_time

        process_message(message2)

        # Assert: sleep should have been called
        assert mock_sleep.called
        # Verify wait time (15 - 5 = 10 seconds)
        sleep_call = mock_sleep.call_args[0][0]
        assert sleep_call == 10.0


@pytest.mark.integration
class TestDlqFlow:
    """Test DLQ flow with real Kafka."""

    def test_dlq_message_structure(self, kafka_bootstrap_servers):
        """Test that DLQ message has correct structure."""
        # Create producer directly with test Kafka servers
        producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: str(k).encode("utf-8") if k else None,
            acks="all",
            enable_idempotence=True,
            compression_type="gzip",
            linger_ms=10,
            retries=5,
        )

        from tasks.dlq_publisher import publish_to_dlq

        # Arrange

        original_message = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
        }
        error = ValueError("Test error")

        # Act
        result = publish_to_dlq(producer, original_message, error)

        # Assert
        assert result is True

        # Consume from DLQ topic
        consumer = KafkaConsumer(
            "financial-data.institution.upserted.enhancement.dlq",
            bootstrap_servers=kafka_bootstrap_servers,
            auto_offset_reset="earliest",
            consumer_timeout_ms=5000,
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        )

        try:
            messages = []
            for message in consumer:
                messages.append(message.value)
                if len(messages) >= 1:
                    break

            assert len(messages) == 1
            dlq_message = messages[0]
            assert "original_message" in dlq_message
            assert "error_type" in dlq_message
            assert "error_message" in dlq_message
            assert "failed_at" in dlq_message
            assert dlq_message["error_type"] == "ValueError"
            assert dlq_message["error_message"] == "Test error"
        finally:
            consumer.close()
            producer.close()

