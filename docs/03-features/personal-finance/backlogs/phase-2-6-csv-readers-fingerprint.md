---
title: Phase 2.6 — Multi-Bank CSV Reader (Fingerprint + Generic Fallback)
type: feature
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, csv, import, parser, fingerprint, fallback]
ai_optimized: true
search_keywords: [csv import, bank csv parser, fingerprint csv, generic csv parser, transaction import]
related:
  - 03-features/personal-finance/phase-2-5-transaction-imports.md
  - 03-features/personal-finance/import-ofx-csv-upload-sequence.md
  - 03-features/personal-finance/phase-2-transactions.md
last_updated: 2026-03-02
---

# Phase 2.6 — Multi-Bank CSV Reader (Fingerprint + Generic Fallback)

## Objective

Expand CSV import coverage across multiple banks with an extensible and safe architecture:
- Prioritize bank/layout-specific `readers`.
- Automatically detect layout through structural `fingerprint`.
- Guarantee fallback to `GenericCsvReader` when there is no confident match.
- Import at least the minimum required fields: `date`, `amount`, `description`.

## Current Problem

The current CSV parser is coupled to the Nubank layout (`CSV_NUBANK`). This limits:
- bank coverage;
- resilience to small layout changes;
- parsing logic reuse;
- incremental evolution for new formats.

## Solution Principles

1. Do not break what already works (Nubank remains a top-priority reader).
2. Separate layout detection from parser execution.
3. Use confidence scoring (not blind binary decisions).
4. Always keep a generic fallback.
5. Collect telemetry for continuous template evolution.

## Scope

### In Scope
- CSV structural fingerprint.
- Reader registry with scoring.
- Bank-specific readers for priority institutions.
- Generic fallback parser.
- Automated tests with sanitized fixtures.
- Match/fallback/parse-error observability.

### Out of Scope (for this phase)
- OCR/PDF parsing.
- Generative AI for runtime column inference.
- Advanced automatic reconciliation across multiple sources.

## Proposed Architecture

## Pipeline Overview

1. Receive CSV bytes.
2. Build `CsvFingerprint`.
3. Query `CsvReaderRegistry` for best matches.
4. If score >= threshold, execute specific reader.
5. If score < threshold or recoverable error, execute `GenericCsvReader`.
6. If minimum parse fails, return standardized error with diagnostics.

## Domain Components

- `CsvFingerprint`
  - detected delimiter
  - detected encoding
  - header present/absent
  - normalized columns
  - date patterns
  - amount patterns (decimal/sign)
  - relevant description tokens

- `CsvReaderMatch`
  - `readerId`
  - `score` (0..1)
  - `reasons` (explainability for logs/debugging)

- `BankCsvReader` (interface)
  - `id()`
  - `supports(fingerprint): CsvReaderMatch`
  - `parse(bytes): List<ParsedTransaction>`

- `CsvReaderRegistry`
  - list of registered readers
  - best-match selection
  - fallback policy

- `GenericCsvReader`
  - minimum-column inference
  - resilient parsing for common BR formats

## Matching Strategy

- Example strong signals:
  - known headers (`data`, `valor`, `descricao`, `historico`)
  - typical column order
  - delimiter and decimal formatting
  - characteristic fields (`identificador`, `lancamento`, `saldo`)

- Example thresholds:
  - `>= 0.85`: use specific reader directly
  - `0.60 - 0.84`: try specific reader and fall back to generic on recoverable error
  - `< 0.60`: go straight to generic

## Contracts and Evolution

- Keep explicit support for `CSV_NUBANK` (compatibility).
- Introduce `CSV_AUTO` as the recommended optional source for multi-bank support.
- If `source` is null, default behavior is automatic detection.

## Layered Implementation Blocks

## Layer 1 — Domain (Core Import)

### Activities
- Create `CsvFingerprint` and `CsvFingerprintBuilder`.
- Create `BankCsvReader` and `CsvReaderRegistry`.
- Create selection policy (`CsvReaderSelectionPolicy`).

### Deliverables
- domain classes decoupled from infrastructure.
- unit tests for score and selection.

## Layer 2 — Parsing/Infra

### Activities
- Refactor `CsvNubankParser` to implement `BankCsvReader`.
- Create initial readers for priority banks.
- Create `GenericCsvReader` with minimum-column heuristics.
- Standardize robust parsing for:
  - `date` (`dd/MM/yyyy`, `yyyy-MM-dd`, `dd-MM-yyyy`)
  - `amount` (`,`/`.` decimals, signs, symbols)
  - `description` (trim/basic sanitization)

### Deliverables
- package `domain/import/parser/csv/readers/*`
- unit coverage per sanitized fixture.

## Layer 3 — Application Service

### Activities
- Integrate registry in `ImportFileConsumer` flow.
- Implement chained fallback with error handling and traceability.
- Persist execution metadata (selected reader, score, fallback used).

### Deliverables
- asynchronous flow preserving correct statuses (`PENDING -> PROCESSING -> COMPLETED/FAILED/REVIEW_REQUIRED`).
- structured logs by `jobId`.

## Layer 4 — API/GraphQL

### Activities
- Expose `source=CSV_AUTO` in schema.
- Include optional diagnostics in `transactionImport`:
  - `detectedReaderId`
  - `readerConfidence`
  - `usedFallback`

### Deliverables
- updated schema + resolver.
- backward compatibility with existing clients.

## Layer 5 — Frontend (optional in this phase)

### Activities
- Adjust UI to send `CSV_AUTO` by default.
- Show user-friendly message when generic fallback is used.
- Prepare future step for manual column mapping (when confidence is low).

### Deliverables
- current flow preserved with no regression in existing imports.

## Layer 6 — Observability and Operations

### Activities
- metrics:
  - `csv_reader_match_total{readerId}`
  - `csv_reader_fallback_total`
  - `csv_parse_failure_total{readerId,reason}`
- selection decision logs (score and reasons).
- coverage dashboard by bank/layout.

### Deliverables
- indicators to prioritize new readers.

## Layer 7 — Security and Privacy

### Activities
- sanitized fixture policy.
- reject persistence of sensitive PII in logs.
- validate real-sample collection with consent and anonymization.

### Deliverables
- sample ingestion guideline for the team.

## Activity Backlog (Implementation-Ready)

### CSVR-01 — Structural Fingerprint
- **Status**: Done
- **Description**: implement fingerprint builder + tests.
- **Acceptance**: detect delimiter/header/columns/date pattern/amount pattern in reference fixtures.

### CSVR-02 — Registry and Selection Policy
- **Status**: Done
- **Description**: implement scoring, ranking, and selection.
- **Acceptance**: high/medium/low match scenarios covered in tests.

### CSVR-03 — Refactor Nubank Reader
- **Status**: Done
- **Description**: migrate current parser to the new contract (`BankCsvReader`).
- **Acceptance**: no regression in current Nubank scenario.

### CSVR-04 — GenericCsvReader
- **Status**: Done
- **Description**: generic parser for minimum fields.
- **Acceptance**: successfully imports unknown CSVs with `date/amount/description`.

### CSVR-05 — Integrate in Consumer
- **Status**: Done
- **Description**: use registry + fallback in async processing.
- **Acceptance**: jobs finish with correct status and no stuck states.

### CSVR-06 — GraphQL/Schema (`CSV_AUTO`)
- **Status**: Done
- **Description**: update schema/resolver for automatic source.
- **Acceptance**: mutation accepts `CSV_AUTO` and preserves compatibility.

### CSVR-07 — Telemetry
- **Status**: Done
- **Description**: metrics and decision logs.
- **Acceptance**: minimum dashboard with fallback and failure rates.

### CSVR-08 — Priority Banks Wave 1
- **Status**: In Progress
- **Description**: dedicated readers (e.g., Itau, BB, Bradesco, Santander, Inter, Caixa).
- **Acceptance**: parse coverage with sanitized fixtures and green tests.

## Rollout Strategy

1. Feature flag `csv.auto-reader.enabled`.
2. Internal canary with anonymized real samples.
3. Enable for a percentage of accounts.
4. Expand by bank according to telemetry.

## Test Strategy

### Unit
- fingerprint builder.
- score by reader.
- `date/amount/description` parsing.

### Integration
- `ImportFileConsumer` with CSV_AUTO + fallback.
- status and counters in `transaction_imports`.

### Contract/GraphQL
- `importTransactionsFile` mutation with `CSV_AUTO`.
- `transactionImport` query with correct final data.

### Fixtures
- folder per bank with sanitized CSV + `manifest.json`:
  - bank
  - encoding
  - delimiter
  - header
  - date/amount format
  - mapped columns

## Feature Success Criteria

- Nubank parser remains stable.
- Unmapped bank CSVs are handled by generic parser with useful success rate.
- CSV jobs stop failing for simple unknown layouts.
- Metrics show progressive fallback reduction as new readers are added.

## Recommended Next Step

Start with `CSVR-01`, `CSVR-02`, and `CSVR-03` in the same cycle, since they provide the base with minimal regression risk for the current flow.
