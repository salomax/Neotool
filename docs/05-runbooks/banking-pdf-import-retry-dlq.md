---
title: Banking PDF Import Retry DLQ Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/banking/src/main/resources/application.yml
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/import/TransactionImportEntity.kt
  - service/kotlin/banking/src/main/resources/db/migration/V0_5__create_document_import_jobs.sql
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/ImportPdfExtractionStatusConsumer.kt
  - workers/python/pdf-extractor/consumer.py
  - docs/06-contracts/kafka/banking-pdf-import
load_when:
  - PDF import jobs stuck or failing
  - replaying Kafka PDF import messages
supersedes:
  - docs/08-workflows/banking-pdf-import-retry-dlq-runbook.md
schema: docs-metadata-v1
---

# Banking PDF Import Retry DLQ Runbook

Purpose: retry, DLQ, and replay guidance for banking PDF import across banking, assistant, and pdf-extractor.

## Symptoms

- Import job remains `PROCESSING` or moves to `FAILED` after PDF extraction/assistant steps.
- Messages accumulating on `pdf-extractor.dlq` or related failure topics.

## Authority

Topic names from banking `application.yml` / `ImportTopics`. Job rows live in **`banking.document_import_jobs`** (old runbook name `transaction_imports` is wrong). Consumers: `ImportPdfExtractionStatusConsumer` and related import jobs; worker `workers/python/pdf-extractor`.

## Commands

**Topics (defaults):**

- `banking.import.file-requested` / `banking.import.file-requested.DLT`
- `pdf-extractor.submitted`, `pdf-extractor.extracted`, `pdf-extractor.dlq`
- Assistant reader topics configured under `kafka.topics.pdf.*` in banking config

**Inspect job**

```sql
SELECT id, status, assistant_request_published_at, error_details, updated_at
FROM banking.document_import_jobs
WHERE id = '<job_id>';
```

**Replay rules**

1. Identify `job_id`, source topic, and status.
2. For `pdf-extractor` failures, replay to `pdf-extractor.submitted` only if the signed URL is still valid; otherwise create a new job from product flow.
3. For `banking.import.file-requested`, replay is safer when `assistant_request_published_at` is NULL.
4. Controlled replay after a lost downstream event may clear the marker:

```sql
UPDATE banking.document_import_jobs
SET assistant_request_published_at = NULL, updated_at = NOW()
WHERE id = '<job_id>' AND status = 'PROCESSING';
```

Only clear the marker with explicit operator intent.

## Rollback

- Do not mass-replay poison messages without quarantining (`banking.import.file-requested.DLT` is reserved).
- Restore `assistant_request_published_at` from backups/AUD if cleared incorrectly.
- Prefer new job creation when signed URLs expired.

## Verification

- Job reaches terminal `COMPLETED` or expected `FAILED` with actionable `error_details`.
- DLQ consumer paths mark jobs failed (see extractor DLQ consumer tests/handlers).
- No duplicate assistant publishes for jobs that still have the idempotency marker set.

## Related

- [Testing strategy](../02-development/testing-strategy.md)
- [Service boundaries](../01-architecture/service-boundaries.md)
- [Credit card bill remediation](credit-card-bill-import-remediation.md)
