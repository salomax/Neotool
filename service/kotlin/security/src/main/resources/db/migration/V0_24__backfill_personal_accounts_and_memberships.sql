-- Backfill M1-T4: one PERSONAL account per existing user, OWNER membership with is_default = true.
-- Safe to re-run: skips users that already have a personal account and memberships that already exist.
SET search_path TO security, public;

-- 1. Create one PERSONAL account per existing user (idempotent: skip if already exists)
INSERT INTO security.accounts (
    account_name,
    account_type,
    account_status,
    owner_user_id,
    created_at,
    updated_at,
    version
)
SELECT
    COALESCE(NULLIF(TRIM(COALESCE(u.display_name, '')), ''), u.email) || '''s Account',
    'PERSONAL',
    'ACTIVE',
    u.id,
    u.created_at,
    u.updated_at,
    0
FROM security.users u
WHERE NOT EXISTS (
    SELECT 1 FROM security.accounts a
    WHERE a.owner_user_id = u.id AND a.account_type = 'PERSONAL'
);

-- 2. Create OWNER membership (ACTIVE, is_default = true) for each personal account (idempotent: ON CONFLICT DO NOTHING)
INSERT INTO security.account_memberships (
    account_id,
    user_id,
    account_role,
    membership_status,
    joined_at,
    is_default,
    version
)
SELECT
    a.id,
    a.owner_user_id,
    'OWNER',
    'ACTIVE',
    NOW(),
    true,
    0
FROM security.accounts a
WHERE a.account_type = 'PERSONAL' AND a.owner_user_id IS NOT NULL
ON CONFLICT (account_id, user_id) DO NOTHING;

-- 3. Optional: update resource_ownership from USER to ACCOUNT when the table exists (e.g. security or per-service schema)
-- When principal_type is USER, set it to ACCOUNT and principal_id to that user's personal account id.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'security' AND table_name = 'resource_ownership'
    ) THEN
        UPDATE security.resource_ownership o
        SET
            principal_type = 'ACCOUNT',
            principal_id = a.id
        FROM security.accounts a
        WHERE a.account_type = 'PERSONAL'
          AND a.owner_user_id = o.principal_id
          AND o.principal_type = 'USER';
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- Verification SQL (run after migration; expect 0 for first two queries)
-- ---------------------------------------------------------------------------
-- Count users without a personal account (expect 0 after backfill):
--   SELECT COUNT(*) FROM security.users u
--   WHERE NOT EXISTS (
--       SELECT 1 FROM security.accounts a
--       WHERE a.owner_user_id = u.id AND a.account_type = 'PERSONAL'
--   );
--
-- Count personal accounts without an OWNER ACTIVE membership (expect 0):
--   SELECT COUNT(*) FROM security.accounts a
--   WHERE a.account_type = 'PERSONAL'
--     AND NOT EXISTS (
--         SELECT 1 FROM security.account_memberships m
--         WHERE m.account_id = a.id AND m.account_role = 'OWNER' AND m.membership_status = 'ACTIVE'
--     );
--
-- If resource_ownership was updated: count legacy USER rows that have a personal account (expect 0 after UPDATE):
--   SELECT COUNT(*) FROM security.resource_ownership o
--   WHERE o.principal_type = 'USER'
--     AND EXISTS (
--         SELECT 1 FROM security.accounts a
--         WHERE a.account_type = 'PERSONAL' AND a.owner_user_id = o.principal_id
--     );
--   (Run only when security.resource_ownership exists.)
