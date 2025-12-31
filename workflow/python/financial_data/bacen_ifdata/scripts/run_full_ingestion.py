#!/usr/bin/env python3
"""
Standalone script to run the full BACEN IFData ingestion workflow for a specific AnoMes.

This script runs the complete ingestion pipeline:
- Registry ingestion (loads banks)
- Report catalog fetch
- Extract task creation
- Paginated extraction
- Data promotion
- Kafka event publishing

Usage:
    python scripts/run_full_ingestion.py <AnoMes> [options]
    python scripts/run_full_ingestion.py 202403
    python scripts/run_full_ingestion.py 202403 --purge
    python scripts/run_full_ingestion.py 202403 --verbose
    python scripts/run_full_ingestion.py 202403 --purge --verbose

Environment Variables:
    The script uses the same environment variables as the Prefect flow:
    - DATABASE_URL: PostgreSQL connection string (required)
    - KAFKA_BROKERS: Kafka broker addresses (comma-separated) (required)
    - BACEN_BASE_URL: BACEN IFData API base URL (optional)
"""
import os
from pathlib import Path
import sys
import traceback
from typing import Any

# Add the src directory to the path so we can import the package
script_dir = Path(__file__).parent
project_root = script_dir.parent
src_dir = project_root / "src"
sys.path.insert(0, str(src_dir))

from bacen_ifdata.flows.bacen_ifdata_ingestion import bacen_ifdata_ingestion_flow
from bacen_ifdata.tasks.constants import ANO_MES_LENGTH, MAX_MONTH, MAX_YEAR, MIN_MONTH, MIN_YEAR
from bacen_ifdata.tasks.quarter_determination import get_db_connection


def format_stats(stats: dict[str, Any], verbose: bool = False) -> str:
    """Format execution statistics for display."""
    lines = []

    # Basic info
    lines.append(f"Batch ID: {stats.get('batch_id', 'N/A')}")
    lines.append(f"Target AnoMes: {stats.get('target_ano_mes', 'N/A')}")
    lines.append(f"Status: {stats.get('status', 'unknown').upper()}")
    lines.append("")

    # Registry
    lines.append("Registry Ingestion:")
    lines.append(f"  - Ingested: {'Yes' if stats.get('registry_ingested') else 'No'}")
    changed = stats.get("changed_institutions", [])
    if changed is None:
        changed = []
    lines.append(f"  - Changed Institutions: {len(changed)}")
    if changed and verbose:
        created = sum(
            1
            for item in changed
            if isinstance(item, dict) and item.get("change_type") == "CREATED"
        )
        updated = sum(
            1
            for item in changed
            if isinstance(item, dict) and item.get("change_type") == "UPDATED"
        )
        lines.append(f"    - CREATED: {created}")
        lines.append(f"    - UPDATED: {updated}")
    lines.append("")

    # Extraction
    extraction = stats.get("extraction_stats", {})
    lines.append("Extraction Phase:")
    lines.append(f"  - Tasks Executed: {extraction.get('tasks_executed', 0)}")
    lines.append(f"  - Tasks Success: {extraction.get('tasks_success', 0)}")
    lines.append(f"  - Tasks Empty: {extraction.get('tasks_empty', 0)}")
    lines.append(f"  - Tasks Error: {extraction.get('tasks_error', 0)}")
    lines.append(f"  - Rows Extracted: {extraction.get('rows_extracted', 0)}")
    lines.append("")

    # Promotion
    promotion_stats = stats.get("promotion_stats", [])
    if promotion_stats is None:
        promotion_stats = []
    lines.append(f"Promotion Phase: {len(promotion_stats)} reports processed")
    if promotion_stats:
        for report_stat in promotion_stats:
            if not isinstance(report_stat, dict):
                continue
            nome = report_stat.get("nome_relatorio", "unknown")
            report_stats = report_stat.get("stats", {})
            if not isinstance(report_stats, dict):
                report_stats = {}
            lines.append(f"  - {nome}:")
            # Use correct field names from promotion task
            lines.append(
                f"    - Records Promoted: {report_stats.get('rows_upserted', 0)}"
            )
            if verbose:
                lines.append(
                    f"    - Rows Fetched: {report_stats.get('rows_fetched', 0)}"
                )
                lines.append(
                    f"    - Documents Created: {report_stats.get('documents_created', 0)}"
                )
    lines.append("")

    # Kafka
    kafka = stats.get("kafka_stats", {})
    lines.append("Kafka Publishing:")
    lines.append(f"  - Total Events: {kafka.get('total', 0)}")
    lines.append(f"  - Published: {kafka.get('published', 0)}")
    lines.append(f"  - Failed: {kafka.get('failed', 0)}")
    lines.append("")

    # Errors
    errors = stats.get("errors", [])
    if errors is None:
        errors = []
    if errors:
        lines.append("Errors:")
        for error in errors:
            # Handle both string errors and exception objects
            error_str = str(error) if error else "Unknown error"
            lines.append(f"  - {error_str}")
        lines.append("")

    return "\n".join(lines)


def purge_anomes_data(ano_mes: str, verbose: bool = False) -> dict[str, int]:
    """
    Purge data from ifdata_extract_task, ifdata_raw, and bank_report tables
    for the given AnoMes.

    Args:
        ano_mes: Target quarter in YYYYMM format (e.g., "202403")
        verbose: If True, log detailed information

    Returns:
        Dictionary with deletion counts per table:
        - ifdata_extract_task: Number of rows deleted
        - ifdata_raw: Number of rows deleted
        - bank_report: Number of rows deleted

    Raises:
        Exception: If database operation fails
    """
    conn = None
    try:
        conn = get_db_connection()
        cur = conn.cursor()

        deleted = {}

        # Purge ifdata_extract_task
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_extract_task
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
        deleted["ifdata_extract_task"] = cur.rowcount

        # Purge ifdata_raw
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_raw
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
        deleted["ifdata_raw"] = cur.rowcount

        # Purge bank_report
        cur.execute(
            """
            DELETE FROM financial_data.bank_report
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
        deleted["bank_report"] = cur.rowcount

        conn.commit()

        if verbose:
            print(f"Purge results for AnoMes {ano_mes}:")
            print(
                f"  - ifdata_extract_task: {deleted['ifdata_extract_task']:,} rows deleted"
            )
            print(f"  - ifdata_raw: {deleted['ifdata_raw']:,} rows deleted")
            print(f"  - bank_report: {deleted['bank_report']:,} rows deleted")
            print(f"  - TOTAL: {sum(deleted.values()):,} rows deleted")
        else:
            total = sum(deleted.values())
            print(f"Purged {total:,} rows from AnoMes {ano_mes}")

        return deleted

    except Exception as e:
        if conn:
            conn.rollback()
        raise Exception(f"Error purging data for AnoMes {ano_mes}: {e}")
    finally:
        if conn:
            cur.close()
            conn.close()


def main():
    if len(sys.argv) < 2:
        print("Usage: python scripts/run_full_ingestion.py <AnoMes> [options]")
        print("Example: python scripts/run_full_ingestion.py 202403")
        print("\nOptions:")
        print(
            "  --purge                     Purge existing data for AnoMes before ingestion"
        )
        print("  --verbose, -v               Show detailed information")
        sys.exit(1)

    target_ano_mes = sys.argv[1]
    purge = "--purge" in sys.argv
    verbose = "--verbose" in sys.argv or "-v" in sys.argv

    # Validate AnoMes format (YYYYMM)
    if len(target_ano_mes) != ANO_MES_LENGTH or not target_ano_mes.isdigit():
        print(
            f"Error: Invalid AnoMes format: {target_ano_mes}. Expected YYYYMM format (e.g., 202403)"
        )
        sys.exit(1)

    try:
        year = int(target_ano_mes[:4])
        month = int(target_ano_mes[4:6])
    except ValueError:
        print(
            f"Error: Invalid AnoMes format: {target_ano_mes}. Expected YYYYMM format (e.g., 202403)"
        )
        sys.exit(1)

    if month < MIN_MONTH or month > MAX_MONTH:
        print(f"Error: Invalid month in AnoMes: {target_ano_mes}. Month must be 01-12.")
        sys.exit(1)

    if year < MIN_YEAR or year > MAX_YEAR:
        print(
            f"Error: Invalid year in AnoMes: {target_ano_mes}. Year must be between 2000 and 2100."
        )
        sys.exit(1)

    # Basic environment variable check (flow will do more detailed validation)
    if not os.getenv("DATABASE_URL"):
        print(
            "Warning: DATABASE_URL environment variable is not set. The flow may fail."
        )
        print(
            "Set it with: export DATABASE_URL=postgresql://user:password@host:port/db"
        )
        print()

    # Purge data if requested
    if purge:
        print(f"Purging existing data for AnoMes: {target_ano_mes}")
        print("=" * 70)
        try:
            purge_anomes_data(target_ano_mes, verbose=verbose)
            print("Purge completed successfully.")
            print("=" * 70)
            print()
        except Exception as e:
            print(f"❌ Error during purge: {e}")
            if verbose:
                traceback.print_exc()
            sys.exit(1)

    print(f"Running full BACEN IFData ingestion for AnoMes: {target_ano_mes}")
    print("=" * 70)
    print()

    try:
        # Call the flow function directly
        # Note: This still uses Prefect flow decorator, but runs locally without Prefect server
        result = bacen_ifdata_ingestion_flow(
            target_ano_mes=target_ano_mes,
        )

        print("\n" + "=" * 70)
        print("EXECUTION SUMMARY")
        print("=" * 70)
        print()
        print(format_stats(result, verbose=verbose))
        print("=" * 70)

        if result.get("status") == "success":
            print("✅ Full ingestion completed successfully!")
        elif result.get("status") == "skipped":
            print(f"⏭️  Flow skipped: {result.get('reason', 'Unknown reason')}")
        else:
            print("❌ Flow failed - check errors above")
            sys.exit(1)

    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user")
        sys.exit(130)  # Standard exit code for SIGINT
    except Exception as e:
        print(f"\n❌ Error: {e}")
        if verbose:
            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
