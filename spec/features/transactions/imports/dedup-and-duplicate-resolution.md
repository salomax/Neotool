---
id: feature.transactions.imports.dedup-and-duplicate-resolution
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportProcessor.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/dedup/SameSourceDedupService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/dedup/ImportFingerprintService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/dedup/CrossSourceDedupService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportJobService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/TransactionEntityRepository.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_7__create_transactions.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_23__fix_uq_transactions_provider_soft_delete.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_5__create_document_import_jobs.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/SameSourceDedupServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/SameSourceDedupServiceIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/ImportFingerprintServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/CrossSourceDedupServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/CrossSourceDedupServiceIT.kt
    - web/tests/e2e/personal-finance/transaction-import.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/transaction/ImportDuplicateReview.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.imports.statement-file-import
  - feature.transactions.imports.import-job-lifecycle
  - feature.transactions.ledger-transactions.transaction-model
schema: spec-metadata-v1
---

# Imports — Dedup And Duplicate Resolution

## Glossary

- `accountId` — tenant owning jobs and soft-deletes.
- `financialAccountId` — FA scope for all row-level dedup lookups.
- Soft-delete — `transactions.deleted_at IS NULL` required for match; deleted rows do **not** block re-import.

## Behavior

### Layered dedup on statement process

Order in `ImportProcessor` (critical):

1. **Level 1 — same source / provider id** (`SameSourceDedupService`): active row with same `(financialAccountId, providerTransactionId, source)` → **skip** (`skippedCount++`), no review.
2. **Level 1b — fingerprint** (`ImportFingerprintService`):  
   `SHA-256(financialAccountId|date|amountCents|trim.lowercase(description)|creditDebitType)`  
   Active row with same `import_fingerprint` → **skip**. App-level only (indexes, **no** DB unique constraint on fingerprint).
3. **Level 2 — cross-source** (`CrossSourceDedupService`): same FA + date + amount, **different** `source`, active → **no insert**; build `ImportDuplicateCandidate` → may drive job `REVIEW_REQUIRED`.

Date guards (initial balance / allowed window) run between fingerprint and cross-source; failures become row errors, not skips.

`allowCrossSourceDuplicates=true` on resolve path bypasses Level 2 (and skips initial-balance cache) so chosen resolutions can insert.

### File-level idempotency (job)

- SHA-256 of file bytes stored as `file_hash`.
- Unique index `uq_document_import_jobs_transaction_account_hash` on `(financial_account_id, file_hash) WHERE job_kind='TRANSACTION_IMPORT'`.
- Re-upload of same bytes may reset/retry eligible job statuses rather than inserting a second job row (see lifecycle).

### Soft-delete impact

- Provider unique constraint is **partial**: `WHERE deleted_at IS NULL AND provider_transaction_id IS NOT NULL` (`V0_23`).
- Same-source, fingerprint, and cross-source queries all ignore soft-deleted txs → soft-delete enables re-import of the same economic event.
- `USE_INCOMING` soft-deletes conflicting existing txs before insert.

### Resolve (`resolveImportDuplicate`)

Permission: `banking:transaction:update`. Job must be `REVIEW_REQUIRED`.

| Resolution | Statement behavior |
|---|---|
| `USE_INCOMING` | Soft-delete each existing candidate; process incoming with cross-source allowed; `importedCount++` |
| `KEEP_BOTH` | Insert incoming with cross-source allowed; keep existing |
| `KEEP_EXISTING` | No insert/delete; no snapshot recompute for that resolution |

After non-`KEEP_EXISTING`: `recomputeChain` from earliest affected date. Decrement `duplicateReviewCount`; when candidates empty → `COMPLETED`, clear review payload.

NFC-e resolution branches exist in the same method — **out of Batch 6 depth**.

### Bill fingerprint variant

`computeForBill` / `isDuplicateForBill` use `referenceMonth` instead of date — **bill import only** (Batch 7), not statement processor path.

## Invariants

- Soft-deleted ledger rows never count as duplicates.
- Fingerprint collision is enforced in application code, not a unique DB constraint (race window possible under concurrent inserts).
- Cross-source conflict never auto-inserts; user resolution is required.

## Permissions

- Resolve: `banking:transaction:update`.
- Cancel review (related): `cancelImportReview` also update permission — see lifecycle.

## Data And Contracts

- GraphQL: `DuplicateResolution`, `ImportDuplicateCandidate`, `ResolveImportDuplicateInput`, `resolveImportDuplicate`.
- Bruno: `Resolve Import Duplicate.bru` (sample `KEEP_BOTH`).
- Web: `ImportDuplicateReview` supports all three resolutions.

## Impact Surface

- Backend dedup services + ImportProcessor + ImportJobService.resolve
- GraphQL/Bruno/web review UI + E2E duplicate cases
- Cross: snapshots on insert resolutions; categorization runs on insert (same as fresh import)

## Decisions

- Same-source / fingerprint = hard skip; cross-source = human review.
- Description normalization for fingerprint is trim + lowercase only (not full merchant canonicalization).

## Known Gaps

- No DB unique on `import_fingerprint` → concurrent double-insert race is theoretical.
- Fingerprint service KDoc says “Level 2”; processor treats it as Level 1b hard skip — naming mismatch.
- User-originated AUTO/unmapped imports store as `TransactionSource.MANUAL` (confirmed product rule); cross-source matches are therefore against non-`MANUAL` sources (e.g. OFX vs prior MANUAL row).
- Bruno lacks coverage of `USE_INCOMING` / `KEEP_EXISTING` variants beyond sample.

## Legacy Notes

- None used as authority.
