"""
Target Quarter Determination Task

Determines the target quarter (ano_mes) for BACEN IFData ingestion based on:
- Last extracted quarter from financial_data.ifdata_load_control
- Publication window rules (60 days for Q1-Q3, 90 days for Q4)
- Historical start date fallback (201603)
"""

from datetime import date, timedelta
import os

from prefect import get_run_logger, task
import psycopg2
from psycopg2.extensions import connection
from psycopg2.extras import RealDictCursor

from .constants import (
    ANO_MES_LENGTH,
    MAX_MONTH,
    MIN_MONTH,
    QUARTERLY_MONTH_DECEMBER,
    QUARTERLY_MONTH_JUNE,
    QUARTERLY_MONTH_MARCH,
    QUARTERLY_MONTH_SEPTEMBER,
    QUARTERLY_MONTHS,
)

# Configuration constants
HISTORICAL_START_ANO_MES = "201603"  # March 2016, first quarter available
PUBLICATION_WINDOW_DAYS_Q1_Q3 = 60  # March, June, September quarters
PUBLICATION_WINDOW_DAYS_Q4 = 90  # December quarter


def parse_ano_mes(ano_mes: str) -> tuple[int, int]:
    """
    Parse ano_mes string (YYYYMM format) into year and month.

    Args:
        ano_mes: String in YYYYMM format (e.g., "202403")

    Returns:
        Tuple of (year, month) as integers

    Raises:
        ValueError: If ano_mes format is invalid
    """
    if len(ano_mes) != ANO_MES_LENGTH or not ano_mes.isdigit():
        raise ValueError(f"Invalid ano_mes format: {ano_mes}. Expected YYYYMM format.")

    year = int(ano_mes[:4])
    month = int(ano_mes[4:6])

    if month < MIN_MONTH or month > MAX_MONTH:
        raise ValueError(f"Invalid month in ano_mes: {ano_mes}. Month must be 01-12.")

    return year, month


def compute_next_quarter(ano_mes: str) -> str:
    """
    Compute the next quarter after the given ano_mes.

    Quarters are: March (03), June (06), September (09), December (12).
    If the input is not a quarterly month, it will be treated as the previous quarter.

    Args:
        ano_mes: Current ano_mes in YYYYMM format

    Returns:
        Next quarter ano_mes in YYYYMM format

    Examples:
        compute_next_quarter("202403") -> "202406"  # March -> June
        compute_next_quarter("202406") -> "202409"  # June -> September
        compute_next_quarter("202409") -> "202412"  # September -> December
        compute_next_quarter("202412") -> "202503"  # December -> March (next year)
    """
    year, month = parse_ano_mes(ano_mes)

    # Find the current or previous quarterly month
    current_quarter_month = None
    for qm in QUARTERLY_MONTHS:
        if month >= qm:
            current_quarter_month = qm
        else:
            break

    # If month is before first quarter (March), use previous year's December
    if current_quarter_month is None:
        year -= 1
        current_quarter_month = 12

    # Find next quarterly month
    next_quarter_index = QUARTERLY_MONTHS.index(current_quarter_month) + 1

    if next_quarter_index < len(QUARTERLY_MONTHS):
        # Next quarter is in the same year
        next_month = QUARTERLY_MONTHS[next_quarter_index]
        next_year = year
    else:
        # Next quarter is in the next year (December -> March)
        next_month = QUARTERLY_MONTHS[0]  # March
        next_year = year + 1

    return f"{next_year:04d}{next_month:02d}"


def get_quarter_end_date(year: int, month: int) -> date:
    """
    Get the end date of the quarter for the given year and month.

    Args:
        year: Year
        month: Month (should be 3, 6, 9, or 12)

    Returns:
        Date object representing the last day of the quarter

    Examples:
        get_quarter_end_date(2024, 3) -> date(2024, 3, 31)   # Q1 ends March 31
        get_quarter_end_date(2024, 6) -> date(2024, 6, 30)   # Q2 ends June 30
        get_quarter_end_date(2024, 9) -> date(2024, 9, 30)  # Q3 ends September 30
        get_quarter_end_date(2024, 12) -> date(2024, 12, 31) # Q4 ends December 31
    """
    if month == QUARTERLY_MONTH_MARCH:
        return date(year, QUARTERLY_MONTH_MARCH, 31)
    elif month == QUARTERLY_MONTH_JUNE:
        return date(year, QUARTERLY_MONTH_JUNE, 30)
    elif month == QUARTERLY_MONTH_SEPTEMBER:
        return date(year, QUARTERLY_MONTH_SEPTEMBER, 30)
    elif month == QUARTERLY_MONTH_DECEMBER:
        return date(year, QUARTERLY_MONTH_DECEMBER, 31)
    else:
        raise ValueError(
            f"Invalid quarterly month: {month}. Must be {QUARTERLY_MONTH_MARCH}, "
            f"{QUARTERLY_MONTH_JUNE}, {QUARTERLY_MONTH_SEPTEMBER}, or {QUARTERLY_MONTH_DECEMBER}."
        )


def get_publication_deadline(ano_mes: str) -> date:
    """
    Get the publication deadline for the given quarter.

    Publication windows:
    - March, June, September quarters: 60 days after quarter end
    - December quarter: 90 days after quarter end

    Args:
        ano_mes: Quarter ano_mes in YYYYMM format

    Returns:
        Date object representing the publication deadline
    """
    year, month = parse_ano_mes(ano_mes)

    # Validate it's a quarterly month
    if month not in QUARTERLY_MONTHS:
        raise ValueError(
            f"Invalid quarterly month in ano_mes: {ano_mes}. Must be 03, 06, 09, or 12."
        )

    quarter_end = get_quarter_end_date(year, month)

    # December quarter has 90 days, others have 60 days
    days = (
        PUBLICATION_WINDOW_DAYS_Q4
        if month == QUARTERLY_MONTH_DECEMBER
        else PUBLICATION_WINDOW_DAYS_Q1_Q3
    )

    return quarter_end + timedelta(days=days)


def is_within_publication_window(ano_mes: str, today: date | None = None) -> bool:
    """
    Check if today is within the publication window for the given quarter.

    Args:
        ano_mes: Quarter ano_mes in YYYYMM format
        today: Date to check (defaults to today)

    Returns:
        True if today >= publication deadline, False otherwise
    """
    if today is None:
        today = date.today()

    deadline = get_publication_deadline(ano_mes)
    return today >= deadline


def get_db_connection() -> connection:
    """
    Get PostgreSQL database connection using environment variables.

    Environment variables:
        POSTGRES_HOST: Database host (default: localhost)
        POSTGRES_PORT: Database port (default: 6432 for pgbouncer)
        POSTGRES_DB: Database name (default: neotool_db)
        POSTGRES_USER: Database user (default: neotool)
        POSTGRES_PASSWORD: Database password (default: neotool)

    Returns:
        psycopg2 connection object

    Raises:
        psycopg2.OperationalError: If connection fails
    """
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = int(os.getenv("POSTGRES_PORT", "6432"))
    database = os.getenv("POSTGRES_DB", "neotool_db")
    user = os.getenv("POSTGRES_USER", "neotool")
    password = os.getenv("POSTGRES_PASSWORD", "neotool")

    return psycopg2.connect(
        host=host,
        port=port,
        database=database,
        user=user,
        password=password,
        connect_timeout=5,
    )


@task(
    name="determine_target_quarter",
    retries=2,
    retry_delay_seconds=30,
    timeout_seconds=60,
    log_prints=True,
    tags=["determine", "quarter", "control"],
)
def determine_target_quarter() -> str | None:
    """
    Determine the target quarter (ano_mes) for BACEN IFData ingestion.

    Logic:
    1. Read last processed quarter from financial_data.ifdata_load_control
       - Prioritizes completed > promoted > extracted to avoid skipping incomplete quarters
    2. If no records exist, fall back to historical start (201603)
    3. Compute next quarter candidate
    4. Check if today is within publication window for next candidate
    5. Return target_ano_mes if ready, None if should wait

    Returns:
        Target ano_mes (YYYYMM format) if ready for extraction, None if should wait

    Raises:
        psycopg2.Error: If database query fails
        ValueError: If data format is invalid
    """
    logger = get_run_logger()

    try:
        # Query database for last processed quarter
        # Processing priority: completed, then promoted, then extracted
        # This ensures we don't skip incomplete quarters
        conn = get_db_connection()
        try:
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(
                    """
                    SELECT COALESCE(
                        (SELECT MAX(ano_mes) FROM financial_data.ifdata_load_control WHERE completed = TRUE),
                        (SELECT MAX(ano_mes) FROM financial_data.ifdata_load_control WHERE promoted = TRUE),
                        (SELECT MAX(ano_mes) FROM financial_data.ifdata_load_control WHERE extracted = TRUE)
                    ) as last_ano_mes
                """
                )
                result = cur.fetchone()
                last_ano_mes = (
                    result["last_ano_mes"]
                    if result and result["last_ano_mes"]
                    else None
                )
        finally:
            conn.close()

        # Fall back to historical start if no records exist
        if last_ano_mes is None:
            logger.info(
                f"No processed quarters found. Using historical start: {HISTORICAL_START_ANO_MES}"
            )
            base_ano_mes = HISTORICAL_START_ANO_MES
        else:
            logger.info(f"Last processed quarter: {last_ano_mes}")
            base_ano_mes = last_ano_mes

        # Compute next quarter candidate
        next_quarter = compute_next_quarter(base_ano_mes)
        logger.info(f"Next quarter candidate: {next_quarter}")

        # Check publication window
        today = date.today()
        deadline = get_publication_deadline(next_quarter)
        is_ready = is_within_publication_window(next_quarter, today)

        logger.info(
            f"Publication window check for {next_quarter}: "
            f"deadline={deadline}, today={today}, ready={is_ready}"
        )

        if is_ready:
            logger.info(f"Target quarter determined: {next_quarter}")
            return next_quarter
        else:
            logger.info(
                f"Publication window not yet reached for {next_quarter}. "
                f"Deadline: {deadline}. Waiting..."
            )
            return None

    except psycopg2.Error:
        logger.exception("Database error while determining target quarter")
        raise
    except ValueError:
        logger.exception("Invalid data format while determining target quarter")
        raise
    except Exception:
        logger.exception("Unexpected error while determining target quarter")
        raise
