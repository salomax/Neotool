-- Minimal security schema for assistant migration tests.
-- Assistant V001 references security.users and security.accounts; this script
-- creates uuidv7() and those tables so Flyway can run assistant migrations.
-- Not used in production: run security service migrations first, then assistant.

-- uuidv7() in public (same as security V0_0)
CREATE OR REPLACE FUNCTION uuidv7() RETURNS uuid
AS $$
DECLARE
    unix_ts_ms bytea;
    uuid_bytes bytea;
BEGIN
    unix_ts_ms = substring(int8send(floor(extract(epoch from clock_timestamp()) * 1000)::bigint) from 3);
    uuid_bytes = unix_ts_ms ||
                 gen_random_bytes(2) ||
                 set_byte(gen_random_bytes(1), 0, (b'0111' || substring(get_byte(gen_random_bytes(1), 0)::bit(8) from 5))::bit(8)::int) ||
                 gen_random_bytes(1) ||
                 set_byte(gen_random_bytes(1), 0, (b'10' || substring(get_byte(gen_random_bytes(1), 0)::bit(8) from 3))::bit(8)::int) ||
                 gen_random_bytes(7);
    RETURN encode(uuid_bytes, 'hex')::uuid;
END;
$$ LANGUAGE plpgsql VOLATILE;

CREATE SCHEMA IF NOT EXISTS security;

CREATE TABLE IF NOT EXISTS security.users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS security.accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_name TEXT NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('PERSONAL', 'FAMILY', 'BUSINESS')),
    account_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
