---
title: Batch Workflow Template
type: template
category: workflow
status: current
version: 1.0.0
tags: [template, workflow, batch, prefect, kafka]
ai_optimized: true
search_keywords: [batch workflow template, prefect, kafka, cron]
related:
  - 05-standards/workflow-standards/batch-workflow-standard.md
  - 06-workflows/deployment-workflow.md
---

# Batch Workflow Template

> **Purpose**: Provide a repeatable structure to document any batch job (Prefect + Kafka + Kotlin consumer) before implementation.

Fill this template and store it alongside the feature or workflow doc so future jobs follow the same contracts and validation.

## Metadata

| Field | Value |
| --- | --- |
| Workflow Name | `<Name>` |
| Description | `<Short summary>` |
| Owner / Squad | `<Team>` |
| Status | `<draft / ready / live>` |
| Environments | `<dev / staging / prod>` |
| Schedule / Trigger | `<Cron string or trigger description>` |
| Runbook URL | `<Link to runbook>` |

## Objective & Scope

- **Business Goal**: `<What outcome?>`
- **Data Domain**: `<Domain/entities involved>`
- **In / Out of Scope**: `<List assumptions, dependencies>`

## Flow Overview

1. `<Task 1 – Extract ...>`
2. `<Task 2 – Transform ...>`
3. `<Task 3 – Publish ...>`
4. `<Task 4 – Notify / fallback ...>`

> Add Mermaid/ASCII diagram if needed.

### Task Inventory

| Task | Description | Retries | Timeout | Inputs | Outputs | Observability |
| --- | --- | --- | --- | --- | --- | --- |
| `fetch_source` | `<REST, file, etc>` | `<3 @ 60s>` | `<120s>` | `<params>` | `<raw data>` | `<logs/metrics>` |
| `transform_records` | `<Normalize>` | `<2>` | `<60s>` | `<raw>` | `<normalized>` | `<artifact summary>` |
| ... | ... | ... | ... | ... | ... | ... |

Describe cache/idempotency rules per task.

## Deployment & Scheduling

- **Flow File**: `workflow/example/<domain>/flows/<file>.py` or `pipelines/<file>.py`
- **Flow Name**: `<Prefect flow name>`
- **Prefect Version**: 3.6.5+ (uses work pools and work queues)
- **Deployment Configuration**: `prefect.yaml` (Prefect 3.x standard)
- **Deployment Command**:
  ```bash
  prefect deploy --name <deployment-name>
  ```
  This uses the `prefect.yaml` configuration file.
- **Prerequisites**:
  - Work pool: `prefect work-pool create <pool-name> --type <process|docker|kubernetes>`
  - Work queue: `prefect work-queue create <queue-name> --pool <pool-name>`
- **Parameters**: `<force_full, since, dry_run>`
- **Concurrency Guardrails**: Prefect 3.x work queues prevent overlapping runs
- **Environment Variables / Secrets**: `<SWAPI_BASE_URL, KAFKA_BROKERS, ...>`
- **Storage**: 
  - Local development: Answer "n" to remote storage (uses filesystem)
  - Production: Use remote storage block (GitHub, S3, GCS, etc.)

## Python Package Structure (Python Consumers)

For Python Kafka consumers, use proper package structure with `pyproject.toml`:

- **Package Structure**:
  ```
  workflow/<domain>/<feature>/
  ├── pyproject.toml              # Package configuration
  ├── Dockerfile                  # Docker deployment
  ├── src/<package_name>/         # Source code
  │   ├── flows/consumer.py       # Main consumer
  │   └── tasks/                  # Business logic
  └── tests/                      # Tests
  ```

- **Package Configuration** (`pyproject.toml`):
  ```toml
  [project]
  name = "<feature-name>"
  version = "0.1.0"
  dependencies = [
      "neotool-common",  # Shared Neotool utilities
      "kafka-python>=2.0.0",
      # ... other dependencies
  ]

  [project.scripts]
  <feature>-consumer = "<package>.flows.consumer:main"
  ```

- **Development Setup**:
  ```bash
  # Install neotool-common (one-time)
  cd workflow/python/common && pip install -e ".[dev]"

  # Install consumer package
  cd ../financial_data/<feature> && pip install -e ".[dev]"

  # Run consumer (no PYTHONPATH needed)
  <feature>-consumer
  ```

- **Import Pattern**:
  - From neotool-common: `from neotool_common.consumer_base import KafkaConsumerRunner`
  - Within package: `from <package>.tasks.processor import process_message`

See [Batch Workflow Standard](../../05-standards/workflow-standards/batch-workflow-standard.md#45-python-package-structure-required-for-python-consumers) for complete requirements.

## Docker Deployment (Kafka Consumers)

For Kafka consumers (Python batch processors), containerize using Docker:

- **Dockerfile Location**: `workflow/<domain>/<feature>/Dockerfile`
- **Base Image**: `python:3.14-alpine`
- **Build Context**: `workflow/` directory (to access `common/` module)
- **Build Command**:
  ```bash
  docker build -f workflow/<domain>/<feature>/Dockerfile -t <consumer-name> workflow/
  ```
- **Key Steps**:
  1. Install `neotool-common` from local path
  2. Copy and install consumer package
  3. Use entry point defined in `pyproject.toml`
- **Docker Compose Service**: Add to `infra/docker/docker-compose.yml` or `docker-compose.local.yml`
- **Health Check**: Expose health endpoint on port 8080 (configurable)
- **Security**: Run as non-root user (UID 1000)

**Example Dockerfile**:
```dockerfile
FROM python:3.14-alpine
WORKDIR /app

# Install neotool-common
COPY --chown=consumer:consumer ../../common /tmp/neotool-common/
RUN pip install --no-cache-dir /tmp/neotool-common && rm -rf /tmp/neotool-common

# Install application
COPY --chown=consumer:consumer . /app/
RUN pip install --no-cache-dir /app

ENTRYPOINT ["<feature>-consumer"]
```

See [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md#docker-deployment) for complete Dockerfile template and best practices.

## Kafka Contracts

| Topic | Direction | Key | Schema Link | Retention | DLQ |
| --- | --- | --- | --- | --- | --- |
| `<domain.entity.v1>` | Produce/Consume | `<record_id>` | `<Schema location>` | `<7d>` | `<domain.entity.dlq>` |

- **Producer Settings**: `<acks, idempotence, compression>`
- **Consumer Module**: `<service/kotlin/.../batch/...>` (include owner, repo link)
- **DLQ Strategy**: `<topic / storage / alert>`

## Testing & Validation

- **Prefect Tests**: `<unit/integration plan>`
- **Consumer Tests**: `<unit/integration plan>`
- **End-to-End Drill**: `<command or steps>`

### Readiness Checklist

- [ ] Cron registered & documented
- [ ] Topics + schemas provisioned
- [ ] Observability (metrics/logs/events) defined
- [ ] DLQ + fallback documented
- [ ] Runbook attached

## Notes

Additional considerations, TODOs, or open questions.
