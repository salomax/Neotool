-- Set search path to security schema
SET search_path TO security, public;

-- Create principals table
-- This table stores both user and service principals in a unified model
CREATE TABLE IF NOT EXISTS security.principals (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    principal_type VARCHAR(16) NOT NULL CHECK (principal_type IN ('USER', 'SERVICE')),
    external_id VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_principals_type_external_id UNIQUE (principal_type, external_id)
);

-- Create principal_permissions table
-- This table stores permission assignments for both user and service principals
-- Uses the same permission vocabulary from the permissions table
-- Note: resource_pattern can be NULL for wildcard permissions
CREATE TABLE IF NOT EXISTS security.principal_permissions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    principal_id UUID NOT NULL REFERENCES security.principals(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES security.permissions(id) ON DELETE CASCADE,
    resource_pattern TEXT
);

-- Create unique index that treats NULL resource_pattern as empty string for uniqueness
-- This ensures one wildcard permission (NULL) per principal+permission combination
CREATE UNIQUE INDEX IF NOT EXISTS uq_principal_permission_pattern 
ON security.principal_permissions (principal_id, permission_id, COALESCE(resource_pattern, ''));

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_external_id ON security.principals(external_id);

-- Sync existing users to principals table
-- Create principal records for all existing users, using users.enabled as the initial value
INSERT INTO security.principals (principal_type, external_id, enabled, created_at, updated_at, version)
SELECT 
    'USER'::VARCHAR(16) as principal_type,
    u.id::VARCHAR(255) as external_id,
    u.enabled as enabled,
    u.created_at,
    u.updated_at,
    0 as version
FROM security.users u
WHERE NOT EXISTS (
    SELECT 1 FROM security.principals p 
    WHERE p.principal_type = 'USER' AND p.external_id = u.id::VARCHAR(255)
)
ON CONFLICT (principal_type, external_id) DO NOTHING;
