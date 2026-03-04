-- ─── Service-to-service permission for membership checks ────────────────────
-- Adds permission required to call the GraphQL query:
--   isUserActiveMemberOfAccount(userId, accountId)
-- Assignment to service principals/roles is intentionally manual.
-- ─────────────────────────────────────────────────────────────────────────────

SET search_path TO security, public;

INSERT INTO security.permissions (name) VALUES
    ('security:account-membership:view')
ON CONFLICT (name) DO NOTHING;
