-- Enforce at most one active bill per card account and reference month.
-- Reassigns transactions off duplicate rows before soft-deleting extras.

WITH canonical AS (
    SELECT DISTINCT ON (financial_account_id, reference_month)
           id AS canonical_id,
           financial_account_id,
           reference_month
      FROM banking.credit_card_bills
     WHERE deleted_at IS NULL
     ORDER BY financial_account_id,
              reference_month,
              updated_at DESC,
              created_at DESC,
              id DESC
),
duplicates AS (
    SELECT b.id AS duplicate_id,
           c.canonical_id
      FROM banking.credit_card_bills b
      JOIN canonical c
        ON b.financial_account_id = c.financial_account_id
       AND b.reference_month = c.reference_month
     WHERE b.deleted_at IS NULL
       AND b.id <> c.canonical_id
)
UPDATE banking.transactions t
   SET bill_id = d.canonical_id,
       updated_at = NOW()
  FROM duplicates d
 WHERE t.bill_id = d.duplicate_id
   AND t.deleted_at IS NULL;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY financial_account_id, reference_month
               ORDER BY updated_at DESC, created_at DESC, id DESC
           ) AS rn
      FROM banking.credit_card_bills
     WHERE deleted_at IS NULL
)
UPDATE banking.credit_card_bills b
   SET deleted_at = NOW(),
       updated_at = NOW()
  FROM ranked r
 WHERE b.id = r.id
   AND r.rn > 1;

CREATE UNIQUE INDEX uq_credit_card_bills_account_reference_month_active
    ON banking.credit_card_bills (financial_account_id, reference_month)
    WHERE deleted_at IS NULL;
