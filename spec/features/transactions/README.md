---
id: features.transactions
domain: transactions
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-03
schema: spec-metadata-v1
---

# Transactions Features

Feature specs for personal-finance transactions. Domain discovery: `spec/domains/transactions.md`.

## Load First

- `spec/domains/transactions.md`
- Then load only the subdomain files needed for the issue.

## Batch 1 — Foundation (created)

### financial-accounts

| Spec | Confidence | Path |
|---|---|---|
| Account model | verified | [`financial-accounts/account-model.md`](./financial-accounts/account-model.md) |
| Account CRUD | verified | [`financial-accounts/account-crud.md`](./financial-accounts/account-crud.md) |
| Issuers reference | verified | [`financial-accounts/issuers-reference.md`](./financial-accounts/issuers-reference.md) |
| Tenant ownership guard | verified | [`financial-accounts/tenant-ownership-guard.md`](./financial-accounts/tenant-ownership-guard.md) |

### ledger-transactions

| Spec | Confidence | Path |
|---|---|---|
| Transaction model | verified | [`ledger-transactions/transaction-model.md`](./ledger-transactions/transaction-model.md) |
| Create / update / delete | verified | [`ledger-transactions/create-update-delete.md`](./ledger-transactions/create-update-delete.md) |

## Batch 2 — Money Movement Edges (created)

| Spec | Confidence | Path |
|---|---|---|
| Transfers and installments | partial | [`ledger-transactions/transfers-and-installments.md`](./ledger-transactions/transfers-and-installments.md) |
| Idempotency and bulk caps | verified | [`ledger-transactions/idempotency-and-bulk-caps.md`](./ledger-transactions/idempotency-and-bulk-caps.md) |
| MCP change log | verified | [`ledger-transactions/mcp-change-log.md`](./ledger-transactions/mcp-change-log.md) |

## Batch 3 — Categorization (created)

| Spec | Confidence | Path |
|---|---|---|
| Category catalog | verified | [`categorization/category-catalog.md`](./categorization/category-catalog.md) |
| Merchants | verified | [`categorization/merchants.md`](./categorization/merchants.md) |
| Rule engine | verified | [`categorization/rule-engine.md`](./categorization/rule-engine.md) |
| Bulk and similar edit | verified | [`categorization/bulk-and-similar-edit.md`](./categorization/bulk-and-similar-edit.md) |

## Batch 4 — Credit Card Bills (created)

| Spec | Confidence | Path |
|---|---|---|
| Bill lifecycle | verified | [`credit-card-bills/bill-lifecycle.md`](./credit-card-bills/bill-lifecycle.md) |
| Pay bill | verified | [`credit-card-bills/pay-bill.md`](./credit-card-bills/pay-bill.md) |
| Reference month and identity | verified | [`credit-card-bills/reference-month-and-identity.md`](./credit-card-bills/reference-month-and-identity.md) |

## Batch 5 — Summaries (created)

| Spec | Confidence | Path |
|---|---|---|
| Monthly balance snapshots | verified | [`summaries/monthly-balance-snapshots.md`](./summaries/monthly-balance-snapshots.md) |
| Month summary query | verified | [`summaries/month-summary-query.md`](./summaries/month-summary-query.md) |
| Dashboard and cashflow | verified | [`summaries/dashboard-and-cashflow.md`](./summaries/dashboard-and-cashflow.md) |

## Batch 6 — Imports A: Statement File Import (created)

| Spec | Confidence | Path |
|---|---|---|
| Statement file import | verified | [`imports/statement-file-import.md`](./imports/statement-file-import.md) |
| Dedup and duplicate resolution | verified | [`imports/dedup-and-duplicate-resolution.md`](./imports/dedup-and-duplicate-resolution.md) |
| Import job lifecycle | verified | [`imports/import-job-lifecycle.md`](./imports/import-job-lifecycle.md) |

## Batch 7 — Imports B: Bill PDF Import (created)

| Spec | Confidence | Path |
|---|---|---|
| Bill PDF import pipeline | verified | [`imports/bill-pdf-import-pipeline.md`](./imports/bill-pdf-import-pipeline.md) |
| Bill import review and confirm | verified | [`imports/bill-import-review-and-confirm.md`](./imports/bill-import-review-and-confirm.md) |
| PDF extractor worker | verified | [`imports/pdf-extractor-worker.md`](./imports/pdf-extractor-worker.md) |

## Batch 8 — Imports C: NFC-e (created)

| Spec | Confidence | Path |
|---|---|---|
| NFC-e import | verified | [`imports/nfce-import.md`](./imports/nfce-import.md) |
| NFC-e products and aliases | partial | [`imports/nfce-products-and-aliases.md`](./imports/nfce-products-and-aliases.md) |

## Batch 9 — Recurring, Attachments, Preferences (created)

| Spec | Confidence | Path |
|---|---|---|
| Recurring model | verified | [`recurring-payments/recurring-model.md`](./recurring-payments/recurring-model.md) |
| Lifecycle and linking | verified | [`recurring-payments/lifecycle-and-linking.md`](./recurring-payments/lifecycle-and-linking.md) |
| Transaction attachments | partial | [`attachments/transaction-attachments.md`](./attachments/transaction-attachments.md) |
| Banking account preferences | partial | [`preferences/banking-account-preferences.md`](./preferences/banking-account-preferences.md) |

## Planned Tree (remaining)

```text
(none for current discovery tree — Batches 1–9 cover the planned transactions feature folders)
```

## Routing Notes

- Tenant `accountId` belongs to security; banking `financialAccountId` belongs to transactions.
- Investment portfolio, wishlist, and market-data workers may live in the banking repo/module but are not transactions specs.
- Imports are split because statement files, bill PDFs, and NFC-e have different lifecycles and dependencies.
- Summaries are derived behavior; do not treat transaction rows as stored balances.
- MCP idempotency / bulk caps / change log are agent-path controls; web UI is not enforcement.
- Merchant create uses `banking:transaction:create`; category/rule manage uses `banking:category:*`.
- Credit-card bill identity is `referenceMonth` (due month); `payBill` posts `TRANSFER` legs, not `BILL_PAYMENT` type.
- Month summary heals dirty snapshots on read; dashboard cashflow reads snapshot cache without heal.
- Statement import commits non-conflict rows during async processing; only cross-source conflicts wait for `resolveImportDuplicate`.
- Soft-deleted transactions do not block statement re-import (provider / fingerprint / cross-source).
- Statement import UX needs both `banking:transaction:create` (start) and `banking:account:view` (SSE progress) by design.
- `TransactionSource.MANUAL` = user-originated / non-integration ledger source (not Open Finance).
- Bill PDF import requires explicit `confirmBillImport` after `AWAITING_REVIEW`; worker only extracts text.
- Bill charge fingerprints use `computeForBill(..., referenceMonth, ...)`; bill-payment transfers keep `billId` null.
- Bruno PDF Import requests use stale mutation/query names vs live `submitBillImport` / `billImportJob`.
- NFC-e import is synchronous (`importNfce`); job source `NFC_E`, ledger txn source `MANUAL`; no fingerprint; SP HTML fetcher + XML fallback.
- NFC-e product catalog (`products` / aliases / purchase_items) is separate from wishlist and from `TransactionCategory`.
- Recurring `confirmRecurringPayment` advances schedule only — does not create a ledger transaction; no FUTURE materializer job found.
- Banking prefs: tenant `defaultTransactionStatus` vs per-user `ruleCreationMode`; not security profile prefs.
- Transaction attachments: max 3; assets namespace `banking-attachments`; holdings attachments are investment scope.

## Next Batch

Discovery tree for transactions feature folders is complete through Batch 9. Further reverse-spec work should be driven by new product areas or refresh of stale specs.
