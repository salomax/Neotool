-- Create security schema
CREATE SCHEMA IF NOT EXISTS security;

-- Set search path for this migration
SET search_path TO security, public;

-- Create tables with schema qualification
CREATE TABLE IF NOT EXISTS security.roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(64) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS security.permissions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(128) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS security.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS security.user_roles (
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES security.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS security.role_permissions (
    role_id INT NOT NULL REFERENCES security.roles(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES security.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO security.roles (name) VALUES ('ADMIN') ON CONFLICT DO NOTHING;
INSERT INTO security.permissions (name) VALUES ('security:user:view'), ('security:user:save'), ('security:user:delete') ON CONFLICT DO NOTHING;
INSERT INTO security.permissions (name) VALUES ('security:group:view'), ('security:group:save'), ('security:group:delete') ON CONFLICT DO NOTHING;
INSERT INTO security.permissions (name) VALUES ('security:role:view'), ('security:role:save'), ('security:role:delete') ON CONFLICT DO NOTHING;

-- Link ADMIN role to user management permissions
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM security.roles r
JOIN security.permissions p ON p.name IN ('security:user:view', 'security:user:save', 'security:user:delete')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
