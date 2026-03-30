# Phase 2.5 — Import Transactions: Full Scope

Backend (migrations, services, repos, entities, models), GraphQL, REST, Kafka, tests, web UI, Bruno collection, and docs.

---

## 1. Migrations (Kotlin / DB)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/resources/db/migration/V0_9__add_import_fields_to_transactions.sql` | Adds `import_fingerprint`, `import_job_id` to `transactions`; extends `source` check; indexes for dedup |
| `service/kotlin/banking/src/main/resources/db/migration/V0_10__create_transaction_imports.sql` | Creates `banking.transaction_imports`; indexes; FK from `transactions.import_job_id` |

---

## 2. Entities / Models (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../model/importjob/TransactionImportEntity.kt` | JPA entity for `transaction_imports` |
| `service/kotlin/banking/src/main/kotlin/.../model/importjob/TransactionImportMapper.kt` | Maps Entity ↔ domain `ImportJob` |

---

## 3. Repositories (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../repo/TransactionImportEntityRepository.kt` | JPA repo for `TransactionImportEntity` |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJobRepository.kt` | Port for import job persistence |
| `service/kotlin/banking/src/main/kotlin/.../repo/DefaultImportJobRepository.kt` | Implementation of `ImportJobRepository` |

---

## 4. Domain — Core (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJob.kt` | Domain model: import job |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJobStatus.kt` | Enum: PENDING, PROCESSING, COMPLETED, FAILED, REVIEW_REQUIRED |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportSource.kt` | Enum: OFX, CSV_*, NFC_E, etc. |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJobService.kt` | Orchestrates: importTransactionsFile, importNfce, resolveImportDuplicate |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportProcessor.kt` | Processes rows: dedup, insert, duplicate candidates |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ParsedTransaction.kt` | Parsed row from file |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/UnsupportedImportSourceException.kt` | Thrown for unsupported source |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/DuplicateResolution.kt` | Enum: KEEP_BOTH, USE_INCOMING, KEEP_EXISTING |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJobRepository.kt` | Port for job persistence |

---

## 5. Domain — Parsers (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../domain/import/parser/OFXParser.kt` | OFX (SGML/XML) → `ParsedTransaction` |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/parser/CsvNubankParser.kt` | CSV Nubank → `ParsedTransaction` |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportSourceDetector.kt` | Detects source from content |

---

## 6. Domain — Dedup (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../domain/import/dedup/SameSourceDedupService.kt` | Same-source dedup (e.g. `providerTransactionId`) |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/dedup/CrossSourceDedupService.kt` | Cross-source dedup (fingerprint) |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/dedup/ImportFingerprintService.kt` | Builds fingerprint for dedup |

---

## 7. Domain — NFC-e (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceClient.kt` | Interface: fetch by QR URL |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceData.kt` | Data: chNFe, dhEmi, vNF, items, etc. |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceItem.kt` | Line item (xProd, vProd, …) |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceHttpFetcher.kt` | Interface: raw HTTP |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceHttpResponse.kt` | statusCode + body |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/DefaultNfceHttpFetcher.kt` | HTTP impl |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/DefaultNfceClient.kt` | Fetches + parses XML → `NfceData` |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceUnavailableException.kt` | SEFAZ/timeout |
| `service/kotlin/banking/src/main/kotlin/.../domain/import/nfce/NfceInvalidException.kt` | Invalid/cancelled NFC-e |

---

## 8. Domain — Port (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../domain/import/port/ImportFileStoragePort.kt` | Port for file storage (e.g. assets) |

---

## 9. Domain — Event Bus (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../domain/import_/ImportEventBus.kt` | Events for import job completion (e.g. SSE) |

---

## 10. GraphQL (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/resources/graphql/schema.graphqls` | Types: TransactionImport, ImportResult, ImportDuplicateCandidate, ParsedImportRow, ResolveImportDuplicateInput, DuplicateResolution; queries: transactionImport, transactionImports; mutations: importTransactionsFile, importNfce, resolveImportDuplicate |
| `service/kotlin/banking/src/main/kotlin/.../graphql/resolver/ImportResolver.kt` | Resolves importTransactionsFile, importNfce, transactionImport(s), resolveImportDuplicate |
| `service/kotlin/banking/src/main/kotlin/.../graphql/dto/ImportDTO.kt` | TransactionImportDTO, ImportResultDTO, ImportDuplicateCandidateDTO, ParsedImportRowDTO |
| `service/kotlin/banking/src/main/kotlin/.../graphql/BankingWiringFactory.kt` | Wires import resolver to schema |

---

## 11. REST / SSE (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../rest/ImportSseController.kt` | SSE endpoint for import job status |

---

## 12. Kafka (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportTopics.kt` | Topic names |
| `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileProducer.kt` | Produces file-import requests |
| `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileConsumer.kt` | Consumes, processes, updates job |
| `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportJobCompletedProducer.kt` | Produces job-completed events |

---

## 13. Backend Tests (Kotlin)

| File | Purpose |
|------|--------|
| `service/kotlin/banking/src/test/.../domain/import/ImportSourceDetectorTest.kt` | Source detection |
| `service/kotlin/banking/src/test/.../domain/import/ImportProcessorTest.kt` | Processor unit |
| `service/kotlin/banking/src/test/.../domain/import/ImportProcessorIT.kt` | Processor integration |
| `service/kotlin/banking/src/test/.../domain/import/ImportJobServiceTest.kt` | ImportJobService (file + NFC-e) |
| `service/kotlin/banking/src/test/.../domain/import/dedup/ImportFingerprintServiceTest.kt` | Fingerprint |
| `service/kotlin/banking/src/test/.../domain/import/nfce/NfceClientTest.kt` | NfceClient (mocked HTTP) |
| `service/kotlin/banking/src/test/.../graphql/resolver/ImportResolverTest.kt` | Import resolver |
| `service/kotlin/banking/src/test/.../rest/ImportSseControllerTest.kt` | SSE unit |
| `service/kotlin/banking/src/test/.../rest/ImportSseControllerIT.kt` | SSE integration |
| `service/kotlin/banking/src/test/.../integration/TransactionImportRepositoryIT.kt` | Repo / entity |
| `service/kotlin/banking/src/test/.../infra/kafka/ImportFileProducerTest.kt` | Producer |
| `service/kotlin/banking/src/test/.../infra/kafka/ImportFileConsumerIT.kt` | Consumer integration |

---

## 14. Web (Phase 2.5)

| File | Purpose |
|------|--------|
| `web/src/lib/graphql/operations/banking/mutations.ts` | IMPORT_TRANSACTIONS_FILE, IMPORT_NFCE, RESOLVE_IMPORT_DUPLICATE |
| `web/src/lib/graphql/operations/banking/queries.ts` | TRANSACTION_IMPORT (full), TRANSACTION_IMPORTS |
| `web/src/lib/hooks/useImportJobStatus.ts` | SSE + onEvent, onReviewRequired, etc. |
| `web/src/app/personal-finance/i18n/locales/en.json` | import, nfce, duplicateReview |
| `web/src/app/personal-finance/i18n/locales/pt.json` | Same keys |
| `web/src/app/personal-finance/finance-transactions/components/ImportTransactionsDrawer.tsx` | File import UI + review step |
| `web/src/app/personal-finance/finance-transactions/components/ScanNfceButton.tsx` | NFC-e URL (desktop) |
| `web/src/app/personal-finance/finance-transactions/components/ImportDuplicateReview.tsx` | Duplicate review UI |
| `web/src/app/personal-finance/finance-transactions/page.tsx` | Import drawer, Scan NFC-e, imports query, review banner |
| `web/src/app/personal-finance/finance-transactions/components/ImportTransactionsDrawer.test.tsx` | Drawer tests |
| `web/src/app/personal-finance/finance-transactions/components/ScanNfceButton.test.tsx` | Scan NFC-e tests |
| `web/src/app/personal-finance/finance-transactions/components/ImportDuplicateReview.test.tsx` | Duplicate review tests |

---

## 15. Bruno Collection (Phase 2.5)

| File | Purpose |
|------|--------|
| `contracts/api-collections/bruno/collections/supergraph/Banking/Import OFX File.bru` | importTransactionsFile; post-response saves `import_job_id` |
| `contracts/api-collections/bruno/collections/supergraph/Banking/Import NFC-e QR.bru` | importNfce |
| `contracts/api-collections/bruno/collections/supergraph/Banking/Get Import Job.bru` | transactionImport({{import_job_id}}) |
| `contracts/api-collections/bruno/collections/supergraph/Banking/List Import Jobs.bru` | transactionImports |
| `contracts/api-collections/bruno/collections/supergraph/Banking/Resolve Import Duplicate.bru` | resolveImportDuplicate (KEEP_BOTH) |
| `contracts/api-collections/bruno/collections/supergraph/Banking/README.md` | Execution order + env vars |

---

## 16. Docs

| File | Purpose |
|------|--------|
| `docs/03-features/personal-finance/phase-2-5-backlog.md` | IMPORT-26, IMPORT-28, IMPORT-29 marked Done |

---

## Summary

- **Backend (existing):** 2 migrations, 2 model files (entity + mapper), 3 repos, ~25 domain files (import + parsers + dedup + nfce + port + event), 4 GraphQL (schema + resolver + DTOs + wiring), 1 REST (SSE), 4 Kafka, 12 test files.
- **Phase 2.5 (this scope):** 3 new web components + 3 test files, mutations/queries/hook/i18n/page changes, 6 Bruno files, backlog doc updates.
