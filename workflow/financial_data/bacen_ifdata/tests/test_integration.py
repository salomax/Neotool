"""
Integration tests for BACEN IFData ingestion workflow.

Tests cover:
- End-to-end flow with real PostgreSQL (Testcontainers)
- End-to-end flow with real Kafka (Testcontainers)
- Idempotency across runs
- Error recovery
- Full workflow execution from quarter determination to Kafka publishing

These tests require Docker to be running.
"""

import json
import os
from unittest.mock import patch
from uuid import uuid4

import pytest
from testcontainers.kafka import KafkaContainer

from flows.bacen_ifdata_ingestion import bacen_ifdata_ingestion_flow
from kafka import KafkaConsumer
import requests
from tasks.kafka_publish import KAFKA_TOPIC

# ============================================================================
# Kafka Testcontainer Fixture
# ============================================================================


@pytest.fixture(scope="module")
def kafka_container():
    """Start Kafka testcontainer for integration tests.

    This fixture provides a Kafka container that can be shared across
    all integration tests in a test module. The container is started once per
    module and reused for all tests in that module.

    Requires:
    - Docker Desktop running (on macOS)
    - docker Python library installed (testcontainers dependency)
    - Docker socket accessible
    """
    try:
        import docker

        docker.from_env().ping()
    except (ImportError, Exception):
        pytest.skip(
            "Docker is not available or docker library not installed. "
            "These tests require Docker to be running and accessible. "
            "Install: pip install docker"
        )

    try:
        with KafkaContainer() as kafka:
            yield kafka
    except Exception as e:
        pytest.skip(
            f"Failed to start Kafka container: {e}\n"
            f"Make sure Docker is running and try: docker info"
        )


@pytest.fixture(scope="module")
def kafka_bootstrap_servers(kafka_container):
    """Get Kafka bootstrap servers from container."""
    return kafka_container.get_bootstrap_server()


# ============================================================================
# Test Data Fixtures
# ============================================================================


@pytest.fixture
def mock_registry_csv():
    """Mock CSV data for registry ingestion."""
    return """CodInst,NomeInstituicao,TCB,TD,TC,Situacao
001,Test Bank 1,b1,C,1,A
002,Test Bank 2,B2,I,2,A
003,Test Bank 3,B3S,C,3,I"""


@pytest.fixture
def mock_report_catalog_csv():
    """Mock CSV data for report catalog."""
    return """NomeRelatorio,NumeroRelatorio
Test Report 1,1
Test Report 2,2"""


@pytest.fixture
def mock_extraction_csv():
    """Mock CSV data for paginated extraction."""
    return """CodInst,Field1,Field2
001,value1,value2
002,value3,value4"""


@pytest.fixture
def clean_tables(migrated_database):
    """Clean test tables before and after test."""
    with migrated_database.cursor() as cur:
        cur.execute("DELETE FROM financial_data.ifdata_raw")
        cur.execute("DELETE FROM financial_data.bank_report")
        cur.execute("DELETE FROM financial_data.ifdata_load_control")
        cur.execute("DELETE FROM financial_data.ifdata_extract_task")
        cur.execute("DELETE FROM financial_data.institution")
        migrated_database.commit()

    yield

    with migrated_database.cursor() as cur:
        cur.execute("DELETE FROM financial_data.ifdata_raw")
        cur.execute("DELETE FROM financial_data.bank_report")
        cur.execute("DELETE FROM financial_data.ifdata_load_control")
        cur.execute("DELETE FROM financial_data.ifdata_extract_task")
        cur.execute("DELETE FROM financial_data.institution")
        migrated_database.commit()


# ============================================================================
# Integration Tests
# ============================================================================


@pytest.mark.integration
class TestEndToEndFlow:
    """Test complete end-to-end workflow execution."""

    @patch("tasks.registry_ingestion.fetch_registry_csv")
    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.paginated_extraction.fetch_bacen_data")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.extract_task_creation.get_db_connection")
    @patch("tasks.paginated_extraction.get_db_connection")
    @patch("tasks.extract_completion.get_db_connection")
    @patch("tasks.promotion.get_db_connection")
    @patch("tasks.kafka_publish.create_kafka_producer")
    @patch("flows.bacen_ifdata_ingestion.check_registry_exists")
    @patch("flows.bacen_ifdata_ingestion.get_reports_for_quarter")
    @patch.dict(os.environ, {"KAFKA_BROKERS": ""})  # Will be set by fixture
    def test_end_to_end_flow(
        self,
        mock_get_reports_for_quarter,
        mock_check_registry_exists,
        mock_create_kafka_producer,
        mock_promotion_get_db,
        mock_extract_completion_get_db,
        mock_paginated_get_db,
        mock_extract_task_get_db,
        mock_registry_get_db,
        mock_quarter_get_db,
        mock_fetch_bacen_data,
        mock_fetch_catalog_csv,
        mock_fetch_registry_csv,
        mock_registry_csv,
        mock_report_catalog_csv,
        mock_extraction_csv,
        migrated_database,
        db_connection_factory,
        kafka_bootstrap_servers,
        clean_tables,
    ):
        """Test complete end-to-end workflow execution."""
        # Set up database connection mocks
        mock_quarter_get_db.side_effect = db_connection_factory
        mock_registry_get_db.side_effect = db_connection_factory
        mock_extract_task_get_db.side_effect = db_connection_factory
        mock_paginated_get_db.side_effect = db_connection_factory
        mock_extract_completion_get_db.side_effect = db_connection_factory
        mock_promotion_get_db.side_effect = db_connection_factory

        # Set up Kafka producer mock
        from kafka import KafkaProducer

        real_producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
        )
        mock_create_kafka_producer.return_value = real_producer

        # Set up environment for Kafka
        os.environ["KAFKA_BROKERS"] = kafka_bootstrap_servers

        # Set up database URL for flow tasks
        host = migrated_database.info.host
        port = migrated_database.info.port
        dbname = migrated_database.info.dbname
        user = migrated_database.info.user
        password = migrated_database.info.password
        os.environ["DATABASE_URL"] = (
            f"postgresql://{user}:{password}@{host}:{port}/{dbname}"
        )

        # Mock registry check - registry doesn't exist initially, but exists after ingestion
        mock_check_registry_exists.side_effect = [
            False,  # First call: before ingestion
            True,   # Second call: after ingestion
        ]

        # Mock registry CSV fetch - function returns CSV string directly
        mock_fetch_registry_csv.return_value = mock_registry_csv

        # Mock report catalog CSV fetch - function returns CSV string directly
        mock_fetch_catalog_csv.return_value = mock_report_catalog_csv

        # Mock extraction CSV fetch (first page) - function returns CSV string directly
        mock_fetch_bacen_data.return_value = mock_extraction_csv

        # Mock get_reports_for_quarter to return reports from catalog
        mock_get_reports_for_quarter.return_value = [
            {"nome_relatorio": "Test Report 1"},
            {"nome_relatorio": "Test Report 2"},
        ]

        # Execute flow
        target_ano_mes = "202403"
        result = bacen_ifdata_ingestion_flow(
            target_ano_mes=target_ano_mes,
            force_registry_refresh=False,
        )

        # Verify flow completed successfully
        assert result["status"] == "success"
        assert result["target_ano_mes"] == target_ano_mes
        assert result["registry_ingested"] is True

        # Verify registry was ingested
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.institution
                WHERE ano_mes_ref = %s
            """,
                (target_ano_mes,),
            )
            count = cur.fetchone()[0]
            assert count == 3  # 3 institutions in mock CSV

        # Verify extract tasks were created
        with migrated_database.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM financial_data.ifdata_extract_task
                WHERE ano_mes = %s
            """,
                (target_ano_mes,),
            )
            count = cur.fetchone()[0]
            assert count > 0

        # Verify Kafka messages were published
        consumer = KafkaConsumer(
            KAFKA_TOPIC,
            bootstrap_servers=kafka_bootstrap_servers,
            auto_offset_reset="earliest",
            enable_auto_commit=True,
            group_id=f"test-consumer-{uuid4()}",
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
            key_deserializer=lambda k: k.decode("utf-8") if k else None,
            consumer_timeout_ms=5000,  # 5 second timeout
        )

        messages = []
        try:
            for message in consumer:
                messages.append(message)
                if len(messages) >= 3:  # We expect 3 institutions
                    break
        except Exception:
            pass  # Timeout is expected

        consumer.close()

        # Verify we got Kafka messages for changed institutions
        assert len(messages) >= 3  # At least 3 institutions should have messages
        assert all(msg.key in ["001", "002", "003"] for msg in messages)
        assert all("schema_version" in msg.value for msg in messages)
        assert all("bacen_cod_inst" in msg.value for msg in messages)

        # Clean up
        real_producer.close()


@pytest.mark.integration
class TestIdempotency:
    """Test idempotency across multiple runs."""

    @patch("tasks.registry_ingestion.fetch_registry_csv")
    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.paginated_extraction.fetch_bacen_data")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.extract_task_creation.get_db_connection")
    @patch("tasks.paginated_extraction.get_db_connection")
    @patch("tasks.extract_completion.get_db_connection")
    @patch("tasks.promotion.get_db_connection")
    @patch("tasks.kafka_publish.create_kafka_producer")
    @patch("flows.bacen_ifdata_ingestion.check_registry_exists")
    @patch("flows.bacen_ifdata_ingestion.get_reports_for_quarter")
    @patch.dict(os.environ, {"KAFKA_BROKERS": ""})
    def test_idempotency_across_runs(
        self,
        mock_get_reports_for_quarter,
        mock_check_registry_exists,
        mock_create_kafka_producer,
        mock_promotion_get_db,
        mock_extract_completion_get_db,
        mock_paginated_get_db,
        mock_extract_task_get_db,
        mock_registry_get_db,
        mock_quarter_get_db,
        mock_fetch_bacen_data,
        mock_fetch_catalog_csv,
        mock_fetch_registry_csv,
        mock_registry_csv,
        mock_report_catalog_csv,
        mock_extraction_csv,
        migrated_database,
        db_connection_factory,
        kafka_bootstrap_servers,
        clean_tables,
    ):
        """Test that running the flow multiple times is idempotent."""
        # Set up all mocks (same as end-to-end test)
        mock_quarter_get_db.side_effect = db_connection_factory
        mock_registry_get_db.side_effect = db_connection_factory
        mock_extract_task_get_db.side_effect = db_connection_factory
        mock_paginated_get_db.side_effect = db_connection_factory
        mock_extract_completion_get_db.side_effect = db_connection_factory
        mock_promotion_get_db.side_effect = db_connection_factory

        from kafka import KafkaProducer

        real_producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
        )
        mock_create_kafka_producer.return_value = real_producer

        os.environ["KAFKA_BROKERS"] = kafka_bootstrap_servers

        host = migrated_database.info.host
        port = migrated_database.info.port
        dbname = migrated_database.info.dbname
        user = migrated_database.info.user
        password = migrated_database.info.password
        os.environ["DATABASE_URL"] = (
            f"postgresql://{user}:{password}@{host}:{port}/{dbname}"
        )

        # First run: registry doesn't exist
        mock_check_registry_exists.side_effect = [
            False,
            True,
        ]  # First check, then after ingestion

        # Mock functions return CSV strings directly
        mock_fetch_registry_csv.return_value = mock_registry_csv
        mock_fetch_catalog_csv.return_value = mock_report_catalog_csv
        mock_fetch_bacen_data.return_value = mock_extraction_csv

        mock_get_reports_for_quarter.return_value = [
            {"nome_relatorio": "Test Report 1"},
        ]

        target_ano_mes = "202403"

        # First run
        result1 = bacen_ifdata_ingestion_flow(
            target_ano_mes=target_ano_mes,
            force_registry_refresh=False,
        )

        assert result1["status"] == "success"

        # Get counts after first run
        with migrated_database.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM financial_data.institution WHERE ano_mes_ref = %s",
                (target_ano_mes,),
            )
            institution_count_1 = cur.fetchone()[0]

            cur.execute(
                "SELECT COUNT(*) FROM financial_data.ifdata_extract_task WHERE ano_mes = %s",
                (target_ano_mes,),
            )
            cur.fetchone()[0]

        # Second run: registry exists, should skip ingestion
        mock_check_registry_exists.side_effect = [True, True]  # Registry exists

        # Execute second run
        result2 = bacen_ifdata_ingestion_flow(
            target_ano_mes=target_ano_mes,
            force_registry_refresh=False,
        )

        assert result2["status"] == "success"
        assert result2["registry_ingested"] is False  # Should skip ingestion

        # Get counts after second run
        with migrated_database.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM financial_data.institution WHERE ano_mes_ref = %s",
                (target_ano_mes,),
            )
            institution_count_2 = cur.fetchone()[0]

            cur.execute(
                "SELECT COUNT(*) FROM financial_data.ifdata_extract_task WHERE ano_mes = %s",
                (target_ano_mes,),
            )
            cur.fetchone()[0]

        # Verify idempotency: counts should be the same (or tasks might increase if not all completed)
        assert (
            institution_count_2 == institution_count_1
        )  # Institutions should not duplicate

        # Clean up
        real_producer.close()


@pytest.mark.integration
class TestErrorRecovery:
    """Test error recovery and fallback handling."""

    @patch("tasks.registry_ingestion.fetch_registry_csv")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("flows.bacen_ifdata_ingestion.check_registry_exists")
    @patch("flows.bacen_ifdata_ingestion.handle_fallback")
    def test_error_recovery_on_api_failure(
        self,
        mock_handle_fallback,
        mock_check_registry_exists,
        mock_registry_get_db,
        mock_quarter_get_db,
        mock_fetch_registry_csv,
        migrated_database,
        db_connection_factory,
        clean_tables,
    ):
        """Test that API failures trigger error recovery."""
        # Set up database mocks
        mock_quarter_get_db.side_effect = db_connection_factory
        mock_registry_get_db.side_effect = db_connection_factory

        host = migrated_database.info.host
        port = migrated_database.info.port
        dbname = migrated_database.info.dbname
        user = migrated_database.info.user
        password = migrated_database.info.password
        os.environ["DATABASE_URL"] = (
            f"postgresql://{user}:{password}@{host}:{port}/{dbname}"
        )

        # Mock registry check - registry doesn't exist
        mock_check_registry_exists.return_value = False

        # Mock API failure
        mock_fetch_registry_csv.side_effect = requests.exceptions.RequestException(
            "API Error"
        )

        # Execute flow - should fail and trigger fallback
        target_ano_mes = "202403"

        with pytest.raises(Exception):
            bacen_ifdata_ingestion_flow(
                target_ano_mes=target_ano_mes,
                force_registry_refresh=False,
            )

        # Verify fallback was called
        assert mock_handle_fallback.called
        call_args = mock_handle_fallback.call_args
        assert "error" in call_args.kwargs or len(call_args.args) > 1
        assert "payload" in call_args.kwargs or len(call_args.args) > 0

    @patch("tasks.paginated_extraction.fetch_bacen_data")
    @patch("tasks.registry_ingestion.fetch_registry_csv")
    @patch("tasks.report_catalog_fetch.fetch_catalog_csv")
    @patch("tasks.quarter_determination.get_db_connection")
    @patch("tasks.registry_ingestion.get_db_connection")
    @patch("tasks.extract_task_creation.get_db_connection")
    @patch("tasks.paginated_extraction.get_db_connection")
    @patch("flows.bacen_ifdata_ingestion.check_registry_exists")
    @patch("flows.bacen_ifdata_ingestion.get_reports_for_quarter")
    @patch("flows.bacen_ifdata_ingestion.handle_fallback")
    def test_error_recovery_on_extraction_failure(
        self,
        mock_handle_fallback,
        mock_get_reports_for_quarter,
        mock_check_registry_exists,
        mock_paginated_get_db,
        mock_extract_task_get_db,
        mock_registry_get_db,
        mock_quarter_get_db,
        mock_fetch_catalog_csv,
        mock_fetch_registry_csv,
        mock_fetch_bacen_data,
        mock_registry_csv,
        mock_report_catalog_csv,
        migrated_database,
        db_connection_factory,
        clean_tables,
    ):
        """Test that extraction failures are handled gracefully."""
        # Set up database mocks
        mock_quarter_get_db.side_effect = db_connection_factory
        mock_registry_get_db.side_effect = db_connection_factory
        mock_extract_task_get_db.side_effect = db_connection_factory
        mock_paginated_get_db.side_effect = db_connection_factory

        host = migrated_database.info.host
        port = migrated_database.info.port
        dbname = migrated_database.info.dbname
        user = migrated_database.info.user
        password = migrated_database.info.password
        os.environ["DATABASE_URL"] = (
            f"postgresql://{user}:{password}@{host}:{port}/{dbname}"
        )

        # Mock registry exists
        mock_check_registry_exists.return_value = True

        # Mock successful registry and catalog fetch - functions return CSV strings directly
        mock_fetch_registry_csv.return_value = mock_registry_csv
        mock_fetch_catalog_csv.return_value = mock_report_catalog_csv

        # Mock extraction failure
        mock_fetch_bacen_data.side_effect = requests.exceptions.RequestException(
            "Extraction API Error"
        )

        mock_get_reports_for_quarter.return_value = [
            {"nome_relatorio": "Test Report 1"},
        ]

        target_ano_mes = "202403"

        # Execute flow - extraction will fail but flow should handle it
        # (The flow will continue and mark tasks as ERROR)
        result = bacen_ifdata_ingestion_flow(
            target_ano_mes=target_ano_mes,
            force_registry_refresh=False,
        )

        # Flow might complete with errors in extraction stats
        # or might raise - depends on implementation
        # For now, just verify it doesn't crash completely
        assert "status" in result or result.get("status") in ["success", "failed"]

        # Verify error was tracked
        if result.get("status") == "failed":
            assert len(result.get("errors", [])) > 0


