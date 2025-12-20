"""Pytest configuration for bacen_ifdata tests."""

import os
import warnings
import logging

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


def pytest_configure():
    """Configure pytest to suppress known warnings from third-party libraries."""
    # Ignore testcontainers legacy wait decorator warnings triggered at import time
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
