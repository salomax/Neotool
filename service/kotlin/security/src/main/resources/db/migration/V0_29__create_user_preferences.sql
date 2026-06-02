CREATE TABLE security.user_preferences (
    user_id           UUID        PRIMARY KEY REFERENCES security.users(id),
    language          VARCHAR(10) NOT NULL DEFAULT 'PORTUGUESE'
                      CHECK (language IN ('ENGLISH', 'PORTUGUESE')),
    theme             VARCHAR(10) NOT NULL DEFAULT 'SYSTEM'
                      CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    blur_balances     BOOLEAN     NOT NULL DEFAULT false,
    first_day_of_week SMALLINT    NOT NULL DEFAULT 1
                      CHECK (first_day_of_week BETWEEN 1 AND 7),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
