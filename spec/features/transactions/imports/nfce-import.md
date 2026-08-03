---
id: feature.transactions.imports.nfce-import
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportJobService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/import/ImportResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/nfce/NfceFetcherRouter.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/nfce/SpNfceFetcher.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/nfce/SpNfceHtmlParser.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/nfce/DefaultNfceClient.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/client/import/nfce/NfceUrlSanitizer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/dedup/SameSourceDedupService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/dedup/CrossSourceDedupService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/TransactionEntityRepository.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_12__create_product_price_tracking_tables.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_5__create_document_import_jobs.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/ImportJobServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/nfce/SpNfceHtmlParserTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/nfce/SpNfceFetcherTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/nfce/NfceFetcherRouterTest.kt
    - web/tests/e2e/personal-finance/transaction-import-nfce.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/transaction/NfceImportDrawer.tsx
    - web/src/app/personal-finance/finance-transactions/transaction/NfceDocumentDrawer.tsx
    - web/src/app/personal-finance/finance-transactions/transaction/ImportDuplicateReview.tsx
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.imports.nfce-products-and-aliases
  - feature.transactions.imports.dedup-and-duplicate-resolution
  - feature.transactions.imports.import-job-lifecycle
  - feature.transactions.summaries.monthly-balance-snapshots
schema: spec-metadata-v1
---

# Imports — NFC-e Import

## Glossary

- `accountId` — tenant owning the import job, purchase document, and ledger row.
- `financialAccountId` — destination banking FA for the created transaction.
- NFC-e import — synchronous GraphQL `importNfce` from a QR/consulta URL. Shares `document_import_jobs` (`TRANSACTION_IMPORT`) and `resolveImportDuplicate` with Batch 6, but **not** Kafka/file parse or bill PDF confirm.

## Behavior

### Cycle

```text
qrCodeUrl → sanitize/validate → SEFAZ fetch/parse (UF router)
  → same-source skip (chNFe) OR manual REVIEW_REQUIRED OR create txn
  → persist purchase_documents + purchase_items (product resolve — see products spec)
  → recomputeChain snapshot
```

- **Synchronous** `@Transactional` path in `ImportJobService.importNfce` — no Kafka, no asset store, no SSE required.
- Job `source=NFC_E`; `fileHash` = access key `chNFe` (not file SHA-256).
- Permission: `banking:transaction:create`. Ownership: FA must belong to caller `accountId`.

### External fetch / parse

| Path | When |
|---|---|
| `NfceFetcherRouter` | UF = first 2 digits of 44-digit `chNFe` in URL |
| `SpNfceFetcher` + `SpNfceHtmlParser` | UF `35` (SP) — HTML summary + POST visualizar abas |
| `DefaultNfceClient` | Other UFs / fallback — XML fetch; accepts cStat `100`/`150` |
| Sanitizer | `|` → `%7C` before request |

Failures: `NfceUnavailableException` → `SERVICE_UNAVAILABLE`; `NfceInvalidException` → `INVALID_INPUT` on `qrCodeUrl`.

Web allowlist (UX only): `nfce.fazenda.sp.gov.br`, `dfe-portal.svrs.rs.gov.br`, `nfe.fazenda.gov.br` — stricter than backend.

### Dedup / review (vs statement)

| Layer | NFC-e |
|---|---|
| Same-source | Active txn with `providerTransactionId=chNFe` and `TransactionSource.MANUAL` → skipped job (`skippedCount=1`, COMPLETED) |
| Manual link | `findManualNfceCandidates`: MANUAL DEBIT, same amount, date ±1 day, **no** existing `purchase_documents`, LIMIT 1 → `REVIEW_REQUIRED` + gate `nfce_manual_link` |
| Fingerprint | **Not used** (`importFingerprint=null` on created txn) |

`resolveImportDuplicate` when `job.source == NFC_E` (re-fetches SEFAZ from stored QR URL):

| Resolution | NFC-e |
|---|---|
| `USE_INCOMING` | **Link** document/items to existing txn (no soft-delete) |
| `KEEP_BOTH` | Create new NFC-e txn + artifacts |
| `KEEP_EXISTING` | No-op |

Web `ImportDuplicateReview` hides Keep Existing for NFC-e and labels Use Incoming as link.

### Created transaction

- Type `OTHER_EXPENSE`, credit/debit DEBIT, status POSTED.
- Ledger `source=MANUAL` (job remains `ImportSource.NFC_E`) — user-originated path, not Open Finance.
- Description/counterparty from emitente; `providerTransactionId=chNFe`.
- **No** `TransactionRuleEngine` / merchant entity / transaction category assignment on commit (unlike statement `ImportProcessor`).
- After success: `SnapshotComputationService.recomputeChain` (failures logged).

### Read-back

- `nfceDocument(transactionId)` — view permission; `Transaction.hasNfceDocument` live lookup.

## Invariants

- Backend owns fetch, ownership, dedup, persist, snapshot; frontend is UX (QR decode / URL / review).
- One access key per tenant (`uq_purchase_documents_account_key`).
- Soft-deleted same-source txs do not block re-import (same as Batch 6 soft-delete rule).

## Permissions

| Op | Permission |
|---|---|
| `importNfce` | `banking:transaction:create` |
| `resolveImportDuplicate` / `cancelImportReview` | `banking:transaction:update` |
| `nfceDocument` / import job queries | `banking:transaction:view` |

## Data And Contracts

- GraphQL: `importNfce`, `ImportResult` / `TransactionImport`, `NfceDocument`, `PurchaseItem`.
- Jobs: shared `document_import_jobs` with statement kind; NFC-e statuses use statement `ImportStatus` enum.
- Bruno: `Import NFC-e QR.bru` (sample often RS URL); Resolve Duplicate generic, not NFC-e link-specific.

## Impact Surface

- Backend ImportJobService NFC-e path, fetchers/parsers, resolvers
- GraphQL + NfceImportDrawer / NfceDocumentDrawer / ImportDuplicateReview + E2E `transaction-import-nfce.e2e.spec.ts`
- Cross: product/alias resolve on item persist; monthly snapshots; Batch 6 resolve mutation shared

## Decisions

- Sync import (SEFAZ in request path) vs async file/PDF pipelines.
- Manual candidate review instead of statement cross-source date+amount+different-source.
- Store ledger source as `MANUAL` while job source is `NFC_E`.
- MVP: specialized HTML fetcher is **SP-only** by design; multi-UF expansion tracked as spike [#590](https://github.com/invistus/invistus/issues/590).

## Known Gaps

- Multi-UF coverage beyond SP MVP — spike [#590](https://github.com/invistus/invistus/issues/590).
- NFC-e create does not run `TransactionRuleEngine` (unlike statement import) — [#591](https://github.com/invistus/invistus/issues/591).
- Manual candidate query uses `LIMIT 1` — multi-match review — [#592](https://github.com/invistus/invistus/issues/592).
- Bruno lacks NFC-e-specific resolve/link coverage and `nfceDocument` asserts.
- `UnmappedItemsDrawer` exists but is unwired from the main PF UI (documentation-only gap; purpose/product entry unclear — see products/aliases spec).

## Legacy Notes

- None used as authority.
