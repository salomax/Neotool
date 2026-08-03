---
title: Credit Card Bill Import Remediation
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/banking/src/main/resources/db/migration/V0_34__add_credit_card_bill_reference_month.sql
  - service/kotlin/banking/src/main/resources/db/migration/V0_35__credit_card_bill_reference_month_unique.sql
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/BillImportCommitPreparer.kt
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/CreditCardBillRepository.kt
load_when:
  - fixing bills that still have transactions on the wrong due-month bill
  - cleaning duplicate active bills after import bugs
supersedes:
schema: docs-metadata-v1
---

# Credit Card Bill Import Remediation

Purpose: remediate credit card bill import data when transactions attach to the wrong bill/due month.

## Symptoms

- A credit card bill shows transactions that belong to a different due month.
- Re-import did not move rows created before a fix was deployed.
- Multiple active bills exist for the same `(financial_account_id, reference_month)`.

## Authority

Operational SQL is verified against `banking.credit_card_bills` / `banking.transactions` migrations and bill-import commit code. Unique active `(financial_account_id, reference_month)` is enforced in Flyway V0_35.

## Commands

1. Identify wrong vs correct bills:

```sql
SELECT b.id, b.financial_account_id, b.reference_month, b.closing_date, b.due_date,
       COUNT(t.id) AS active_transactions
FROM banking.credit_card_bills b
LEFT JOIN banking.transactions t
  ON t.bill_id = b.id AND t.deleted_at IS NULL
WHERE b.financial_account_id = :financial_account_id
  AND b.deleted_at IS NULL
GROUP BY b.id, b.financial_account_id, b.reference_month, b.closing_date, b.due_date
ORDER BY b.reference_month DESC, b.updated_at DESC;
```

2. Soft-delete import-owned rows on the wrong bill by `import_job_id` + `account_id`, including transfer peers when present.
3. Re-run bill import confirmation from the app for the corrected review payload.
4. Soft-delete orphaned bills with no active transactions.
5. Recompute bill totals / month snapshots via banking service or admin recompute path.

## Rollback

- Soft-deletes are reversible only via careful undelete or restore-from-backup; take row snapshots before updating.
- Do not hard-delete production financial rows in this procedure.

## Verification

- Bills for the account show transactions only on the correct `reference_month`/`due_date`.
- No duplicate active bills for the same account+reference month (unique index).
- Totals/snapshots match expected post-recompute values.

## Related

- [Banking PDF import DLQ](banking-pdf-import-retry-dlq.md)
- [Data and persistence](../01-architecture/data-and-persistence.md)
