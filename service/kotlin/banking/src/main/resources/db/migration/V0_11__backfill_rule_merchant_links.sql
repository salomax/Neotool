WITH matched_rules AS (
    SELECT
        tr.id AS rule_id,
        COALESCE(
            (
                SELECT m.id
                FROM banking.merchants m
                WHERE m.account_id = tr.account_id
                  AND m.merchant_name = tr.pattern
                ORDER BY m.created_at ASC
                LIMIT 1
            ),
            (
                SELECT m.id
                FROM banking.merchants m
                WHERE m.account_id IS NULL
                  AND m.is_system = true
                  AND m.merchant_name = tr.pattern
                ORDER BY m.created_at ASC
                LIMIT 1
            )
        ) AS merchant_id
    FROM banking.transaction_rules tr
    WHERE tr.deleted_at IS NULL
      AND tr.merchant_id IS NULL
)
UPDATE banking.transaction_rules tr
SET merchant_id = mr.merchant_id,
    updated_at = NOW()
FROM matched_rules mr
WHERE tr.id = mr.rule_id
  AND mr.merchant_id IS NOT NULL;
