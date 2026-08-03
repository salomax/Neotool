---
id: feature.transactions.imports.bill-pdf-import-pipeline
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/BillImportService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/BillInputStrategy.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/ExtractionRoutingService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/BillImportStatusConsumer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/client/import/extractor/PdfExtractorSubmittedProducer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/client/assets/BillImportAssetStorage.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/billimport/BillImportResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/billimport/BillImportJobStatus.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_5__create_document_import_jobs.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/billimport/BillImportServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/infra/kafka/BillImportStatusConsumerTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/BillImportWorkflowIT.kt
  frontend:
    - web/src/app/personal-finance/finance-transactions/bill/BillImportDrawer.tsx
    - web/src/app/personal-finance/finance-transactions/bill/hooks/useFileUpload.ts
  mobile: []
  workers:
    - workers/python/pdf-extractor/
legacy_refs: []
related_specs:
  - feature.transactions.imports.bill-import-review-and-confirm
  - feature.transactions.imports.pdf-extractor-worker
  - feature.transactions.imports.import-job-lifecycle
schema: spec-metadata-v1
---

# Imports — Bill PDF Import Pipeline

## Glossary

- `accountId` — tenant owning the bill-import job (`job_kind=BILL_IMPORT` on shared `document_import_jobs`).
- `financialAccountId` — optional at submit for `CREDIT_CARD_BILL` (card resolved at confirm); required + BANK type when `importType=BANK_STATEMENT` (non-PDF depth out of scope).
- Statement OFX/CSV/XLSX jobs share the table pattern from Batch 6 but use `TRANSACTION_IMPORT` — do not conflate status enums.

## Behavior

### Submit (`submitBillImport`)

- GraphQL → `BillImportService.submitBillImport`: validate size (20MB) + `%PDF` magic for PDF strategy; optional FA ownership under caller `accountId`.
- Permission: `banking:transaction:create`.
- SHA-256 `file_hash`; lookup existing `BILL_IMPORT` by `(accountId, file_hash)`.
  - Retryable statuses: `FAILED` | `AWAITING_REVIEW` | `DONE` → reset/re-enqueue.
  - In-flight (`PENDING` / `EXTRACTING` / `LLM_PROCESSING` / …): return existing **without** re-publish.
- PDF path (`AsyncPdfBillInputStrategy`): upload asset (`BillImportAssetStorage`, namespace `transaction-imports`) → insert job `PENDING` → publish `PdfExtractorSubmittedEvent` to `pdf-extractor.submitted` (password if provided). Kafka producer required or `IMPORT_ASYNC_PIPELINE_UNAVAILABLE`.

### Status machine (PDF)

```text
PENDING → EXTRACTING → EXTRACTED
  → AWAITING_REVIEW                         (deterministic Nubank reader)
  → LLM_PROCESSING → AWAITING_REVIEW        (unknown / LLM path)
→ IMPORTING → DONE                          (confirm — see review/confirm spec)
Any extraction/LLM/confirm hard failure → FAILED
```

Who sets:

| Status | Setter |
|---|---|
| `PENDING` | PDF submit strategy |
| `EXTRACTING` | `BillImportStatusConsumer` on submitted ack / pipeline |
| `EXTRACTED` | `ExtractionRoutingService` via `markExtracted` (often transient) |
| `LLM_PROCESSING` | routing → assistant request |
| `AWAITING_REVIEW` | deterministic canonical **or** assistant done |
| `IMPORTING` / `DONE` | confirm path |
| `FAILED` | DLQ / assistant failed / confirm catch |

Queries: `billImportJob` / `billImportJobs` — `banking:transaction:view`.

### Extraction routing (after worker)

1. Mark extracted.
2. `PdfFingerprintClassifier` (nubank/itau/bradesco/santander bill fingerprints).
3. If `banking.bill-import.deterministic-enabled` and known profile **and** reader returns output → `markAwaitingReviewFromCanonical` (skip LLM). **Only implemented deterministic PDF reader today: Nubank.**
4. Else publish `assistant.banking-reader.requested` → `LLM_PROCESSING`.
5. **Gap:** known profile with **no** deterministic reader logs a warning and returns job still at `EXTRACTED` (does **not** fall through to LLM in that branch).

### Shared table vs Batch 6

- Same `banking.document_import_jobs`; bill rows use `job_kind=BILL_IMPORT` and GraphQL `BillImportJobStatus` (not statement `ImportStatus`).
- Unique hash index for bills: `(account_id, file_hash) WHERE job_kind='BILL_IMPORT'` (tenant-scoped, not FA-scoped like statement).

## Invariants

- Worker extracts text only; banking owns status and domain validation.
- Confirm is not part of this pipeline stage (separate mutation after review).
- Password is opaque transport to the worker; banking maps `PDF_PASSWORD_REQUIRED` from DLQ.

## Permissions

- Submit: `banking:transaction:create`.
- Poll job: `banking:transaction:view`.
- Web UX may omit FA on bill submit and assign cards in review.

## Data And Contracts

- GraphQL: `SubmitBillImportInput` (`importType`, optional `financialAccountId`, `fileName`, `fileBase64`, optional `password`), `BillImportJob`, `BillImportJobStatus`.
- Kafka out: `pdf-extractor.submitted` (see worker spec).
- Bruno Submit/List/Get PDF files exist but use **stale** operation names (`submitPdfBillImport` / `pdfImportJob`) — not live schema.

## Impact Surface

- Backend: BillImportService, strategies, status consumer, extraction routing, asset storage, producer
- GraphQL + web BillImportDrawer processing step + E2E bill-import (often mocked)
- Cross: pdf-extractor worker; assistant reader for unknown profiles; Batch 6 shared table only

## Decisions

- Async PDF-only extraction via dedicated worker topics.
- Card FA may be deferred until confirm for `CREDIT_CARD_BILL`.
- Deterministic Nubank path preferred when fingerprint matches and reader exists.

## Known Gaps

- Known non-Nubank PDF fingerprints can leave jobs at `EXTRACTED` without LLM — tracked in [#588](https://github.com/invistus/invistus/issues/588) (fall through to LLM until dedicated readers exist).
- Bruno collections not aligned with current GraphQL names/fields.
- Web result handling also checks obsolete `COMPLETED` string (schema uses `DONE`).
- Live pdf-extractor + password path lightly covered in E2E (UI often mocked).

## Legacy Notes

- None used as authority.
