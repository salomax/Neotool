"""Pytest configuration for bacen_ifdata tests."""

import logging
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

# Suppress testcontainers deprecation warnings BEFORE importing
# These warnings are emitted at import time, so we need to filter them early
warnings.filterwarnings(
    "ignore",
    message=".*@wait_container_is_ready.*deprecated.*",
    category=DeprecationWarning,
)
warnings.filterwarnings(
    "ignore",
    message=".*wait_for.*deprecated.*",
    category=DeprecationWarning,
)
# Also filter by module path (more aggressive)
warnings.filterwarnings(
    "ignore",
    category=DeprecationWarning,
    module=r".*testcontainers.*waiting_utils.*",
)

from testcontainers.core.container import DockerContainer

# Prefect's rich console handler can try writing to closed file handles during
# pytest teardown. Disable the console handler up front to avoid noisy errors.
os.environ.setdefault("PREFECT__LOGGING__CONSOLE__ENABLED", "false")

# Apply warning filters as early as possible so imports don't spam the output.
warnings.filterwarnings(
    "ignore",
    message="codecs.open\\(\\) is deprecated.*",
    category=DeprecationWarning,
    module=r"coolname\\.loader",
)
warnings.filterwarnings(
    "ignore",
    category=DeprecationWarning,
    module=r"coolname\\.loader",
)
# Additional testcontainers warning filters (redundant but ensures coverage)

# Suppress urllib3 SystemTimeWarning when system time is mocked in tests
# This warning occurs when tests mock dates before 2025-01-01 and urllib3
# checks system time for SSL verification. This is expected in test environments.
warnings.filterwarnings(
    "ignore",
    message=".*System time is way off.*",
    category=UserWarning,
    module=r"urllib3.*",
)
warnings.filterwarnings(
    "ignore",
    message=".*System time is way off.*",
    category=Warning,
    module=r"urllib3.*",
)


def pytest_configure(config):
    """Configure pytest to suppress known warnings from third-party libraries."""
    # Ignore testcontainers legacy wait decorator warnings triggered at import time
    # (Also filtered at module level, but keeping here for redundancy)
    # All warning filters must be in conftest.py per testing standards (not in pytest.ini)
    config.addinivalue_line(
        "filterwarnings",
        "ignore::DeprecationWarning:testcontainers.core.waiting_utils",
    )
    config.addinivalue_line(
        "filterwarnings",
        "ignore::DeprecationWarning:testcontainers.core",
    )
    config.addinivalue_line(
        "filterwarnings",
        "ignore::DeprecationWarning:testcontainers",
    )
    config.addinivalue_line(
        "filterwarnings",
        "ignore:The @wait_container_is_ready decorator is deprecated:DeprecationWarning:testcontainers.core.waiting_utils",
    )
    config.addinivalue_line(
        "filterwarnings",
        "ignore:The wait_for function is deprecated:DeprecationWarning:testcontainers.core.waiting_utils",
    )
    # Also use warnings module for runtime filtering
    warnings.filterwarnings(
        "ignore",
        message="The @wait_container_is_ready decorator is deprecated.*",
        category=DeprecationWarning,
        module=r"testcontainers.*",
    )
    warnings.filterwarnings(
        "ignore",
        message="The wait_for function is deprecated.*",
        category=DeprecationWarning,
        module=r"testcontainers.*",
    )
    # Ignore coolname library deprecation warnings about codecs.open()
    # These come from the coolname library used by Prefect for generating run names
    warnings.filterwarnings(
        "ignore",
        message="codecs.open\\(\\) is deprecated.*",
        category=DeprecationWarning,
    )
    warnings.filterwarnings(
        "ignore",
        category=DeprecationWarning,
        module=r"coolname\.loader",
    )
    # Suppress urllib3 SystemTimeWarning when system time is mocked in tests
    # This warning occurs when tests mock dates before 2025-01-01 and urllib3
    # checks system time for SSL verification. This is expected in test environments.
    warnings.filterwarnings(
        "ignore",
        message=".*System time is way off.*",
        category=UserWarning,
        module=r"urllib3.*",
    )
    warnings.filterwarnings(
        "ignore",
        message=".*System time is way off.*",
        category=Warning,
        module=r"urllib3.*",
    )
    # Also add pytest-level filter
    config.addinivalue_line(
        "filterwarnings",
        "ignore::UserWarning:urllib3.connection",
    )
    config.addinivalue_line(
        "filterwarnings",
        "ignore:System time is way off.*:UserWarning:urllib3.*",
    )
    # Suppress Prefect logging errors during test teardown
    # These occur when Prefect tries to log during shutdown after file handles are closed.
    # This is a known Prefect issue (see: https://github.com/PrefectHQ/prefect/issues/...)
    # and doesn't affect test results. Setting the logger to CRITICAL prevents most
    # logging attempts, but some errors may still appear during teardown.
    prefect_logger = logging.getLogger("prefect.logging.handlers")
    prefect_logger.setLevel(logging.CRITICAL)
    prefect_logger.propagate = False

    # Monkeypatch PrefectConsoleHandler.emit to no-op if the stream is already closed.
    # This avoids "I/O operation on closed file" errors when pytest tears down stdout.
    try:
        from prefect.logging.handlers import PrefectConsoleHandler

        original_emit = PrefectConsoleHandler.emit

        def safe_emit(self, record):
            stream = getattr(self.console, "file", None)
            if getattr(stream, "closed", False):
                return
            return original_emit(self, record)

        PrefectConsoleHandler.emit = safe_emit
    except Exception:
        # If Prefect changes internals, fall back silently; tests will still run.
        pass


def pytest_collection_modifyitems(config, items):
    """Modify test items during collection."""
    # Suppress testcontainers warnings from appearing in summary
    # This hook runs after collection, allowing us to filter warnings
    warnings.filterwarnings(
        "ignore",
        message=".*@wait_container_is_ready.*deprecated.*",
        category=DeprecationWarning,
    )
    warnings.filterwarnings(
        "ignore",
        message=".*wait_for.*deprecated.*",
        category=DeprecationWarning,
    )


# ============================================================================
# Shared Testcontainers Fixtures for Integration Tests
# ============================================================================


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
    """Start PostgreSQL testcontainer for integration tests.

    This fixture provides a PostgreSQL 18+ container that can be shared across
    all integration tests in a test module. The container is started once per
    module and reused for all tests in that module.

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
def migrated_database(postgres_container):
    """Run pyway migrations and return database connection.

    This fixture:
    1. Starts a PostgreSQL container (via postgres_container fixture)
    2. Runs all database migrations using pyway
    3. Returns a database connection ready for testing

    The migrations are run once per module, and the connection is shared
    across all tests in that module.

    Note: This connection should NOT be closed by task functions. Use
    `db_connection_factory` fixture if you need task functions to close
    their own connections.
    """
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

    try:
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

        # Create database connection for testing
        conn = psycopg2.connect(
            host=postgres_container.get_container_host_ip(),
            port=postgres_container.get_exposed_port(5432),
            database=postgres_container.dbname,
            user=postgres_container.username,
            password=postgres_container.password,
        )

        yield conn

        conn.close()
    finally:
        # Clean up: remove temporary config file
        if config_file.exists():
            config_file.unlink()


@pytest.fixture(scope="module")
def db_connection_factory(postgres_container):
    """Return a factory function that creates new database connections.

    This fixture is useful when task functions close their connections,
    as it allows each task call to get a fresh connection that can be
    safely closed without affecting other tests.

    Usage:
        def test_something(db_connection_factory):
            # Patch get_db_connection to use the factory
            with patch('tasks.module.get_db_connection', db_connection_factory):
                result = some_task.fn(...)
    """

    def create_connection():
        """Create a new connection to the testcontainer database."""
        return psycopg2.connect(
            host=postgres_container.get_container_host_ip(),
            port=postgres_container.get_exposed_port(5432),
            database=postgres_container.dbname,
            user=postgres_container.username,
            password=postgres_container.password,
            connect_timeout=5,
        )

    return create_connection
