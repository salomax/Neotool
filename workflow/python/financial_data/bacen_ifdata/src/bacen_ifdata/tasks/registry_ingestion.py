"""
Registry Ingestion Task

Fetches institution registry data from BACEN API (IfDataCadastro endpoint),
parses CSV data, computes source_hash for change detection, and upserts into
financial_data.institution table. Returns list of institutions that need Kafka events.
"""

import csv
import hashlib
from io import StringIO
import json
import os
from typing import Any
from urllib.parse import quote

from urllib3.util.retry import Retry

from prefect import get_run_logger, task
import psycopg2
from psycopg2.extras import RealDictCursor
import requests
from requests.adapters import HTTPAdapter

from .constants import ANO_MES_LENGTH, BACEN_BASE_URL

# Configuration constants
REGISTRY_ENDPOINT = "IfDataCadastro(AnoMes=@AnoMes)"
PAGE_SIZE = 1000
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

# Retry configuration for BACEN API requests
RETRY_STRATEGY = Retry(
    total=5,  # Total number of retries
    backoff_factor=1,  # Wait 1s, 2s, 4s, 8s, 16s between retries
    status_forcelist=[429, 500, 502, 503, 504],  # Retry on these HTTP status codes
    allowed_methods=["GET"],  # Only retry GET requests
    raise_on_status=False,  # Don't raise exception on status codes, let raise_for_status handle it
)

# BACEN API column name mapping to internal field names
# Note: Actual BACEN column names may vary; this is a reasonable mapping
BACEN_COLUMN_MAPPING = {
    "CodInst": "bacen_cod_inst",
    "NomeInstituicao": "nome_instituicao",
    "Situacao": "situacao",
    "SegmentoTB": "segmento_tb",
    "Atividade": "atividade",
    "TCB": "tipo_consolidado_bancario",  # Tipo de Consolidado Bancário
    "TD": "tipo_consolidacao",  # Tipo de Consolidação
    "TC": "tipo_controle",  # Tipo de Controle
    "UF": "uf",
    "Municipio": "municipio",
    "CodConglomeradoFinanceiro": "cod_conglomerado_financeiro",
    "CodConglomeradoPrudencial": "cod_conglomerado_prudencial",
    "CNPJInstituicaoLider": "cnpj_instituicao_lider",
}

# Fields used for hash computation (must match memory file)
HASH_FIELDS = [
    "bacen_cod_inst",
    "nome_instituicao",
    "situacao",
    "segmento_tb",
    "atividade",
    "tipo_consolidado_bancario",
    "tipo_consolidacao",
    "tipo_controle",
    "uf",
    "municipio",
    "cod_conglomerado_financeiro",
    "cod_conglomerado_prudencial",
    "cnpj_instituicao_lider",
]


def get_bacen_session() -> requests.Session:
    """
    Get a requests.Session configured with retry/backoff for BACEN API requests.

    The session includes:
    - Retry strategy with exponential backoff
    - User-Agent header
    - HTTP adapter with retry configuration

    Returns:
        Configured requests.Session object
    """
    session = requests.Session()

    # Set User-Agent header
    session.headers.update(
        {
            "User-Agent": USER_AGENT,
        }
    )

    # Configure retry adapter
    adapter = HTTPAdapter(max_retries=RETRY_STRATEGY)
    session.mount("http://", adapter)
    session.mount("https://", adapter)

    return session


def get_db_connection():
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


def build_bacen_query_string(params: dict[str, Any]) -> str:
    """
    Build query string for BACEN API with proper encoding.

    BACEN API requires OData parameters ($filter, $top, etc.) and @AnoMes
    to NOT be URL-encoded in parameter names, but values should be encoded.

    The filter value should have spaces encoded as %20 and preserve single quotes.

    Args:
        params: Dictionary of query parameters

    Returns:
        Query string with proper encoding
    """
    # Manually build query string to preserve $ and @ in parameter names
    query_parts = []
    for key, value in params.items():
        # Encode the value, but keep the parameter name as-is
        # Use quote with safe="'" to preserve single quotes (needed for filter values)
        # This encodes spaces as %20 (not +) which is what BACEN expects
        encoded_value = quote(str(value), safe="'")
        query_parts.append(f"{key}={encoded_value}")

    return "&".join(query_parts)


def fetch_registry_csv(ano_mes: str) -> str:
    """
    Fetch institution registry CSV from BACEN API with pagination support.

    Fetches all pages of results by following OData pagination ($skip parameter)
    until no more results are available. Combines all pages into a single CSV string.

    Uses a requests.Session with retry/backoff to handle transient network issues
    and BACEN API hiccups.

    Args:
        ano_mes: Target quarter in YYYYMM format

    Returns:
        Raw CSV content as string (header row + all data rows from all pages)

    Raises:
        requests.RequestException: If HTTP request fails after retries
        ValueError: If ano_mes format is invalid
    """
    # Validate ano_mes format
    if len(ano_mes) != ANO_MES_LENGTH or not ano_mes.isdigit():
        raise ValueError(f"Invalid ano_mes format: {ano_mes}. Expected YYYYMM format.")

    # Get session with retry configuration
    session = get_bacen_session()

    try:
        # Build base URL
        url = f"{BACEN_BASE_URL}{REGISTRY_ENDPOINT}"

        # Build query string manually to preserve $ and @ characters in parameter names
        # BACEN API requires these to NOT be URL-encoded
        base_params = {
            "@AnoMes": ano_mes,
            "$filter": "Situacao eq 'A'",  # Only active institutions
            "$format": "text/csv",
        }

        # Fetch first page to get header row
        first_page_params = {
            **base_params,
            "$top": PAGE_SIZE,
            "$skip": 0,
        }

        # Build query string manually and append to URL
        query_string = build_bacen_query_string(first_page_params)
        full_url = f"{url}?{query_string}"

        response = session.get(full_url, timeout=30)
        response.raise_for_status()

        first_page_csv = response.text.strip()

        # If first page is empty, return empty CSV
        if not first_page_csv:
            return ""

        # Split first page into lines to extract header
        lines = first_page_csv.splitlines()
        if not lines:
            return ""

        header_line = lines[0]
        data_lines = lines[1:] if len(lines) > 1 else []

        # If first page has fewer than PAGE_SIZE rows, we're done
        if len(data_lines) < PAGE_SIZE:
            return first_page_csv

        # Continue fetching subsequent pages
        all_data_lines = data_lines.copy()
        skip = PAGE_SIZE

        while True:
            # Fetch next page
            page_params = {
                **base_params,
                "$top": PAGE_SIZE,
                "$skip": skip,
            }

            # Build query string manually and append to URL
            query_string = build_bacen_query_string(page_params)
            full_url = f"{url}?{query_string}"

            response = session.get(full_url, timeout=30)
            response.raise_for_status()

            page_csv = response.text.strip()

            # If page is empty, we've reached the end
            if not page_csv:
                break

            # Split page into lines (skip header, get data rows)
            page_lines = page_csv.splitlines()
            if len(page_lines) <= 1:
                # Only header or empty, we're done
                break

            page_data_lines = page_lines[1:]  # Skip header row
            all_data_lines.extend(page_data_lines)

            # If we got fewer than PAGE_SIZE rows, we've reached the end
            if len(page_data_lines) < PAGE_SIZE:
                break

            # Move to next page
            skip += PAGE_SIZE

        # Combine header with all data rows
        combined_csv = header_line + "\n" + "\n".join(all_data_lines)
        return combined_csv

    finally:
        # Close session to free resources
        session.close()


def parse_csv_to_institutions(csv_content: str, logger=None) -> tuple[list[dict], int]:
    """
    Parse CSV content into list of institution dictionaries.

    Maps BACEN column names to internal field names and handles missing/empty fields.
    Tracks and logs skipped rows (rows without bacen_cod_inst) for data quality monitoring.

    Note: BACEN/Olinda CSV responses use comma (,) as delimiter.

    Args:
        csv_content: Raw CSV content as string
        logger: Optional logger for logging skipped rows (if None, skipped rows are silently counted)

    Returns:
        Tuple of (institutions list, skipped_count)

    Raises:
        ValueError: If CSV is invalid or missing required columns
    """
    if not csv_content or not csv_content.strip():
        raise ValueError("CSV content is empty")

    institutions = []
    skipped_count = 0
    total_rows = 0

    # BACEN/Olinda CSV responses use comma (,) as delimiter
    reader = csv.DictReader(StringIO(csv_content), delimiter=",")

    # Check if required column exists (at least one identifier)
    fieldnames = reader.fieldnames
    if not fieldnames:
        raise ValueError("CSV has no headers")

    # Map BACEN columns to internal names
    for row_num, row in enumerate(reader, start=2):  # Start at 2 (row 1 is header)
        total_rows += 1
        institution = {}

        # Map each BACEN column to internal field name
        for bacen_col, internal_field in BACEN_COLUMN_MAPPING.items():
            # Try exact match first, then case-insensitive match
            value = None
            for col_name in fieldnames:
                if col_name == bacen_col or col_name.lower() == bacen_col.lower():
                    value = row.get(col_name, "").strip()
                    break

            # Store value as-is (empty strings preserved, will be normalized later)
            institution[internal_field] = value

        # Ensure bacen_cod_inst exists (required field)
        if not institution.get("bacen_cod_inst"):
            # Try alternative column names
            for col_name in fieldnames:
                if "cod" in col_name.lower() and "inst" in col_name.lower():
                    cod_inst_value = row.get(col_name, "").strip()
                    # Only set if we found a non-empty value
                    if cod_inst_value:
                        institution["bacen_cod_inst"] = cod_inst_value
                    break

        # Skip if still no identifier
        if not institution.get("bacen_cod_inst"):
            skipped_count += 1
            if logger:
                logger.warning(
                    f"Skipped row {row_num}: missing bacen_cod_inst. "
                    f"Available columns: {list(fieldnames)}. "
                    f"Row data: {dict(row)}"
                )
            continue

        institutions.append(institution)

    # Log summary if logger provided
    if logger and skipped_count > 0:
        logger.warning(
            f"Skipped {skipped_count} out of {total_rows} rows "
            f"({skipped_count/total_rows*100:.1f}%) due to missing bacen_cod_inst"
        )

    return institutions, skipped_count


def normalize_institution_fields(inst: dict) -> dict:
    """
    Normalize institution fields for hash computation.

    Normalization rules:
    1. Trim all string values
    2. Convert empty strings and literal "null" (any casing) to None (NULL)

    Args:
        inst: Institution dictionary

    Returns:
        Normalized institution dictionary
    """
    normalized = {}

    for key, value in inst.items():
        if isinstance(value, str):
            # Trim string
            trimmed_value = value.strip()
            # Convert empty string and literal "null" to None
            if trimmed_value == "" or trimmed_value.lower() == "null":
                normalized_value = None
            else:
                normalized_value = trimmed_value
        else:
            normalized_value = value
        normalized[key] = normalized_value

    return normalized


def compute_source_hash(inst: dict) -> str:
    """
    Compute SHA-256 hex hash for change detection.

    Hash inputs (from HASH_FIELDS):
    - bacen_cod_inst
    - nome_instituicao
    - situacao
    - segmento_tb
    - atividade
    - tipo_consolidado_bancario
    - tipo_consolidacao
    - tipo_controle
    - uf
    - municipio
    - cod_conglomerado_financeiro
    - cod_conglomerado_prudencial
    - cnpj_instituicao_lider

    Normalization:
    1. Trim strings
    2. Convert empty strings to NULL
    3. Serialize as JSON with sorted keys
    4. Compute SHA-256 hex digest

    Args:
        inst: Institution dictionary (should be normalized)

    Returns:
        SHA-256 hex hash as string (64 characters)
    """
    # Extract hash fields in order
    hash_data = {}
    for field in HASH_FIELDS:
        hash_data[field] = inst.get(field)

    # Serialize as JSON with sorted keys (deterministic)
    json_str = json.dumps(hash_data, sort_keys=True, ensure_ascii=False)

    # Compute SHA-256 hex digest
    hash_obj = hashlib.sha256(json_str.encode("utf-8"))
    return hash_obj.hexdigest()


def upsert_institution(_conn, cur, inst: dict, ano_mes: str) -> str | None:
    """
    Upsert institution into database with change detection.

    Note: This function does NOT commit. The caller must manage the transaction.

    Upsert rules:
    - NEW: bacen_cod_inst not found -> INSERT -> return "CREATED"
    - CHANGED: found AND source_hash differs -> UPDATE -> return "UPDATED"
    - UNCHANGED: found AND source_hash equal -> no-op -> return None

    Args:
        conn: PostgreSQL connection object (for transaction management)
        cur: Database cursor (must be from the same connection)
        inst: Institution dictionary (normalized, with source_hash computed)
        ano_mes: Reference quarter in YYYYMM format

    Returns:
        "CREATED" if new institution, "UPDATED" if changed, None if unchanged

    Raises:
        psycopg2.Error: If database operation fails
    """
    bacen_cod_inst = inst["bacen_cod_inst"]
    source_hash = inst["source_hash"]

    # Check if institution exists and get current hash
    cur.execute(
        """
        SELECT source_hash
        FROM financial_data.institution
        WHERE bacen_cod_inst = %s
    """,
        (bacen_cod_inst,),
    )

    result = cur.fetchone()

    if result is None:
        # NEW: Insert institution
        cur.execute(
            """
            INSERT INTO financial_data.institution (
                bacen_cod_inst, ano_mes_ref, nome_instituicao,
                situacao, segmento_tb, atividade,
                tipo_consolidado_bancario, tipo_consolidacao, tipo_controle,
                uf, municipio, cod_conglomerado_financeiro,
                cod_conglomerado_prudencial, cnpj_instituicao_lider, source_hash
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
            )
        """,
            (
                inst.get("bacen_cod_inst"),
                ano_mes,
                inst.get("nome_instituicao"),
                inst.get("situacao"),
                inst.get("segmento_tb"),
                inst.get("atividade"),
                inst.get("tipo_consolidado_bancario"),
                inst.get("tipo_consolidacao"),
                inst.get("tipo_controle"),
                inst.get("uf"),
                inst.get("municipio"),
                inst.get("cod_conglomerado_financeiro"),
                inst.get("cod_conglomerado_prudencial"),
                inst.get("cnpj_instituicao_lider"),
                source_hash,
            ),
        )
        return "CREATED"

    elif result["source_hash"] != source_hash:
        # CHANGED: Update institution
        cur.execute(
            """
            UPDATE financial_data.institution
            SET ano_mes_ref = %s,
                nome_instituicao = %s,
                situacao = %s,
                segmento_tb = %s,
                atividade = %s,
                tipo_consolidado_bancario = %s,
                tipo_consolidacao = %s,
                tipo_controle = %s,
                uf = %s,
                municipio = %s,
                cod_conglomerado_financeiro = %s,
                cod_conglomerado_prudencial = %s,
                cnpj_instituicao_lider = %s,
                source_hash = %s,
                updated_at = now()
            WHERE bacen_cod_inst = %s
        """,
            (
                ano_mes,
                inst.get("nome_instituicao"),
                inst.get("situacao"),
                inst.get("segmento_tb"),
                inst.get("atividade"),
                inst.get("tipo_consolidado_bancario"),
                inst.get("tipo_consolidacao"),
                inst.get("tipo_controle"),
                inst.get("uf"),
                inst.get("municipio"),
                inst.get("cod_conglomerado_financeiro"),
                inst.get("cod_conglomerado_prudencial"),
                inst.get("cnpj_instituicao_lider"),
                source_hash,
                bacen_cod_inst,
            ),
        )
        return "UPDATED"

    else:
        # Institution unchanged, no database update needed
        return None


@task(
    name="ingest_registry",
    retries=3,
    retry_delay_seconds=60,
    timeout_seconds=300,
    log_prints=True,
    tags=["registry", "ingestion", "extract"],
)
def ingest_registry(ano_mes: str) -> list[dict]:
    """
    Ingest institution registry from BACEN API and upsert into database.

    This task:
    1. Fetches IfDataCadastro from BACEN API (CSV format)
    2. Parses CSV data into institution records
    3. Computes source_hash for change detection
    4. Upserts into financial_data.institution
    5. Detects NEW/CHANGED/UNCHANGED institutions
    6. Returns list of institutions that need Kafka events (NEW or CHANGED only)

    Args:
        ano_mes: Target quarter in YYYYMM format

    Returns:
        List of dictionaries, each containing:
        - bacen_cod_inst: Institution code
        - change_type: "CREATED" or "UPDATED"
        - institution_data: Full institution record

    Raises:
        requests.RequestException: If HTTP request fails
        psycopg2.Error: If database operation fails
        ValueError: If data format is invalid
    """
    logger = get_run_logger()

    try:
        # Step 1: Fetch CSV from BACEN API
        logger.info(f"Fetching registry CSV for ano_mes={ano_mes}")
        csv_content = fetch_registry_csv(ano_mes)
        logger.info(f"Fetched {len(csv_content)} characters of CSV data")

        # Step 2: Parse CSV to institutions
        logger.info("Parsing CSV to institutions")
        institutions, skipped_count = parse_csv_to_institutions(
            csv_content, logger=logger
        )
        total_rows = len(institutions) + skipped_count

        # Check skip rate to detect upstream schema shifts or data quality issues
        SKIP_RATE_THRESHOLD = 0.10  # 10% skip rate threshold
        if total_rows > 0:
            skip_rate = skipped_count / total_rows
            logger.info(
                f"Parsed {len(institutions)} institutions from CSV "
                f"(skipped {skipped_count} rows, {skip_rate*100:.1f}% skip rate)"
            )

            if skip_rate > SKIP_RATE_THRESHOLD:
                error_msg = (
                    f"High skip rate detected: {skip_rate*100:.1f}% "
                    f"({skipped_count}/{total_rows} rows skipped). "
                    f"This may indicate upstream schema shifts or data quality issues. "
                    f"Threshold: {SKIP_RATE_THRESHOLD*100:.1f}%"
                )
                logger.error(error_msg)
                raise ValueError(error_msg)
        else:
            logger.warning("No rows found in CSV (empty or header-only)")
            if skipped_count > 0:
                logger.warning(
                    f"Skipped {skipped_count} rows due to missing bacen_cod_inst"
                )

        # Step 3: Normalize and compute hashes
        logger.info("Normalizing fields and computing source hashes")
        normalized_institutions = []
        for inst in institutions:
            normalized = normalize_institution_fields(inst)
            normalized["source_hash"] = compute_source_hash(normalized)
            normalized_institutions.append(normalized)

        # Step 4: Upsert into database and collect changes
        # Use a single transaction for atomicity and performance
        logger.info("Upserting institutions into database")
        conn = get_db_connection()
        try:
            changed_institutions = []
            stats = {"new": 0, "changed": 0, "unchanged": 0}

            # Use a single cursor for the entire batch within one transaction
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                try:
                    # Process all institutions in a single transaction
                    for inst in normalized_institutions:
                        change_type = upsert_institution(conn, cur, inst, ano_mes)

                        if change_type == "CREATED":
                            stats["new"] += 1
                            changed_institutions.append(
                                {
                                    "bacen_cod_inst": inst["bacen_cod_inst"],
                                    "change_type": "CREATED",
                                    "institution_data": inst,
                                }
                            )
                        elif change_type == "UPDATED":
                            stats["changed"] += 1
                            changed_institutions.append(
                                {
                                    "bacen_cod_inst": inst["bacen_cod_inst"],
                                    "change_type": "UPDATED",
                                    "institution_data": inst,
                                }
                            )
                        else:
                            stats["unchanged"] += 1

                    # Commit entire batch atomically
                    conn.commit()
                    logger.info(
                        f"Registry ingestion complete: "
                        f"new={stats['new']}, changed={stats['changed']}, "
                        f"unchanged={stats['unchanged']}, total={len(normalized_institutions)}"
                    )

                except Exception:
                    # Rollback on any error to maintain atomicity
                    conn.rollback()
                    logger.exception(
                        "Error during batch upsert, transaction rolled back"
                    )
                    raise

            return changed_institutions

        finally:
            conn.close()

    except requests.RequestException:
        logger.exception("HTTP error while fetching registry")
        raise
    except psycopg2.Error:
        logger.exception("Database error while ingesting registry")
        raise
    except ValueError:
        logger.exception("Invalid data format while ingesting registry")
        raise
    except Exception:
        logger.exception("Unexpected error while ingesting registry")
        raise
