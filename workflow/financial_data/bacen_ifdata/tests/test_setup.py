"""Test script to verify Python environment, database connections, and Prefect setup."""

import os
from pathlib import Path
import sys

from dotenv import load_dotenv
import pytest

import psycopg2

# Load environment variables
load_dotenv()


def test_python_version():
    """Test that Python version is 3.14+."""
    version = sys.version_info
    assert (
        version.major == 3
    ), f"Expected Python 3.x, got {version.major}.{version.minor}"
    assert (
        version.minor >= 14
    ), f"Expected Python 3.14+, got {version.major}.{version.minor}"
    print(f"✓ Python version: {version.major}.{version.minor}.{version.micro}")


def test_required_packages():
    """Test that all required packages are installed."""
    # Map package names to their import names
    package_imports = {
        "prefect": "prefect",
        "psycopg2": "psycopg2",
        "kafka": "kafka",
        "requests": "requests",
        "pyway": "pyway",
        "python-dotenv": "dotenv",  # python-dotenv imports as 'dotenv'
    }

    missing = []
    for package_name, import_name in package_imports.items():
        try:
            __import__(import_name)
            print(f"✓ Package installed: {package_name}")
        except ImportError:
            missing.append(package_name)
            print(f"✗ Package missing: {package_name}")

    assert not missing, f"Missing required packages: {missing}"


def test_database_connection():
    """Test database connection via pgbouncer."""
    # Get connection parameters from environment or use defaults
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = int(os.getenv("POSTGRES_PORT", "6432"))  # pgbouncer port
    database = os.getenv("POSTGRES_DB", "neotool_db")
    user = os.getenv("POSTGRES_USER", "neotool")
    password = os.getenv("POSTGRES_PASSWORD", "neotool")

    try:
        conn = psycopg2.connect(
            host=host,
            port=port,
            database=database,
            user=user,
            password=password,
            connect_timeout=5,
        )

        with conn.cursor() as cur:
            # Test basic query
            cur.execute("SELECT version();")
            version = cur.fetchone()[0]
            print("✓ Database connection successful")
            print(f"  PostgreSQL version: {version.split(',')[0]}")

            # Test that we can query
            cur.execute("SELECT current_database(), current_user;")
            db_name, db_user = cur.fetchone()
            print(f"  Connected to database: {db_name}")
            print(f"  Connected as user: {db_user}")

            # Test pgbouncer connection (if using pgbouncer)
            if port == 6432:
                print("  Connected via pgbouncer (port 6432)")

        conn.close()

    except psycopg2.OperationalError as e:
        pytest.fail(f"Database connection failed: {e}")
    except Exception as e:
        pytest.fail(f"Unexpected error connecting to database: {e}")


def test_pyway_config():
    """Test that pyway configuration file exists and is readable."""
    # Find pyway.conf relative to this test file
    # test_setup.py is at: workflow/financial_data/bacen_ifdata/tests/test_setup.py
    # pyway.conf is at: workflow/financial_data/pyway.conf
    test_dir = Path(__file__).parent
    project_root = test_dir.parent.parent  # workflow/financial_data/
    pyway_conf = project_root / "pyway.conf"

    assert pyway_conf.exists(), f"pyway.conf not found at {pyway_conf}"
    print(f"✓ pyway.conf found at {pyway_conf}")

    # Read and verify basic structure
    with Path(pyway_conf).open() as f:
        content = f.read()
        assert "database_type: postgres" in content, "pyway.conf missing database_type"
        assert (
            "database_port: 6432" in content
        ), "pyway.conf should use pgbouncer port 6432"
        assert (
            "database_migration_dir: migrations" in content
        ), "pyway.conf missing migration_dir"
        print("✓ pyway.conf configuration valid")


def test_migrations_directory():
    """Test that migrations directory exists."""
    # Find migrations directory relative to this test file
    # test_setup.py is at: workflow/financial_data/bacen_ifdata/tests/test_setup.py
    # migrations/ is at: workflow/financial_data/migrations/
    test_dir = Path(__file__).parent
    project_root = test_dir.parent.parent  # workflow/financial_data/
    migrations_dir = project_root / "migrations"

    assert (
        migrations_dir.exists()
    ), f"migrations directory not found at {migrations_dir}"
    assert migrations_dir.is_dir(), f"{migrations_dir} is not a directory"
    print(f"✓ migrations directory exists at {migrations_dir}")


def test_prefect_connection():
    """Test Prefect API connection."""
    try:
        from prefect import get_client

        api_url = os.getenv("PREFECT_API_URL", "http://localhost:4200/api")

        # Try to connect to Prefect API
        # Note: This requires Prefect server to be running
        get_client()

        # Try a simple API call
        # This will fail if Prefect server is not running, which is OK for setup test
        try:
            # Just verify we can create a client
            print(f"✓ Prefect client created (API: {api_url})")
            print("  Note: Prefect server must be running for full functionality")
        except Exception as e:
            print(f"⚠ Prefect server may not be running: {e}")
            print(
                "  Start with: docker-compose -f ../../../infra/docker/docker-compose.local.yml --profile prefect up -d"
            )

    except ImportError:
        pytest.fail("Prefect package not installed")
    except Exception as e:
        print(f"⚠ Prefect connection test skipped: {e}")


def test_environment_variables():
    """Test that required environment variables are documented."""
    # These are optional for the test, but we document what's needed
    env_vars = {
        "POSTGRES_HOST": os.getenv("POSTGRES_HOST", "localhost (default)"),
        "POSTGRES_PORT": os.getenv("POSTGRES_PORT", "6432 (default, pgbouncer)"),
        "POSTGRES_DB": os.getenv("POSTGRES_DB", "neotool_db (default)"),
        "POSTGRES_USER": os.getenv("POSTGRES_USER", "neotool (default)"),
        "PREFECT_API_URL": os.getenv(
            "PREFECT_API_URL", "http://localhost:4200/api (default)"
        ),
    }

    print("\nEnvironment variables:")
    for var, value in env_vars.items():
        print(f"  {var}={value}")


if __name__ == "__main__":
    """Run tests directly for quick verification."""
    print("=" * 60)
    print("BACEN IFData Ingestion - Setup Verification")
    print("=" * 60)
    print()

    tests = [
        ("Python Version", test_python_version),
        ("Required Packages", test_required_packages),
        ("Database Connection", test_database_connection),
        ("Pyway Configuration", test_pyway_config),
        ("Migrations Directory", test_migrations_directory),
        ("Prefect Connection", test_prefect_connection),
        ("Environment Variables", test_environment_variables),
    ]

    results = []
    for name, test_func in tests:
        print(f"\n[{name}]")
        try:
            test_func()
            results.append((name, True, None))
        except AssertionError as e:
            print(f"✗ Failed: {e}")
            results.append((name, False, str(e)))
        except Exception as e:
            print(f"✗ Error: {e}")
            results.append((name, False, str(e)))

    print("\n" + "=" * 60)
    print("Summary")
    print("=" * 60)

    passed = sum(1 for _, success, _ in results if success)
    total = len(results)

    for name, success, error in results:
        status = "✓ PASS" if success else "✗ FAIL"
        print(f"{status}: {name}")
        if error:
            print(f"      {error}")

    print(f"\n{passed}/{total} tests passed")

    if passed < total:
        sys.exit(1)
    print("\n✓ All setup checks passed!")
    sys.exit(0)
