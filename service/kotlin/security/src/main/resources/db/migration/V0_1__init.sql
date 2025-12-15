-- Create security schema
CREATE SCHEMA IF NOT EXISTS security;

-- Set search path for this migration
SET search_path TO security, public;

-- Create tables with schema qualification
CREATE TABLE IF NOT EXISTS security.roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(64) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS security.permissions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(128) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS security.users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    avatar_url TEXT,
    avatar_provider TEXT,
    avatar_updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS security.role_permissions (
    role_id UUID NOT NULL REFERENCES security.roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES security.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO security.roles (name) VALUES ('Authorization Manager') ON CONFLICT DO NOTHING;
INSERT INTO security.permissions (name) VALUES ('security:user:view'), ('security:user:save'), ('security:user:delete') ON CONFLICT DO NOTHING;
INSERT INTO security.permissions (name) VALUES ('security:group:view'), ('security:group:save'), ('security:group:delete') ON CONFLICT DO NOTHING;
INSERT INTO security.permissions (name) VALUES ('security:role:view'), ('security:role:save'), ('security:role:delete') ON CONFLICT DO NOTHING;

-- Link Authorization Manager role to wildcard permission (grants all security permissions)
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM security.roles r
JOIN security.permissions p ON p.name ilike 'security:%:%'
WHERE r.name = 'Authorization Manager'
ON CONFLICT DO NOTHING;
