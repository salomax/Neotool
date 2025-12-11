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
