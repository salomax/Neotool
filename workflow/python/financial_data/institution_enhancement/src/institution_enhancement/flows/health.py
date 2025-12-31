"""
Health Check Server

HTTP health check endpoints for Kubernetes readiness and liveness probes.
"""

import http.server
import json
import logging
import threading
from typing import Optional, Tuple

from kafka import KafkaConsumer

from institution_enhancement.tasks.config import Config, HealthCheckConfig
from institution_enhancement.tasks.db_connection import get_db_connection, return_db_connection

logger = logging.getLogger(__name__)

# Global state for health checks
_consumer_running: bool = False
_health_config: Optional[HealthCheckConfig] = None


def set_consumer_running(running: bool) -> None:
    """
    Set consumer running state for liveness probe.

    Args:
        running: True if consumer loop is active, False otherwise
    """
    global _consumer_running
    _consumer_running = running


def _check_database() -> Tuple[bool, str]:
    """
    Check database connectivity.

    Returns:
        Tuple of (is_healthy, message)
    """
    try:
        conn = get_db_connection()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                cur.fetchone()
            return_db_connection(conn)
            return True, "Database connection OK"
        except Exception as e:
            return_db_connection(conn)
            return False, f"Database query failed: {e}"
    except Exception as e:
        return False, f"Database connection failed: {e}"


def _check_kafka() -> Tuple[bool, str]:
    """
    Check Kafka connectivity.

    Returns:
        Tuple of (is_healthy, message)
    """
    try:
        config = Config.from_env()
        # Try to create a consumer to test connectivity
        consumer = KafkaConsumer(
            bootstrap_servers=config.kafka.brokers,
            consumer_timeout_ms=1000,  # Short timeout for health check
        )
        consumer.close()
        return True, "Kafka connection OK"
    except Exception as e:
        return False, f"Kafka connection failed: {e}"


class HealthCheckHandler(http.server.BaseHTTPRequestHandler):
    """HTTP request handler for health check endpoints."""

    def do_GET(self) -> None:
        """Handle GET requests for health check endpoints."""
        if self.path == "/health":
            self._handle_health()
        elif self.path == "/ready":
            self._handle_ready()
        elif self.path == "/live":
            self._handle_live()
        else:
            self._send_response(404, {"error": "Not found"})

    def _handle_health(self) -> None:
        """Handle /health endpoint - basic health check."""
        self._send_response(200, {"status": "healthy", "service": "institution-enhancement-consumer"})

    def _handle_ready(self) -> None:
        """Handle /ready endpoint - readiness probe (checks external dependencies)."""
        db_ok, db_msg = _check_database()
        kafka_ok, kafka_msg = _check_kafka()

        if db_ok and kafka_ok:
            self._send_response(
                200,
                {
                    "status": "ready",
                    "checks": {
                        "database": {"status": "ok", "message": db_msg},
                        "kafka": {"status": "ok", "message": kafka_msg},
                    },
                },
            )
        else:
            checks = {}
            if not db_ok:
                checks["database"] = {"status": "error", "message": db_msg}
            if not kafka_ok:
                checks["kafka"] = {"status": "error", "message": kafka_msg}
            self._send_response(503, {"status": "not ready", "checks": checks})

    def _handle_live(self) -> None:
        """Handle /live endpoint - liveness probe (checks if consumer is running)."""
        global _consumer_running

        if _consumer_running:
            self._send_response(200, {"status": "alive", "consumer": "running"})
        else:
            self._send_response(503, {"status": "not alive", "consumer": "not running"})

    def _send_response(self, status_code: int, data: dict) -> None:
        """Send JSON response."""
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

    def log_message(self, format: str, *args: tuple) -> None:
        """Override to use our logger instead of stderr."""
        logger.debug(f"{self.address_string()} - {format % args}")


def start_health_server(config: Optional[HealthCheckConfig] = None) -> Optional[threading.Thread]:
    """
    Start health check HTTP server in a separate thread.

    Args:
        config: Health check configuration. If None, loads from environment.

    Returns:
        Thread running the health check server
    """
    if config is None:
        config = Config.from_env().health_check

    if not config.enabled:
        logger.info("Health check server is disabled")
        return None

    global _health_config
    _health_config = config

    def run_server() -> None:
        """Run the health check server."""
        try:
            server = http.server.HTTPServer(("0.0.0.0", config.port), HealthCheckHandler)
            logger.info(f"Health check server started on port {config.port}")
            server.serve_forever()
        except OSError as e:
            if e.errno == 48:  # Address already in use
                logger.error(f"Health check port {config.port} is already in use")
            else:
                logger.error(f"Failed to start health check server: {e}", exc_info=True)
        except Exception as e:
            logger.error(f"Health check server error: {e}", exc_info=True)

    thread = threading.Thread(target=run_server, daemon=True, name="health-check-server")
    thread.start()
    return thread


def stop_health_server() -> None:
    """
    Stop the health check server.

    Note: This is a placeholder. The server runs in a daemon thread
    and will be terminated when the main process exits.
    """
    logger.info("Health check server will be stopped with main process")

