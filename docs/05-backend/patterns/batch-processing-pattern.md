---
title: Batch Workflow Standard
type: standard
category: workflow
status: current
version: 1.1.0
tags: [workflow, batch, prefect, kafka, kotlin, cron, retries]
ai_optimized: true
search_keywords: [batch job, workflow standard, prefect, kafka, cron, retry, fallback, prefect3]
related:
  - 04-patterns/backend-patterns/repository-pattern.md
  - 05-standards/observability-standards.md
  - 05-standards/testing-standards/unit-test-standards.md
  - 06-workflows/deployment-workflow.md
---

# Batch Workflow Standard

> **Purpose**: Define mandatory rules for designing, implementing, and operating batch workflows orchestrated via Prefect 3.x and processed by Kotlin services using Kafka.

This standard applies to every job that runs under `workflow/` or `pipelines/` and to all Kotlin consumers that belong to `service/kotlin/**/batch`.

**Note**: This standard is based on Prefect 3.6.5+ which uses work pools and work queues instead of the older deployment model.

## 1. Architecture Rules

### 1.1 Separation of Concerns
- **Rule**: Orchestration lives in Prefect flows (Python) and execution/processing logic lives in Kotlin services.
- **Rationale**: Keeps scheduling, retries, and monitoring centralized while maximizing reuse of Kotlin domain services.

### 1.2 Deterministic Flow Graphs
- **Rule**: Flows MUST define explicit tasks for *extract*, *transform*, *load/publish*, and *notify/fallback* stages. Dynamic task creation is discouraged unless documented.
- **Implementation**: Use Prefect task primitives, named tasks, and tags to identify each stage.

### 1.3 Idempotency
- **Rule**: Each task MUST be idempotent. Inputs must contain deterministic identifiers (`batch_id`, `record_id`) so retries never duplicate side effects.
- **Verification**: Provide idempotency notes inside the workflow template when registering the job.

## 2. Scheduling & Deployments

### 2.1 Cron Requirements
- **Rule**: All recurring jobs MUST specify a `CronSchedule` (UTC) in Prefect deployments. Ad-hoc jobs MUST still document trigger mechanism (manual CLI/API).
- **Rule**: Cron strings MUST avoid overlaps with other critical jobs; annotate environment/timezone in the template.

### 2.2 Deployments
- Prefect 3.x uses `prefect.yaml` configuration files (located under `workflow/` or `pipelines/`).
- Prefect 3.x requires work pools and work queues (replaces older deployment model).
- Deployment command: `prefect deploy --name <deployment-name>` (uses `prefect.yaml`).
- For local development: use `process` work pool type, answer "n" to remote storage.
- For production: use `docker`/`kubernetes` work pool type, use remote storage (GitHub, S3, etc.).
- Store deployment configuration, parameters, and environment variables in `prefect.yaml` and template.

### 2.3 Concurrency Controls
- **Rule**: Set Prefect concurrency limits (tags or concurrency key) to prevent overlapping runs per environment.

### 2.4 Database Migrations (Python Workflows)
- **Rule**: Python-based workflows using pyway for database migrations MUST follow these requirements:
  - **Migration Tool**: Use `pyway` (Python migration tool) for database schema management
  - **Config File**: Place `pyway.conf` or `pyway.yaml` in the workflow root directory (e.g., `workflow/{domain}/`)
  - **Migration Directory**: Store migrations in `workflow/{domain}/migrations/` directory
  - **Naming Convention**: Follow format `V{version}__{description}.sql` (e.g., `V1_1__create_schema.sql`)
  - **Running Migrations**: 
    - Always run pyway from the directory containing `pyway.conf` (workflow root)
    - Use `-c pyway.conf` flag to explicitly specify config file: `pyway -c pyway.conf migrate`
    - Check status with: `pyway -c pyway.conf info`
  - **History Table Naming**: Pyway history tables MUST follow the pattern `pyway_schema_history_{domain}` (e.g., `pyway_schema_history_financial_data`). This distinguishes them from Flyway history tables which use `flyway_schema_history_{service}` (e.g., `flyway_schema_history_app`). The table should be in the `public` schema: `public.pyway_schema_history_{domain}`
  - **Schema Bootstrap**: If pyway's tracking table is configured in a custom schema (e.g., `financial_data.pyway`), the schema MUST exist before running migrations. For first-time setup:
    - Create the schema manually first (one-time bootstrap)
    - Then run migrations normally
    - Migrations themselves use `IF NOT EXISTS` for idempotency
    - **Note**: Using `public.pyway_schema_history_{domain}` for the tracking table eliminates the need for schema bootstrap, as the `public` schema always exists in PostgreSQL
  - **Idempotency**: All migrations MUST use `IF NOT EXISTS` / `IF EXISTS` clauses to be safely re-runnable
  - **Schema Organization**: Follow [Database Schema Standards](../database-standards/schema-standards.md) - all tables must be in named schemas, never in `public`
  - **Example Structure**:
    ```
    workflow/{domain}/
    ├── pyway.conf              # Pyway configuration
    ├── migrations/              # Migration files
    │   ├── V1_1__create_schema.sql
    │   └── V1_2__create_table.sql
    └── {workflow_name}/         # Python workflow code
    ```
- **Rationale**: Ensures consistent database setup across environments and prevents migration execution errors.

## 3. Task Design

### 3.1 Observability
- All tasks MUST emit structured logs and Prefect events with `batch_id`, `task_name`, `records_processed`.
- Use Prefect Artifacts for aggregate stats (counts, failure reasons).
- Each Kotlin consumer MUST expose Micrometer metrics: processed count, ack latency, DLQ count (tie-in with Observability Standard).

### 3.2 Retries & Timeouts
- Each task MUST declare retries with exponential backoff:
  - Extract: ≥3 attempts, 1–5 min backoff.
  - Transform: ≥2 attempts, 30–60 sec backoff.
  - Publish: ≥5 attempts, 30 sec backoff.
- Set timeouts per task to detect hangs and escalate states.

### 3.3 Fallbacks
- Every flow MUST define a fallback strategy when retries exhaust:
  - Persist payload to durable storage (S3, GCS, disk) with metadata.
  - Notify responsible channel (Slack/PagerDuty/email).
  - Optionally trigger compensating run (manual or automated).

## 4. Kafka Integration Rules

### 4.1 Topic Naming
- Produce to topics under the pattern `{domain}.{entity}.{version}` (e.g., `people.ingest.v1`).
- DLQ topics MUST append `.dlq`.

### 4.2 Schema & Serialization
- **Rule**: Schemas MUST be documented (JSON Schema or Avro) and versioned. Include link in template.
- Payload MUST contain:
  - `batch_id` (UUIDv4/v7)
  - `record_id`
  - `ingested_at` (ISO 8601)
  - `payload` (domain fields)
- Use deterministic keys (usually `record_id` or `domain_id`) for ordering.

### 4.3 Producer Settings
- Required configs: `acks=all`, `enable.idempotence=true`, `compression.type=zstd|snappy`, `linger.ms<=10`.
- Retries MUST be enabled (`retries>=5`) and errors forwarded to fallback storage.

### 4.4 Consumer Requirements

**Python Consumers** (for batch processing):
- Consumers live in `workflow/<domain>/<feature>/` as installable packages.
- MUST use the shared `neotool-common` package for retry logic and DLQ handling.
- MUST be structured as proper Python packages with `pyproject.toml`.
- MUST use dedicated consumer groups per job.
- Manual commits only after successful processing.
- Implement retry logic using `neotool_common.consumer_base.KafkaConsumerRunner`.
- Hard failures (poison messages) go to DLQ topic and raise alerts.
- **All consumer implementations MUST comply with the [Kafka Consumer Standard](./kafka-consumer-standard.md).**

**Kotlin Consumers** (for real-time processing):
- Consumers live in `service/kotlin/<module>/batch/**`.
- MUST use dedicated consumer groups per job.
- Manual commits only after successful processing.
- Implement retry-with-backoff inside consumer (Resilience4j or manual).
- Hard failures (poison messages) go to DLQ topic and raise alerts.
- **All consumer implementations MUST comply with the [Kafka Consumer Standard](./kafka-consumer-standard.md).**

### 4.5 Python Package Structure (Required for Python Consumers)

All Python batch consumers MUST follow this package structure:

```
workflow/<domain>/<feature>/
├── pyproject.toml              # Package configuration with dependencies
├── Dockerfile                  # Docker deployment configuration
├── .dockerignore               # Docker build exclusions
├── pytest.ini                  # Test configuration (no pythonpath needed)
├── requirements.txt            # Optional: can use pyproject.toml instead
├── src/                        # Source code directory
│   └── <package_name>/         # Package namespace
│       ├── __init__.py         # Package initialization
│       ├── flows/              # Consumer entry points
│       │   └── consumer.py     # Main consumer loop
│       └── tasks/              # Business logic modules
│           ├── message.py      # Message data classes
│           ├── processor.py    # Processing logic
│           ├── config.py       # Configuration
│           └── ...             # Other task modules
└── tests/                      # Test files
    └── test_*.py
```

**Key Requirements**:
- Package name in `pyproject.toml` MUST depend on `neotool-common`
- Source code MUST be under `src/<package_name>/` (src layout)
- NO `PYTHONPATH` manipulation - use proper package imports
- Imports MUST use fully qualified names: `from <package_name>.tasks.processor import ...`
- Imports from common: `from neotool_common.consumer_base import KafkaConsumerRunner`
- Entry points MUST be defined in `pyproject.toml` [project.scripts] section
- Dockerfile MUST install `neotool-common` first, then the consumer package

**Example `pyproject.toml`**:
```toml
[project]
name = "institution-enhancement"
version = "0.1.0"
dependencies = [
    "neotool-common",
    "kafka-python>=2.0.0",
    "psycopg2-binary>=2.9.0",
    # ... other dependencies
]

[project.scripts]
institution-enhancement-consumer = "institution_enhancement.flows.consumer:main"
```

**Example Dockerfile**:
```dockerfile
FROM python:3.14-alpine
WORKDIR /app

# Install neotool-common from local path
COPY --chown=consumer:consumer ../../common /tmp/neotool-common/
RUN pip install --no-cache-dir /tmp/neotool-common && rm -rf /tmp/neotool-common

# Copy and install application
COPY --chown=consumer:consumer . /app/
RUN pip install --no-cache-dir /app

ENTRYPOINT ["<package-name>-consumer"]
```

**Development Setup**:
```bash
# Install neotool-common (one-time)
cd workflow/python/common
pip install -e ".[dev]"

# Install consumer package (editable mode)
cd ../financial_data/<feature>
pip install -e ".[dev]"

# Run consumer (no PYTHONPATH needed)
<package-name>-consumer
```

**Rationale**: Proper package structure eliminates PYTHONPATH hacks, enables proper dependency management, improves IDE support, and follows Python packaging best practices.

## 5. Testing & Validation

### 5.1 Prefect Flow Testing
- Provide unit tests for task functions (mock network + Kafka).
- Smoke test entire flow locally using Prefect CLI against docker-compose stack.

### 5.2 Kotlin Consumer Testing
- Unit tests verifying success path, retry path, DLQ path.
- Optional integration tests using Testcontainers Kafka.

### 5.3 Validation Checklist
Before releasing a new job:
1. Cron registered and visible in Prefect UI.
2. Tasks emit metrics/logs (verify via Prefect + Grafana).
3. Kafka topic reachable and schema validated.
4. Consumer processed sample data end-to-end.
5. Fallback path manually exercised.
6. Runbook updated (see template).

## 6. Documentation Requirements

- Every job MUST have a completed [Batch Workflow Template](../../08-templates/documents/batch-workflow-template.md) stored under `docs/90-examples` or project-specific folder.
- Every job MUST have a runbook (operations guide) committed alongside the job docs (e.g., under `docs/90-examples/<job>/` or the service module’s docs). The runbook MUST cover: how to start/stop the job, required env vars/secrets, health checks, Kafka commands/topics/groups, DLQ handling, troubleshooting steps, and escalation. Use the SWAPI ETL runbook as a reference pattern.
- Link template + runbook inside feature specs when relevant.
- Update `docs/manifest.md` when adding new job docs.

## 7. Compliance

- PRs introducing new jobs MUST reference this standard and include evidence (template link, tests, screenshots).
- Non-compliant jobs cannot be deployed to staging/production.

## References

- [Observability Rules](../observability-standards.md)
- [Testing Standards](../testing-standards/unit-test-standards.md)
- [Deployment Workflow](../../06-workflows/deployment-workflow.md)
