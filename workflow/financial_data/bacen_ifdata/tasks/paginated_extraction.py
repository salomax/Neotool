"""
Paginated Extraction Task

Executes extract tasks by picking PENDING tasks, calling BACEN API with pagination,
parsing CSV responses, storing raw data, and managing task lifecycle with proper
error handling and retry logic.
"""

import csv
from io import StringIO
from typing import Any

from prefect import get_run_logger, task
import psycopg2
from psycopg2.extensions import connection, cursor
import requests

from .constants import BACEN_BASE_URL, DEFAULT_PAGE_TOP
from .quarter_determination import get_db_connection
from .registry_ingestion import build_bacen_query_string, get_bacen_session

# Configuration constants
VALUES_ENDPOINT = "IfDataValores(AnoMes=@AnoMes,TipoInstituicao=@TipoInstituicao,Relatorio=@Relatorio)"
MAX_ATTEMPTS = 3  # Extract: ≥3 attempts per batch workflow standard


class RetryablePaginatedExtractionError(Exception):
    """Signal handled errors that should trigger Prefect retry."""


def pick_pending_task(_conn: connection, cur: cursor) -> dict[str, Any] | None:
    """
    Pick a PENDING extract task from the queue with row-level locking.

    Uses FOR UPDATE SKIP LOCKED to prevent multiple workers from picking the same task.
    Orders by created_at ASC to process oldest tasks first.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object

    Returns:
        Dictionary with task details (ano_mes, tipo_instituicao, nome_relatorio,
        numero_relatorio, page_skip, page_top) or None if no tasks available
    """
    cur.execute(
        """
        SELECT
            ano_mes,
            tipo_instituicao,
            nome_relatorio,
            numero_relatorio,
            page_skip,
            page_top,
            attempt_count
        FROM financial_data.ifdata_extract_task
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
    """
    )

    row = cur.fetchone()
    if row:
        page_top = row[5] or DEFAULT_PAGE_TOP
        return {
            "ano_mes": row[0],
            "tipo_instituicao": row[1],
            "nome_relatorio": row[2],
            "numero_relatorio": row[3],
            "page_skip": row[4],
            "page_top": page_top,
            "attempt_count": row[6],
        }
    return None


def update_task_status(
    _conn: connection,
    cur: cursor,
    task_key: dict[str, Any],
    status: str,
    error: str | None = None,
    http_status: int | None = None,
    logger: Any = None,
    set_finished: bool = False,
) -> None:
    """
    Update task status and related fields in ifdata_extract_task table.

    Sets started_at when transitioning to RUNNING.
    Sets finished_at when status is terminal (SUCCESS, EMPTY, ERROR).
    Increments attempt_count and stores error details.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        task_key: Dictionary with task natural key fields
        status: New status (PENDING, RUNNING, SUCCESS, EMPTY, ERROR)
        error: Optional error message to store
        http_status: Optional HTTP status code to store
        logger: Optional logger for logging
    """
    # Build WHERE clause from natural key
    where_clause = """
        ano_mes = %s
        AND tipo_instituicao = %s
        AND nome_relatorio = %s
        AND numero_relatorio = %s
        AND page_skip = %s
    """
    where_params = (
        task_key["ano_mes"],
        task_key["tipo_instituicao"],
        task_key["nome_relatorio"],
        task_key["numero_relatorio"],
        task_key["page_skip"],
    )

    update_fields = [
        "status = %s",
        "updated_at = now()",
    ]
    update_params = [status]

    if status == "RUNNING":
        update_fields.extend(
            [
                "started_at = now()",
                "attempt_count = attempt_count + 1",
            ]
        )

    if status in ("SUCCESS", "EMPTY", "ERROR") or set_finished:
        update_fields.append("finished_at = now()")

    if error is not None:
        update_fields.append("last_error = %s")
        update_params.append(error)

    if http_status is not None:
        update_fields.append("last_http_status = %s")
        update_params.append(http_status)

    cur.execute(
        f"""
        UPDATE financial_data.ifdata_extract_task
        SET {', '.join(update_fields)}
        WHERE {where_clause}
    """,
        tuple(update_params) + where_params,
    )

    if logger:
        logger.debug(
            f"Updated task status: {status}, "
            f"task_key={task_key}, error={error}, http_status={http_status}"
        )


def should_purge_raw(_conn: connection, cur: cursor, scope: dict[str, Any], page_skip: int) -> bool:
    """
    Determine if raw data should be purged for the given scope.

    Purge only when:
    - page_skip = 0 (first page of scope)
    - Raw data already exists for scope (reloading scenario)

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        scope: Dictionary with scope fields (ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
        page_skip: Current page_skip value

    Returns:
        True if raw data should be purged, False otherwise
    """
    # Only purge on first page (page_skip = 0)
    if page_skip != 0:
        return False

    # Check if raw data exists for scope
    cur.execute(
        """
        SELECT COUNT(*)
        FROM financial_data.ifdata_raw
        WHERE ano_mes = %s
          AND nome_relatorio = %s
          AND numero_relatorio = %s
          AND tipo_instituicao = %s
    """,
        (
            scope["ano_mes"],
            scope["nome_relatorio"],
            scope["numero_relatorio"],
            scope["tipo_instituicao"],
        ),
    )

    count = cur.fetchone()[0]
    return count > 0


def purge_raw_for_scope(_conn: connection, cur: cursor, scope: dict[str, Any], logger: Any = None) -> int:
    """
    Purge raw data for the given scope.

    Deletes all rows from ifdata_raw table matching the scope.
    This is safe because it only happens on page_skip=0 (first page),
    ensuring pagination safety.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        scope: Dictionary with scope fields (ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
        logger: Optional logger for logging

    Returns:
        Number of rows deleted
    """
    cur.execute(
        """
        DELETE FROM financial_data.ifdata_raw
        WHERE ano_mes = %s
          AND nome_relatorio = %s
          AND numero_relatorio = %s
          AND tipo_instituicao = %s
    """,
        (
            scope["ano_mes"],
            scope["nome_relatorio"],
            scope["numero_relatorio"],
            scope["tipo_instituicao"],
        ),
    )

    deleted_count = cur.rowcount

    if logger:
        logger.info(
            f"Purged {deleted_count} raw rows for scope: "
            f"ano_mes={scope['ano_mes']}, "
            f"nome_relatorio={scope['nome_relatorio']}, "
            f"numero_relatorio={scope['numero_relatorio']}, "
            f"tipo_instituicao={scope['tipo_instituicao']}"
        )

    return deleted_count


def build_bacen_url(
    ano_mes: str,
    tipo_instituicao: int,
    relatorio: str,
    page_skip: int,
    page_top: int,
) -> str:
    """
    Build BACEN API URL for IfDataValores endpoint with pagination.

    Args:
        ano_mes: Target quarter in YYYYMM format
        tipo_instituicao: Institution type (1-4)
        relatorio: Report number (NumeroRelatorio)
        page_skip: Pagination skip value
        page_top: Pagination top value (page size)

    Returns:
        Full URL with OData parameters
    """
    # Build base URL with endpoint
    url = f"{BACEN_BASE_URL}{VALUES_ENDPOINT}"

    # Build query parameters
    # Note: BACEN API requires @AnoMes, @TipoInstituicao, @Relatorio in parameter names
    # and $top, $skip, $format in query string
    # IMPORTANT: @Relatorio value must be quoted (e.g., '1' not 1)
    params = {
        "@AnoMes": ano_mes,
        "@TipoInstituicao": tipo_instituicao,
        "@Relatorio": f"'{relatorio}'",  # Report number must be quoted (e.g., "'1'", "'2'")
        "$top": page_top or DEFAULT_PAGE_TOP,
        "$skip": page_skip,
        "$format": "text/csv",
    }

    # Build query string manually to preserve $ and @ characters
    query_string = build_bacen_query_string(params)
    full_url = f"{url}?{query_string}"

    return full_url


def fetch_bacen_data(session: requests.Session, url: str, logger: Any = None) -> str:
    """
    Fetch data from BACEN API.

    Args:
        session: requests.Session with retry configuration
        url: Full URL to fetch
        logger: Optional logger for logging

    Returns:
        CSV content as string

    Raises:
        requests.RequestException: If HTTP request fails
    """
    if logger:
        logger.debug(f"Fetching BACEN data from: {url}")

    response = session.get(url, timeout=60)
    response.raise_for_status()

    return response.text


def parse_csv_rows(csv_text: str, scope: dict[str, Any]) -> list[dict[str, Any]]:
    """
    Parse CSV content into list of row dictionaries.

    Extracts cod_inst if available from CSV row.
    Stores entire CSV row as raw_data (TEXT) to preserve original values.

    Note: BACEN/Olinda CSV responses use comma (,) as delimiter.
    The raw_data is reconstructed from the parsed dict to ensure consistency
    and handle quoted fields with newlines correctly.

    Args:
        csv_text: Raw CSV content as string
        scope: Dictionary with scope fields to include in each row

    Returns:
        List of row dictionaries with scope fields + cod_inst + raw_data
    """
    if not csv_text or not csv_text.strip():
        return []

    # Handle BOM if present (like in registry_ingestion)
    if csv_text.startswith("\ufeff"):
        csv_text = csv_text[1:]

    # Parse CSV to get field names and row data
    reader = csv.DictReader(StringIO(csv_text), delimiter=",")
    fieldnames = reader.fieldnames

    if not fieldnames:
        return []

    rows = []
    for row_dict in reader:
        # Extract cod_inst if available (may be named CodInst or similar)
        cod_inst = None
        for key in row_dict:
            if key.lower() in ("codinst", "cod_inst", "codigo_instituicao"):
                cod_inst_value = row_dict[key]
                if cod_inst_value:
                    cod_inst = cod_inst_value.strip()
                break

        # Reconstruct CSV row from dict to preserve all data correctly
        # This handles quoted fields with newlines and special characters properly
        output = StringIO()
        writer = csv.writer(output, delimiter=",", quoting=csv.QUOTE_MINIMAL)
        writer.writerow([row_dict.get(field, "") for field in fieldnames])
        raw_data = output.getvalue().rstrip("\n\r")  # Remove trailing newline

        # Create row with scope + cod_inst + raw_data
        row = {
            "ano_mes": scope["ano_mes"],
            "tipo_instituicao": scope["tipo_instituicao"],
            "nome_relatorio": scope["nome_relatorio"],
            "numero_relatorio": scope["numero_relatorio"],
            "cod_inst": cod_inst,
            "raw_data": raw_data,
        }

        rows.append(row)

    return rows


def insert_raw_rows(_conn: connection, cur: cursor, rows: list[dict[str, Any]], logger: Any = None) -> int:
    """
    Batch insert rows into ifdata_raw table.

    Uses executemany for performance.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        rows: List of row dictionaries to insert
        logger: Optional logger for logging

    Returns:
        Number of rows inserted
    """
    if not rows:
        return 0

    insert_sql = """
        INSERT INTO financial_data.ifdata_raw
            (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, cod_inst, raw_data)
        VALUES
            (%s, %s, %s, %s, %s, %s)
    """

    # Prepare data tuples
    data_tuples = [
        (
            row["ano_mes"],
            row["tipo_instituicao"],
            row["nome_relatorio"],
            row["numero_relatorio"],
            row["cod_inst"],
            row["raw_data"],
        )
        for row in rows
    ]

    cur.executemany(insert_sql, data_tuples)
    inserted_count = cur.rowcount

    if logger:
        logger.debug(f"Inserted {inserted_count} rows into ifdata_raw")

    return inserted_count


def create_next_page_task(
    _conn: connection, cur: cursor, current_task: dict[str, Any], page_top: int, logger: Any = None
) -> bool:
    """
    Create next page task for pagination continuation.

    Only creates if rows were inserted (rows > 0).
    New task has same scope but page_skip += page_top.
    Uses ON CONFLICT DO NOTHING for idempotency.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        current_task: Dictionary with current task details
        page_top: Page size (used to compute next page_skip)
        logger: Optional logger for logging

    Returns:
        True if next page task was created, False if it already existed
    """
    next_page_skip = current_task["page_skip"] + page_top

    insert_sql = """
        INSERT INTO financial_data.ifdata_extract_task
            (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip, page_top, status)
        VALUES
            (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip)
        DO NOTHING
    """

    cur.execute(
        insert_sql,
        (
            current_task["ano_mes"],
            current_task["tipo_instituicao"],
            current_task["nome_relatorio"],
            current_task["numero_relatorio"],
            next_page_skip,
            page_top,
            "PENDING",
        ),
    )

    created = cur.rowcount > 0

    if logger:
        if created:
            logger.debug(
                f"Created next page task: page_skip={next_page_skip}, "
                f"scope={current_task['ano_mes']}/{current_task['nome_relatorio']}/"
                f"{current_task['numero_relatorio']}/{current_task['tipo_instituicao']}"
            )
        else:
            logger.debug(f"Next page task already exists: page_skip={next_page_skip}")

    return created


def handle_task_failure(
    conn: connection,
    cur: cursor,
    task: dict[str, Any],
    task_key: dict[str, Any],
    error_msg: str,
    logger: Any,
    http_status: int | None = None,
) -> bool:
    """
    Update task for failure, deciding between retry (PENDING) and terminal ERROR.

    Returns:
        True if task should be retried (status set back to PENDING), False if terminal.
    """
    attempts = task["attempt_count"] + 1  # attempt_count already incremented on RUNNING
    should_retry = attempts < MAX_ATTEMPTS
    next_status = "PENDING" if should_retry else "ERROR"

    update_task_status(
        conn,
        cur,
        task_key,
        next_status,
        error=error_msg,
        http_status=http_status,
        logger=logger,
        set_finished=not should_retry,
    )
    conn.commit()

    if should_retry:
        logger.warning(
            f"Task failed (attempt {attempts}/{MAX_ATTEMPTS}). Re-queued for retry. "
            f"task_key={task_key}, error={error_msg}"
        )
    else:
        logger.error(
            f"Task failed and reached max attempts ({MAX_ATTEMPTS}). "
            f"Marked as ERROR. task_key={task_key}, error={error_msg}"
        )

    return should_retry


@task(
    name="execute_paginated_extraction",
    retries=3,  # Extract: ≥3 attempts per batch workflow standard
    retry_delay_seconds=60,  # 1-5 min backoff
    timeout_seconds=300,  # 5 min timeout for API calls
    log_prints=True,
    tags=["extract", "pagination"],
)
def execute_paginated_extraction() -> dict[str, Any]:
    """
    Pick a PENDING extract task, execute it, and manage lifecycle.

    Process:
    1. Pick a PENDING task from ifdata_extract_task (with row-level locking)
    2. Set task status to RUNNING
    3. If page_skip=0 and reloading, purge raw data for scope
    4. Call BACEN API with pagination parameters
    5. Parse CSV response (comma delimiter)
    6. Insert rows into ifdata_raw table
    7. If rows > 0: mark SUCCESS, create next page task
    8. If rows = 0: mark EMPTY (terminal state)
    9. Handle errors: update attempt tracking, re-queue for retry until max attempts, then mark ERROR

    Returns:
        Dictionary with execution results:
        - task_key: Natural key of executed task (or None if no task available)
        - status: Final status (SUCCESS, EMPTY, ERROR, or None if no task)
        - rows_inserted: Number of rows inserted into ifdata_raw
        - next_page_created: Whether next page task was created
        - error: Error message if status is ERROR

    Raises:
        RetryablePaginatedExtractionError: For Prefect retry when attempts remain
        psycopg2.Error: If database operation fails
    """
    logger = get_run_logger()

    conn = None
    session = None

    try:
        # Get database connection
        conn = get_db_connection()
        cur = conn.cursor()

        # Pick a PENDING task
        task = pick_pending_task(conn, cur)

        if not task:
            logger.info("No PENDING tasks available")
            return {
                "task_key": None,
                "status": None,
                "rows_inserted": 0,
                "next_page_created": False,
                "error": None,
            }

        # Build task key for updates
        task_key = {
            "ano_mes": task["ano_mes"],
            "tipo_instituicao": task["tipo_instituicao"],
            "nome_relatorio": task["nome_relatorio"],
            "numero_relatorio": task["numero_relatorio"],
            "page_skip": task["page_skip"],
        }

        # Build scope for raw operations
        scope = {
            "ano_mes": task["ano_mes"],
            "tipo_instituicao": task["tipo_instituicao"],
            "nome_relatorio": task["nome_relatorio"],
            "numero_relatorio": task["numero_relatorio"],
        }
        page_top = task["page_top"] or DEFAULT_PAGE_TOP

        logger.info(
            f"Executing task: ano_mes={task['ano_mes']}, "
            f"tipo_instituicao={task['tipo_instituicao']}, "
            f"nome_relatorio={task['nome_relatorio']}, "
            f"numero_relatorio={task['numero_relatorio']}, "
            f"page_skip={task['page_skip']}, "
            f"page_top={page_top}, "
            f"attempt_count={task['attempt_count']}"
        )

        # Set task to RUNNING
        update_task_status(conn, cur, task_key, "RUNNING", logger=logger)
        conn.commit()

        # Check if raw data should be purged (page_skip=0 and reloading)
        if should_purge_raw(conn, cur, scope, task["page_skip"]):
            logger.info("Purging raw data for scope (reloading scenario)")
            purge_raw_for_scope(conn, cur, scope, logger=logger)
            conn.commit()

        # Get BACEN session
        session = get_bacen_session()

        # Build BACEN URL
        url = build_bacen_url(
            task["ano_mes"],
            task["tipo_instituicao"],
            task["numero_relatorio"],
            task["page_skip"],
            page_top,
        )

        # Fetch data from BACEN API
        try:
            csv_text = fetch_bacen_data(session, url, logger=logger)
        except requests.RequestException as e:
            # Handle HTTP errors
            error_msg = f"HTTP error: {e}"
            http_status = None
            if hasattr(e, "response") and e.response is not None:
                http_status = e.response.status_code
                error_msg = f"HTTP {http_status}: {e}"

            logger.exception(f"Failed to fetch BACEN data: {error_msg}")

            should_retry = handle_task_failure(
                conn,
                cur,
                task,
                task_key,
                error_msg,
                logger=logger,
                http_status=http_status,
            )
            if should_retry:
                # Status already committed as PENDING, raise for Prefect retry
                raise RetryablePaginatedExtractionError(error_msg) from e

            # Max attempts reached, return ERROR status
            return {
                "task_key": task_key,
                "status": "ERROR",
                "rows_inserted": 0,
                "next_page_created": False,
                "error": error_msg,
            }

        # Parse CSV rows
        try:
            rows = parse_csv_rows(csv_text, scope)
        except Exception as e:
            # Handle CSV parsing errors
            error_msg = f"CSV parsing error: {e}"
            logger.exception(f"Failed to parse CSV: {error_msg}")

            should_retry = handle_task_failure(
                conn, cur, task, task_key, error_msg, logger=logger
            )
            if should_retry:
                # Status already committed as PENDING, raise for Prefect retry
                raise RetryablePaginatedExtractionError(error_msg) from e

            # Max attempts reached, return ERROR status
            return {
                "task_key": task_key,
                "status": "ERROR",
                "rows_inserted": 0,
                "next_page_created": False,
                "error": error_msg,
            }

        # Insert rows into ifdata_raw
        rows_inserted = insert_raw_rows(conn, cur, rows, logger=logger)

        # Determine final status and create next page if needed
        # Pagination logic:
        # - If rows_inserted == page_top: Full page, might be more data → create next page
        # - If 0 < rows_inserted < page_top: Partial page, reached end → don't create next page
        # - If rows_inserted == 0: No data, reached end → mark as EMPTY
        if rows_inserted == 0:
            # Mark task as EMPTY (terminal state, end of pagination)
            update_task_status(conn, cur, task_key, "EMPTY", logger=logger)
            conn.commit()

            logger.info("Task completed with EMPTY status (end of pagination)")

            return {
                "task_key": task_key,
                "status": "EMPTY",
                "rows_inserted": 0,
                "next_page_created": False,
                "error": None,
            }
        elif rows_inserted == page_top:
            # Full page returned - might be more data, create next page
            update_task_status(conn, cur, task_key, "SUCCESS", logger=logger)
            next_page_created = create_next_page_task(
                conn, cur, task, page_top, logger=logger
            )
            conn.commit()

            logger.info(
                f"Task completed successfully: rows_inserted={rows_inserted}, "
                f"next_page_created={next_page_created} (full page, more data may exist)"
            )

            return {
                "task_key": task_key,
                "status": "SUCCESS",
                "rows_inserted": rows_inserted,
                "next_page_created": next_page_created,
                "error": None,
            }
        else:
            # Partial page returned (0 < rows_inserted < page_top) - reached end, don't create next page
            update_task_status(conn, cur, task_key, "SUCCESS", logger=logger)
            conn.commit()

            logger.info(
                f"Task completed successfully: rows_inserted={rows_inserted}, "
                f"next_page_created=False (partial page, end of data)"
            )

            return {
                "task_key": task_key,
                "status": "SUCCESS",
                "rows_inserted": rows_inserted,
                "next_page_created": False,
                "error": None,
            }

    except RetryablePaginatedExtractionError:
        # Status update already committed in handle_task_failure, no rollback needed
        # This exception is raised to trigger Prefect retry mechanism
        raise
    except psycopg2.Error:
        if conn:
            conn.rollback()
        logger.exception("Database error during paginated extraction")
        raise
    except Exception:
        if conn:
            conn.rollback()
        logger.exception("Unexpected error during paginated extraction")
        raise
    finally:
        # Clean up resources
        if conn:
            conn.close()
        if session:
            session.close()
