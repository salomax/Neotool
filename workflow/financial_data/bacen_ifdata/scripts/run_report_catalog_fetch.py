#!/usr/bin/env python3
"""
Standalone script to run report catalog fetch task.

This script allows running the report catalog fetch task directly without Prefect orchestration,
useful for testing, debugging, or manual catalog retrieval.

Usage:
    python scripts/run_report_catalog_fetch.py
    python scripts/run_report_catalog_fetch.py --verbose

Environment Variables:
    The script uses the same environment variables as the Prefect flow:
    - POSTGRES_HOST (default: localhost)
    - POSTGRES_PORT (default: 6432)
    - POSTGRES_DB (default: neotool_db)
    - POSTGRES_USER (default: neotool)
    - POSTGRES_PASSWORD (default: neotool)
"""
from pathlib import Path
import sys

# Add the src directory to the path so we can import the package
script_dir = Path(__file__).parent
project_root = script_dir.parent
src_dir = project_root / "src"
sys.path.insert(0, str(src_dir))

from prefect import flow
from bacen_ifdata.tasks.report_catalog_fetch import fetch_report_catalog


# Define a minimal flow to provide Prefect context for the task
@flow(name="standalone_report_catalog_fetch", log_prints=True)
def run_report_catalog_fetch_flow():
    """Wrapper flow to provide Prefect context for standalone execution."""
    return fetch_report_catalog()


def main():
    verbose = "--verbose" in sys.argv or "-v" in sys.argv

    print("Running report catalog fetch")
    print("=" * 60)

    try:
        # Call the flow which will create the necessary Prefect context for the task
        # This allows the task to use get_run_logger() without full Prefect orchestration
        result = run_report_catalog_fetch_flow()

        print(f"\n✅ Successfully fetched {len(result)} reports from catalog")

        if result:
            if verbose:
                print("\nReports in catalog:")
                for i, report in enumerate(result[:50], 1):  # Show first 50
                    nome = report.get("nome_relatorio", "N/A")
                    numero = report.get("numero_relatorio", "N/A")
                    print(f"   {i:3d}. {numero:3s} - {nome}")
                if len(result) > 50:
                    print(f"   ... and {len(result) - 50} more reports")
            else:
                print("\nFirst 10 reports:")
                for i, report in enumerate(result[:10], 1):
                    nome = report.get("nome_relatorio", "N/A")
                    numero = report.get("numero_relatorio", "N/A")
                    print(f"   {i:2d}. {numero:3s} - {nome}")
                if len(result) > 10:
                    print(
                        f"   ... and {len(result) - 10} more (use --verbose to see all)"
                    )
        else:
            print("   ⚠️  Catalog is empty - no reports found")

        print("\n" + "=" * 60)
        print("Report catalog fetch completed successfully!")

    except Exception as e:
        print(f"\n❌ Error: {e}")
        if verbose:
            import traceback

            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
