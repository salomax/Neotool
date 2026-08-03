---
id: feature.transactions.imports.import-job-lifecycle
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportJobService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportValidationGate.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/ImportFileConsumer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/ImportReaderStatusConsumer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/rest/import/ImportSseController.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/event/ImportEventBus.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/import/ImportResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_5__create_document_import_jobs.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/ImportJobServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/ImportValidationGateTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/rest/ImportSseControllerTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/rest/ImportSseControllerIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/graphql/resolver/ImportResolverTest.kt
    - web/src/lib/hooks/__tests__/useImportJobStatus.test.ts
  frontend:
    - web/src/lib/hooks/useImportJobStatus.ts
    - web/src/app/api/imports/[jobId]/stream/route.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.imports.statement-file-import
  - feature.transactions.imports.dedup-and-duplicate-resolution
schema: spec-metadata-v1
---

# Imports — Import Job Lifecycle

## Glossary

- `accountId` — tenant; all job reads/writes are scoped by it + `job_kind=TRANSACTION_IMPORT`.
- `financialAccountId` — destination FA on the job.
- Statement job statuses (GraphQL `ImportStatus`): `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `REVIEW_REQUIRED`, `REVIEW_CANCELLED`.  
  Extra DB CHECK values (`EXTRACTING`, `AWAITING_REVIEW`, …) serve **bill** / other job kinds — not statement GraphQL enum.

## Behavior

### Create → process → terminal

1. `initiateFileImport`: ownership check → file SHA-256 → format discovery / resolved source → insert or reuse job by `(FA, file_hash)` → store asset → Kafka enqueue → return job still `PENDING`.
2. `ImportFileConsumer`: `PENDING`→`PROCESSING` (+ SSE) → parse (or PDF/assistant branch) → `ImportProcessor` → optional `recomputeChain` → `ImportValidationGate.decideFinalStatus` → `COMPLETED` | `REVIEW_REQUIRED` | `FAILED` (+ SSE + completed event) → best-effort asset delete.
3. XLSX unknown / assistant path: may wait on `ImportReaderStatusConsumer` before process; LLM quality gate (`evaluateBeforeCommit`, min confidence 0.5) can force `FAILED` / `REVIEW_REQUIRED` **before** commit on LLM processor only.

### Final status gate (statement counters)

- `duplicateReviewCount > 0` → `REVIEW_REQUIRED`
- else if `imported==0 && skipped==0 && errors>0` → `FAILED`
- else → `COMPLETED` (including all-skipped / empty successful parse)

### Review follow-ups

- `resolveImportDuplicate` — see dedup spec; may leave job in `REVIEW_REQUIRED` until candidates empty → then `COMPLETED`.
- `cancelImportReview`: only from `REVIEW_REQUIRED` → `REVIEW_CANCELLED`; clears review payload (discards unresolved candidates; already-imported rows from the same job stay).

### Same-file retry

- Unique `(financial_account_id, file_hash)` for transaction jobs.
- Existing job may be reset/re-enqueued for statuses such as `COMPLETED` / `PROCESSING` / `FAILED` (implementation in `handleExistingImportJob`); conflict when still `REVIEW_REQUIRED` / `PENDING` (and similar in-flight cases).

### Progress / SSE

- REST `GET /imports/{jobId}/events` (`ImportSseController`): permission **`banking:account:view`**.
- Full UX flow needs **two** permissions by design: `banking:transaction:create` to start the job, plus `banking:account:view` for SSE progress (GraphQL poll fallback still uses `banking:transaction:view`).
- Web proxy: `web/src/app/api/imports/[jobId]/stream` → banking SSE; `useImportJobStatus` falls back to GraphQL poll (~5s).
- Terminal statuses stop the stream; web terminal set may omit `REVIEW_CANCELLED` (backend treats it as terminal).

### Queries

- `transactionImports`, `transactionImport`, `pendingReviewImports` — view permission; scoped to caller `accountId`.

## Invariants

- Kafka must be available to start a file import (enqueue required).
- Job row is the authority for status/counters; SSE is a projection for UX.
- Statement GraphQL status enum is a subset of the DB CHECK list.

## Permissions

| Surface | Permission |
|---|---|
| `importTransactionsFile` | `banking:transaction:create` |
| list/get/pending review | `banking:transaction:view` |
| resolve / cancel review | `banking:transaction:update` |
| SSE events | `banking:account:view` |

## Data And Contracts

- Table: `banking.document_import_jobs` (`V0_5`).
- GraphQL: `TransactionImport`, `ImportStatus`, mutations above.
- Bruno: Get/List Import Jobs; no Bruno for `cancelImportReview` or SSE.

## Impact Surface

- Backend job service, consumers, validation gate, SSE controller, event bus
- GraphQL wiring + web stream proxy/hook + drawer progress
- Cross: snapshots after successful imports; assistant/Kafka infra for unknown XLSX

## Decisions

- Async job model with SSE for UX; no long-polling-only requirement (poll is fallback).
- LLM quality gate applies to assistant/LLM commit path, not deterministic OFX/CSV.
- Statement import UX intentionally requires two permissions: `banking:transaction:create` (mutate/start) and `banking:account:view` (SSE progress). This is not a bug.

## Known Gaps

- `cancelImportReview` has backend + GraphQL but **no** web UI / Bruno / E2E.
- Web SSE terminal handling of `REVIEW_CANCELLED` may diverge from backend.
- Bill-oriented statuses in DB CHECK are shared table noise for statement agents — ignore unless `job_kind` says otherwise.

## Legacy Notes

- None used as authority.
