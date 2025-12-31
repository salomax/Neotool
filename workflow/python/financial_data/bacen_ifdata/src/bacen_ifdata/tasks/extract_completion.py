"""
Extract Completion Check Task

Checks if all extract tasks for a quarter+report are terminal (SUCCESS or EMPTY)
and marks ifdata_load_control as extracted when complete.
"""

from typing import Any

from prefect import get_run_logger, task
import psycopg2

from .constants import ANO_MES_LENGTH
from .quarter_determination import get_db_connection

# Terminal statuses: these indicate the task is complete (not an error)
TERMINAL_STATUSES = ("SUCCESS", "EMPTY")


def check_extract_completion(
    _conn, cur, ano_mes: str, nome_relatorio: str, logger
) -> dict[str, Any]:
    """
    Check if all extract tasks for a quarter+report are terminal.

    Queries all ifdata_extract_task records for the given ano_mes and nome_relatorio,
    and checks if all tasks have terminal status (SUCCESS or EMPTY).

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        nome_relatorio: Report name
        logger: Logger instance for logging

    Returns:
        Dictionary with completion status:
        - is_complete: Boolean indicating if all tasks are terminal
        - total_tasks: Total number of tasks for this quarter+report
        - terminal_tasks: Number of tasks with terminal status
        - non_terminal_tasks: Number of tasks with non-terminal status (PENDING, RUNNING, ERROR)
        - status_breakdown: Dictionary with counts per status
    """
    # Query all tasks for this quarter+report
    cur.execute(
        """
        SELECT
            status,
            COUNT(*) as count
        FROM financial_data.ifdata_extract_task
        WHERE ano_mes = %s AND nome_relatorio = %s
        GROUP BY status
    """,
        (ano_mes, nome_relatorio),
    )

    status_counts = {}
    total_tasks = 0
    terminal_tasks = 0
    non_terminal_tasks = 0

    for row in cur.fetchall():
        status = row[0]
        count = row[1]
        status_counts[status] = count
        total_tasks += count

        if status in TERMINAL_STATUSES:
            terminal_tasks += count
        else:
            non_terminal_tasks += count

    is_complete = total_tasks > 0 and non_terminal_tasks == 0

    result = {
        "is_complete": is_complete,
        "total_tasks": total_tasks,
        "terminal_tasks": terminal_tasks,
        "non_terminal_tasks": non_terminal_tasks,
        "status_breakdown": status_counts,
    }

    logger.debug(
        f"Extract completion check for ano_mes={ano_mes}, nome_relatorio={nome_relatorio}: "
        f"is_complete={is_complete}, total={total_tasks}, "
        f"terminal={terminal_tasks}, non_terminal={non_terminal_tasks}, "
        f"status_breakdown={status_counts}"
    )

    return result


def mark_load_control_extracted(
    _conn, cur, ano_mes: str, nome_relatorio: str, logger
) -> bool:
    """
    Mark ifdata_load_control as extracted for a quarter+report.

    Uses INSERT ... ON CONFLICT UPDATE to handle both new and existing records.
    Sets extracted=TRUE and extracted_at=now().

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        nome_relatorio: Report name
        logger: Logger instance for logging

    Returns:
        True if record was created/updated, False otherwise
    """
    # Use INSERT ... ON CONFLICT UPDATE to handle both new and existing records
    # This ensures idempotency - can be called multiple times safely
    cur.execute(
        """
        INSERT INTO financial_data.ifdata_load_control
            (ano_mes, nome_relatorio, extracted, extracted_at)
        VALUES
            (%s, %s, TRUE, now())
        ON CONFLICT (ano_mes, nome_relatorio)
        DO UPDATE SET
            extracted = TRUE,
            extracted_at = CASE
                WHEN ifdata_load_control.extracted = FALSE THEN now()
                ELSE ifdata_load_control.extracted_at
            END,
            updated_at = now()
    """,
        (ano_mes, nome_relatorio),
    )

    was_updated = cur.rowcount > 0

    if was_updated:
        logger.info(
            f"Marked ifdata_load_control as extracted: "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )
    else:
        logger.warning(
            f"No rows updated in ifdata_load_control: "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )

    return was_updated


@task(
    name="check_extract_completion",
    retries=2,
    retry_delay_seconds=30,
    timeout_seconds=120,
    log_prints=True,
    tags=["extract", "completion", "control"],
)
def check_extract_completion_task(ano_mes: str, nome_relatorio: str) -> dict[str, Any]:
    """
    Check if all extract tasks for a quarter+report are terminal and mark as extracted.

    This task:
    1. Checks if all ifdata_extract_task records for the given quarter+report
       have terminal status (SUCCESS or EMPTY)
    2. If all tasks are terminal, marks ifdata_load_control as extracted
    3. Returns completion status and statistics

    This task is idempotent - can be called multiple times safely. If all tasks
    are already terminal, it will mark ifdata_load_control as extracted (if not
    already marked) without side effects.

    Args:
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        nome_relatorio: Report name

    Returns:
        Dictionary with completion status:
        - is_complete: Boolean indicating if all tasks are terminal
        - total_tasks: Total number of tasks for this quarter+report
        - terminal_tasks: Number of tasks with terminal status
        - non_terminal_tasks: Number of tasks with non-terminal status
        - status_breakdown: Dictionary with counts per status
        - marked_extracted: Boolean indicating if ifdata_load_control was marked as extracted

    Raises:
        psycopg2.Error: If database operation fails
        ValueError: If ano_mes format is invalid or nome_relatorio is empty
    """
    logger = get_run_logger()

    # Validate inputs
    if not ano_mes or len(ano_mes) != ANO_MES_LENGTH or not ano_mes.isdigit():
        raise ValueError(
            f"Invalid ano_mes format: {ano_mes}. Expected YYYYMM format (e.g., '202403')"
        )

    if not nome_relatorio or not nome_relatorio.strip():
        raise ValueError("nome_relatorio cannot be empty")

    logger.info(
        f"Checking extract completion for ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
    )

    # Get database connection
    conn = None
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            # Check completion status
            completion_status = check_extract_completion(
                conn, cur, ano_mes, nome_relatorio, logger
            )

            # If all tasks are terminal, mark as extracted
            marked_extracted = False
            if completion_status["is_complete"]:
                marked_extracted = mark_load_control_extracted(
                    conn, cur, ano_mes, nome_relatorio, logger
                )
            else:
                logger.info(
                    f"Extract not complete for ano_mes={ano_mes}, nome_relatorio={nome_relatorio}: "
                    f"{completion_status['non_terminal_tasks']} non-terminal tasks remaining"
                )

        # Commit transaction
        conn.commit()

        # Add marked_extracted to result
        result = {
            **completion_status,
            "marked_extracted": marked_extracted,
        }

        logger.info(
            f"Extract completion check complete for ano_mes={ano_mes}, nome_relatorio={nome_relatorio}: "
            f"is_complete={result['is_complete']}, marked_extracted={marked_extracted}"
        )

        return result

    except psycopg2.Error:
        if conn:
            conn.rollback()
        logger.exception(
            f"Database error while checking extract completion. "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )
        raise
    except Exception:
        if conn:
            conn.rollback()
        logger.exception(
            f"Unexpected error while checking extract completion. "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )
        raise
    finally:
        # Always close connection
        if conn:
            conn.close()
