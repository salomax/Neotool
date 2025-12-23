"""
Report Catalog Fetching Task

Fetches report catalog from BACEN API (ListaDeRelatorio endpoint),
parses CSV data, and returns list of reports for extraction task creation.
"""

import csv
from io import StringIO

from prefect import get_run_logger, task
import requests

from .registry_ingestion import build_bacen_query_string, get_bacen_session

# Configuration constants
BACEN_BASE_URL = "https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata/"
CATALOG_ENDPOINT = "ListaDeRelatorio()"
MAX_REPORTS = 100  # Safety limit, though catalog is small

# BACEN API column name mapping to internal field names
CATALOG_COLUMN_MAPPING = {
    "NomeRelatorio": "nome_relatorio",
    "NumeroRelatorio": "numero_relatorio",
}


def fetch_catalog_csv() -> str:
    """
    Fetch report catalog CSV from BACEN API.

    Fetches the catalog using CSV format for consistency with other endpoints.
    Uses a requests.Session with retry/backoff to handle transient network issues
    and BACEN API hiccups.

    Returns:
        Raw CSV content as string (header row + data rows)

    Raises:
        requests.RequestException: If HTTP request fails after retries
    """
    # Get session with retry configuration
    session = get_bacen_session()

    try:
        # Build URL and parameters
        url = f"{BACEN_BASE_URL}{CATALOG_ENDPOINT}"
        params = {
            "$format": "text/csv",
            "$select": "NomeRelatorio,NumeroRelatorio",
            "$top": MAX_REPORTS,
        }

        # Build query string manually to preserve $ characters in parameter names
        # BACEN API requires these to NOT be URL-encoded
        query_string = build_bacen_query_string(params)
        full_url = f"{url}?{query_string}"

        response = session.get(full_url, timeout=30)
        response.raise_for_status()

        csv_content = response.text.strip()
        return csv_content

    finally:
        # Close session to free resources
        session.close()


def parse_csv_to_reports(csv_content: str, logger=None) -> list[dict[str, str]]:
    """
    Parse CSV content into list of report dictionaries.

    Maps BACEN column names to internal field names and handles missing/empty fields.
    Deduplicates by the catalog natural key (NomeRelatorio, NumeroRelatorio) to
    keep the extraction plan deterministic and idempotent.

    Note: BACEN/Olinda CSV responses use comma (,) as delimiter.

    Args:
        csv_content: Raw CSV content as string
        logger: Optional logger for logging warnings (if None, warnings are silently ignored)

    Returns:
        List of report dictionaries, each containing:
        - nome_relatorio: Report name (str)
        - numero_relatorio: Report number (str)

    Raises:
        ValueError: If CSV is invalid or missing required columns
    """
    if not csv_content or not csv_content.strip():
        raise ValueError("CSV content is empty")

    reports = []

    # BACEN/Olinda CSV responses use comma (,) as delimiter
    reader = csv.DictReader(StringIO(csv_content), delimiter=",")

    fieldnames = reader.fieldnames
    if not fieldnames:
        raise ValueError("CSV has no headers")

    # Normalize headers (trim, drop BOM) before validation
    normalized_fieldnames = [(col or "").strip().lstrip("\ufeff") for col in fieldnames]
    reader.fieldnames = normalized_fieldnames

    # Check for required columns (case-insensitive)
    required_columns = {"NomeRelatorio", "NumeroRelatorio"}
    available_columns_lower = {col.lower() for col in normalized_fieldnames}
    required_columns_lower = {col.lower() for col in required_columns}

    missing_columns = required_columns_lower - available_columns_lower
    if missing_columns:
        raise ValueError(
            f"CSV missing required columns: {missing_columns}. "
            f"Available columns: {normalized_fieldnames}"
        )

    seen_keys = set()

    # Map BACEN columns to internal names
    for row_num, row in enumerate(reader, start=2):  # Start at 2 (row 1 is header)
        report = {}

        # Map each BACEN column to internal field name
        for bacen_col, internal_field in CATALOG_COLUMN_MAPPING.items():
            matching_column = next(
                (
                    col_name
                    for col_name in normalized_fieldnames
                    if col_name.lower() == bacen_col.lower()
                ),
                None,
            )

            value = ""
            if matching_column:
                raw_value = row.get(matching_column, "")
                value = raw_value.strip() if raw_value is not None else ""

            report[internal_field] = value

        # Validate required fields are present
        if not report.get("nome_relatorio") or not report.get("numero_relatorio"):
            if logger:
                logger.warning(
                    f"Skipped row {row_num}: missing required fields. "
                    f"Row data: {dict(row)}"
                )
            continue

        natural_key = (report["nome_relatorio"], report["numero_relatorio"])
        if natural_key in seen_keys:
            if logger:
                logger.warning(
                    "Skipped duplicate catalog row for natural key "
                    f"{natural_key}. Row data: {dict(row)}"
                )
            continue

        seen_keys.add(natural_key)

        reports.append(report)

    return reports


@task(
    name="fetch_report_catalog",
    retries=3,
    retry_delay_seconds=60,
    timeout_seconds=120,
    log_prints=True,
    tags=["catalog", "extract"],
)
def fetch_report_catalog() -> list[dict[str, str]]:
    """
    Fetch report catalog from BACEN API.

    This task:
    1. Fetches ListaDeRelatorio from BACEN API (CSV format)
    2. Parses CSV data into report records
    3. Returns list of reports with nome_relatorio and numero_relatorio

    Returns:
        List of dictionaries, each containing:
        - nome_relatorio: Report name (str)
        - numero_relatorio: Report number (str)

    Raises:
        requests.RequestException: If HTTP request fails
        ValueError: If data format is invalid
    """
    logger = get_run_logger()

    try:
        # Step 1: Fetch CSV from BACEN API
        logger.info("Fetching report catalog CSV from BACEN API")
        csv_content = fetch_catalog_csv()
        logger.info(f"Fetched {len(csv_content)} characters of CSV data")

        # Step 2: Parse CSV to reports
        logger.info("Parsing CSV to reports")
        reports = parse_csv_to_reports(csv_content, logger=logger)

        logger.info(f"Fetched {len(reports)} reports from catalog")

        if len(reports) == 0:
            logger.warning("Catalog is empty - no reports found")

        return reports

    except requests.RequestException:
        logger.exception("HTTP error while fetching catalog")
        raise
    except ValueError:
        logger.exception("Invalid data format while parsing catalog")
        raise
    except Exception:
        logger.exception("Unexpected error while fetching catalog")
        raise
