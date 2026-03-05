---
title: Phase 4 — PDF Credit Card Bill Import
type: feature-spec
category: personal-finance
status: planned
version: 1.1.0
tags: [feature, personal-finance, credit-card, pdf, import, kafka, llm, extraction, pipeline, generic-extractor]
ai_optimized: true
search_keywords: [pdf, fatura, credit card bill, bank statement, IR report, import, pdfplumber, kafka, llm, extraction, pipeline, python worker, pdf_import_jobs, AWAITING_REVIEW, document_type, pdf-extractor, assistant.banking-pdf-reader, callback_context]
related:
  - 03-features/personal-finance/phase-2-5-transaction-imports.md
  - 03-features/personal-finance/phase-3-credit-card-bills.md
  - 03-features/personal-finance/personal-finance-overview.md
last_updated: 2026-03-05
---

# Phase 4 — PDF Credit Card Bill Import

> **Purpose**: Allow users to import credit card bill statements (faturas) from PDF files, using a multi-stage async pipeline: PDF text extraction (Python worker) → AI-powered structured extraction (LLM Service, TBD) → user review → transaction commit. This is the primary fallback for banks that do not offer OFX or CSV export, and a product differentiator in the Brazilian PFM market.

---

## Overview

OFX and CSV imports cover the majority of Brazilian banks (Itaú, Bradesco, Santander, BB, Nubank, Inter, C6). However, several banks — particularly smaller institutions, fintechs, and private-label cards — do not offer structured statement exports. PDF is the universal format: every bank issues statements as PDF.

PDF import is intentionally designed as a **fallback and differentiator**, not a replacement for OFX/CSV. It accepts the trade-off of higher per-import cost (LLM API call) and mandatory user review in exchange for coverage of any bank.

The pipeline is **fully asynchronous and event-driven via Kafka**. Workers (Python extractor, LLM service) are stateless event processors — they never access any domain database and never call any domain GraphQL API. All state is owned by the originating domain service, which reacts to Kafka events from workers.

The **Python PDF Extractor is intentionally domain-agnostic**: it receives any PDF, extracts text and tables, and publishes the result. It has no knowledge of credit cards, bank statements, or investment reports. Domain semantics live in the LLM Service (which interprets the extracted text based on `document_type`) and the originating domain service (banking, investments, etc.).

---

## Problem Statement

### Why PDF parsing requires AI

Credit card bill PDFs share no common schema across banks. Format differences include:

- Table layout: single-column, multi-column, merged cells, rotated headers
- Date formats: `dd/mm/yyyy`, `dd/mm`, `MMM dd`, epoch-based
- Amount encoding: comma as decimal separator, parentheses for credits, sign-based
- Installment representation: free text ("3/12", "Parcela 3 de 12", "3x")
- Multi-card PDFs: sections per card, sometimes interleaved
- Boilerplate noise: marketing text, legal disclaimers, bank branch info, loyalty points summaries

Writing and maintaining a parser per bank is impractical: formats change silently with each bank redesign, parsers become stale with no error signal, and the tail of smaller banks is long.

An LLM handles format variation naturally. Maintenance cost approaches zero because the model generalizes across layouts without code changes. The trade-off — per-call cost and non-determinism — is managed by the validation layer and mandatory user review.

### Why OCR is not needed

Modern credit card bill PDFs are **digital documents** (text is embedded in the PDF binary, not rendered as an image). A library like `pdfplumber` extracts text and table structure directly from the PDF character stream — no image rendering, no OCR. This gives:

- Exact character positions (no OCR transcription errors)
- Native table detection (rows/columns, cell boundaries)
- Zero marginal cost (open-source, runs locally)

Scanned/photocopied PDFs are out of scope for the initial implementation. Banks do not distribute scanned statements; this affects only very old documents.

---

## Industry Research

### How market players handle PDF statement import

| App | PDF support | Approach | Notes |
|---|---|---|---|
| **Organizze** | No | OFX + CSV only | Explicit gap in their feature set |
| **Mobills** | No | OFX + CSV + Pluggy | Relies on Open Finance for coverage |
| **Minhas Economias** | No | OFX only | Legacy; strong OFX parser |
| **YNAB** | No | OFX + CSV | Redirects users to bank's export feature |
| **Monarch Money** | Partial | Email parsing (US only) | Parses bank email notifications, not PDF |
| **Copilot** | No | Plaid only | US market; Plaid covers most banks |
| **Lunch Money** | CSV upload | Manual CSV mapping | No AI; user maps columns |
| **Cleo (UK)** | No | Open Banking | UK market; regulated API |

**Key finding**: No major Brazilian PFM competitor supports PDF credit card bill import. This is a genuine market gap, driven by the technical complexity of heterogeneous PDF formats. Players that achieve broad coverage do so via Open Finance (Pluggy/Belvo) or screen scraping — both of which carry higher cost and regulatory friction than PDF import.

### Why not Open Finance instead

Open Finance Brasil (OFB) offers structured transaction data via regulated API. It is the optimal long-term solution (Phase 5 in the roadmap). However:

- OFB adoption among banks is uneven; many smaller institutions are technically non-compliant
- User consent flow (OAuth via OFB) adds friction at onboarding
- Aggregator cost (Pluggy: ~R$ 3–8/connection/month) is recurring and scales with MAU
- OFB connection requires periodic re-consent (max 12 months per spec)

PDF import has zero recurring cost per import (only LLM API call, ~R$ 0.003–0.05 per statement) and no consent management overhead. It complements Open Finance — users without OFB-compatible banks can still import.

### Chosen approach

- **Text extraction**: `pdfplumber` (Python) — digital PDFs only, no OCR
- **AI extraction**: LLM API via dedicated LLM Service (TBD, separate feature)
- **Pipeline**: fully async Kafka-based; banking service owns state; workers are stateless
- **User review**: mandatory before any transaction is committed
- **Scope**: credit card bills only (not bank account statements — those are covered by OFX)

---

## Architecture

### Pipeline overview

```
[1] Banking Service
    User submits PDF via GQL mutation
    → Validates: MIME type, size ≤ 20MB, SHA-256 hash (idempotency check)
    → POST /assets to Asset Service: { file, account_id, content_type: "application/pdf" }
    ← Asset Service returns: { asset_id, download_url }   (signed URL, 2h TTL)
    → INSERT pdf_import_jobs (status=PENDING, asset_id=...)
    → Publish to Kafka: pdf-extractor.submitted
         {
           job_id:          "bank-123",
           asset_id:        "uuid",
           download_url:    "https://assets.invistus.com/pdf-imports/.../fatura.pdf?sig=...",
           document_type:   "CREDIT_CARD_BILL",
           callback_context: { job_id: "bank-123", account_id: "tenant-abc", user_id: "user-xyz" }
         }

[2] Python PDF Extractor Worker  (invistus/workers/python/pdf-extractor/)
    Reads: pdf-extractor.submitted   [all document_types — fully generic, no filtering]
    → GET download_url  (plain HTTPS — no AWS credentials, no Asset Service dependency)
    → On 403/410 (signed URL expired): error → DLQ  [see note below]
    → Extract text + tables with pdfplumber (extract_tables() preferred, extract_text() fallback)
    → Serialize tables as markdown (pipe-delimited) — compact and LLM-friendly
    → Clean: remove boilerplate, collapse whitespace, cap at 12k chars
    → Passes document_type and callback_context through UNCHANGED
    → Publish to Kafka: pdf-extractor.extracted
         {
           job_id:              "bank-123",
           document_type:       "CREDIT_CARD_BILL",
           extracted_text:      "| Data | Descrição | Valor |\n|---|---|---|\n| 15/01 | MERCADO EXTRA | 45,90 |...",
           extraction_metadata: { page_count: 3, tables_found: 2, bank_name_hint: "Nubank", extraction_duration_ms: 420 },
           callback_context:    { job_id: "bank-123", account_id: "tenant-abc", user_id: "user-xyz" }
         }

[3] Banking Service — status consumer (consumer group: banking-service-pdf-status)
    Reads: pdf-extractor.extracted  [filters: document_type IN ('CREDIT_CARD_BILL','BANK_STATEMENT')]
    → Uses callback_context.job_id to locate job in pdf_import_jobs
    → UPDATE pdf_import_jobs SET status=EXTRACTED, extracted_text=..., extraction_metadata=...

[4] LLM Service  [TBD — separate feature]
    Reads: pdf-extractor.extracted   [all document_types]
    → Selects prompt template by document_type (CREDIT_CARD_BILL → banking prompt)
    → Calls LLM provider (Gemini Flash / Claude Haiku) with extracted_text + prompt
    → Validates output: computed_total ≈ declared_total, dates within period, confidence thresholds
    → Publish to Kafka: assistant.banking-pdf-reader.done
         {
           job_id:          "bank-123",
           document_type:   "CREDIT_CARD_BILL",
           llm_output:      { bank_name: "Nubank", cards: [...], extraction_confidence: 0.96, ... },
           callback_context: { job_id: "bank-123", account_id: "tenant-abc", user_id: "user-xyz" }
         }

[5] Banking Service — status consumer (consumer group: banking-service-pdf-status)
    Reads: assistant.banking-pdf-reader.done
    → Uses callback_context.job_id to locate job
    → UPDATE pdf_import_jobs SET status=AWAITING_REVIEW, review_payload=llm_output
    → Notify user via WebSocket / push notification

[6] User reviews in frontend
    → Queries pdfImportJob(jobId) to get review_payload
    → Sees extracted cards, transactions, confidence warnings, total validation
    → Corrects errors, associates unmatched cards to existing financial accounts or creates new
    → Checks/unchecks individual transactions

[7] Banking Service — confirm mutation
    User calls confirmPdfBillImport mutation
    → Validates job status = AWAITING_REVIEW, belongs to tenant
    → UPDATE status = IMPORTING
    → For each card: resolve financialAccountId (user-confirmed association)
    → For each included transaction:
        - Compute import_fingerprint = SHA-256(financialAccountId || date || amount_cents || normalized_description)
        - Dedup check against existing transactions (same as Phase 2.5 machinery)
        - INSERT transaction (source=PDF_BILL, import_job_id=job_id)
    → Call CreditCardBillService.recomputeBill() for each affected bill
    → UPDATE pdf_import_jobs SET status=DONE, completed_at=now()
```

### Boundary rules

- **Workers never access any domain DB.** Not via direct connection, not via GraphQL API.
- **Workers never call each other.** Only Kafka events cross service boundaries.
- **Workers pass `callback_context` through unchanged.** They never inspect or branch on it.
- **Banking service is the sole writer** of `pdf_import_jobs` and `banking.transactions`.
- **Kafka messages carry all data needed** by the next stage. Workers are stateless — they process and forget.
- **The PDF is fetched via signed URL** — the worker performs a plain HTTPS GET. It has no AWS credentials, no Asset Service auth. The signed URL (2h TTL) is embedded in the Kafka message by the banking service at submission time.
- **Asset Service owns all file storage.** Banking service never integrates with S3/R2 directly. The PDF is never stored in a Kafka message or the DB.
- **Extracted text travels in the Kafka message** (not S3) — after cleaning it is typically 5–30 KB, well within Kafka's 1 MB default limit.

### Kafka topics

The extractor uses a **dedicated topic namespace** (`pdf-extractor.*`) for inputs and outputs of the Python worker. The LLM Service outputs use a **per-use-case namespace** (`assistant.<domain>-pdf-reader.*`), so domain services subscribe directly to their own output topic without any filtering.

#### Topic inventory

| Topic | Producer | Consumers | Payload size |
|---|---|---|---|
| `pdf-extractor.submitted` | Any domain service | Python Extractor | ~1 KB |
| `pdf-extractor.extracted` | Python Extractor | LLM Service, domain status consumers | ~5–30 KB |
| `pdf-extractor.dlq` | Python Extractor (on error) | Domain status consumers (mark job FAILED) | ~1 KB |
| `assistant.banking-pdf-reader.done` | LLM Service | Banking Service status consumer | ~5–20 KB |
| `assistant.investments-pdf-reader.done` | LLM Service (future) | Investments Service status consumer | ~5–20 KB |

#### Topic routing by `document_type`

The LLM Service is the **only component that knows the routing** from `document_type` to output topic. Adding a new document type requires only a new prompt template + new output topic in the LLM Service — no changes to the Python extractor or any domain service.

| `document_type` | Originating service | LLM output topic | LLM extracts |
|---|---|---|---|
| `CREDIT_CARD_BILL` | Banking Service | `assistant.banking-pdfđ-reader.done` | Cards, transactions, closing/due dates |
| `BANK_STATEMENT` | Banking Service | `assistant.banking-pdf-reader.done` | Transactions, opening/closing balance |
| `INVESTMENT_IR_REPORT` | Investments Service (future) | `assistant.investments-pdf-reader.done` | Assets, income events, tax basis |
| `BROKERAGE_NOTE` | Investments Service (future) | `assistant.investments-pdf-reader.done` | Trades, fees, settlement dates |

#### `callback_context`

An opaque JSON blob set by the originating service at submission time. Passed through **unchanged** by every worker. Allows each domain service to correlate a Kafka event back to its internal job record without any shared state between services.

```json
{
  "job_id":     "uuid",
  "account_id": "uuid",
  "user_id":    "uuid"
}
```

The originating service uses `callback_context.job_id` to locate its internal job row when consuming the output event.

#### Consumer groups

| Consumer group | Service | Subscribes to |
|---|---|---|
| `pdf-extractor` | Python Worker | `pdf-extractor.submitted` |
| `llm-service-pdf` | LLM Service | `pdf-extractor.extracted` |
| `banking-service-pdf-status` | Banking Service | `pdf-extractor.extracted`, `pdf-extractor.dlq`, `assistant.banking-pdf-reader.done` |
| `financial-data-pdf-status` | Financial Data Service (future) | `pdf-extractor.extracted`, `pdf-extractor.dlq`, `assistant.investments-pdf-reader.done` |

#### Retention policy

Topics are pipeline-transient — a message has no value after all consumers have processed it. Retention is set to **30 days** to support post-mortem debugging, LLM output quality analysis, and pipeline behaviour monitoring. Kafka applies whichever limit is reached first.

| Topic | `retention.ms` | `retention.bytes` | Rationale |
|---|---|---|---|
| `pdf-extractor.submitted` | 30 days | 200 MB | Light; 1 month for replay/debug |
| `pdf-extractor.extracted` | 30 days | 500 MB | Heaviest (~20 KB/msg); 500 MB safety cap |
| `pdf-extractor.dlq` | 90 days | 200 MB | DLQ needs longer window for investigation |
| `assistant.banking-pdf-reader.done` | 30 days | 200 MB | Compact JSON; useful for LLM quality analysis |

Volume estimate at steady state (10k imports/month, 20 KB avg extracted text):
- `pdf-extractor.extracted` monthly write: ~200 MB → well within 500 MB cap at 30-day retention.

### Multi-service fan-out example

This example shows **banking service** and **financial_data service** both using the same extractor concurrently. Each service is fully independent — no shared state, no coordination.

```
banking service                          financial_data service
(job_id=bank-123)                        (job_id=fin-456)
document_type=CREDIT_CARD_BILL           document_type=INVESTMENT_IR_REPORT
       │                                         │
       │ asset_id=... + signed download_url      │ asset_id=... + signed download_url
       └──────────────────┬──────────────────────┘
                          ▼
               pdf-extractor.submitted
                          │
               ┌──────────┘   (single consumer group: pdf-extractor)
               ▼
        Python Extractor
        processes both messages sequentially
        passes document_type + callback_context through unchanged
               │
               ▼
        pdf-extractor.extracted
               │
       ┌───────┴─────────────────────────┐
       │                                 │
       ▼                                 ▼
consumer group:                  consumer group:
banking-service-pdf-status       financial-data-pdf-status
(receives ALL messages,          (receives ALL messages,
 filters CREDIT_CARD_BILL        filters INVESTMENT_IR_REPORT
 + BANK_STATEMENT)               + BROKERAGE_NOTE)
       │                                 │
       ▼                                 ▼
UPDATE pdf_import_jobs           UPDATE ir_import_jobs
WHERE id = bank-123              WHERE id = fin-456
SET status = EXTRACTED           SET status = EXTRACTED
       │                                 │
       └───────────────┐ ┌───────────────┘
                       ▼ ▼
                   LLM Service
             consumer group: llm-service-pdf
             (receives ALL messages, routes by document_type)
                       │
           ┌───────────┴───────────────┐
           │                           │
           ▼                           ▼
  assistant.banking-     assistant.investments-
  pdf-reader.done        pdf-reader.done
  { job_id: bank-123,    { job_id: fin-456,
    llm_output: ... }      llm_output: ... }
           │                           │
           ▼                           ▼
  banking-service-       financial-data-
  pdf-status             pdf-status
  (subscribes only       (subscribes only
   to banking topic,      to investments topic,
   no filtering needed)   no filtering needed)
           │                           │
           ▼                           ▼
  UPDATE pdf_import_jobs  UPDATE ir_import_jobs
  WHERE id = bank-123     WHERE id = fin-456
  SET status =            SET status =
  AWAITING_REVIEW         AWAITING_REVIEW
  review_payload = ...    review_payload = ...
```

Key properties demonstrated:
- **No shared state** — each service uses `callback_context.job_id` to find its own record
- **No filtering on LLM output** — each service subscribes to its own dedicated output topic
- **LLM Service is the router** — it decides which output topic to use based on `document_type`
- **Python Extractor is unchanged** — it has no knowledge of which services are consuming

### Python worker structure

The worker is **entirely domain-agnostic**. It contains no knowledge of credit cards, bank statements, or investment reports. It receives any PDF, extracts text and tables, and publishes the result. All domain logic lives downstream (LLM Service, domain services).

```
invistus/workers/python/pdf-extractor/
├── consumer.py       # Kafka consumer loop — reads pdf-extractor.submitted (all document_types)
├── extractor.py      # pdfplumber extraction: extract_tables() preferred, extract_text() fallback
├── cleaner.py        # Remove boilerplate, normalize whitespace, cap at 12k chars
├── downloader.py     # GET download_url (plain HTTPS, no credentials) → PDF bytes
├── schema.py         # Pydantic: ExtractionResult, ExtractionMetadata (no domain types)
├── publisher.py      # Publish to pdf-extractor.extracted / pdf-extractor.dlq
├── config.py         # All settings from env vars: KAFKA_BROKER, TOPIC_IN, TOPIC_OUT, TOPIC_DLQ,
│                     #   DOWNLOAD_TIMEOUT_SECONDS (no AWS credentials)
├── tests/
│   ├── test_extractor.py
│   ├── test_cleaner.py
│   └── fixtures/     # Anonymized real-bank PDFs: nubank.pdf, itau.pdf, bradesco_multi_card.pdf
├── requirements.txt  # Pinned: pdfplumber, confluent-kafka, httpx, pydantic, pytest
└── Dockerfile        # python:3.12-slim base
```

---

## Data Model

### New table: `banking.pdf_import_jobs`

Tracks the full lifecycle of a PDF import job. Written **exclusively** by the banking service internal consumers and mutation handlers — never by any worker.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | Job identifier; propagated as `callback_context.job_id` |
| `financial_account_id` | UUID FK → banking.financial_accounts? | Target account if known at submission; null if PDF covers unknown cards |
| `account_id` | UUID FK → security.accounts | Tenant |
| `created_by_user_id` | UUID FK → security.users | |
| `status` | VARCHAR(32) NOT NULL | See job status lifecycle below |
| `asset_id` | UUID NOT NULL | Reference to the Asset Service record for this PDF |
| `file_name` | VARCHAR(255) | Original filename (display only) |
| `file_hash` | VARCHAR(64) NOT NULL | SHA-256 of raw PDF bytes — job-level idempotency key |
| `extracted_text` | TEXT | Output of pdfplumber after cleaning; stored for audit/debug |
| `extraction_metadata` | JSONB | `{ page_count, tables_found, bank_name_hint, extraction_duration_ms }` |
| `review_payload` | JSONB | `PdfBillExtractionResult` from LLM Service; shown to user for review |
| `error_message` | TEXT | Human-readable error for FAILED status |
| `completed_at` | TIMESTAMPTZ | Set when status → DONE or FAILED |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

**Job status lifecycle**:

```
PENDING          — PDF uploaded via Asset Service, job row created, published to pdf-extractor.submitted
                   (set by: submitPdfBillImport mutation)

EXTRACTING       — Python worker has started downloading/processing the PDF
                   (set by: banking-service-pdf-status consuming pdf-extractor.submitted)

EXTRACTED        — pdfplumber done; extracted_text and extraction_metadata stored in DB
                   (set by: banking-service-pdf-status consuming pdf-extractor.extracted)

LLM_PROCESSING   — LLM Service has received the extracted text and is calling the provider
                   (set by: banking-service-pdf-status consuming pdf-extractor.extracted
                    — second consumer action on the same event, after EXTRACTED is written)

AWAITING_REVIEW  — LLM done; review_payload populated; user must review before commit
                   (set by: banking-service-pdf-status consuming assistant.banking-pdf-reader.done)

IMPORTING        — User confirmed; transactions are being committed to banking.transactions
                   (set by: confirmPdfBillImport mutation)

DONE             — All transactions committed; job complete
                   (set by: confirmPdfBillImport mutation after successful commit)

FAILED           — Unrecoverable error at any stage; error_message populated
                   (set by: banking-service-pdf-status consuming pdf-extractor.dlq,
                    or by confirmPdfBillImport on unexpected exception)
```

**Unique constraint**: `uq_pdf_import_jobs_account_hash (account_id, file_hash)` — prevents reprocessing the same PDF within the same tenant. Jobs with status=FAILED are **not** excluded — retrying a failed job creates a new row (the old one remains for audit).

### `banking.transactions` — new source value

Add `PDF_BILL` to the existing source CHECK constraint:

```sql
CHECK (source IN ('MANUAL','OPEN_FINANCE','PLUGGY','OFX','CSV_NUBANK','CSV_ITAU',
                  'CSV_BRADESCO','NFC_E','RECEIPT_OCR','PDF_BILL'))
```

Transactions imported from PDF carry `source = 'PDF_BILL'` and `import_job_id` pointing to the `pdf_import_jobs` row.

### Flyway migration

`V0_12__create_pdf_import_jobs.sql`

---

## LLM Extraction Schema

The following is the **contract** between the LLM Service and the Banking Service — the structure of `llm_output` in `assistant.banking-pdf-reader.done` for `document_type = CREDIT_CARD_BILL`. Defined in `PdfBillExtractionResult`.

```json
{
  "job_id": "bank-123",
  "bank_name": "Nubank",
  "statement_period": {
    "from": "2026-01-08",
    "to": "2026-02-07"
  },
  "cards": [
    {
      "last_four_digits": "1234",
      "holder_name": "JOAO DA SILVA",
      "closing_date": "2026-02-07",
      "due_date": "2026-02-15",
      "declared_total": 1523.45,
      "transactions": [
        {
          "date": "2026-01-15",
          "description": "MERCADO EXTRA",
          "amount": 45.90,
          "credit_debit_type": "DEBIT",
          "installment": { "current": 1, "total": 3 },
          "confidence": 0.98
        },
        {
          "date": "2026-01-22",
          "description": "ESTORNO IFOOD",
          "amount": 32.00,
          "credit_debit_type": "CREDIT",
          "installment": null,
          "confidence": 0.95
        }
      ],
      "computed_total": 1523.45,
      "total_matches_declared": true,
      "confidence": 0.97
    }
  ],
  "extraction_confidence": 0.96,
  "needs_review": false,
  "warnings": []
}
```

**Validation rules applied by LLM Service before publishing to `assistant.banking-pdf-reader.done`**:

| Rule | Action on failure |
|---|---|
| `computed_total` ≈ `declared_total` (within R$ 0.10 tolerance) | Set `total_matches_declared = false`, add warning |
| All `date` values fall within `statement_period` | Flag transaction with `confidence < 0.5` |
| At least one card with at least one transaction | Publish to `pdf-extractor.dlq` instead; mark job FAILED |
| `extraction_confidence < 0.70` | Set `needs_review = true`, add warning |

**Note**: The LLM Service and its prompt design are **TBD** — planned as a separate feature. The schema above is the agreed contract. Neither side may change it without updating this spec.

---

## Business Rules

### 1. Card association at review time

A PDF may contain one or more cards, identified by last four digits. At the review step, the banking service attempts to match each extracted card to an existing `financial_account` in the tenant:

```
For each card in review_payload.cards:
  → SELECT id, name FROM banking.financial_accounts
    WHERE account_id = :accountId
      AND subtype IN ('CREDIT_CARD', 'CHARGE_CARD')
      AND card_last_four_digits = :lastFourDigits
      AND deleted_at IS NULL

  Exactly one match → auto-associate (matchedFinancialAccountId populated; user can override)
  No match          → matchedFinancialAccountId = null; frontend prompts to select or create
  Multiple matches  → matchedFinancialAccountId = null; frontend shows disambiguation
                      (same last 4 on different networks — rare but possible)
```

The `financial_accounts` table must have a `card_last_four_digits` column (see PDF-14).

### 2. Bill assignment after import

After transactions are committed, the existing `CreditCardBillService.recomputeBill()` is called for each affected bill — identical to the behavior after manual transaction creation. No special PDF import logic is needed in the bill layer.

The `closing_date` and `due_date` extracted from the PDF are used **only to validate** the auto-computed bill dates (from `closingDay` / `dueDay` on the account). If they diverge by more than 2 days, a warning is surfaced to the user at review time.

### 3. Idempotency

**Job level**: `file_hash` on `pdf_import_jobs` prevents reprocessing the same PDF. On `submitPdfBillImport`, if a job with the same `(account_id, file_hash)` already exists and is not FAILED, the banking service returns the existing job immediately — no new upload, no new Kafka event.

**Transaction level**: Standard dedup machinery from Phase 2.5 applies. PDF-imported transactions carry `source = 'PDF_BILL'`. The dedup fingerprint is:

```
fingerprint = SHA-256( financial_account_id || date_iso || amount_cents || normalized_description )
```

This is compared against existing transactions of **any** source. Conflicts are surfaced in the review step as "possible duplicates" — same mechanism as cross-source dedup in OFX/NFC-e imports.

### 4. Installment handling

Installment info extracted from PDF (`installment.current`, `installment.total`) is stored in the transaction's `notes` field as structured JSON for this phase. A dedicated `installments` table is out of scope.

### 5. Out of scope

- Scanned / photographed PDFs (OCR path) — only digital PDFs supported
- Bank account statement PDFs (current accounts, savings) — use OFX
- Automatic re-import when new statement is available — requires Open Finance (Phase 5)
- Splitting installments across future months automatically — separate feature

---

## Backlog

### Sprint 1 — Database & Infrastructure

---

#### PDF-01 — Flyway migration: `pdf_import_jobs`

**Description**: Create the `banking.pdf_import_jobs` table as specified in the Data Model section. Also add `PDF_BILL` to the `source` CHECK constraint on `banking.transactions`.

**File**: `src/main/resources/db/migration/V0_14__create_pdf_import_jobs.sql`

```sql
CREATE TABLE banking.pdf_import_jobs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_account_id  UUID REFERENCES banking.financial_accounts(id),
    account_id            UUID NOT NULL REFERENCES security.accounts(id),
    created_by_user_id    UUID NOT NULL,
    status                VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    asset_id              UUID NOT NULL,
    file_name             VARCHAR(255),
    file_hash             VARCHAR(64) NOT NULL,
    extracted_text        TEXT,
    extraction_metadata   JSONB,
    review_payload        JSONB,
    error_message         TEXT,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pdf_import_jobs_account_hash UNIQUE (account_id, file_hash)
);

CREATE INDEX idx_pdf_import_jobs_account_status
    ON banking.pdf_import_jobs (account_id, status);
```

**Acceptance criteria**:
- Migration runs cleanly when the banking service starts with Flyway enabled
- Table and constraints exist as specified
- `source` CHECK on `banking.transactions` includes `PDF_BILL`

---

#### PDF-02 — Asset Service integration in banking service

**Description**: Integrate with the existing Asset Service for PDF upload. The banking service must not create any direct connection to S3/R2. All file storage goes through the Asset Service, which returns an `asset_id` and a time-limited signed `download_url`.

**Where**: `infra/assets/AssetsServiceClient.kt` (banking service), using `common/security/service/GraphQLServiceClient` for service-to-service GraphQL calls via Apollo Router.

```kotlin
interface AssetsServiceClient {
    fun upload(accountId: UUID, fileName: String, bytes: ByteArray, contentType: String): AssetUploadResult
}

data class AssetUploadResult(
    val assetId: UUID,
    val downloadUrl: String    // signed URL, 2h TTL — embedded in Kafka message
)
```

The `download_url` is passed in the Kafka message so the Python worker can fetch the PDF via plain HTTPS with no credentials. If the signed URL expires before the worker processes the message (high Kafka lag scenario), the worker publishes to the DLQ and the job transitions to FAILED — the user re-submits.

Config: `banking.assets-service.url`, `graphql.router.url`, `security.service.id`, `security.service.secret`.

Integration rule: use the common `GraphQLServiceClient` as the transport/auth layer for Asset Service GraphQL operations (service token + target audience). Avoid creating a parallel raw HTTP integration path for production calls.

**Signed URL TTL**: 2 hours — sufficient for any realistic Kafka processing lag. If lag consistently exceeds this, the KEDA ScaledObject (PDF-23) will have scaled up workers to clear the backlog.

**Acceptance criteria**:
- `upload()` calls Asset Service and returns `asset_id` + `download_url`
- No AWS credentials anywhere in the banking service for PDF storage
- `asset_id` stored in `pdf_import_jobs`; `download_url` never persisted (only in Kafka message)
- Works against local Asset Service instance in dev Docker Compose

---

#### PDF-03 — Kafka topic setup, consumer groups, and retention policy

**Description**: Create and configure all PDF pipeline topics with the correct retention policy. Topics are **platform-level** — not scoped to the banking service.

**Topics to create**:

| Topic | `retention.ms` | `retention.bytes` |
|---|---|---|
| `pdf-extractor.submitted` | 30 days | 200 MB |
| `pdf-extractor.extracted` | 30 days | 500 MB |
| `pdf-extractor.dlq` | 90 days | 200 MB |
| `assistant.banking-pdf-reader.done` | 30 days | 200 MB |

**Consumer groups to register**:
- `pdf-extractor` — Python Worker → `pdf-extractor.submitted`
- `llm-service-pdf` — LLM Service → `pdf-extractor.extracted`
- `banking-service-pdf-status` — Banking Service → `pdf-extractor.extracted`, `pdf-extractor.dlq`, `assistant.banking-pdf-reader.done`

Config: all topic names externalized as env vars / `application.yml` properties. Never hardcoded.

**Acceptance criteria**:
- Topics created in dev Kafka (Docker Compose) with correct per-topic retention config (not broker defaults)
- All topic names documented in `application.yml` under `kafka.topics.pdf.*`

---

### Sprint 2 — Banking Service: Upload & Status Tracking

---

#### PDF-04 — GraphQL mutation: `submitPdfBillImport`

**Description**: Entry point for the import flow. Receives the PDF, uploads via Asset Service, creates the job, and publishes to `pdf-extractor.submitted`.

**GraphQL schema addition**:

```graphql
type Mutation {
  submitPdfBillImport(input: SubmitPdfBillImportInput!): PdfImportJob!
}

input SubmitPdfBillImportInput {
  financialAccountId: ID        # Optional — if user pre-selects a card account
  fileName: String!
  fileBase64: String!           # TBD: multipart vs base64 — see OQ-1
}

type PdfImportJob {
  id: ID!
  status: PdfImportJobStatus!
  fileName: String
  createdAt: DateTime!
  reviewPayload: PdfBillExtractionResult  # Non-null only when status=AWAITING_REVIEW
  errorMessage: String
}

enum PdfImportJobStatus {
  PENDING
  EXTRACTING
  EXTRACTED
  LLM_PROCESSING
  AWAITING_REVIEW
  IMPORTING
  DONE
  FAILED
}
```

**Service logic** (`PdfImportService.submit()`):
1. Validate: file extension `.pdf`, MIME type `application/pdf`, size ≤ 20 MB
2. Compute `file_hash` = SHA-256 of raw bytes
3. Query `pdf_import_jobs` for `(account_id, file_hash)` — if found and not FAILED, return existing job
4. Call `AssetServiceClient.upload(accountId, fileName, bytes)` → `{ asset_id, download_url }`
5. INSERT `pdf_import_jobs` (status=PENDING, asset_id=...)
6. Publish to `pdf-extractor.submitted`:
   ```json
   {
     "job_id":        "<newJobId>",
     "asset_id":      "<assetId>",
     "download_url":  "https://assets.invistus.com/...?sig=...",
     "document_type": "CREDIT_CARD_BILL",
     "callback_context": { "job_id": "<newJobId>", "account_id": "...", "user_id": "..." }
   }
   ```
7. Return job DTO (status=PENDING)

**Acceptance criteria**:
- Duplicate file (same hash, non-FAILED) → returns existing job without reprocessing
- PDF > 20 MB → validation error, Asset Service not called
- Wrong MIME type → validation error
- Asset Service unavailable → mutation fails; no job row created
- Kafka publish failure → job row rolled back (transactional outbox or at-least-once with dedup)

---

#### PDF-05 — Banking Service: internal Kafka status consumer

**Description**: `@KafkaListener` consumer group `banking-service-pdf-status` within the banking service. Reacts to three topics to keep `pdf_import_jobs` state current.

**Class**: `infrastructure/kafka/PdfImportStatusConsumer.kt`

| Topic consumed | Kafka message content | Action |
|---|---|---|
| `pdf-extractor.submitted` | `{ job_id, callback_context }` | UPDATE status=EXTRACTING |
| `pdf-extractor.extracted` | `{ job_id, extracted_text, extraction_metadata, callback_context }` | UPDATE status=EXTRACTED, persist extracted_text + metadata; then UPDATE status=LLM_PROCESSING |
| `pdf-extractor.dlq` | `{ job_id, error, callback_context }` | UPDATE status=FAILED, error_message |
| `assistant.banking-pdf-reader.done` | `{ job_id, llm_output, callback_context }` | UPDATE status=AWAITING_REVIEW, review_payload=llm_output; notify user |

Job lookup always uses `callback_context.job_id`. Error handling: unknown `job_id` → log warning, commit offset, do not throw.

**Acceptance criteria**:
- Status transitions are correct at each topic event
- `extracted_text`, `extraction_metadata`, `review_payload` persisted from respective events
- DLQ event → job marked FAILED with error_message
- Unknown `job_id` → warning logged, offset committed, no exception thrown

---

#### PDF-06 — GraphQL query: `pdfImportJob`

**Description**: Allows the frontend to poll job status and retrieve the review payload when ready.

```graphql
type Query {
  pdfImportJob(jobId: ID!): PdfImportJob
  pdfImportJobs(financialAccountId: ID): [PdfImportJob!]!
}
```

`pdfImportJob` returns null if not found or if the job belongs to a different `account_id`.

**Acceptance criteria**:
- Returns correct status at each pipeline stage
- `reviewPayload` is non-null only when `status = AWAITING_REVIEW`
- Tenant isolation enforced: cannot access another tenant's jobs

---

### Sprint 3 — Python PDF Extractor Worker

---

#### PDF-07 — Python worker project setup

**Description**: Bootstrap `invistus/workers/python/pdf-extractor/`.

**Dependencies** (pinned in `requirements.txt`):
- `pdfplumber` — PDF extraction
- `confluent-kafka` — Kafka client
- `httpx` — download PDF via signed HTTPS URL
- `pydantic` — schema validation and serialization
- `pytest` — unit tests

**Environment variables** (read by `config.py`):

| Variable | Description |
|---|---|
| `KAFKA_BROKER` | Kafka bootstrap servers |
| `KAFKA_GROUP_ID` | Consumer group (`pdf-extractor`) |
| `KAFKA_TOPIC_IN` | `pdf-extractor.submitted` |
| `KAFKA_TOPIC_OUT` | `pdf-extractor.extracted` |
| `KAFKA_TOPIC_DLQ` | `pdf-extractor.dlq` |
| `DOWNLOAD_TIMEOUT_SECONDS` | HTTP timeout for signed URL download |

**Deliverables**:
- `Dockerfile` (python:3.12-slim)
- `docker-compose` service entry for local dev
- `README.md` with local run instructions

**Acceptance criteria**:
- `docker compose up pdf-extractor` starts without errors
- Worker connects to Kafka on startup
- All config readable from env vars — no hardcoded values

---

#### PDF-08 — Kafka consumer loop

**File**: `consumer.py`

**Description**: Subscribe to `pdf-extractor.submitted`. For each message: download PDF from signed `download_url`, invoke extractor, publish result to `pdf-extractor.extracted`. Passes `document_type` and `callback_context` through unchanged — never reads or branches on their contents.

**Processing flow**:
```python
for message in consumer.poll():
    payload = deserialize(message)
    pdf_bytes = downloader.get(payload.download_url)         # plain HTTPS GET, retry up to 3x
    result = extractor.extract(pdf_bytes)                    # extract + clean
    publisher.publish_extracted(
        job_id=payload.job_id,
        document_type=payload.document_type,
        extracted_text=result.extracted_text,
        extraction_metadata=result.metadata,
        callback_context=payload.callback_context            # passed through unchanged
    )
    consumer.commit(message)
```

**Error handling**:
- HTTP 403/410 on `download_url` (signed URL expired) → publish to `pdf-extractor.dlq`; job → FAILED; user re-submits
- Download failure after 3 retries (exponential backoff, other HTTP errors) → same DLQ path
- Unhandled extraction exception → publish to `pdf-extractor.dlq`
- DLQ message payload: `{ job_id, asset_id, document_type, error, callback_context }`
- Never crash the consumer loop — catch all exceptions at the message level

**Acceptance criteria**:
- Valid PDF: end-to-end (S3 download → extraction → `pdf-extractor.extracted`)
- Malformed PDF: lands in DLQ, consumer continues
- Offset committed only **after** successful publish to output topic or DLQ

---

#### PDF-09 — pdfplumber extraction

**File**: `extractor.py`

**Description**: Core extraction logic. Receives PDF bytes, returns `ExtractionResult`.

```python
@dataclass
class ExtractionResult:
    extracted_text: str
    metadata: ExtractionMetadata

@dataclass
class ExtractionMetadata:
    page_count: int
    tables_found: int
    bank_name_hint: str | None    # e.g. "Nubank" — informational only, not used for branching
    extraction_duration_ms: int
```

**Extraction strategy** (per page):
1. `page.extract_tables()` — structured table extraction (preferred)
2. Serialize each table as markdown (pipe-delimited) — compact and LLM-friendly:
   ```
   | Data | Descrição | Valor |
   |---|---|---|
   | 15/01 | MERCADO EXTRA | 45,90 |
   ```
3. `page.extract_text()` — for non-table text regions (fallback)
4. Concatenate pages with `--- Page N ---` separator

**Bank name hint**: scan first-page text with regex for known patterns (`Nubank`, `Itaú`, `Bradesco`, `Santander`, `Inter`, `C6 Bank`). Informational only — used in `extraction_metadata`, never used to branch extraction logic.

**Acceptance criteria**:
- Nubank PDF: all transaction rows extracted as markdown table
- Itaú PDF: same
- Multi-card PDF: both card sections present in output
- `tables_found` count is accurate
- No unhandled exceptions for any well-formed digital PDF

---

#### PDF-10 — Text cleaning

**File**: `cleaner.py`

**Description**: Reduces token count before LLM call without losing transaction data.

**Rules applied** (in order):
1. Remove lines matching boilerplate patterns: addresses, legal disclaimers, marketing copy (`"Acumule pontos"`, `"Central de atendimento"`, `"SAC"`, `"Ouvidoria"`, etc.)
2. Remove consecutive blank lines (collapse to single blank)
3. Normalize whitespace within table cells (collapse multiple spaces to one)
4. Cap total output at 12,000 characters — truncate from the bottom, never from detected transaction table regions

Goal: reduce LLM input tokens by ~40–60% without removing any transaction data.

**Acceptance criteria**:
- A 5-page Itaú PDF of ~80 KB raw text → ≤ 15 KB cleaned
- No transaction row removed
- Cleaning is idempotent (running twice produces same result)

---

### Sprint 4 — LLM Service  [TBD]

---

#### PDF-11 — LLM Service consumer and prompt  [TBD]

**Depends on**: PDF-08, PDF-10, LLM Service feature (separate)

**Description**: The LLM Service subscribes to `pdf-extractor.extracted` (all document types), selects the prompt template by `document_type`, calls the LLM provider, validates the output, and publishes to the appropriate output topic:

| `document_type` | Output topic |
|---|---|
| `CREDIT_CARD_BILL` | `assistant.banking-pdf-reader.done` |
| `BANK_STATEMENT` | `assistant.banking-pdf-reader.done` |
| `INVESTMENT_IR_REPORT` | `assistant.investments-pdf-reader.done` |

The LLM Service is the **only component responsible for routing** extracted text to domain-specific output topics. New document types require only a new prompt + new output topic — no changes to the Python extractor or any domain service.

**This task is out of scope for Phase 4.** The LLM Service is a separate infrastructure feature. The contract (Kafka topic names, `document_type` values, `PdfBillExtractionResult` JSON schema) is fully defined in this document and must not change without updating this spec.

**Stub for local development**: A minimal mock LLM consumer that reads `pdf-extractor.extracted`, branches by `document_type`, and publishes a hardcoded `PdfBillExtractionResult` fixture to `assistant.banking-pdf-reader.done`. Enables end-to-end integration tests without a live LLM.

---

### Sprint 5 — Banking Service: Review & Commit

---

#### PDF-12 — GraphQL types: `PdfBillExtractionResult`

**Description**: Add GraphQL types to expose the LLM extraction result to the frontend for the review step.

```graphql
type PdfBillExtractionResult {
  bankName: String
  statementPeriod: DateRange
  cards: [PdfExtractedCard!]!
  extractionConfidence: Float!
  needsReview: Boolean!
  warnings: [String!]!
}

type PdfExtractedCard {
  lastFourDigits: String!
  holderName: String
  closingDate: Date!
  dueDate: Date!
  declaredTotal: BigDecimal!
  computedTotal: BigDecimal!
  totalMatchesDeclared: Boolean!
  transactions: [PdfExtractedTransaction!]!
  matchedFinancialAccountId: ID     # Null if no match found — user must disambiguate
  confidence: Float!
}

type PdfExtractedTransaction {
  date: Date!
  description: String!
  amount: BigDecimal!
  creditDebitType: CreditDebitType!
  installment: InstallmentInfo
  confidence: Float!
}

type InstallmentInfo {
  current: Int!
  total: Int!
}

type DateRange {
  from: Date!
  to: Date!
}
```

---

#### PDF-13 — GraphQL mutation: `confirmPdfBillImport`

**Description**: User has reviewed and confirmed. Commits transactions to `banking.transactions`.

```graphql
type Mutation {
  confirmPdfBillImport(input: ConfirmPdfBillImportInput!): PdfImportJob!
}

input ConfirmPdfBillImportInput {
  jobId: ID!
  cards: [ConfirmPdfCardInput!]!
}

input ConfirmPdfCardInput {
  lastFourDigits: String!
  financialAccountId: ID!          # User-confirmed card association
  transactions: [ConfirmPdfTransactionInput!]!
}

input ConfirmPdfTransactionInput {
  date: Date!
  description: String!
  amount: BigDecimal!
  creditDebitType: CreditDebitType!
  include: Boolean!                # User can deselect individual transactions
}
```

**Service logic** (`PdfImportService.confirm()`):
1. Validate job: exists, belongs to tenant, `status = AWAITING_REVIEW`
2. UPDATE `status = IMPORTING`
3. For each card (`financialAccountId` confirmed by user):
   - For each transaction where `include = true`:
     - Compute `import_fingerprint`
     - Level-1 dedup: skip if fingerprint exists (same source)
     - Level-2 dedup: flag if same date+amount exists from different source (Phase 2.5 machinery)
     - INSERT transaction: `source=PDF_BILL`, `import_job_id=jobId`
   - Call `CreditCardBillService.recomputeBill()` for the bill affected by this card's transactions
4. UPDATE `status = DONE`, `completed_at = now()`
5. Return final job DTO

**Acceptance criteria**:
- `include=false` transactions are not committed
- Duplicate fingerprint → silently skipped, no error
- `CreditCardBill.totalAmount` recomputed after commit
- Job status = DONE on success

---

#### PDF-14 — Card association logic

**Description**: When banking status consumer receives `assistant.banking-pdf-reader.done`, auto-match each extracted card (by `lastFourDigits`) to existing financial accounts before persisting `review_payload`.

**Matching query**:
```sql
SELECT id FROM banking.financial_accounts
WHERE account_id = :accountId
  AND subtype IN ('CREDIT_CARD', 'CHARGE_CARD')
  AND card_last_four_digits = :lastFourDigits
  AND deleted_at IS NULL
```

If `card_last_four_digits` column does not exist on `financial_accounts`, add via `V0_13__add_card_last_four_digits.sql`.

Matching result is stored in the `review_payload` JSONB as `matchedFinancialAccountId` per card before persisting — frontend reads it directly from the review payload.

**Acceptance criteria**:
- Exact match → `matchedFinancialAccountId` populated in review_payload
- No match → null; frontend shows card selector
- Multiple matches → null; frontend shows disambiguation
- `card_last_four_digits` migration applied if column was missing

---

### Sprint 6 — Tests

---

#### PDF-15 — Banking Service unit tests: `PdfImportService`

| Test | What to verify |
|---|---|
| `submit` — new PDF | Job created, status=PENDING, event published to `pdf-extractor.submitted` |
| `submit` — duplicate hash (non-FAILED) | Returns existing job, no new DB row, no S3 upload |
| `submit` — duplicate hash (FAILED) | Creates new job (retry allowed) |
| `submit` — file > 20 MB | Validation error, no upload |
| `submit` — wrong MIME type | Validation error |
| `confirm` — happy path | Transactions inserted, bill recomputed, status=DONE |
| `confirm` — transaction include=false | Transaction skipped |
| `confirm` — duplicate fingerprint | Silently skipped, no error |
| `confirm` — wrong status | Returns error |
| Card association — exact match | `matchedFinancialAccountId` set in review_payload |
| Card association — no match | Null |
| Card association — multiple matches | Null |

---

#### PDF-16 — Banking Service integration test: full pipeline (mock LLM)

**Extends**: `BankingPostgresIntegrationTest`

**Description**: End-to-end test of the banking service using a hardcoded `PdfBillExtractionResult` fixture published directly to `assistant.banking-pdf-reader.done` — bypasses Python worker and LLM Service entirely.

| Scenario | What to verify |
|---|---|
| Simulate `pdf-extractor.submitted` event | Status → EXTRACTING |
| Simulate `pdf-extractor.extracted` event | Status → EXTRACTED then LLM_PROCESSING |
| Simulate `assistant.banking-pdf-reader.done` event | Status → AWAITING_REVIEW, review_payload stored |
| Simulate `pdf-extractor.dlq` event | Status → FAILED, error_message populated |
| Confirm: 2 cards × 5 transactions | 10 transactions inserted, 2 bills recomputed |
| Confirm: one transaction excluded | 9 transactions inserted |
| Confirm twice (idempotency) | Second call returns DONE immediately, no duplicates |
| LLM output with `total_matches_declared=false` | Warning present in review_payload |

---

#### PDF-17 — Python worker unit tests

**File**: `workers/python/pdf-extractor/tests/test_extractor.py`

**Test fixtures**: Include 3–4 anonymized real-bank PDFs in `tests/fixtures/`.

| Test | What to verify |
|---|---|
| Nubank PDF | All transaction rows in markdown table output |
| Itaú PDF | Tables extracted; no boilerplate in cleaned output |
| Multi-card PDF (Bradesco) | Both card sections present in output |
| Cleaner removes boilerplate | `"Central de atendimento"` not in cleaned output |
| Cleaner preserves transactions | All amounts present before and after cleaning |
| Cleaner is idempotent | Running twice yields same output |
| Empty PDF | Raises `ExtractionError`, DLQ path triggered |
| `callback_context` pass-through | Exactly equal in output vs input, no mutation |

---

### Sprint 7 — Polish

---

#### PDF-18 — Bruno API collection update

**Requests to add**:
- `POST submitPdfBillImport` (base64 PDF fixture)
- `GET pdfImportJob(jobId)`
- `GET pdfImportJobs()`
- `POST confirmPdfBillImport`

---

#### PDF-19 — Performance review and index audit

```sql
-- Job lookup by account + hash (every submit call)
SELECT id, status FROM banking.pdf_import_jobs
WHERE account_id = $1 AND file_hash = $2;
-- Covered by: uq_pdf_import_jobs_account_hash ✓

-- Job by PK (status polling)
SELECT * FROM banking.pdf_import_jobs WHERE id = $1;
-- Covered by: PK ✓

-- Job list for import history page
SELECT * FROM banking.pdf_import_jobs
WHERE account_id = $1 ORDER BY created_at DESC;
-- Covered by: idx_pdf_import_jobs_account_status (account_id) ✓

-- Dedup fingerprint check at confirm time
SELECT id FROM banking.transactions
WHERE financial_account_id = $1 AND import_fingerprint = $2 AND deleted_at IS NULL;
-- Verify index (financial_account_id, import_fingerprint) from Phase 2.5 exists; add if missing
```

---

### Sprint 8 — CI/CD & Deployment

---

#### PDF-20 — Dockerfile: pdf-extractor Python worker

**Location**: `invistus/workers/python/pdf-extractor/Dockerfile`

**Description**: Multi-stage Dockerfile following the same security and build patterns as existing Go workers (`institution_enhancement_consumer` is the reference).

```dockerfile
# ── builder ──────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ── runtime ──────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

# Non-root user (mirrors Go workers: UID 1000)
RUN addgroup --gid 1000 extractor && \
    adduser --uid 1000 --gid 1000 --disabled-password --gecos "" extractor

# CA certs + timezone data (needed for HTTPS S3 calls + São Paulo TZ)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates tzdata wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /install /usr/local
COPY --chown=extractor:extractor . /app

WORKDIR /app
USER extractor

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["python", "consumer.py"]
```

Add a minimal HTTP health endpoint in `consumer.py` (background thread on port 8080) that returns 200 when the Kafka consumer is connected and 503 during startup or after a fatal error.

**Acceptance criteria**:
- Image builds successfully: `docker build -t pdf-extractor:local .`
- Runs as UID 1000 (non-root)
- Health probe returns 200 when consumer is polling
- Image size ≤ 300 MB

---

#### PDF-21 — CI: add pdf-extractor to `ci.yml` test matrix

**File**: `.github/workflows/ci.yml`

**Description**: Add the Python worker to the Nx-affected test matrix so `pytest` runs on PRs that touch `workers/python/pdf-extractor/`.

**Changes**:
1. Add `project.json` at `workers/python/pdf-extractor/project.json` with Nx project definition:
   ```json
   {
     "name": "pdf-extractor",
     "targets": {
       "test": { "executor": "nx:run-commands", "options": { "command": "pytest tests/ -v" } },
       "lint": { "executor": "nx:run-commands", "options": { "command": "ruff check ." } }
     }
   }
   ```
2. Add a `python` entry to the CI matrix in `ci.yml`:
   ```yaml
   - type: python
     project: pdf-extractor
     path: workers/python/pdf-extractor
   ```
3. Add Python setup step to the matrix job (uses `actions/setup-python@v5`, `pip install -r requirements.txt`):
   ```yaml
   - name: Set up Python
     if: matrix.type == 'python'
     uses: actions/setup-python@v5
     with:
       python-version: '3.12'
       cache: 'pip'
       cache-dependency-path: workers/python/pdf-extractor/requirements.txt
   - name: Install Python dependencies
     if: matrix.type == 'python'
     run: pip install -r requirements.txt
     working-directory: workers/python/pdf-extractor
   - name: Run Python tests
     if: matrix.type == 'python'
     run: pytest tests/ -v --tb=short
     working-directory: workers/python/pdf-extractor
   ```

**Acceptance criteria**:
- PR that changes `workers/python/pdf-extractor/` triggers pytest in CI
- PR that does NOT change the worker skips the step (Nx affected detection)
- Test failures block merge

---

#### PDF-22 — CI: add pdf-extractor to `build-production.yml` image matrix

**File**: `.github/workflows/build-production.yml`

**Description**: Add the Python worker to the Docker build matrix so it gets built and pushed to GHCR on release tag.

**Tag format**: `pdf-extractor-v{major}.{minor}.{patch}` — follows the existing pattern `{service}-v{version}`.

**Matrix entry to add** (under the Go workers section):
```yaml
- project: pdf-extractor
  type: python
  context: workers/python/pdf-extractor
  dockerfile: workers/python/pdf-extractor/Dockerfile
  target: runtime
  image: ghcr.io/invistus/neotool-pdf-extractor
```

**Note**: Python workers do NOT trigger supergraph composition (no GraphQL schema). The existing `if: matrix.type == 'kotlin'` guard on the supergraph step already excludes it.

**Acceptance criteria**:
- Tag `pdf-extractor-v0.1.0` triggers build of `ghcr.io/invistus/neotool-pdf-extractor:0.1.0`
- Image pushed to GHCR with both version tag and `latest`
- Old image versions cleaned up (retain 3 most recent — existing cleanup job covers it)

---

#### PDF-23 — Flux: `apps/services/pdf-extractor/` deployment manifests

**Location**: `invistus-flux/infra/kubernetes/flux/apps/services/pdf-extractor/`

**Reference**: `apps/services/institution-enhancement-consumer/` — same pattern (Kafka consumer Deployment + KEDA ScaledObject).

**Files to create**:

**`kustomization.yaml`**:
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - configmap.yaml
  - externalsecret.yaml
  - deployment.yaml
  - scaledobject.yaml
```

**`configmap.yaml`**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pdf-extractor-config
  namespace: production
data:
  KAFKA_BROKER: "kafka.production.svc.cluster.local:9092"
  KAFKA_GROUP_ID: "pdf-extractor"
  KAFKA_TOPIC_IN: "pdf-extractor.submitted"
  KAFKA_TOPIC_OUT: "pdf-extractor.extracted"
  KAFKA_TOPIC_DLQ: "pdf-extractor.dlq"
  DOWNLOAD_TIMEOUT_SECONDS: "30"
  # No S3/R2 config — PDF fetched via signed URL from Kafka message (no credentials needed)
```

**`externalsecret.yaml`**:
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: pdf-extractor-secret
  namespace: production
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: pdf-extractor-secret
  data:
    - secretKey: KAFKA_SASL_USERNAME
      remoteRef: { key: invistus/pdf-extractor, property: kafka_sasl_username }
    - secretKey: KAFKA_SASL_PASSWORD
      remoteRef: { key: invistus/pdf-extractor, property: kafka_sasl_password }
  # No AWS credentials — worker fetches PDF via signed URL (plain HTTPS, no auth)
```

**`deployment.yaml`**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pdf-extractor
  namespace: production
spec:
  replicas: 1                  # KEDA will override this
  selector:
    matchLabels:
      app: pdf-extractor
  template:
    metadata:
      labels:
        app: pdf-extractor
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: pdf-extractor
          image: ghcr.io/invistus/neotool-pdf-extractor:latest   # {"$imagepolicy": "flux-system:neotool-pdf-extractor"}
          resources:
            requests: { memory: "256Mi", cpu: "100m" }
            limits:   { memory: "512Mi", cpu: "500m" }
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
          envFrom:
            - configMapRef:  { name: pdf-extractor-config }
            - secretRef:     { name: pdf-extractor-secret }
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet: { path: /health, port: 8080 }
            initialDelaySeconds: 10
            periodSeconds: 30
```

**`scaledobject.yaml`** — KEDA scales on `pdf-extractor` consumer group lag on `pdf-extractor.submitted`:
```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: pdf-extractor-scaler
  namespace: production
spec:
  scaleTargetRef:
    name: pdf-extractor
  minReplicaCount: 0           # Scale to zero when queue is empty
  maxReplicaCount: 5
  pollingInterval: 15
  cooldownPeriod: 60
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: "kafka.production.svc.cluster.local:9092"
        consumerGroup: "pdf-extractor"
        topic: "pdf-extractor.submitted"
        lagThreshold: "5"      # 1 replica per 5 pending messages
        offsetResetPolicy: latest
```

**Acceptance criteria**:
- `kubectl apply -k apps/services/pdf-extractor/` applies without errors
- Pod runs as UID 1000, read-only root filesystem
- KEDA scales from 0 → 1 when a message lands on `pdf-extractor.submitted`
- KEDA scales back to 0 after cooldown with no pending messages

---

#### PDF-24 — Flux: Image Automation for pdf-extractor

**Location**: `invistus-flux/infra/kubernetes/flux/infrastructure/image-automation/`

**Description**: Add `ImageRepository` and `ImagePolicy` for the new image so Flux automatically updates the deployment tag when a new version is pushed to GHCR.

**Files to add** (alongside existing image policies):

```yaml
# image-repository-pdf-extractor.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImageRepository
metadata:
  name: neotool-pdf-extractor
  namespace: flux-system
spec:
  image: ghcr.io/invistus/neotool-pdf-extractor
  interval: 5m
  secretRef:
    name: ghcr-credentials
---
# image-policy-pdf-extractor.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: neotool-pdf-extractor
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: neotool-pdf-extractor
  policy:
    semver:
      range: ">=0.1.0"
```

The `{"$imagepolicy": "flux-system:neotool-pdf-extractor"}` marker in `deployment.yaml` (PDF-23) links this policy to the deployment — Flux Image Automation writes the resolved tag directly into the manifest on the `flux-updates` branch.

**Acceptance criteria**:
- `flux get imagepolicies -n flux-system` shows `neotool-pdf-extractor` with a resolved tag
- Pushing a new image to GHCR triggers a `flux-updates` branch commit within 5 minutes
- The auto-merge flow (existing `flux-pr.yml`) merges the update to main

---

#### PDF-25 — Flux: register pdf-extractor in services kustomization

**File**: `invistus-flux/infra/kubernetes/flux/apps/services/kustomization.yaml`

**Description**: Add the new service directory so Flux includes it in the production cluster reconciliation.

```yaml
# add to resources list:
- pdf-extractor
```

This is the only change needed in the cluster directory — the rest of the dependency ordering (apps waits for infrastructure + streaming) is already handled by the existing `clusters/production/kustomization.yaml`.

**Acceptance criteria**:
- `flux reconcile kustomization apps --with-source` applies the pdf-extractor manifests
- `kubectl get deployment pdf-extractor -n production` shows the deployment
- `kubectl get scaledobject pdf-extractor-scaler -n production` shows the KEDA scaler

---

## Open Questions

| # | Question | Owner | Priority |
|---|---|---|---|
| OQ-1 | Multipart upload vs base64 for PDF submission — depends on frontend/mobile constraints | Frontend + Backend | High |
| OQ-2 | WebSocket vs polling for job status — real-time notify vs simpler polling loop | Frontend | Medium |
| OQ-3 | `extracted_text` in DB TEXT column vs S3 — at ≤100 KB DB is fine; revisit if volume grows | Backend | Low |
| OQ-4 | DLQ consumer strategy — dedicated banking service consumer vs manual ops intervention + alerting | Backend | Medium |
| OQ-5 | `card_last_four_digits` on `financial_accounts` — confirm if exists in Phase 1 schema or needs PDF-14 migration | Backend | High |
