"""
Extract Task Creation

Creates initial extract tasks for BACEN IFData ingestion.
For each report and TipoInstituicao (1-4), creates an ifdata_extract_task
record with page_skip=0 to kick off paginated extraction.
"""

from prefect import get_run_logger, task
import psycopg2

from .constants import ANO_MES_LENGTH
from .quarter_determination import get_db_connection

# Configuration constants
TIPO_INSTITUICAO_MIN = 1
TIPO_INSTITUICAO_MAX = 4
DEFAULT_PAGE_TOP = 5000
DEFAULT_PAGE_SKIP = 0
DEFAULT_STATUS = "PENDING"


def create_extract_tasks_batch(
    _conn, cur, ano_mes: str, reports: list[dict[str, str]], logger
) -> dict[str, int]:
    """
    Create extract tasks in batch for a given quarter and list of reports.

    For each report and each TipoInstituicao (1-4), creates an ifdata_extract_task
    record with page_skip=0. Uses INSERT ... ON CONFLICT DO NOTHING for idempotency.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        reports: List of report dictionaries with nome_relatorio and numero_relatorio
        logger: Logger instance for logging

    Returns:
        Dictionary with statistics:
        - created: Number of new tasks created
        - existing: Number of tasks that already existed
        - total: Total number of tasks that should exist
    """
    stats = {"created": 0, "existing": 0, "total": 0}

    # SQL for inserting tasks with idempotency
    # ON CONFLICT DO NOTHING ensures idempotency - if task exists, no error is raised
    # rowcount will be 0 if conflict occurred (task already exists), 1 if inserted
    insert_sql = """
        INSERT INTO financial_data.ifdata_extract_task
            (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip, page_top, status)
        VALUES
            (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio, page_skip)
        DO NOTHING
    """

    # Process each report and tipo_instituicao combination
    for report in reports:
        nome_relatorio = report.get("nome_relatorio")
        numero_relatorio = report.get("numero_relatorio")

        # Validate required fields
        if not nome_relatorio or not numero_relatorio:
            logger.warning(
                f"Skipping report with missing fields: {report}. "
                "Required: nome_relatorio, numero_relatorio"
            )
            continue

        # Create tasks for each TipoInstituicao (1-4)
        for tipo_instituicao in range(TIPO_INSTITUICAO_MIN, TIPO_INSTITUICAO_MAX + 1):
            stats["total"] += 1

            # Insert task (idempotent via ON CONFLICT DO NOTHING)
            cur.execute(
                insert_sql,
                (
                    ano_mes,
                    tipo_instituicao,
                    nome_relatorio,
                    numero_relatorio,
                    DEFAULT_PAGE_SKIP,
                    DEFAULT_PAGE_TOP,
                    DEFAULT_STATUS,
                ),
            )

            # Check if row was actually inserted
            # rowcount = 1 means row was inserted, rowcount = 0 means conflict (already exists)
            if cur.rowcount > 0:
                stats["created"] += 1
                logger.debug(
                    f"Created task: ano_mes={ano_mes}, "
                    f"tipo_instituicao={tipo_instituicao}, "
                    f"nome_relatorio={nome_relatorio}, "
                    f"numero_relatorio={numero_relatorio}, "
                    f"page_skip={DEFAULT_PAGE_SKIP}"
                )
            else:
                stats["existing"] += 1
                logger.debug(
                    f"Task already exists: ano_mes={ano_mes}, "
                    f"tipo_instituicao={tipo_instituicao}, "
                    f"nome_relatorio={nome_relatorio}, "
                    f"numero_relatorio={numero_relatorio}, "
                    f"page_skip={DEFAULT_PAGE_SKIP}"
                )

    return stats


@task(
    name="create_extract_tasks",
    retries=2,
    retry_delay_seconds=30,
    timeout_seconds=120,
    log_prints=True,
    tags=["extract", "task-creation"],
)
def create_extract_tasks(ano_mes: str, reports: list[dict[str, str]]) -> dict[str, int]:
    """
    Create extract tasks for a given quarter and list of reports.

    For each report and each TipoInstituicao (1-4), creates an ifdata_extract_task
    record with page_skip=0. Uses INSERT ... ON CONFLICT DO NOTHING for idempotency.

    This task seeds the extraction queue with initial tasks that will be picked up
    by the paginated extraction task (workflow-006).

    Args:
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        reports: List of report dictionaries with nome_relatorio and numero_relatorio
                 (typically from fetch_report_catalog() task)

    Returns:
        Dictionary with statistics:
        - created: Number of new tasks created
        - existing: Number of tasks that already existed
        - total: Total number of tasks that should exist (reports * 4 tipos)

    Raises:
        psycopg2.Error: If database operation fails
        ValueError: If ano_mes format is invalid or reports list is empty
    """
    logger = get_run_logger()

    # Validate inputs
    if not ano_mes or len(ano_mes) != ANO_MES_LENGTH or not ano_mes.isdigit():
        raise ValueError(
            f"Invalid ano_mes format: {ano_mes}. Expected YYYYMM format (e.g., '202403')"
        )

    if not reports:
        logger.warning("Empty reports list provided - no tasks to create")
        return {"created": 0, "existing": 0, "total": 0}

    logger.info(
        f"Creating extract tasks for ano_mes={ano_mes}, "
        f"reports={len(reports)}, "
        f"expected_tasks={len(reports) * (TIPO_INSTITUICAO_MAX - TIPO_INSTITUICAO_MIN + 1)}"
    )

    # Get database connection
    conn = None
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            # Create tasks in batch
            stats = create_extract_tasks_batch(conn, cur, ano_mes, reports, logger)

        # Commit transaction
        conn.commit()

        logger.info(
            f"Extract task creation complete: "
            f"created={stats['created']}, "
            f"existing={stats['existing']}, "
            f"total={stats['total']}"
        )

        return stats

    except psycopg2.Error:
        if conn:
            conn.rollback()
        logger.exception(
            f"Database error while creating extract tasks. "
            f"ano_mes={ano_mes}, reports_count={len(reports)}"
        )
        raise
    except Exception:
        if conn:
            conn.rollback()
        logger.exception(
            f"Unexpected error while creating extract tasks. "
            f"ano_mes={ano_mes}, reports_count={len(reports)}"
        )
        raise
    finally:
        # Always close connection
        if conn:
            conn.close()
