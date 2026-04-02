CREATE TABLE IF NOT EXISTS banking.user_preferences (
    user_id                UUID        PRIMARY KEY,
    rule_application_mode  TEXT NOT NULL DEFAULT 'ALWAYS_PROMPT'
                           CHECK (rule_application_mode IN ('NEVER', 'ALWAYS', 'ALWAYS_PROMPT')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'security'
          AND table_name = 'user_preferences'
          AND column_name = 'rule_application_mode'
    ) THEN
        EXECUTE $sql$
            INSERT INTO banking.user_preferences (user_id, rule_application_mode, created_at, updated_at)
            SELECT user_id, rule_application_mode, created_at, updated_at
            FROM security.user_preferences
            ON CONFLICT (user_id) DO NOTHING
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'security'
          AND table_name = 'account_memberships'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'banking'
          AND table_name = 'account_preferences'
          AND column_name = 'rule_application'
    ) THEN
        EXECUTE $sql$
            INSERT INTO banking.user_preferences (user_id, rule_application_mode, created_at, updated_at)
            SELECT membership.user_id, account_pref.rule_application, NOW(), NOW()
            FROM banking.account_preferences account_pref
            JOIN security.account_memberships membership
              ON membership.account_id = account_pref.account_id
             AND membership.membership_status = 'ACTIVE'
            ON CONFLICT (user_id) DO NOTHING
        $sql$;
    END IF;
END
$$;

ALTER TABLE banking.account_preferences
    DROP COLUMN IF EXISTS rule_application;
