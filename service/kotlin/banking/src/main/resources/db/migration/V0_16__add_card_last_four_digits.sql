ALTER TABLE banking.financial_accounts
    ADD COLUMN IF NOT EXISTS card_last_four_digits CHAR(4);

UPDATE banking.financial_accounts fa
SET card_last_four_digits = RIGHT(regexp_replace(card.masked_number, '\\D', '', 'g'), 4)
FROM banking.financial_account_card_data card
WHERE card.account_id = fa.id
  AND fa.card_last_four_digits IS NULL
  AND card.masked_number IS NOT NULL
  AND LENGTH(regexp_replace(card.masked_number, '\\D', '', 'g')) >= 4;

CREATE INDEX IF NOT EXISTS idx_banking_financial_accounts_last4_active
    ON banking.financial_accounts (account_id, card_last_four_digits)
    WHERE deleted_at IS NULL
      AND card_last_four_digits IS NOT NULL;
