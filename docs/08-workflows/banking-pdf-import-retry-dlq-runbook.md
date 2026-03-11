---
title: Banking PDF Import Retry and DLQ Runbook
type: runbook
category: workflows
status: current
version: 1.0.0
tags: [runbook, kafka, retry, dlq, banking, assistant, pdf-extractor]
related:
  - 03-features/personal-finance/phase-4-pdf-bill-import.md
  - 06-contracts/kafka/banking-pdf-import/README.md
  - service/kotlin/banking/README.md
---

# Banking PDF Import Retry and DLQ Runbook

Operational policy for retry, dead-letter handling, and replay across:
- `banking` (Kotlin)
- `assistant` (Kotlin)
- `pdf-extractor` (Python)

## Quick Reference

| Item | Value |
|---|---|
| Primary correlation key | `job_id` (Kafka key + payload field) |
| Callback context | `callback_context.job_id/account_id/user_id` |
| Banking import request topic | `banking.import.file-requested` |
| Banking import quarantine topic | `banking.import.file-requested.DLT` |
| PDF extraction topics | `pdf-extractor.submitted`, `pdf-extractor.extracted`, `pdf-extractor.dlq` |
| Assistant topics | `assistant.banking-reader.requested`, `assistant.banking-pdf-reader.done`, `assistant.banking-pdf-reader.failed` |

## Retry Policy Matrix

| Service | Stage | Retry policy | Backoff | Terminal action |
|---|---|---|---|---|
| `pdf-extractor` | Signed URL download | 3 attempts (`DOWNLOAD_MAX_RETRIES`) | Exponential: `0.5s`, `1.0s`, `2.0s` | Publish to `pdf-extractor.dlq`, commit input offset |
| `assistant` | LLM call | 4 attempts (`llm.retry.max-attempts`) | Exponential: `250ms` initial, multiplier `2.0`, max `2s` | Publish `assistant.banking-pdf-reader.failed` |
| `banking` | Import file -> assistant request publish | Retryable publish errors leave offset uncommitted | Kafka re-delivery (consumer-managed) | Job remains `PROCESSING` until publish succeeds |
| `banking` | Import file parse/process non-retryable failures | No in-method retries | N/A | Job -> `FAILED`, offset committed |
| `banking` | PDF status consumers (`pdf-extractor.*`, `assistant.*`) | No in-method retries (handler catches/logs) | N/A | Replay from topic if needed |

## Idempotency Guards

### Banking file-import assistant dispatch

`banking.transaction_imports.assistant_request_published_at` is the idempotency marker.

- First successful publish to `assistant.banking-reader.requested` sets marker.
- Reprocessing the same `banking.import.file-requested` event for a job already marked:
  - skips assistant publish
  - keeps job in `PROCESSING`
  - avoids duplicate assistant work.

### Downstream status consumers

`ImportReaderStatusConsumer` and `PdfImportStatusConsumer` are terminal-state aware and ignore work when jobs are already terminal.

## DLQ Handling

### `pdf-extractor.dlq`

- Source: `pdf-extractor` worker when extraction cannot complete.
- Consumer: `banking` `PdfImportStatusConsumer.onExtractorDlq`.
- Result: corresponding `pdf_import_jobs` row transitions to `FAILED` with error details.

### `banking.import.file-requested.DLT`

- Reserved quarantine topic for file import pipeline.
- Use when repeated poison events cannot be processed safely in-place.
- Current banking consumer does not auto-publish to this DLT; on-call may route manually.

### `assistant.banking-pdf-reader.failed`

- Business-failure topic, not a quarantine DLQ.
- Consumed by banking to set import job status to `FAILED` with structured reason.

## Replay Procedure (No Code Changes)

1. Identify failing `job_id`, source topic, and current status.
2. Validate persisted state before replay:

```sql
SELECT id, status, assistant_request_published_at, error_details, updated_at
FROM banking.transaction_imports
WHERE id = '<job_id>';
```

3. Choose replay path:
- `pdf-extractor` failure:
  - replay original message to `pdf-extractor.submitted` after confirming signed URL is still valid.
  - if URL expired, regenerate a new job from product flow (recommended).
- `assistant` failure:
  - replay to `assistant.banking-reader.requested` only after validating payload contract.
- `banking.import.file-requested` failure:
  - if `assistant_request_published_at` is null, safe to replay same event.
  - if marker is set and downstream event was lost, clear marker only for controlled replay:

```sql
UPDATE banking.transaction_imports
SET assistant_request_published_at = NULL, updated_at = NOW()
WHERE id = '<job_id>' AND status = 'PROCESSING';
```

4. Replay one message keyed by `job_id`.
5. Confirm completion:
- `transaction_imports.status` transitions to terminal expected state.
- no duplicate assistant requests for same `job_id`.
- no unexpected growth in DLQ/error topics.

## Escalation Rules

- Escalate after 5 consecutive failures for the same `job_id` within 15 minutes.
- Move poison payloads to DLT/quarantine topic before further retries.
- Do not mass-replay topics without key-filtering by `job_id`.

## Post-Incident Checklist

- Record root cause category: payload, transient dependency, contract drift, infra outage.
- Record whether replay required marker reset.
- Add/adjust alert thresholds when repeated class of failures is observed.
