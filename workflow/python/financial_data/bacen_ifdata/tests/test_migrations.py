"""Test database migrations for BACEN IFData ingestion feature.

This module tests that all migrations run successfully, create the correct
schema and table structures, indexes, constraints, and are idempotent.

These tests require Docker to be running, as they use testcontainers to spin up
a PostgreSQL database. If Docker is not available, tests will be skipped.
"""

import os
from pathlib import Path
import subprocess
import time
from types import SimpleNamespace
import warnings

import pytest

import psycopg2

# Disable testcontainers Reaper to avoid Colima socket mount issues
# The Reaper tries to mount the Docker socket which fails with Colima
os.environ.setdefault("TESTCONTAINERS_RYUK_DISABLED", "true")

# Suppress testcontainers legacy wait decorator warnings emitted at import time
warnings.filterwarnings(
    "ignore",
    message="The @wait_container_is_ready decorator is deprecated.*",
    category=DeprecationWarning,
)
warnings.filterwarnings(
    "ignore",
    message="The wait_for function is deprecated.*",
    category=DeprecationWarning,
)

from testcontainers.core.container import DockerContainer


def _is_docker_available():
    """Check if Docker is available and accessible.

    Returns True if Docker CLI is available and working.
    The docker Python library is optional - testcontainers will handle it.
    """
    # Check if docker CLI command exists and can connect to daemon
    try:
        result = subprocess.run(
            ["docker", "info"],
            check=False,
            capture_output=True,
            text=True,
            timeout=5,
        )
        # If docker info succeeds, Docker is available
        return result.returncode == 0
    except FileNotFoundError:
        # docker command not found
        return False
    except subprocess.TimeoutExpired:
        # docker command timed out
        return False
    except Exception:
        # Any other error - assume Docker not available
        return False


# Mark all tests in this module as integration tests requiring Docker
pytestmark = pytest.mark.integration


def _wait_for_postgres(
    container, username: str, password: str, database: str, timeout: float = 30
) -> None:
    """Poll postgres until it accepts connections to avoid deprecated wait decorators."""
    host = container.get_container_host_ip()
    port = container.get_exposed_port(5432)
    start = time.time()
    while time.time() - start < timeout:
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
        except Exception:
            time.sleep(0.5)
    pytest.fail(f"PostgreSQL container not ready after {timeout} seconds")


@pytest.fixture(scope="module")
def postgres_container():
    """Start PostgreSQL testcontainer for migration testing.

    Requires:
    - Docker Desktop running (on macOS)
    - docker Python library installed (testcontainers dependency)
    - Docker socket accessible

    If you get connection errors, try:
    - Ensure Docker Desktop is running
    - Check: docker info
    - Install docker library: pip install docker
    """
    if not _is_docker_available():
        pytest.skip(
            "Docker is not available or not accessible. "
            "These tests require Docker to be running and accessible. "
            "Try: docker info"
        )

    # Check if docker library is available (testcontainers dependency)
    try:
        import docker
    except ImportError:
        pytest.skip(
            "docker Python library not installed. "
            "testcontainers requires the docker library to work.\n"
            "Install it with: pip install docker\n"
            "Or install all dev dependencies: pip install -r requirements-dev.txt"
        )

    # Test Docker connection with better error handling
    # Detect Docker socket location (for Colima/Docker Desktop compatibility)
    # Check if DOCKER_HOST is already set (e.g., by docker context)
    if "DOCKER_HOST" not in os.environ:
        # Try common Docker socket locations
        colima_socket = os.path.expanduser("~/.colima/default/docker.sock")
        if os.path.exists(colima_socket):
            # Colima socket found - set DOCKER_HOST
            os.environ["DOCKER_HOST"] = f"unix://{colima_socket}"
        elif os.path.exists("/var/run/docker.sock"):
            # Standard Docker Desktop socket
            os.environ["DOCKER_HOST"] = "unix:///var/run/docker.sock"

    try:
        # docker.from_env() will use DOCKER_HOST if set, otherwise default locations
        client = docker.from_env()
        client.ping()
    except docker.errors.DockerException as e:
        # Check if it's a connection issue
        error_msg = str(e)
        if (
            "Connection" in error_msg
            or "socket" in error_msg.lower()
            or "FileNotFoundError" in str(type(e))
        ):
            pytest.skip(
                f"Docker daemon is not accessible.\n"
                f"Error: {error_msg}\n\n"
                f"Troubleshooting steps:\n"
                f"1. Verify Docker is accessible: docker info\n"
                f"2. If using Docker Desktop: ensure it's running (check menu bar icon)\n"
                f"3. If using Colima: ensure it's running: colima status\n"
                f"   - If not running: colima start\n"
                f"   - Check socket exists: ls -la ~/.colima/default/docker.sock\n"
                f"4. Check Docker context: docker context ls (should show active context)\n"
                f"5. Try restarting Docker/Colima"
            )
        else:
            pytest.skip(f"Docker connection error: {error_msg}")
    except Exception as conn_err:
        pytest.skip(
            f"Failed to connect to Docker daemon.\n"
            f"Error type: {type(conn_err).__name__}\n"
            f"Error: {conn_err}\n\n"
            f"Make sure Docker Desktop is running and try: docker info"
        )

    # Use PostgreSQL 18+ to match production (has uuidv7() built-in)
    try:
        # Use DockerContainer and manual polling to avoid deprecated decorator
        username = "test"
        password = "test"
        database = "test"
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

            # Simple shim to mirror PostgresContainer API used by the tests
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
        error_msg = str(e)
        error_type = type(e).__name__

        # Import traceback for better error details
        import traceback

        error_traceback = traceback.format_exc()

        # Handle common Docker connection errors
        if (
            "Connection aborted" in error_msg
            or "FileNotFoundError" in error_msg
            or "docker.errors.DockerException" in error_type
            or "DockerException" in error_type
        ):
            pytest.skip(
                f"Docker daemon is not accessible.\n"
                f"Error type: {error_type}\n"
                f"Error message: {error_msg}\n\n"
                f"Troubleshooting:\n"
                f"1. Make sure Docker Desktop is running (check the Docker icon in menu bar)\n"
                f"2. Verify Docker is accessible: docker info\n"
                f"3. If using Docker Desktop on macOS, ensure it's fully started\n"
                f"4. If using Colima or other Docker runtime, check socket permissions\n"
                f"5. Ensure docker Python library is installed: pip install docker"
            )
        else:
            # Show full error details for debugging container startup issues
            pytest.fail(
                f"Failed to start PostgreSQL container.\n"
                f"Error type: {error_type}\n"
                f"Error message: {error_msg}\n\n"
                f"Full traceback:\n{error_traceback}\n\n"
                f"Troubleshooting:\n"
                f"1. Check if Docker has enough resources (memory, disk space)\n"
                f"2. Verify image is available: docker images | grep postgres\n"
                f"3. Try pulling the image: docker pull postgres:18-alpine\n"
                f"4. Check Docker logs: docker ps -a (if container was created)\n"
                f"5. Verify Docker is running: docker ps"
            )


@pytest.fixture(scope="module")
def test_pyway_config(postgres_container, tmp_path_factory):
    """Create temporary pyway config file pointing to testcontainer."""
    if not _is_docker_available():
        pytest.skip(
            "Docker is not available. These tests require Docker to be running."
        )

    # Get paths
    test_dir = Path(__file__).parent
    project_root = test_dir.parent.parent  # workflow/python/financial_data/

    # Create temporary config file in project root (where pyway expects it)
    # Use absolute path to avoid issues with working directory changes
    config_file = project_root / "pyway_test.conf"

    # Write config with testcontainer connection details
    config_content = f"""# Temporary pyway config for testing
database_type: postgres
database_username: {postgres_container.username}
database_password: {postgres_container.password}
database_host: {postgres_container.get_container_host_ip()}
database_port: {postgres_container.get_exposed_port(5432)}
database_name: {postgres_container.dbname}
database_migration_dir: migrations
database_table: public.pyway_schema_history_financial_data
"""

    config_file.write_text(config_content)

    yield config_file, project_root

    # Clean up: remove temporary config file
    if config_file.exists():
        config_file.unlink()


@pytest.fixture(scope="module")
def migrated_database(postgres_container, test_pyway_config):
    """Run pyway migrations and return database connection."""
    if not _is_docker_available():
        pytest.skip(
            "Docker is not available. These tests require Docker to be running."
        )

    config_file, project_root = test_pyway_config

    # Change to project root directory (where migrations/ is located)
    original_cwd = os.getcwd()
    try:
        os.chdir(project_root)

        # Run pyway migrate with test config (use absolute path for config file)
        result = subprocess.run(
            ["pyway", "-c", str(config_file.absolute()), "migrate"],
            capture_output=True,
            text=True,
            check=True,
        )

        # Verify migrations ran successfully
        assert result.returncode == 0, (
            f"Pyway migrate failed: {result.stderr}\n" f"Stdout: {result.stdout}"
        )

    finally:
        os.chdir(original_cwd)

    # Create database connection for assertions
    conn = psycopg2.connect(
        host=postgres_container.get_container_host_ip(),
        port=postgres_container.get_exposed_port(5432),
        database=postgres_container.dbname,
        user=postgres_container.username,
        password=postgres_container.password,
    )

    yield conn

    conn.close()


def test_schema_exists(migrated_database):
    """Test that financial_data schema is created."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name = 'financial_data'
        """
        )
        result = cur.fetchone()
        assert result is not None, "financial_data schema does not exist"
        assert result[0] == "financial_data"


def test_all_tables_exist(migrated_database):
    """Test that all required tables exist in financial_data schema."""
    expected_tables = [
        "institution",
        "ifdata_extract_task",
        "ifdata_load_control",
        "ifdata_raw",
        "bank_report",
        "bank_report_data",
    ]

    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'financial_data'
            ORDER BY table_name
        """
        )
        existing_tables = [row[0] for row in cur.fetchall()]

    for table in expected_tables:
        assert table in existing_tables, f"Table financial_data.{table} does not exist"

    assert len(existing_tables) == len(
        expected_tables
    ), f"Expected {len(expected_tables)} tables, found {len(existing_tables)}"


def test_institution_table_structure(migrated_database):
    """Test institution table has correct columns and types."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'financial_data' AND table_name = 'institution'
            ORDER BY ordinal_position
        """
        )
        columns = {row[0]: row[1:] for row in cur.fetchall()}

    # Verify primary key
    # Row structure: [data_type, character_maximum_length, is_nullable, column_default]
    assert "bacen_cod_inst" in columns
    assert columns["bacen_cod_inst"][0] == "text"  # data_type (index 0)
    assert (
        columns["bacen_cod_inst"][1] is None
    )  # character_maximum_length is None for text (index 1)
    assert columns["bacen_cod_inst"][2] == "NO"  # is_nullable (index 2)

    # Verify required columns
    assert "ano_mes_ref" in columns
    assert columns["ano_mes_ref"][0] == "character"
    assert columns["ano_mes_ref"][1] == 6  # character_maximum_length

    assert "source_hash" in columns
    assert columns["source_hash"][0] == "character"
    assert columns["source_hash"][1] == 64  # character_maximum_length (index 1)
    assert columns["source_hash"][2] == "NO"  # is_nullable (index 2)

    # Verify timestamp columns
    assert "created_at" in columns
    assert columns["created_at"][0] == "timestamp with time zone"
    assert (
        columns["created_at"][1] is None
    )  # character_maximum_length is None for timestamptz
    assert columns["created_at"][2] == "NO"  # is_nullable (index 2)

    assert "updated_at" in columns
    assert columns["updated_at"][0] == "timestamp with time zone"
    assert (
        columns["updated_at"][1] is None
    )  # character_maximum_length is None for timestamptz
    assert columns["updated_at"][2] == "NO"  # is_nullable (index 2)


def test_ifdata_extract_task_table_structure(migrated_database):
    """Test ifdata_extract_task table has correct columns and constraints."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'financial_data' AND table_name = 'ifdata_extract_task'
            ORDER BY ordinal_position
        """
        )
        columns = {row[0]: row[1:] for row in cur.fetchall()}

    # Verify composite primary key columns
    # Row structure: [data_type, is_nullable, column_default]
    assert "ano_mes" in columns
    assert columns["ano_mes"][0] == "character"  # data_type (index 0)
    assert columns["ano_mes"][1] == "NO"  # is_nullable (index 1)

    assert "tipo_instituicao" in columns
    assert columns["tipo_instituicao"][0] == "integer"
    assert columns["tipo_instituicao"][1] == "NO"  # is_nullable (index 1)

    assert "nome_relatorio" in columns
    assert columns["nome_relatorio"][0] == "text"
    assert columns["nome_relatorio"][1] == "NO"  # is_nullable (index 1)

    assert "numero_relatorio" in columns
    assert columns["numero_relatorio"][0] == "text"
    assert columns["numero_relatorio"][1] == "NO"  # is_nullable (index 1)

    assert "page_skip" in columns
    assert columns["page_skip"][0] == "integer"
    assert columns["page_skip"][1] == "NO"  # is_nullable (index 1)

    # Verify page_top column with default
    assert "page_top" in columns
    assert columns["page_top"][0] == "integer"
    assert columns["page_top"][1] == "NO"  # is_nullable (index 1)
    assert "5000" in str(columns["page_top"][2])  # default value (index 2) - matches DEFAULT_PAGE_TOP

    # Verify status column with default
    assert "status" in columns
    assert columns["status"][0] == "text"
    assert columns["status"][1] == "NO"  # is_nullable (index 1)
    assert "PENDING" in str(columns["status"][2])  # default value (index 2)

    # Verify attempt_count default
    assert "attempt_count" in columns
    assert columns["attempt_count"][0] == "integer"
    assert columns["attempt_count"][1] == "NO"  # is_nullable (index 1)
    assert "0" in str(columns["attempt_count"][2])  # default value (index 2)


def test_ifdata_load_control_table_structure(migrated_database):
    """Test ifdata_load_control table has correct columns."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'financial_data' AND table_name = 'ifdata_load_control'
            ORDER BY ordinal_position
        """
        )
        columns = {row[0]: row[1:] for row in cur.fetchall()}

    # Verify composite primary key
    assert "ano_mes" in columns
    assert "nome_relatorio" in columns

    # Verify boolean flags with defaults
    # Row structure: [data_type, is_nullable, column_default]
    assert "extracted" in columns
    assert columns["extracted"][0] == "boolean"  # data_type (index 0)
    assert columns["extracted"][1] == "NO"  # is_nullable (index 1)
    default_extracted = columns["extracted"][2]  # column_default (index 2)
    assert default_extracted is not None and "false" in str(default_extracted).lower()

    assert "promoted" in columns
    assert columns["promoted"][0] == "boolean"
    assert columns["promoted"][1] == "NO"  # is_nullable (index 1)
    default_promoted = columns["promoted"][2]  # column_default (index 2)
    assert default_promoted is not None and "false" in str(default_promoted).lower()

    assert "completed" in columns
    assert columns["completed"][0] == "boolean"
    assert columns["completed"][1] == "NO"  # is_nullable (index 1)
    default_completed = columns["completed"][2]  # column_default (index 2)
    assert default_completed is not None and "false" in str(default_completed).lower()


def test_ifdata_raw_table_structure(migrated_database):
    """Test ifdata_raw table has correct columns including UUID v7."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'financial_data' AND table_name = 'ifdata_raw'
            ORDER BY ordinal_position
        """
        )
        columns = {row[0]: row[1:] for row in cur.fetchall()}

    # Verify UUID v7 primary key
    # Row structure: [data_type, is_nullable, column_default]
    assert "id" in columns
    assert columns["id"][0] == "uuid"  # data_type (index 0)
    assert columns["id"][1] == "NO"  # is_nullable (index 1)
    default_id = columns["id"][2]  # column_default (index 2)
    assert default_id is not None and "uuidv7()" in str(default_id)

    # Verify dimension columns
    assert "ano_mes" in columns
    assert "tipo_instituicao" in columns
    assert "nome_relatorio" in columns
    assert "numero_relatorio" in columns

    # Verify cod_inst is nullable
    # Row structure: [data_type, is_nullable, column_default]
    assert "cod_inst" in columns
    assert columns["cod_inst"][0] == "text"  # data_type (index 0)
    # is_nullable is at index 1: 'YES' means nullable, 'NO' means NOT NULL
    is_nullable_cod_inst = columns["cod_inst"][1]
    assert (
        is_nullable_cod_inst == "YES"
    ), f"cod_inst should be nullable, got: {is_nullable_cod_inst}"

    # Verify raw_data column
    # Row structure: [data_type, is_nullable, column_default]
    assert "raw_data" in columns
    assert columns["raw_data"][0] == "text"  # data_type (index 0)
    assert columns["raw_data"][1] == "NO"  # is_nullable (index 1)


def test_bank_report_table_structure(migrated_database):
    """Test bank_report table has correct columns including UUID v7 primary key."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'financial_data' AND table_name = 'bank_report'
            ORDER BY ordinal_position
        """
        )
        columns = {row[0]: row[1:] for row in cur.fetchall()}

        # Verify UUID v7 primary key
        assert "id" in columns
        assert columns["id"][0] == "uuid"  # data_type
        assert columns["id"][1] == "NO"  # is_nullable
        assert columns["id"][2] is not None  # column_default
        assert "uuidv7()" in str(columns["id"][2])  # default should be uuidv7()

        # Verify natural key columns
        assert "cod_inst" in columns
        assert "ano_mes" in columns
        assert "nome_relatorio" in columns
        assert "numero_relatorio" in columns
        assert "tipo_instituicao" in columns

        # Verify report_data column is removed
        assert "report_data" not in columns

        # Verify unique constraint on natural key exists
        cur.execute(
            """
            SELECT conname, contype
            FROM pg_constraint
            WHERE conrelid = 'financial_data.bank_report'::regclass
            AND contype = 'u'
            AND conname = 'bank_report_natural_key_unique'
        """
        )
        constraint = cur.fetchone()
        assert constraint is not None, "Unique constraint on natural key should exist"


def test_institution_indexes(migrated_database):
    """Test that institution table has correct indexes."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'financial_data' AND tablename = 'institution'
        """
        )
        indexes = {row[0] for row in cur.fetchall()}

    expected_indexes = {
        "idx_institution_ano_mes_ref",
        "idx_institution_situacao",
        "idx_institution_source_hash",
    }

    for idx in expected_indexes:
        assert idx in indexes, f"Index {idx} does not exist on institution table"


def test_ifdata_extract_task_indexes(migrated_database):
    """Test that ifdata_extract_task table has correct indexes."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'financial_data' AND tablename = 'ifdata_extract_task'
        """
        )
        indexes = {row[0] for row in cur.fetchall()}

    expected_indexes = {
        "idx_ifdata_extract_task_status",
        "idx_ifdata_extract_task_ano_mes_nome_relatorio",
        "idx_ifdata_extract_task_created_at",
    }

    for idx in expected_indexes:
        assert (
            idx in indexes
        ), f"Index {idx} does not exist on ifdata_extract_task table"


def test_ifdata_load_control_indexes(migrated_database):
    """Test that ifdata_load_control table has correct indexes."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'financial_data' AND tablename = 'ifdata_load_control'
        """
        )
        indexes = {row[0] for row in cur.fetchall()}

    expected_indexes = {
        "idx_ifdata_load_control_ano_mes",
        "idx_ifdata_load_control_extracted",
        "idx_ifdata_load_control_promoted",
        "idx_ifdata_load_control_completed",
        "idx_ifdata_load_control_created_at",
    }

    for idx in expected_indexes:
        assert (
            idx in indexes
        ), f"Index {idx} does not exist on ifdata_load_control table"


def test_ifdata_raw_indexes(migrated_database):
    """Test that ifdata_raw table has correct indexes including partial index."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE schemaname = 'financial_data' AND tablename = 'ifdata_raw'
        """
        )
        indexes = {row[0]: row[1] for row in cur.fetchall()}

    expected_indexes = {
        "idx_ifdata_raw_scope",
        "idx_ifdata_raw_cod_inst",
        "idx_ifdata_raw_created_at",
    }

    for idx in expected_indexes:
        assert idx in indexes, f"Index {idx} does not exist on ifdata_raw table"

    # Verify partial index on cod_inst
    cod_inst_index_def = indexes["idx_ifdata_raw_cod_inst"]
    # PostgreSQL formats WHERE clause with parentheses: WHERE (cod_inst IS NOT NULL)
    assert "cod_inst IS NOT NULL" in cod_inst_index_def, (
        f"idx_ifdata_raw_cod_inst should be a partial index with WHERE cod_inst IS NOT NULL. "
        f"Got: {cod_inst_index_def}"
    )


def test_bank_report_indexes(migrated_database):
    """Test that bank_report table has correct indexes."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'financial_data' AND tablename = 'bank_report'
        """
        )
        indexes = {row[0] for row in cur.fetchall()}

    expected_indexes = {
        "idx_bank_report_id",
        "idx_bank_report_cod_inst",
        "idx_bank_report_ano_mes",
        "idx_bank_report_scope",
        "idx_bank_report_created_at",
    }

    for idx in expected_indexes:
        assert idx in indexes, f"Index {idx} does not exist on bank_report table"


def test_bank_report_data_table_structure(migrated_database):
    """Test bank_report_data table has correct structure."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'financial_data' AND table_name = 'bank_report_data'
            ORDER BY ordinal_position
        """
        )
        columns = {row[0]: row[1:] for row in cur.fetchall()}

        # Verify UUID v7 primary key
        assert "id" in columns
        assert columns["id"][0] == "uuid"  # data_type
        assert columns["id"][1] == "NO"  # is_nullable
        assert columns["id"][2] is not None  # column_default
        assert "uuidv7()" in str(columns["id"][2])  # default should be uuidv7()

        # Verify foreign key column
        assert "bank_report_id" in columns
        assert columns["bank_report_id"][0] == "uuid"  # data_type
        assert columns["bank_report_id"][1] == "NO"  # is_nullable

        # Verify data columns
        assert "grupo" in columns
        assert columns["grupo"][0] == "text"  # data_type
        assert columns["grupo"][1] == "YES"  # is_nullable

        assert "conta" in columns
        assert columns["conta"][0] == "text"  # data_type
        assert columns["conta"][1] == "YES"  # is_nullable

        assert "nome_coluna" in columns
        assert columns["nome_coluna"][0] == "text"  # data_type
        assert columns["nome_coluna"][1] == "YES"  # is_nullable

        assert "descricao_coluna" in columns
        assert columns["descricao_coluna"][0] == "text"  # data_type
        assert columns["descricao_coluna"][1] == "YES"  # is_nullable

        assert "saldo" in columns
        assert columns["saldo"][0] == "numeric"  # data_type
        assert columns["saldo"][1] == "YES"  # is_nullable

        # Verify timestamps
        assert "created_at" in columns
        assert "updated_at" in columns

        # Verify foreign key constraint exists
        cur.execute(
            """
            SELECT conname, contype, confdeltype
            FROM pg_constraint
            WHERE conrelid = 'financial_data.bank_report_data'::regclass
            AND contype = 'f'
            AND conname = 'fk_bank_report_data_bank_report'
        """
        )
        fk_constraint = cur.fetchone()
        assert fk_constraint is not None, "Foreign key constraint should exist"
        # confdeltype 'c' means CASCADE
        assert fk_constraint[2] == "c", "Foreign key should have ON DELETE CASCADE"


def test_bank_report_data_indexes(migrated_database):
    """Test that bank_report_data table has correct indexes."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'financial_data' AND tablename = 'bank_report_data'
        """
        )
        indexes = {row[0] for row in cur.fetchall()}

    expected_indexes = {
        "idx_bank_report_data_bank_report_id",
        "idx_bank_report_data_lookup",
    }

    for idx in expected_indexes:
        assert idx in indexes, f"Index {idx} does not exist on bank_report_data table"


def test_ifdata_extract_task_check_constraint(migrated_database):
    """Test that ifdata_extract_task has CHECK constraint on status."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT conname, pg_get_constraintdef(oid) as definition
            FROM pg_constraint
            WHERE conrelid = 'financial_data.ifdata_extract_task'::regclass
            AND contype = 'c'
        """
        )
        constraints = {row[0]: row[1] for row in cur.fetchall()}

    assert (
        "chk_ifdata_extract_task_status" in constraints
    ), "CHECK constraint on status does not exist"

    constraint_def = constraints["chk_ifdata_extract_task_status"]
    assert "PENDING" in constraint_def
    assert "RUNNING" in constraint_def
    assert "SUCCESS" in constraint_def
    assert "EMPTY" in constraint_def
    assert "ERROR" in constraint_def


def test_primary_key_constraints(migrated_database):
    """Test that all tables have correct PRIMARY KEY constraints."""
    expected_primary_keys = {
        "institution": ["bacen_cod_inst"],
        "ifdata_extract_task": [
            "ano_mes",
            "tipo_instituicao",
            "nome_relatorio",
            "numero_relatorio",
            "page_skip",
        ],
        "ifdata_load_control": ["ano_mes", "nome_relatorio"],
        "ifdata_raw": ["id"],
        "bank_report": ["id"],
        "bank_report_data": ["id"],
    }

    for table_name, expected_cols in expected_primary_keys.items():
        with migrated_database.cursor() as cur:
            cur.execute(
                f"""
                SELECT a.attname
                FROM pg_index i
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                WHERE i.indrelid = 'financial_data.{table_name}'::regclass
                AND i.indisprimary = true
                ORDER BY a.attnum
            """
            )
            pk_columns = [row[0] for row in cur.fetchall()]

        assert pk_columns == expected_cols, (
            f"Primary key on {table_name} is incorrect. "
            f"Expected {expected_cols}, got {pk_columns}"
        )


def test_migrations_idempotency(postgres_container, test_pyway_config):
    """Test that migrations can be run twice without errors."""
    config_file, project_root = test_pyway_config

    # Run migrations first time
    original_cwd = os.getcwd()
    try:
        os.chdir(project_root)

        result1 = subprocess.run(
            ["pyway", "-c", str(config_file.absolute()), "migrate"],
            capture_output=True,
            text=True,
            check=True,
        )
        assert result1.returncode == 0, (
            f"First migration run failed: {result1.stderr}\n"
            f"Stdout: {result1.stdout}"
        )

        # Run migrations second time (should be idempotent)
        result2 = subprocess.run(
            ["pyway", "-c", str(config_file.absolute()), "migrate"],
            capture_output=True,
            text=True,
            check=True,
        )
        assert result2.returncode == 0, (
            f"Second migration run failed: {result2.stderr}\n"
            f"Stdout: {result2.stdout}"
        )

    finally:
        os.chdir(original_cwd)

    # Verify schema state is identical
    conn = psycopg2.connect(
        host=postgres_container.get_container_host_ip(),
        port=postgres_container.get_exposed_port(5432),
        database=postgres_container.dbname,
        user=postgres_container.username,
        password=postgres_container.password,
    )

    try:
        with conn.cursor() as cur:
            # Count tables
            cur.execute(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'financial_data'
            """
            )
            table_count = cur.fetchone()[0]

            # Count indexes
            cur.execute(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'financial_data'
            """
            )
            index_count = cur.fetchone()[0]

        # Verify counts are reasonable (should have 6 tables and multiple indexes)
        assert table_count == 6, f"Expected 6 tables, found {table_count}"
        assert index_count > 10, f"Expected multiple indexes, found {index_count}"

    finally:
        conn.close()


def test_uuid_v7_function_exists(migrated_database):
    """Test that uuidv7() function is available (PostgreSQL 18+)."""
    with migrated_database.cursor() as cur:
        cur.execute(
            """
            SELECT EXISTS(
                SELECT 1
                FROM pg_proc
                WHERE proname = 'uuidv7'
            )
        """
        )
        exists = cur.fetchone()[0]

    assert exists, "uuidv7() function is not available (requires PostgreSQL 18+)"


def test_data_types(migrated_database):
    """Test that data types match specifications."""
    with migrated_database.cursor() as cur:
        # Test TEXT type preserves leading zeros (institution codes)
        cur.execute(
            """
            SELECT data_type
            FROM information_schema.columns
            WHERE table_schema = 'financial_data'
            AND table_name = 'institution'
            AND column_name = 'bacen_cod_inst'
        """
        )
        assert cur.fetchone()[0] == "text"

        # Test CHAR(6) for ano_mes
        cur.execute(
            """
            SELECT data_type, character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = 'financial_data'
            AND table_name = 'institution'
            AND column_name = 'ano_mes_ref'
        """
        )
        result = cur.fetchone()
        assert result[0] == "character"
        assert result[1] == 6

        # Test CHAR(64) for source_hash
        cur.execute(
            """
            SELECT data_type, character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = 'financial_data'
            AND table_name = 'institution'
            AND column_name = 'source_hash'
        """
        )
        result = cur.fetchone()
        assert result[0] == "character"
        assert result[1] == 64

        # Test NUMERIC for saldo in bank_report_data
        cur.execute(
            """
            SELECT data_type
            FROM information_schema.columns
            WHERE table_schema = 'financial_data'
            AND table_name = 'bank_report_data'
            AND column_name = 'saldo'
        """
        )
        assert cur.fetchone()[0] == "numeric"

        # Test TIMESTAMPTZ for timestamp columns
        cur.execute(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'financial_data'
            AND data_type = 'timestamp with time zone'
        """
        )
        timestamptz_count = cur.fetchone()[0]
        assert timestamptz_count >= 10, "Expected multiple TIMESTAMPTZ columns"
