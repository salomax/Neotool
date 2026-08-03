---
id: feature.transactions.imports.statement-file-import
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportJobService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportProcessor.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/parser/ImportParserRouter.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/parser/ImportFormatDiscoveryService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/parser/OfxImportParserStrategy.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/parser/CsvImportParserStrategy.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/parser/ExcelImportParserStrategy.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ofx/OfxDestinationCompatibilityService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/ImportFileConsumer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/import/ImportResolver.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_5__create_document_import_jobs.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/ImportProcessorTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/ImportProcessorIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/infra/kafka/ImportFileConsumerIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/infra/kafka/parser/ImportParserRouterTest.kt
    - web/tests/e2e/personal-finance/transaction-import.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/transaction/ImportTransactionsDrawer.tsx
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.imports.dedup-and-duplicate-resolution
  - feature.transactions.imports.import-job-lifecycle
  - feature.transactions.ledger-transactions.create-update-delete
  - feature.transactions.categorization.rule-engine
  - feature.transactions.summaries.monthly-balance-snapshots
schema: spec-metadata-v1
---

# Imports — Statement File Import

## Glossary

- `accountId` — tenant/workspace from security claims; scopes the import job.
- `financialAccountId` — destination banking FA for created ledger rows.
- Statement import — OFX / CSV / XLSX (and AUTO discovery) via `importTransactionsFile`. **Not** bill PDF confirm flow (Batch 7) or NFC-e QR import (Batch 8).

## Behavior

### Entry

- GraphQL `importTransactionsFile(input: ImportTransactionsFileInput!)` → `ImportResult`.
- Input: `financialAccountId`, `fileName`, optional `source`, `fileBase64` (resolver enforces file-import sources and ~3MB cap).
- Ownership: destination FA must belong to caller `accountId`.
- Permission: `banking:transaction:create`.
- Persists `banking.document_import_jobs` with `job_kind=TRANSACTION_IMPORT`, stores asset, publishes Kafka `ImportFileRequestedEvent` (async). **There is no separate preview/commit mutation** for statements.

### Format discovery and parsers

| Path | How | Notes |
|---|---|---|
| OFX | `OfxImportParserStrategy` → `OFXParser` | Sync. `OfxDestinationCompatibilityService` requires bank vs card FA subtype to match OFX intent when known. |
| CSV | `CsvImportParserStrategy` + bank readers (Nubank, BB, Itaú, Bradesco, Santander) + `GenericCsvReader` | Sync. Selection policy may force specific, specific-then-generic, or generic-only. |
| XLSX | `ExcelImportParserStrategy` **only when requested source is AUTO** | Deterministic tabular reader first; unknown profile → assistant/LLM reader async (`ImportReaderStatusConsumer`). |
| AUTO (null source) | Discovery: OFX sig → XLSX ZIP → CSV detector/extension → else AUTO | Web omits `source`; discovery drives routing. PDF under AUTO is a shared pipeline boundary — not Batch 6 depth. |

`ImportSource` → stored `TransactionSource`: OFX→OFX; CSV_*→CSV; AUTO/unknown→**MANUAL**.  
`MANUAL` means user-originated (upload / non-integration path), not Open Finance / Pluggy / direct financial-institution feed. That mapping affects same-source vs cross-source dedup keying.

### Row processing (after parse)

- `ImportProcessor.process`: same-source provider-id skip → fingerprint skip → initial-balance / date-window row errors → cross-source duplicate candidates (no insert) → else insert.
- Inserted rows: default type `OTHER_EXPENSE` / `OTHER_INCOME` from credit/debit; **rule engine** preload + suggest (category + type override); set `importFingerprint`, `importJobId`, source metadata.
- After batch: `SnapshotComputationService.recomputeChain` when any row imported (failures logged; sweeper heals).
- **No** credit-card bill linking on this path.

### UX

- `ImportTransactionsDrawer`: BANK accounts only; accepts OFX/CSV/XLSX/XLS/PDF file picker; waits via SSE (`useImportJobStatus`) with GraphQL poll fallback; surfaces duplicate review UI.
- Bruno: Import OFX, Custom Base64, XLSX Unknown Profile, List/Get jobs (PDF/NFC-e Bruno files exist but are other batches).

## Invariants

- Backend owns parse, ownership, insert, and counters; frontend is UX/progress only.
- Non-conflict rows commit during async processing — not held for a global “confirm all” step.
- Tenant `accountId` ≠ banking `financialAccountId`.

## Permissions

Backend: create = `banking:transaction:create`; list/get jobs = `banking:transaction:view`.

UX: drawer further limits destination to BANK type accounts.

## Data And Contracts

- GraphQL: `ImportTransactionsFileInput`, `TransactionImport`, `ImportResult`, `ImportSource`, `ImportStatus`.
- Jobs live in `document_import_jobs` (not a separate `transaction_imports` table).
- File-hash unique index per FA for `TRANSACTION_IMPORT` (see lifecycle / dedup specs).

## Impact Surface

- Backend: ImportJobService, parsers, ImportProcessor, ImportFileConsumer, OFX compatibility, Excel/assistant readers
- GraphQL + Bruno Import (statement requests) + web drawer/hook + E2E `transaction-import.e2e.spec.ts`
- Cross: rule engine on insert; monthly snapshot recompute; bills **not** linked here

## Decisions

- Async Kafka processing with immediate insert of non-conflicts.
- XLSX unknown profiles use assistant extraction rather than failing closed immediately.
- `TransactionSource.MANUAL` is the ledger source for user-originated imports that are not integration feeds (Open Finance / institution). AUTO/unmapped import sources store as `MANUAL` by design.

## Known Gaps

- Limited E2E for XLSX→assistant path and OFX bank/card destination mismatch UX.
- Web rarely sends explicit `CSV_NUBANK` etc.; bank detection is server-side.
- Naming: comments/legacy text may say `transaction_imports`; DB table is `document_import_jobs`.

## Legacy Notes

- None used as authority; historical docs were not consulted.
