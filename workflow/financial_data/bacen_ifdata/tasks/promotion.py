"""
Promotion Task (Raw to Hot)

Promotes raw CSV data from ifdata_raw into normalized bank_report and bank_report_data tables.
Filters by active institutions (situacao='A') and marks ifdata_load_control as completed.
Converts Saldo field from string to numeric (Decimal) and preserves zero and null values.
"""

from collections import defaultdict
import csv
from decimal import Decimal, InvalidOperation
from io import StringIO
import json
import re
from typing import Any

from prefect import get_run_logger, task
import psycopg2

from .constants import ANO_MES_LENGTH, EMPTY_ROW_COUNT, MAX_FIELD_NUM
from .quarter_determination import get_db_connection


def fetch_raw_data_for_promotion(
    _conn, cur, ano_mes: str, nome_relatorio: str, logger
) -> list[dict[str, Any]]:
    """
    Fetch raw data rows for promotion, filtered by active institutions.

    Queries ifdata_raw for the given quarter+report, joining with institution
    table to filter only active institutions (situacao='A').

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        nome_relatorio: Report name
        logger: Logger instance for logging

    Returns:
        List of row dictionaries, each containing:
        - ano_mes: Quarter (CHAR(6))
        - tipo_instituicao: Institution type (INTEGER)
        - nome_relatorio: Report name (TEXT)
        - numero_relatorio: Report number (TEXT)
        - cod_inst: Institution code (TEXT)
        - raw_data: CSV row as string (TEXT)
    """
    cur.execute(
        """
        SELECT
            r.ano_mes,
            r.tipo_instituicao,
            r.nome_relatorio,
            r.numero_relatorio,
            r.cod_inst,
            r.raw_data
        FROM financial_data.ifdata_raw r
        INNER JOIN financial_data.institution i
            ON i.bacen_cod_inst = r.cod_inst
            AND i.situacao = 'A'
        WHERE r.ano_mes = %s
            AND r.nome_relatorio = %s
            AND r.cod_inst IS NOT NULL
        ORDER BY r.cod_inst, r.tipo_instituicao
    """,
        (ano_mes, nome_relatorio),
    )

    rows = []
    for row in cur.fetchall():
        rows.append(
            {
                "ano_mes": row[0],
                "tipo_instituicao": row[1],
                "nome_relatorio": row[2],
                "numero_relatorio": row[3],
                "cod_inst": row[4],
                "raw_data": row[5],
            }
        )

    logger.debug(
        f"Fetched {len(rows)} raw rows for promotion: "
        f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
    )

    return rows


def get_headers_from_first_row(
    _conn, _cur, _ano_mes: str, _nome_relatorio: str, _logger
) -> list[str]:
    """
    Get CSV headers by fetching the first raw row and inferring structure.

    Since raw_data doesn't store headers, we need to infer them. We can:
    1. Fetch the first row from ifdata_raw
    2. Try to parse it and infer common BACEN field patterns
    3. Or use a generic positional approach

    For now, we'll use a simpler approach: fetch headers from the extraction
    task's first response if available, or use positional keys.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        ano_mes: Target quarter in YYYYMM format
        nome_relatorio: Report name
        logger: Logger instance

    Returns:
        List of header names, or empty list if cannot be determined
    """
    # For now, return empty list - we'll use positional keys
    # In a future enhancement, we could store headers in extraction metadata
    # or fetch from the first API response
    return []


def parse_raw_csv_row(
    raw_data: str, headers: list[str] | None = None, logger=None
) -> dict[str, str]:
    """
    Parse a CSV row string from raw_data column into a dictionary.

    Handles comma delimiter and quoted fields. The raw_data column contains
    a single CSV row (without header) that was stored during extraction.

    Since headers are not stored with raw_data, we use positional keys
    or provided headers if available.

    Args:
        raw_data: CSV row as string (comma-delimited, may have quoted fields)
        headers: Optional list of header names (if known)
        logger: Optional logger for logging warnings

    Returns:
        Dictionary with field names as keys and values as strings.
        If headers provided, uses header names; otherwise uses positional keys.
    """
    if not raw_data or not raw_data.strip():
        return {}

    try:
        reader = csv.reader(
            StringIO(raw_data), delimiter=",", quoting=csv.QUOTE_MINIMAL
        )
        row_values = next(reader, None)

        if not row_values:
            return {}

        result = {}

        if headers and len(headers) > 0:
            # Use provided headers
            for idx, header in enumerate(headers):
                if idx < len(row_values):
                    result[header] = row_values[idx]
                else:
                    result[header] = ""
        else:
            # Use positional keys
            for idx, value in enumerate(row_values):
                result[f"field_{idx}"] = value

        return result

    except Exception as e:
        if logger:
            logger.warning(f"Failed to parse CSV row: {e}. Raw data: {raw_data[:100]}")
        return {}


def convert_saldo_to_numeric(saldo_value: str | None) -> Decimal | None:
    """
    Convert Saldo string to numeric, handling zero and null.
    
    Conversion rules:
    - If saldo_value is None, "null", or "" → return None (SQL NULL)
    - If saldo_value is "0" or "0.0" → return Decimal("0") (numeric zero)
    - Otherwise → convert to Decimal (handles currency and percentage)
    - Handles Brazilian format (comma as decimal separator) by converting to dot format
    
    Args:
        saldo_value: Saldo value as string or None
        
    Returns:
        Decimal value or None
    """
    if saldo_value is None or saldo_value in {"null", ""}:
        return None
    if saldo_value == "0" or saldo_value == "0.0":
        return Decimal("0")
    try:
        # Normalize the value: remove quotes and handle Brazilian decimal format
        normalized_value = saldo_value.strip()
        
        # Remove quotes if present (from CSV quoted fields like "590878586,18")
        if normalized_value.startswith('"') and normalized_value.endswith('"'):
            normalized_value = normalized_value[1:-1]
        
        # Handle Brazilian format: comma as decimal separator
        # Pattern: digits followed by comma and 1-2 digits (decimal part)
        # Example: "590878586,18" -> "590878586.18"
        import re
        # Match pattern: one or more digits, comma, 1-2 digits at end
        brazilian_decimal_pattern = re.compile(r'^(\d+),(\d{1,2})$')
        match = brazilian_decimal_pattern.match(normalized_value)
        if match:
            # Brazilian format: replace comma with dot
            normalized_value = f"{match.group(1)}.{match.group(2)}"
        elif ',' in normalized_value:
            # Comma exists but doesn't match Brazilian pattern
            # Could be thousands separator - remove all commas
            normalized_value = normalized_value.replace(',', '')
        
        return Decimal(normalized_value)
    except (ValueError, InvalidOperation):
        # Invalid numeric value, preserve as None
        return None


def map_csv_fields_to_names(parsed_row: dict[str, str]) -> dict[str, Any]:
    """
    Map positional CSV fields to named fields based on BACEN IFData structure.

    Common structure (may vary by report):
    - field_0: TipoInstituicao (redundant - already in table column)
    - field_1: CodInst (redundant - already in table column)
    - field_2: AnoMes (redundant - already in table column)
    - field_3: NomeRelatorio (redundant - already in table column)
    - field_4: NumeroRelatorio (redundant - already in table column)
    - field_5: Grupo
    - field_6: Conta
    - field_7: NomeColuna
    - field_8: DescricaoColuna
    - field_9: Saldo

    Args:
        parsed_row: Dictionary with positional field keys (field_0, field_1, etc.)

    Returns:
        Dictionary with mapped field names (excluding redundant natural key fields).
        Saldo is converted to Decimal or None (not string).
    """
    mapped: dict[str, Any] = {}

    # Map CSV-specific fields (exclude natural key fields that are already in table columns)
    field_mapping = {
        "field_5": "Grupo",
        "field_6": "Conta",
        "field_7": "NomeColuna",
        "field_8": "DescricaoColuna",
        "field_9": "Saldo",
    }

    # Map known fields
    for field_key, field_name in field_mapping.items():
        if field_key in parsed_row:
            value = parsed_row[field_key]
            # Convert "null" string to None for text fields
            if value in {"null", ""}:
                mapped[field_name] = None
            elif field_name == "Saldo":
                # Convert Saldo to numeric
                mapped[field_name] = convert_saldo_to_numeric(value)
            else:
                mapped[field_name] = value

    # Include any unmapped fields beyond field_9 as-is (for flexibility with additional fields)
    for key, value in parsed_row.items():
        if key not in field_mapping:
            # Check if it's a field beyond field_9
            if key.startswith("field_"):
                try:
                    field_num = int(key.split("_")[1])
                    if field_num > MAX_FIELD_NUM:
                        mapped[key] = value
                except (ValueError, IndexError):
                    # Not a valid field_N format, include it
                    mapped[key] = value
            else:
                # Not a positional field, include it
                mapped[key] = value

    return mapped


def aggregate_rows_to_structured_data(
    rows: list[dict[str, Any]], headers: list[str] | None = None, logger=None
) -> dict[tuple, list[dict[str, Any]]]:
    """
    Aggregate raw rows into structured data grouped by natural key.

    Groups rows by (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
    and creates a list of row objects where each object represents one CSV row for
    insertion into bank_report_data table.

    Natural key fields (TipoInstituicao, CodInst, AnoMes, NomeRelatorio, NumeroRelatorio)
    are excluded since they're already stored as table columns in bank_report.

    For each group:
    - Parse each raw_data CSV row into a dictionary
    - Map positional fields to named fields
    - Convert Saldo to numeric (Decimal or None)
    - Create a list of row objects with only CSV-specific fields
    - Preserve all rows including those with zero or null Saldo values

    Args:
        rows: List of raw row dictionaries from fetch_raw_data_for_promotion
        headers: Optional list of CSV header names (if known)
        logger: Optional logger for logging

    Returns:
        Dictionary mapping (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
        to list of row objects (as Python list of dicts). Each dict has keys:
        - grupo, conta, nome_coluna, descricao_coluna, saldo (Decimal or None)
    """
    # Group rows by natural key
    grouped = defaultdict(list)

    for row in rows:
        key = (
            row["cod_inst"],
            row["ano_mes"],
            row["nome_relatorio"],
            row["numero_relatorio"],
            row["tipo_instituicao"],
        )
        grouped[key].append(row["raw_data"])

    # Aggregate each group into list of row objects
    result = {}

    for key, raw_data_list in grouped.items():
        # Parse all CSV rows for this group and create row objects
        row_objects = []

        for raw_data in raw_data_list:
            # Parse CSV row
            parsed = parse_raw_csv_row(raw_data, headers, logger)
            if not parsed:
                continue

            # Map CSV fields to named fields (excludes redundant natural key fields)
            # Saldo is already converted to Decimal/None in map_csv_fields_to_names
            csv_fields = map_csv_fields_to_names(parsed)

            # Only include CSV-specific fields (natural key fields are in table columns)
            # Preserve all rows, including those with zero or null Saldo
            if csv_fields:
                row_objects.append(csv_fields)

        # Include group even if empty (for consistency)
        result[key] = row_objects

    if logger:
        total_rows = sum(len(v) for v in result.values())
        logger.debug(
            f"Aggregated {len(rows)} raw rows into {len(result)} report groups "
            f"({total_rows} total data rows)"
        )

    return result


def upsert_bank_report(
    _conn, cur, structured_data: dict[tuple, list[dict[str, Any]]], logger
) -> int:
    """
    UPSERT bank_report and bank_report_data tables (two-step process).

    Step 1: UPSERT bank_report table using natural key.
    Step 2: DELETE existing bank_report_data rows and INSERT new ones.

    Uses INSERT ... ON CONFLICT UPDATE for idempotency.
    Preserves created_at on first insert.

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        structured_data: Dictionary mapping (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
                         to list of row objects (as Python list of dicts). Each dict has:
                         - grupo, conta, nome_coluna, descricao_coluna, saldo (Decimal or None)
        logger: Logger instance for logging

    Returns:
        Number of bank_report rows upserted
    """
    if not structured_data:
        return 0

    # Step 1: UPSERT bank_report and get the id
    upsert_report_sql = """
        INSERT INTO financial_data.bank_report
            (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao, updated_at)
        VALUES
            (%s, %s, %s, %s, %s, now())
        ON CONFLICT (cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao)
        DO UPDATE SET
            updated_at = now()
        RETURNING id
    """

    # Step 2: DELETE existing data rows (for idempotency)
    delete_data_sql = """
        DELETE FROM financial_data.bank_report_data
        WHERE bank_report_id = %s
    """

    # Step 3: INSERT new data rows
    insert_data_sql = """
        INSERT INTO financial_data.bank_report_data
            (bank_report_id, grupo, conta, nome_coluna, descricao_coluna, saldo)
        VALUES
            (%s, %s, %s, %s, %s, %s)
    """

    upserted_count = 0
    total_data_rows = 0

    for key, data_rows in structured_data.items():
        cod_inst, ano_mes, nome_relatorio, numero_relatorio, tipo_instituicao = key

        # Step 1: UPSERT bank_report and get id
        cur.execute(
            upsert_report_sql,
            (
                cod_inst,
                ano_mes,
                nome_relatorio,
                numero_relatorio,
                tipo_instituicao,
            ),
        )
        result = cur.fetchone()
        if result:
            bank_report_id = result[0]
            upserted_count += 1

            # Step 2: DELETE existing data rows (idempotency)
            cur.execute(delete_data_sql, (bank_report_id,))

            # Step 3: INSERT new data rows
            for row in data_rows:
                cur.execute(
                    insert_data_sql,
                    (
                        bank_report_id,
                        row.get("Grupo"),
                        row.get("Conta"),
                        row.get("NomeColuna"),
                        row.get("DescricaoColuna"),
                        row.get("Saldo"),  # Already Decimal or None
                    ),
                )
                total_data_rows += 1

    if logger:
        logger.info(
            f"UPSERTed {upserted_count} rows into bank_report, "
            f"inserted {total_data_rows} rows into bank_report_data"
        )

    return upserted_count


def mark_load_control_completed(
    _conn, cur, ano_mes: str, nome_relatorio: str, logger
) -> bool:
    """
    Mark ifdata_load_control as promoted and completed.

    Sets promoted=TRUE, completed=TRUE and corresponding timestamps.
    Uses idempotent update (preserves timestamps on subsequent calls).

    Args:
        conn: PostgreSQL connection object
        cur: PostgreSQL cursor object
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        nome_relatorio: Report name
        logger: Logger instance for logging

    Returns:
        True if record was updated, False otherwise
    """
    cur.execute(
        """
        UPDATE financial_data.ifdata_load_control
        SET
            promoted = TRUE,
            promoted_at = COALESCE(promoted_at, now()),
            completed = TRUE,
            completed_at = COALESCE(completed_at, now()),
            updated_at = now()
        WHERE ano_mes = %s AND nome_relatorio = %s
    """,
        (ano_mes, nome_relatorio),
    )

    was_updated = cur.rowcount > 0

    if was_updated:
        logger.info(
            f"Marked ifdata_load_control as promoted and completed: "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )
    else:
        logger.warning(
            f"No rows updated in ifdata_load_control: "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )

    return was_updated


@task(
    name="promote_raw_to_hot",
    retries=2,  # Transform: ≥2 attempts per batch workflow standard
    retry_delay_seconds=30,  # 30-60 sec backoff
    timeout_seconds=120,
    log_prints=True,
    tags=["promote", "transform"],
)
def promote_raw_to_hot(ano_mes: str, nome_relatorio: str) -> dict[str, Any]:
    """
    Promote raw CSV data from ifdata_raw to normalized bank_report and bank_report_data tables.

    This task:
    1. Fetches raw data from ifdata_raw for the given quarter+report
    2. Filters by active institutions (situacao='A')
    3. Parses CSV rows from raw_data column
    4. Aggregates rows into structured data grouped by natural key
    5. UPSERTs bank_report and bank_report_data tables (normalized structure)
    6. Marks ifdata_load_control as promoted and completed

    This task is idempotent - can be called multiple times safely. UPSERT
    operations ensure no duplicates, and load control marking preserves timestamps.

    Args:
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        nome_relatorio: Report name

    Returns:
        Dictionary with promotion statistics:
        - rows_fetched: Number of raw rows fetched
        - documents_created: Number of report groups created
        - rows_upserted: Number of rows upserted into bank_report
        - marked_completed: Boolean indicating if load control was marked

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
        f"Starting promotion for ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
    )

    # Get database connection
    conn = None
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            # Step 1: Fetch raw data with active institution filter
            raw_rows = fetch_raw_data_for_promotion(
                conn, cur, ano_mes, nome_relatorio, logger
            )

            rows_fetched = len(raw_rows)

            if rows_fetched == EMPTY_ROW_COUNT:
                logger.warning(
                    f"No raw data found for promotion: "
                    f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
                )
                # Still mark as completed if no data (empty report)
                marked_completed = mark_load_control_completed(
                    conn, cur, ano_mes, nome_relatorio, logger
                )
                return {
                    "rows_fetched": 0,
                    "documents_created": 0,
                    "rows_upserted": 0,
                    "marked_completed": marked_completed,
                }

            # Step 2: Try to get headers (optional - for better field names)
            # For now, we'll use positional keys since headers aren't stored
            headers = get_headers_from_first_row(
                conn, cur, ano_mes, nome_relatorio, logger
            )

            # Step 3: Aggregate rows into structured data
            structured_data = aggregate_rows_to_structured_data(raw_rows, headers, logger)
            documents_created = len(structured_data)

            # Step 4: UPSERT into bank_report and bank_report_data
            rows_upserted = upsert_bank_report(conn, cur, structured_data, logger)

            # Step 5: Mark load control as completed
            marked_completed = mark_load_control_completed(
                conn, cur, ano_mes, nome_relatorio, logger
            )

        # Commit transaction
        conn.commit()

        result = {
            "rows_fetched": rows_fetched,
            "documents_created": documents_created,
            "rows_upserted": rows_upserted,
            "marked_completed": marked_completed,
        }

        logger.info(
            f"Promotion complete for ano_mes={ano_mes}, nome_relatorio={nome_relatorio}: "
            f"rows_fetched={rows_fetched}, documents_created={documents_created}, "
            f"rows_upserted={rows_upserted}, marked_completed={marked_completed}"
        )

        return result

    except psycopg2.Error:
        if conn:
            conn.rollback()
        logger.exception(
            f"Database error during promotion. "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )
        raise
    except Exception:
        if conn:
            conn.rollback()
        logger.exception(
            f"Unexpected error during promotion. "
            f"ano_mes={ano_mes}, nome_relatorio={nome_relatorio}"
        )
        raise
    finally:
        # Always close connection
        if conn:
            conn.close()
