---
id: feature.transactions.imports.pdf-extractor-worker
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/client/import/extractor/PdfExtractorSubmittedProducer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/BillImportStatusConsumer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/ImportPdfExtractionStatusConsumer.kt
    - service/kotlin/banking/src/main/resources/application.yml
  graphql: []
  database: []
  tests:
    - workers/python/pdf-extractor/tests/test_consumer.py
    - workers/python/pdf-extractor/tests/test_extractor.py
    - workers/python/pdf-extractor/tests/test_cleaner.py
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/infra/kafka/BillImportStatusConsumerTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/infra/extractor/pdf/ImportPdfExtractionStatusConsumerTest.kt
  frontend: []
  mobile: []
  workers:
    - workers/python/pdf-extractor/consumer.py
    - workers/python/pdf-extractor/extractor.py
    - workers/python/pdf-extractor/downloader.py
    - workers/python/pdf-extractor/publisher.py
    - workers/python/pdf-extractor/schema.py
    - workers/python/pdf-extractor/config.py
    - workers/python/pdf-extractor/cleaner.py
legacy_refs: []
related_specs:
  - feature.transactions.imports.bill-pdf-import-pipeline
  - feature.transactions.imports.bill-import-review-and-confirm
  - feature.transactions.imports.statement-file-import
schema: spec-metadata-v1
---

# Imports — PDF Extractor Worker

## Glossary

- Worker — `workers/python/pdf-extractor`: download PDF from signed URL, extract/clean text, republish. **Not** financial-domain authority.
- Bill jobs vs statement PDF jobs — both may use the same Kafka topics; banking consumers separate by looking up `BILL_IMPORT` vs statement `ImportJob`.

## Behavior

### Topics (defaults in `config.py` / banking `application.yml`)

| Direction | Topic | Payload |
|---|---|---|
| In | `pdf-extractor.submitted` | `job_id`, optional `asset_id`, `download_url`, optional `password`, `document_type`, `callback_context` |
| Out OK | `pdf-extractor.extracted` | `job_id`, `asset_id`, `document_type`, `extracted_text`, `extraction_metadata`, `callback_context` |
| Out fail | `pdf-extractor.dlq` | `job_id`, error, optional `error_code`, context |

### Worker loop

1. Consume submitted → download (`downloader.py`; signed URL expiry → DLQ).
2. Extract with pdfplumber (`extractor.py`); password from payload; `PdfPasswordRequiredError` → DLQ `error_code=PDF_PASSWORD_REQUIRED`.
3. Clean text (`cleaner.py`) → publish extracted → commit offset.
4. Health HTTP endpoint for ops; no DB writes to banking.

### Banking consumers (dual)

| Consumer | Group | Handles |
|---|---|---|
| `BillImportStatusConsumer` | `banking-service-bill-import-status` | Bill jobs: submitted→EXTRACTING, extracted→routing, DLQ→FAILED (`PDF_PASSWORD_REQUIRED` preserved; else often `SERVICE_UNAVAILABLE`), assistant done/failed |
| `ImportPdfExtractionStatusConsumer` | `banking-service-import-pdf-status` | Statement `ImportJob` PDF path only; ignores unknown/missing bill jobs |

Separation is **job table lookup**, not a hard `document_type` filter on the bus.

### Authority boundary

- Worker may hint `bank_name_hint` / page counts in metadata.
- Fingerprint classification, deterministic readers, LLM routing, review payload, confirm, bills/txs — **all banking**.

## Invariants

- Worker never creates transactions, bills, or import job domain status beyond what banking applies after Kafka events.
- Password never logged as secret material in happy-path publish schema (opaque field).

## Permissions

N/A in worker. Banking GraphQL/SSE permissions live in pipeline/lifecycle specs.

## Data And Contracts

- Pydantic: `SubmittedPayload`, `ExtractionResult`, `DlqPayload` (`schema.py`).
- Banking producer: `PdfExtractorSubmittedProducer` / `PdfExtractorSubmittedEvent`.

## Impact Surface

- Worker Python package + Docker/compose workers profile
- Banking producers/consumers for bill **and** statement PDF AUTO path (Batch 6 boundary)
- Tests: worker unit tests + banking consumer unit tests

## Decisions

- Shared extraction topics for any PDF import needing text; domain fork happens in banking consumers/routing.
- Explicit DLQ code for password so web can re-prompt (`PDF_PASSWORD_REQUIRED`).

## Known Gaps

- Dual consumers on shared topics: cross-kind noise/race not heavily integration-tested.
- E2E rarely runs real worker end-to-end against banking.
- Non-password DLQ cases often collapse to generic banking error codes.

## Legacy Notes

- None used as authority.
