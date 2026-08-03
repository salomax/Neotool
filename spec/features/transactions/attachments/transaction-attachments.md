---
id: feature.transactions.attachments.transaction-attachments
domain: transactions
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/attachment/RecordAttachment.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/attachment/AttachmentService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/attachment/
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_9__create_attachments.sql
  tests: []
  frontend:
    - web/src/app/personal-finance/components/AttachmentSection.tsx
    - web/src/app/personal-finance/finance-transactions/transaction/TransactionFormMetaFields.tsx
    - web/src/lib/hooks/useAttachmentPreview.ts
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.ledger-transactions.transaction-model
schema: spec-metadata-v1
---

# Attachments — Transaction Attachments

## Glossary

- `accountId` — tenant on attachment row and ownership check of parent transaction.
- `financialAccountId` — not stored on attachments; scope is via parent txn.
- Asset — file in assets service (`banking-attachments` namespace); attachment row is the link.
- Holding attachments share the same service/limits — **out of Batch 9 depth** (investment).

## Behavior

### Flow

1. Client uploads via assets service (`confirmAssetUpload` → READY asset).
2. `attachFileToTransaction(transactionId, assetId)` — parent txn must exist under caller `accountId`; max **3** attachments per transaction (`MAX_ATTACHMENTS_PER_RECORD`).
3. `Transaction.attachments` lists links with filename/mime/size/downloadUrl (from assets).
4. `removeTransactionAttachment(attachmentId)` — removes link and deletes underlying asset via assets client.

### Limits

| Layer | Rule |
|---|---|
| Backend | Max 3 per transaction; unique `(transaction_id, asset_id)`; tenant txn must exist |
| Schema docs | Asset must be READY; supported formats documented |
| Web UX | Max 3; max **3 MB**; accept image/text/csv/xls/xlsx/pdf |

Backend `AttachmentService` does **not** re-check READY status or MIME/size — UX/assets service are the practical gates.

### Storage

- Table `banking.transaction_attachments` (V0_9): `account_id`, `transaction_id`, `asset_id` — **no FK** to `transactions`.

## Invariants

- Attachments never orphan ownership: always keyed by tenant + verified parent txn on attach.
- Frontend is UX; backend enforces count + ownership.

## Permissions

- Field resolve / list under transaction view path.
- Attach / remove: `banking:transaction:update`.

## Data And Contracts

- GraphQL: `TransactionAttachment`, `AttachFileToTransactionInput`, `attachFileToTransaction`, `removeTransactionAttachment`.
- No Bruno banking attach/remove requests found (assets upload collection may exist separately).

## Impact Surface

- Backend AttachmentService + GraphQL attachment wiring
- Web AttachmentSection / form meta / row preview
- Cross: assets service; ledger txn UI only (not holdings in this spec)

## Decisions

- Private assets namespace + thin banking link table.
- Cap of 3 attachments per record at service layer.

## Known Gaps

- No Kotlin unit/IT for transaction attachments → confidence **partial**.
- Schema READY requirement not enforced in `attachToTransaction`.
- Client MIME/size not mirrored as backend validation.
- No E2E / Bruno for attach/remove.

## Legacy Notes

- None used as authority.
