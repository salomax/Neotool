ALTER TABLE security.user_preferences
    ADD COLUMN keep_me_connected BOOLEAN NOT NULL DEFAULT true;
