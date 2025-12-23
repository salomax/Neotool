#!/usr/bin/env python3
"""
Script to promote data from ifdata_raw to bank_report.

This script promotes raw CSV data from ifdata_raw into normalized bank_report and 
bank_report_data tables. It works for both initial promotion and re-promotion of 
existing data, since UPSERT operations are idempotent.

Usage:
    python scripts/promote_data.py [--ano-mes ANOMES] [--report REPORT] [--verbose]

    # Promote all available data
    python scripts/promote_data.py

    # Promote specific AnoMes
    python scripts/promote_data.py --ano-mes 202403

    # Promote specific AnoMes and report
    python scripts/promote_data.py --ano-mes 202403 --report "Ativo"

    # Verbose output
    python scripts/promote_data.py --verbose

Environment Variables:
    - DATABASE_URL: PostgreSQL connection string (required)
"""
import argparse
import logging
from pathlib import Path
import sys
from typing import Any

# Add the parent directory to the path so we can import tasks
script_dir = Path(__file__).parent
project_root = script_dir.parent
sys.path.insert(0, str(project_root))

# Import only the helper functions, NOT the Prefect task
import psycopg2
from tasks.constants import ANO_MES_LENGTH
from tasks.promotion import (
    aggregate_rows_to_structured_data,
    fetch_raw_data_for_promotion,
    get_headers_from_first_row,
    mark_load_control_completed,
    upsert_bank_report,
)
from tasks.quarter_determination import get_db_connection

# Set up a simple logger for standalone execution
logger = logging.getLogger("promote_data")
logger.setLevel(logging.INFO)
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter("%(levelname)s: %(message)s"))
logger.addHandler(handler)


def promote_raw_to_hot_standalone(ano_mes: str, nome_relatorio: str) -> dict[str, Any]:
    """
    Standalone version of promote_raw_to_hot that doesn't require Prefect context.

    This function replicates the logic from the Prefect task but uses a regular logger.
    """
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

            if rows_fetched == 0:
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


def get_reports_from_raw(
    ano_mes: str | None = None, nome_relatorio: str | None = None
) -> list[dict[str, str]]:
    """
    Get list of AnoMes/report combinations that exist in ifdata_raw.

    This queries the source table (ifdata_raw) to find all available data to promote,
    regardless of whether it has been promoted to bank_report before.

    Args:
        ano_mes: Optional filter for specific AnoMes
        nome_relatorio: Optional filter for specific report name

    Returns:
        List of dictionaries with 'ano_mes' and 'nome_relatorio' keys
    """
    conn = None
    try:
        conn = get_db_connection()
        cur = conn.cursor()

        query = """
            SELECT DISTINCT ano_mes, nome_relatorio
            FROM financial_data.ifdata_raw
            WHERE 1=1
        """
        params = []

        if ano_mes:
            query += " AND ano_mes = %s"
            params.append(ano_mes)

        if nome_relatorio:
            query += " AND nome_relatorio = %s"
            params.append(nome_relatorio)

        query += " ORDER BY ano_mes, nome_relatorio"

        cur.execute(query, params)
        results = []
        for row in cur.fetchall():
            results.append(
                {
                    "ano_mes": row[0],
                    "nome_relatorio": row[1],
                }
            )

        return results

    finally:
        if conn:
            conn.close()


def promote_data(
    ano_mes: str | None = None, nome_relatorio: str | None = None, verbose: bool = False
) -> dict[str, Any]:
    """
    Promote data from ifdata_raw to bank_report.

    This function finds all available reports in ifdata_raw (optionally filtered)
    and promotes them to bank_report. Since promotion uses UPSERT operations,
    it works for both initial promotion and re-promotion of existing data.

    Args:
        ano_mes: Optional filter for specific AnoMes
        nome_relatorio: Optional filter for specific report name
        verbose: If True, show detailed output

    Returns:
        Dictionary with statistics:
        - total_processed: Total number of AnoMes/report combinations processed
        - successful: Number of successful promotions
        - failed: Number of failed promotions
        - results: List of promotion results
    """
    # Set logger level based on verbose flag
    if verbose:
        logger.setLevel(logging.DEBUG)

    # Get list of reports from ifdata_raw (source of truth)
    reports = get_reports_from_raw(ano_mes, nome_relatorio)

    if not reports:
        print("No data found in ifdata_raw to promote.")
        if ano_mes:
            print(f"  (Filtered by ano_mes={ano_mes})")
        if nome_relatorio:
            print(f"  (Filtered by nome_relatorio={nome_relatorio})")
        return {
            "total_processed": 0,
            "successful": 0,
            "failed": 0,
            "results": [],
        }

    print(f"Found {len(reports)} AnoMes/report combination(s) to promote:")
    for item in reports:
        print(f"  - {item['ano_mes']} / {item['nome_relatorio']}")
    print()

    # Promote each combination
    results = []
    successful = 0
    failed = 0

    for idx, item in enumerate(reports, 1):
        ano_mes_val = item["ano_mes"]
        nome_relatorio_val = item["nome_relatorio"]

        print(
            f"[{idx}/{len(reports)}] Promoting {ano_mes_val} / {nome_relatorio_val}..."
        )

        try:
            # Call the standalone promotion function
            result = promote_raw_to_hot_standalone(ano_mes_val, nome_relatorio_val)

            results.append(
                {
                    "ano_mes": ano_mes_val,
                    "nome_relatorio": nome_relatorio_val,
                    "status": "success",
                    "stats": result,
                }
            )
            successful += 1

            if verbose:
                print("  ✓ Success:")
                print(f"    - Rows Fetched: {result.get('rows_fetched', 0)}")
                print(f"    - Documents Created: {result.get('documents_created', 0)}")
                print(f"    - Rows Upserted: {result.get('rows_upserted', 0)}")
            else:
                print(f"  ✓ Success: {result.get('rows_upserted', 0)} rows upserted")

        except Exception as e:
            results.append(
                {
                    "ano_mes": ano_mes_val,
                    "nome_relatorio": nome_relatorio_val,
                    "status": "error",
                    "error": str(e),
                }
            )
            failed += 1
            print(f"  ✗ Failed: {e}")
            if verbose:
                import traceback

                traceback.print_exc()

        print()

    # Summary
    print("=" * 60)
    print("Summary:")
    print(f"  Total Processed: {len(reports)}")
    print(f"  Successful: {successful}")
    print(f"  Failed: {failed}")
    print("=" * 60)

    return {
        "total_processed": len(reports),
        "successful": successful,
        "failed": failed,
        "results": results,
    }


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Promote data from ifdata_raw to bank_report (works for both initial and re-promotion)"
    )
    parser.add_argument(
        "--ano-mes",
        type=str,
        help="Filter by specific AnoMes (YYYYMM format, e.g., 202403)",
    )
    parser.add_argument("--report", type=str, help="Filter by specific report name")
    parser.add_argument(
        "--verbose", action="store_true", help="Show detailed output for each promotion"
    )

    args = parser.parse_args()

    # Validate ano_mes format if provided
    if args.ano_mes and (len(args.ano_mes) != ANO_MES_LENGTH or not args.ano_mes.isdigit()):
        print(
            f"Error: Invalid ano_mes format: {args.ano_mes}. Expected YYYYMM format (e.g., '202403')"
        )
        sys.exit(1)

    try:
        promote_data(
            ano_mes=args.ano_mes,
            nome_relatorio=args.report,
            verbose=args.verbose,
        )
    except KeyboardInterrupt:
        print("\n\nInterrupted by user.")
        sys.exit(1)
    except Exception as e:
        print(f"\nError: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
