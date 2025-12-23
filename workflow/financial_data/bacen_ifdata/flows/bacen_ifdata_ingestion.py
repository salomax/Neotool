"""
BACEN IFData Ingestion Flow

Main Prefect flow orchestrating the BACEN IFData ingestion pipeline:
- Determines target quarter based on publication windows
- Ensures registry exists (ingests if needed)
- Fetches report catalog
- Creates extract tasks
- Executes paginated extraction with parallelism
- Checks extract completion
- Promotes raw data to hot table
- Publishes Kafka events for institution changes
- Handles errors with fallback
- Provides observability (logs, metrics, artifacts)
"""

from datetime import UTC, datetime
import os
from typing import Any
from uuid import uuid4

from prefect import flow, get_run_logger, task
from prefect.artifacts import create_markdown_artifact
from tasks import (
    check_extract_completion_task,
    create_extract_tasks,
    determine_target_quarter,
    execute_paginated_extraction,
    fetch_report_catalog,
    handle_fallback,
    ingest_registry,
    promote_raw_to_hot,
    publish_institution_events,
)
from tasks.paginated_extraction import RetryablePaginatedExtractionError

# Configuration
MAX_EXTRACTION_WORKERS = int(
    os.getenv("MAX_EXTRACTION_WORKERS", "5")
)  # Parallel extraction workers
EXTRACTION_LOOP_MAX_ITERATIONS = int(
    os.getenv("EXTRACTION_LOOP_MAX_ITERATIONS", "1000")
)  # Safety limit


@task(
    name="check_registry_exists",
    retries=1,
    retry_delay_seconds=30,
    timeout_seconds=60,
    log_prints=True,
    tags=["registry", "check"],
)
def check_registry_exists(ano_mes: str) -> bool:
    """
    Check if registry data exists for the given ano_mes.

    Args:
        ano_mes: Target quarter in YYYYMM format

    Returns:
        True if registry exists, False otherwise
    """
    import psycopg2
    from psycopg2.extras import RealDictCursor

    logger = get_run_logger()
    database_url = os.getenv("DATABASE_URL")

    if not database_url:
        raise ValueError("DATABASE_URL environment variable is required")

    try:
        conn = psycopg2.connect(database_url)
        cur = conn.cursor(cursor_factory=RealDictCursor)

        try:
            # Check if any institutions exist for this ano_mes
            cur.execute(
                """
                SELECT COUNT(*) as count
                FROM financial_data.institution
                WHERE ano_mes_ref = %s
            """,
                (ano_mes,),
            )

            result = cur.fetchone()
            count = result["count"] if result else 0

            exists = count > 0
            logger.info(
                f"Registry check for {ano_mes}: {count} institutions found (exists={exists})"
            )

            return exists

        finally:
            cur.close()
            conn.close()

    except Exception:
        logger.exception("Error checking registry existence")
        raise


@task(
    name="get_reports_for_quarter",
    retries=1,
    retry_delay_seconds=30,
    timeout_seconds=60,
    log_prints=True,
    tags=["extract", "control"],
)
def get_reports_for_quarter(ano_mes: str) -> list[dict[str, str]]:
    """
    Get list of reports that need extraction for the given quarter.

    This queries ifdata_load_control to find reports that are not yet completed.

    Args:
        ano_mes: Target quarter in YYYYMM format

    Returns:
        List of report dictionaries with nome_relatorio
    """
    import psycopg2
    from psycopg2.extras import RealDictCursor

    logger = get_run_logger()
    database_url = os.getenv("DATABASE_URL")

    if not database_url:
        raise ValueError("DATABASE_URL environment variable is required")

    try:
        conn = psycopg2.connect(database_url)
        cur = conn.cursor(cursor_factory=RealDictCursor)

        try:
            # Get reports that are not completed for this quarter
            cur.execute(
                """
                SELECT DISTINCT nome_relatorio
                FROM financial_data.ifdata_load_control
                WHERE ano_mes = %s
                  AND completed = FALSE
                ORDER BY nome_relatorio
            """,
                (ano_mes,),
            )

            reports = [
                {"nome_relatorio": row["nome_relatorio"]} for row in cur.fetchall()
            ]

            logger.info(f"Found {len(reports)} incomplete reports for {ano_mes}")

            return reports

        finally:
            cur.close()
            conn.close()

    except Exception:
        logger.exception("Error getting reports for quarter")
        raise


@flow(
    name="bacen-ifdata-ingestion",
    log_prints=True,
    retries=0,  # Flow-level retries disabled, task-level retries handle it
)
def bacen_ifdata_ingestion_flow(
    target_ano_mes: str | None = None,
    force_registry_refresh: bool = False,
) -> dict[str, Any]:
    """
    Main BACEN IFData ingestion flow.

    This flow orchestrates the complete ingestion pipeline:
    1. Determines target quarter (or uses provided target_ano_mes)
    2. Ensures registry exists (ingests if needed)
    3. Fetches report catalog
    4. Creates extract tasks
    5. Executes paginated extraction with parallelism
    6. Checks extract completion
    7. Promotes raw data to hot table
    8. Publishes Kafka events for institution changes
    9. Handles errors with fallback
    10. Provides observability (logs, metrics, artifacts)

    Args:
        target_ano_mes: Optional target quarter in YYYYMM format.
            If None, determines target quarter automatically based on publication windows.
            If provided, bypasses publication window checks (for manual runs).
        force_registry_refresh: If True, forces registry ingestion even if it exists.
            Default: False.

    Returns:
        Dictionary with flow execution statistics:
        - batch_id: Flow run identifier
        - target_ano_mes: Target quarter processed
        - status: "success" or "failed"
        - registry_ingested: Whether registry was ingested
        - changed_institutions: Number of institutions that changed
        - reports_processed: Number of reports processed
        - extraction_stats: Statistics from extraction phase
        - promotion_stats: Statistics from promotion phase
        - kafka_stats: Statistics from Kafka publishing
        - errors: List of any errors encountered
    """
    logger = get_run_logger()
    batch_id = str(uuid4())

    logger.info(f"Starting BACEN IFData ingestion flow. Batch ID: {batch_id}")

    # Initialize statistics
    stats = {
        "batch_id": batch_id,
        "target_ano_mes": None,
        "status": "failed",
        "registry_ingested": False,
        "changed_institutions": [],
        "reports_processed": [],
        "extraction_stats": {},
        "promotion_stats": {},
        "kafka_stats": {},
        "errors": [],
    }

    try:
        # Step 1: Determine target quarter
        if target_ano_mes:
            logger.info(f"Using provided target_ano_mes: {target_ano_mes}")
            stats["target_ano_mes"] = target_ano_mes
        else:
            logger.info("Determining target quarter automatically...")
            target_ano_mes = determine_target_quarter()

            if not target_ano_mes:
                logger.info(
                    "No target quarter available (publication window not met). Skipping execution."
                )
                stats["status"] = "skipped"
                stats["reason"] = (
                    "No target quarter available (publication window not met)"
                )
                return stats

            stats["target_ano_mes"] = target_ano_mes
            logger.info(f"Target quarter determined: {target_ano_mes}")

        # Step 2: Ensure registry exists
        registry_exists = check_registry_exists(target_ano_mes)

        if not registry_exists or force_registry_refresh:
            logger.info(
                f"Registry {'does not exist' if not registry_exists else 'refresh forced'} for {target_ano_mes}. Ingesting..."
            )
            changed_institutions = ingest_registry(target_ano_mes)
            stats["registry_ingested"] = True
            stats["changed_institutions"] = changed_institutions
            logger.info(
                f"Registry ingestion complete. {len(changed_institutions)} institutions changed (NEW/CHANGED)"
            )
        else:
            logger.info(
                f"Registry already exists for {target_ano_mes}. Skipping ingestion."
            )
            stats["registry_ingested"] = False

        # Verify registry exists after ingestion attempt
        registry_exists_after = check_registry_exists(target_ano_mes)
        if not registry_exists_after:
            logger.warning(
                f"Registry still does not exist for {target_ano_mes} after ingestion attempt. Skipping extraction."
            )
            stats["status"] = "skipped"
            stats["reason"] = "Registry does not exist after ingestion attempt"
            return stats

        # Step 3: Fetch report catalog
        logger.info("Fetching report catalog...")
        reports = fetch_report_catalog()
        logger.info(f"Fetched {len(reports)} reports from catalog")

        # Step 4: Create extract tasks
        logger.info(f"Creating extract tasks for {len(reports)} reports...")
        extract_task_stats = create_extract_tasks(target_ano_mes, reports)
        logger.info(
            f"Extract tasks created: {extract_task_stats.get('created', 0)} new, "
            f"{extract_task_stats.get('existing', 0)} existing, "
            f"{extract_task_stats.get('total', 0)} total"
        )

        # Step 5: Execute paginated extraction with parallelism
        logger.info("Starting paginated extraction...")
        extraction_iterations = 0
        extraction_stats = {
            "tasks_executed": 0,
            "tasks_success": 0,
            "tasks_empty": 0,
            "tasks_error": 0,
            "rows_extracted": 0,
        }

        # Loop until no more PENDING tasks
        while extraction_iterations < EXTRACTION_LOOP_MAX_ITERATIONS:
            # Execute extraction tasks in parallel (up to MAX_EXTRACTION_WORKERS)
            # Note: Prefect 3.x handles parallelism via work pools and work queues
            # For true parallelism, we need to submit multiple tasks and let Prefect schedule them
            # For now, we execute sequentially but Prefect can parallelize if multiple workers are available

            try:
                extraction_result = execute_paginated_extraction()
            except RetryablePaginatedExtractionError as e:
                # Task will be retried by Prefect, but we continue the loop
                # The task status is already set to PENDING for retry
                logger.warning(
                    f"Extraction task raised retryable error (will be retried by Prefect): {e}"
                )
                extraction_stats["tasks_error"] += 1
                extraction_iterations += 1
                continue

            if not extraction_result or extraction_result.get("task_key") is None:
                # No more tasks available
                logger.info("No more PENDING tasks available. Extraction complete.")
                break

            # Update statistics
            extraction_stats["tasks_executed"] += 1
            if extraction_result.get("status") == "SUCCESS":
                extraction_stats["tasks_success"] += 1
                extraction_stats["rows_extracted"] += extraction_result.get(
                    "rows_inserted", 0
                )
            elif extraction_result.get("status") == "EMPTY":
                extraction_stats["tasks_empty"] += 1
            elif extraction_result.get("status") == "ERROR":
                extraction_stats["tasks_error"] += 1

            extraction_iterations += 1

            # Log progress periodically
            if extraction_iterations % 10 == 0:
                logger.info(
                    f"Extraction progress: {extraction_iterations} iterations, "
                    f"{extraction_stats['tasks_executed']} tasks executed, "
                    f"{extraction_stats['rows_extracted']} rows extracted"
                )

        if extraction_iterations >= EXTRACTION_LOOP_MAX_ITERATIONS:
            logger.warning(
                f"Extraction loop reached maximum iterations ({EXTRACTION_LOOP_MAX_ITERATIONS}). "
                "Some tasks may still be pending."
            )

        stats["extraction_stats"] = extraction_stats
        logger.info(
            f"Extraction phase complete: {extraction_stats['tasks_executed']} tasks executed, "
            f"{extraction_stats['rows_extracted']} rows extracted"
        )

        # Step 6: Check extract completion and promote for each report
        logger.info("Checking extract completion and promoting data...")

        # Use reports from catalog (we created extract tasks for all of them)
        # Note: get_reports_for_quarter queries ifdata_load_control which may not have records yet
        # We process all reports from the catalog that we created extract tasks for
        promotion_stats_list = []

        for report in reports:
            nome_relatorio = report["nome_relatorio"]

            # Check extract completion
            completion_result = check_extract_completion_task(
                target_ano_mes, nome_relatorio
            )

            if not completion_result.get("is_complete"):
                logger.warning(
                    f"Extraction not complete for {target_ano_mes}/{nome_relatorio}. "
                    f"Status: {completion_result.get('status_breakdown', {})}"
                )
                continue

            logger.info(
                f"Extraction complete for {target_ano_mes}/{nome_relatorio}. Promoting..."
            )

            # Promote raw to hot
            promotion_result = promote_raw_to_hot(target_ano_mes, nome_relatorio)
            promotion_stats_list.append(
                {
                    "nome_relatorio": nome_relatorio,
                    "stats": promotion_result,
                }
            )

            logger.info(
                f"Promotion complete for {target_ano_mes}/{nome_relatorio}: "
                f"{promotion_result.get('records_promoted', 0)} records promoted"
            )

        stats["promotion_stats"] = promotion_stats_list
        stats["reports_processed"] = [r["nome_relatorio"] for r in promotion_stats_list]

        logger.info(
            f"Promotion phase complete: {len(promotion_stats_list)} reports processed"
        )

        # Step 7: Publish Kafka events for institution changes
        if stats["changed_institutions"]:
            logger.info(
                f"Publishing Kafka events for {len(stats['changed_institutions'])} changed institutions..."
            )
            kafka_stats = publish_institution_events(stats["changed_institutions"])
            stats["kafka_stats"] = kafka_stats
            logger.info(
                f"Kafka publishing complete: {kafka_stats.get('published', 0)} published, "
                f"{kafka_stats.get('failed', 0)} failed"
            )
        else:
            logger.info("No institution changes detected. Skipping Kafka publishing.")
            stats["kafka_stats"] = {"published": 0, "failed": 0, "total": 0}

        # Step 8: Create summary artifact
        summary = _create_summary_artifact(stats)
        create_markdown_artifact(
            key=f"bacen-ifdata-ingestion-summary-{batch_id}",
            markdown=summary,
        )

        stats["status"] = "success"
        logger.info(f"Flow completed successfully. Batch ID: {batch_id}")

        return stats

    except Exception as e:
        logger.error(f"Flow failed: {e}", exc_info=True)
        stats["errors"].append(str(e))

        # Handle fallback
        try:
            fallback_context = {
                "batch_id": batch_id,
                "target_ano_mes": stats.get("target_ano_mes"),
                "stage": "unknown",
            }
            handle_fallback(
                payload=stats,
                error=str(e),
                task_name="bacen-ifdata-ingestion-flow",
                context=fallback_context,
            )
        except Exception as fallback_error:
            logger.error(
                f"Fallback handler also failed: {fallback_error}", exc_info=True
            )
            stats["errors"].append(f"Fallback error: {fallback_error!s}")

        raise


def _create_summary_artifact(stats: dict[str, Any]) -> str:
    """
    Create markdown summary artifact for the flow run.

    Args:
        stats: Flow execution statistics

    Returns:
        Markdown string for artifact
    """
    batch_id = stats.get("batch_id", "unknown")
    target_ano_mes = stats.get("target_ano_mes", "N/A")
    status = stats.get("status", "unknown")

    summary = f"""# BACEN IFData Ingestion Summary

**Batch ID**: `{batch_id}`

**Execution Time**: {datetime.now(UTC).isoformat()}

## Target Quarter

- **AnoMes**: `{target_ano_mes}`
- **Status**: {status.upper()}

## Registry Ingestion

- **Ingested**: {"Yes" if stats.get("registry_ingested") else "No"}
- **Changed Institutions**: {len(stats.get("changed_institutions", []))}

## Extraction Phase

"""

    extraction_stats = stats.get("extraction_stats", {})
    summary += f"""
- **Tasks Executed**: {extraction_stats.get("tasks_executed", 0)}
- **Tasks Success**: {extraction_stats.get("tasks_success", 0)}
- **Tasks Empty**: {extraction_stats.get("tasks_empty", 0)}
- **Tasks Error**: {extraction_stats.get("tasks_error", 0)}
- **Rows Extracted**: {extraction_stats.get("rows_extracted", 0)}
"""

    summary += "\n## Promotion Phase\n\n"
    promotion_stats_list = stats.get("promotion_stats", [])
    summary += f"- **Reports Processed**: {len(promotion_stats_list)}\n\n"

    for report_stat in promotion_stats_list:
        nome_relatorio = report_stat.get("nome_relatorio", "unknown")
        report_stats = report_stat.get("stats", {})
        summary += f"### {nome_relatorio}\n"
        summary += (
            f"- **Records Promoted**: {report_stats.get('records_promoted', 0)}\n"
        )
        summary += f"- **Records Updated**: {report_stats.get('records_updated', 0)}\n"
        summary += (
            f"- **Records Inserted**: {report_stats.get('records_inserted', 0)}\n\n"
        )

    summary += "## Kafka Publishing\n\n"
    kafka_stats = stats.get("kafka_stats", {})
    summary += f"""
- **Total Events**: {kafka_stats.get("total", 0)}
- **Published**: {kafka_stats.get("published", 0)}
- **Failed**: {kafka_stats.get("failed", 0)}
- **DLQ Published**: {kafka_stats.get("dlq_published", 0)}
"""

    errors = stats.get("errors", [])
    if errors:
        summary += "\n## Errors\n\n"
        for error in errors:
            summary += f"- {error}\n"

    summary += "\n## Status\n\n"
    if status == "success":
        summary += "✅ Flow completed successfully\n"
    elif status == "skipped":
        summary += f"⏭️ Flow skipped: {stats.get('reason', 'Unknown reason')}\n"
    else:
        summary += "❌ Flow failed\n"

    return summary


if __name__ == "__main__":
    # Allow running flow directly for testing
    import sys

    target_ano_mes = None
    force_registry_refresh = False

    if len(sys.argv) > 1:
        target_ano_mes = sys.argv[1]

    if len(sys.argv) > 2 and sys.argv[2].lower() == "true":
        force_registry_refresh = True

    result = bacen_ifdata_ingestion_flow(
        target_ano_mes=target_ano_mes,
        force_registry_refresh=force_registry_refresh,
    )

    print("\nFlow execution result:")
    print(f"Status: {result.get('status')}")
    print(f"Target AnoMes: {result.get('target_ano_mes')}")
    print(f"Batch ID: {result.get('batch_id')}")

