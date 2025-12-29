#!/usr/bin/env python3
"""
Standalone script to run registry ingestion task with a specific AnoMes.

This script allows running the registry ingestion task directly without Prefect orchestration,
useful for testing, debugging, or manual data ingestion.

Usage:
    python scripts/run_registry_ingestion.py <AnoMes>
    python scripts/run_registry_ingestion.py 202403
    python scripts/run_registry_ingestion.py 202403 --verbose

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
from bacen_ifdata.tasks.constants import ANO_MES_LENGTH, MAX_MONTH, MIN_MONTH
from bacen_ifdata.tasks.registry_ingestion import ingest_registry


# Define a minimal flow to provide Prefect context for the task
@flow(name="standalone_registry_ingestion", log_prints=True)
def run_registry_ingestion_flow(ano_mes: str):
    """Wrapper flow to provide Prefect context for standalone execution."""
    return ingest_registry(ano_mes)


def main():
    if len(sys.argv) < 2:
        print("Usage: python scripts/run_registry_ingestion.py <AnoMes> [--verbose]")
        print("Example: python scripts/run_registry_ingestion.py 202403")
        print("\nOptions:")
        print("  --verbose    Show detailed information about processed institutions")
        sys.exit(1)

    ano_mes = sys.argv[1]
    verbose = "--verbose" in sys.argv or "-v" in sys.argv

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

    print(f"Running registry ingestion for AnoMes: {ano_mes}")
    print("=" * 60)

    try:
        # Call the flow which will create the necessary Prefect context for the task
        # This allows the task to use get_run_logger() without full Prefect orchestration
        result = run_registry_ingestion_flow(ano_mes)

        print(f"\n✅ Successfully processed {len(result)} institutions")

        if result:
            created = sum(1 for item in result if item.get("change_type") == "CREATED")
            updated = sum(1 for item in result if item.get("change_type") == "UPDATED")
            print(f"   - CREATED: {created}")
            print(f"   - UPDATED: {updated}")

            if verbose:
                print("\nInstitutions processed:")
                for item in result[:20]:  # Show first 20
                    inst_data = item.get("institution_data", {})
                    nome = inst_data.get("nome_instituicao", "N/A")
                    print(
                        f"   - {item['bacen_cod_inst']}: {item['change_type']} - {nome}"
                    )
                if len(result) > 20:
                    print(f"   ... and {len(result) - 20} more")
        else:
            print("   No NEW or CHANGED institutions found (all were UNCHANGED)")

        print("\n" + "=" * 60)
        print("Registry ingestion completed successfully!")

    except Exception as e:
        print(f"\n❌ Error: {e}")
        if verbose:
            import traceback

            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
