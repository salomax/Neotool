"""Tasks package for BACEN IFData ingestion workflows."""

from .extract_completion import check_extract_completion_task
from .extract_task_creation import create_extract_tasks
from .fallback import handle_fallback
from .kafka_publish import publish_institution_events
from .paginated_extraction import execute_paginated_extraction
from .promotion import promote_raw_to_hot
from .quarter_determination import determine_target_quarter
from .registry_ingestion import ingest_registry
from .report_catalog_fetch import fetch_report_catalog

__all__ = [
    "check_extract_completion_task",
    "create_extract_tasks",
    "determine_target_quarter",
    "execute_paginated_extraction",
    "fetch_report_catalog",
    "handle_fallback",
    "ingest_registry",
    "promote_raw_to_hot",
    "publish_institution_events",
]
