"""
Database Connection Management

Connection pooling and lifecycle management for PostgreSQL connections.
"""

import logging
from typing import Optional

import psycopg2
from psycopg2 import pool
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
    before_sleep_log,
    after_log,
)

from institution_enhancement.tasks.config import DatabaseConfig, Config

logger = logging.getLogger(__name__)

# Global connection pool (initialized lazily)
_connection_pool: Optional[pool.ThreadedConnectionPool] = None
_config: Optional[DatabaseConfig] = None


def initialize_pool(config: Optional[DatabaseConfig] = None) -> pool.ThreadedConnectionPool:
    """
    Initialize the database connection pool.

    Args:
        config: Database configuration. If None, loads from environment.

    Returns:
        ThreadedConnectionPool instance

    Raises:
        psycopg2.Error: If pool initialization fails
    """
    global _connection_pool, _config

    if config is None:
        config = Config.from_env().database

    _config = config

    logger.info(
        f"Initializing connection pool: host={config.host}, "
        f"port={config.port}, database={config.database}, "
        f"pool_min={config.pool_min}, pool_max={config.pool_max}"
    )

    try:
        _connection_pool = pool.ThreadedConnectionPool(
            minconn=config.pool_min,
            maxconn=config.pool_max,
            host=config.host,
            port=config.port,
            database=config.database,
            user=config.user,
            password=config.password,
            connect_timeout=5,
        )
        logger.info("Connection pool initialized successfully")
        return _connection_pool
    except psycopg2.Error as e:
        logger.error(f"Failed to initialize connection pool: {e}", exc_info=True)
        raise


@retry(
    retry=retry_if_exception_type((pool.PoolError, psycopg2.OperationalError)),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=10),
    before_sleep=before_sleep_log(logger, logging.WARNING),
    after=after_log(logger, logging.INFO),
    reraise=True,
)
def get_db_connection() -> psycopg2.extensions.connection:
    """
    Get PostgreSQL database connection from the connection pool.

    Returns:
        psycopg2 connection object from pool

    Raises:
        psycopg2.pool.PoolError: If pool is exhausted or connection cannot be obtained after retries
        psycopg2.OperationalError: If database connection fails after retries
    """
    global _connection_pool

    if _connection_pool is None:
        initialize_pool()

    try:
        return _connection_pool.getconn()
    except (pool.PoolError, psycopg2.OperationalError) as e:
        logger.warning(f"Failed to get connection from pool: {e}")
        raise


def return_db_connection(conn: psycopg2.extensions.connection) -> None:
    """
    Return a database connection to the pool.

    Args:
        conn: Connection to return to pool
    """
    global _connection_pool

    if _connection_pool is not None:
        try:
            _connection_pool.putconn(conn)
        except Exception as e:
            # If connection is bad, discard it
            logger.warning(f"Error returning connection to pool, discarding: {e}")
            _connection_pool.putconn(conn, close=True)


def close_pool() -> None:
    """
    Close all connections in the pool and clean up.

    Should be called on application shutdown.
    """
    global _connection_pool

    if _connection_pool is not None:
        logger.info("Closing connection pool")
        _connection_pool.closeall()
        _connection_pool = None
        logger.info("Connection pool closed")

