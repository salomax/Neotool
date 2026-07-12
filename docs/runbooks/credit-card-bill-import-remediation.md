# Credit card bill import remediation runbook

Use this runbook when production data still has bill-import transactions attached to the wrong due-month bill after deploying issue #367.

## Symptoms

- A credit card bill shows transactions that belong to a different due month.
- Re-importing the same statement with corrected due date did not move rows before the fix was deployed.
- Multiple active bills exist for the same `(financial_account_id, reference_month)`.

## Safe remediation steps

1. Identify the affected card account and the wrong vs correct bill ids:

```sql
SELECT b.id,
       b.financial_account_id,
       b.reference_month,
       b.closing_date,
       b.due_date,
       COUNT(t.id) AS active_transactions
  FROM banking.credit_card_bills b
  LEFT JOIN banking.transactions t
         ON t.bill_id = b.id
        AND t.deleted_at IS NULL
 WHERE b.financial_account_id = :financial_account_id
   AND b.deleted_at IS NULL
 GROUP BY b.id, b.financial_account_id, b.reference_month, b.closing_date, b.due_date
 ORDER BY b.reference_month DESC, b.updated_at DESC;
```

2. For import-owned rows on the wrong bill, soft-delete them by import job scope:

```sql
UPDATE banking.transactions
   SET deleted_at = NOW(),
       updated_at = NOW()
 WHERE import_job_id = :import_job_id
   AND account_id = :account_id
   AND deleted_at IS NULL;
```

Also soft-delete linked transfer peers when present:

```sql
UPDATE banking.transactions peer
   SET deleted_at = NOW(),
       updated_at = NOW()
 WHERE peer.account_id = :account_id
   AND peer.deleted_at IS NULL
   AND peer.id IN (
       SELECT transfer_peer_transaction_id
         FROM banking.transactions
        WHERE import_job_id = :import_job_id
          AND transfer_peer_transaction_id IS NOT NULL
          AND deleted_at IS NULL
   );
```

3. Re-run the bill import confirmation flow from the app for the corrected review payload. The application will reconcile import-owned rows and attach them to the bill resolved by `reference_month`.

4. Soft-delete orphaned bills with no active transactions:

```sql
UPDATE banking.credit_card_bills b
   SET deleted_at = NOW(),
       updated_at = NOW()
 WHERE b.id = :orphan_bill_id
   AND b.deleted_at IS NULL
   AND NOT EXISTS (
       SELECT 1
         FROM banking.transactions t
        WHERE t.bill_id = b.id
          AND t.deleted_at IS NULL
   );
```

5. Recompute bill totals for every affected bill id and trigger month snapshot recomputation from the earliest impacted month through the banking service or the existing admin recompute path.

## Notes

- Only soft-delete import-owned rows (`import_job_id IS NOT NULL`, typically `source = 'PDF'`). Never delete manual or Open Finance transactions in this runbook.
- Do not add a uniqueness constraint on `(financial_account_id, reference_month)` until duplicate active rows are cleaned up.
- After remediation, verify month totals and bill carousel ordering by `reference_month DESC`.
