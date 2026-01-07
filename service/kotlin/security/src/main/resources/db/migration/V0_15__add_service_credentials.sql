-- Set search path to security schema
SET search_path TO security, public;

-- Create service_credentials table
-- This table stores hashed client secrets for service principals
-- Separate table allows for future extensions (rotation history, expiration)
CREATE TABLE IF NOT EXISTS security.service_credentials (
    principal_id UUID PRIMARY KEY REFERENCES security.principals(id) ON DELETE CASCADE,
    credential_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create index on principal_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_service_credentials_principal_id ON security.service_credentials(principal_id);

