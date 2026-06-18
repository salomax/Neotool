DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'banking'
          AND table_name = 'user_preferences'
          AND column_name = 'rule_application_mode'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'banking'
          AND table_name = 'user_preferences'
          AND column_name = 'rule_creation_mode'
    ) THEN
        EXECUTE 'ALTER TABLE banking.user_preferences RENAME COLUMN rule_application_mode TO rule_creation_mode';
    END IF;
END
$$;
