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

### 4.4 Consumer Requirements (Kotlin)
- Consumers live in `service/kotlin/<module>/batch/**`.
- MUST use dedicated consumer groups per job.
- Manual commits only after successful processing.
- Implement retry-withbackoff inside consumer (Resilience4j or manual).
- Hard failures (poison messages) go to DLQ topic and raise alerts.

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

- Every job MUST have a completed [Batch Workflow Template](../../08-templates/documents/batch-workflow-template.md) stored under `docs/07-examples` or project-specific folder.
- Every job MUST have a runbook (operations guide) committed alongside the job docs (e.g., under `docs/07-examples/<job>/` or the service module’s docs). The runbook MUST cover: how to start/stop the job, required env vars/secrets, health checks, Kafka commands/topics/groups, DLQ handling, troubleshooting steps, and escalation. Use the SWAPI ETL runbook as a reference pattern.
- Link template + runbook inside feature specs when relevant.
- Update `docs/MANIFEST.md` when adding new job docs.

## 7. Compliance

- PRs introducing new jobs MUST reference this standard and include evidence (template link, tests, screenshots).
- Non-compliant jobs cannot be deployed to staging/production.

## References

- [Observability Rules](../observability-standards.md)
- [Testing Standards](../testing-standards/unit-test-standards.md)
- [Deployment Workflow](../../06-workflows/deployment-workflow.md)
