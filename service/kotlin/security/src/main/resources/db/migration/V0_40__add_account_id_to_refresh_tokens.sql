ALTER TABLE security.refresh_tokens
    ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES security.accounts(id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_account
    ON security.refresh_tokens(account_id)
    WHERE account_id IS NOT NULL;

UPDATE security.refresh_tokens AS rt
SET account_id = req.account_id
FROM security.oauth2_authorization_codes AS code
JOIN security.oauth2_authorization_requests AS req
    ON req.id = code.authorization_request_id
WHERE rt.account_id IS NULL
  AND rt.client_id IS NOT NULL
  AND code.issued_refresh_token_family_id = rt.family_id
  AND req.account_id IS NOT NULL;

COMMENT ON COLUMN security.refresh_tokens.account_id IS
    'OAuth account binding originally granted to this refresh token family; null for first-party app refresh tokens.';
