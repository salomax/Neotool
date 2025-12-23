#!/usr/bin/env python3
"""
Standalone script to purge BACEN IFData report data for a specific AnoMes and report.

This script purges data from all relevant tables:
- financial_data.ifdata_raw (raw CSV data)
- financial_data.bank_report (promoted/hot data)
- financial_data.ifdata_load_control (load control status)
- financial_data.ifdata_extract_task (extraction tasks)

Usage:
    python scripts/purge_report_data.py <AnoMes> [nome_relatorio] [options]
    python scripts/purge_report_data.py 202403
    python scripts/purge_report_data.py 202403 "Resumo"
    python scripts/purge_report_data.py 202403 --dry-run
    python scripts/purge_report_data.py 202403 --force

Options:
    --dry-run    Show what would be purged without actually deleting
    --force      Skip confirmation prompt
    --verbose    Show detailed information

Environment Variables:
    - POSTGRES_HOST (default: localhost)
    - POSTGRES_PORT (default: 6432)
    - POSTGRES_DB (default: neotool_db)
    - POSTGRES_USER (default: neotool)
    - POSTGRES_PASSWORD (default: neotool)
"""
from pathlib import Path
import sys

# Add the parent directory to the path so we can import tasks
script_dir = Path(__file__).parent
project_root = script_dir.parent
sys.path.insert(0, str(project_root))

from tasks.constants import ANO_MES_LENGTH, MAX_MONTH, MIN_MONTH
from tasks.quarter_determination import get_db_connection


def count_rows_to_purge(
    conn, ano_mes: str, nome_relatorio: str | None = None
) -> dict[str, int]:
    """Count rows that would be purged."""
    cur = conn.cursor()
    counts = {}

    # Count ifdata_raw
    if nome_relatorio:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.ifdata_raw
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.ifdata_raw
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    counts["ifdata_raw"] = cur.fetchone()[0]

    # Count bank_report
    if nome_relatorio:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.bank_report
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.bank_report
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    counts["bank_report"] = cur.fetchone()[0]

    # Count ifdata_load_control
    if nome_relatorio:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.ifdata_load_control
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.ifdata_load_control
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    counts["ifdata_load_control"] = cur.fetchone()[0]

    # Count ifdata_extract_task
    if nome_relatorio:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.ifdata_extract_task
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            SELECT COUNT(*)
            FROM financial_data.ifdata_extract_task
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    counts["ifdata_extract_task"] = cur.fetchone()[0]

    return counts


def purge_data(conn, ano_mes: str, nome_relatorio: str | None = None) -> dict[str, int]:
    """Purge data from all relevant tables."""
    cur = conn.cursor()
    deleted = {}

    # Purge ifdata_raw
    if nome_relatorio:
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_raw
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_raw
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    deleted["ifdata_raw"] = cur.rowcount

    # Purge bank_report
    if nome_relatorio:
        cur.execute(
            """
            DELETE FROM financial_data.bank_report
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            DELETE FROM financial_data.bank_report
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    deleted["bank_report"] = cur.rowcount

    # Purge ifdata_load_control
    if nome_relatorio:
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_load_control
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_load_control
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    deleted["ifdata_load_control"] = cur.rowcount

    # Purge ifdata_extract_task
    if nome_relatorio:
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_extract_task
            WHERE ano_mes = %s AND nome_relatorio = %s
        """,
            (ano_mes, nome_relatorio),
        )
    else:
        cur.execute(
            """
            DELETE FROM financial_data.ifdata_extract_task
            WHERE ano_mes = %s
        """,
            (ano_mes,),
        )
    deleted["ifdata_extract_task"] = cur.rowcount

    conn.commit()
    return deleted


def main():
    if len(sys.argv) < 2:
        print(
            "Usage: python scripts/purge_report_data.py <AnoMes> [nome_relatorio] [options]"
        )
        print("Example: python scripts/purge_report_data.py 202403")
        print("Example: python scripts/purge_report_data.py 202403 'Resumo'")
        print("\nOptions:")
        print("  --dry-run    Show what would be purged without actually deleting")
        print("  --force      Skip confirmation prompt")
        print("  --verbose    Show detailed information")
        sys.exit(1)

    # Parse arguments - handle flags that might be in any position
    args = [arg for arg in sys.argv[1:] if not arg.startswith("--") and arg != "-v"]
    flags = [arg for arg in sys.argv[1:] if arg.startswith("--") or arg == "-v"]

    if not args:
        print("Error: AnoMes is required")
        sys.exit(1)

    ano_mes = args[0]
    nome_relatorio = args[1] if len(args) > 1 else None
    dry_run = "--dry-run" in flags
    force = "--force" in flags
    verbose = "--verbose" in flags or "-v" in flags

    # Validate AnoMes format (YYYYMM)
    if len(ano_mes) != ANO_MES_LENGTH or not ano_mes.isdigit():
        print(
            f"Error: Invalid AnoMes format: {ano_mes}. Expected YYYYMM format (e.g., 202403)"
        )
        sys.exit(1)

    int(ano_mes[:4])
    month = int(ano_mes[4:6])
    if month < MIN_MONTH or month > MAX_MONTH:
        print(f"Error: Invalid month in AnoMes: {ano_mes}. Month must be 01-12.")
        sys.exit(1)

    scope = f"{ano_mes}"
    if nome_relatorio:
        scope += f"/{nome_relatorio}"

    print(f"Purging BACEN IFData report data for: {scope}")
    if dry_run:
        print("🔍 DRY RUN MODE - No data will be deleted")
    print("=" * 70)

    try:
        conn = get_db_connection()

        # Count rows to be purged
        counts = count_rows_to_purge(conn, ano_mes, nome_relatorio)
        total = sum(counts.values())

        if total == 0:
            print("\n✅ No data found to purge.")
            conn.close()
            return

        print("\nRows to be purged:")
        print(f"  - ifdata_raw: {counts['ifdata_raw']:,}")
        print(f"  - bank_report: {counts['bank_report']:,}")
        print(f"  - ifdata_load_control: {counts['ifdata_load_control']:,}")
        print(f"  - ifdata_extract_task: {counts['ifdata_extract_task']:,}")
        print(f"  - TOTAL: {total:,}")

        if dry_run:
            print("\n🔍 DRY RUN - No data was deleted")
            conn.close()
            return

        # Confirmation prompt
        if not force:
            print(f"\n⚠️  WARNING: This will permanently delete {total:,} rows!")
            response = input("Type 'yes' to confirm: ")
            if response.lower() != "yes":
                print("❌ Purge cancelled.")
                conn.close()
                return

        # Purge data
        print("\nPurging data...")
        deleted = purge_data(conn, ano_mes, nome_relatorio)

        print("\n✅ Purge completed:")
        print(f"  - ifdata_raw: {deleted['ifdata_raw']:,} rows deleted")
        print(f"  - bank_report: {deleted['bank_report']:,} rows deleted")
        print(
            f"  - ifdata_load_control: {deleted['ifdata_load_control']:,} rows deleted"
        )
        print(
            f"  - ifdata_extract_task: {deleted['ifdata_extract_task']:,} rows deleted"
        )
        print(f"  - TOTAL: {sum(deleted.values()):,} rows deleted")

        conn.close()
        print("\n" + "=" * 70)
        print("Purge completed successfully!")

    except Exception as e:
        print(f"\n❌ Error: {e}")
        if verbose:
            import traceback

            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
