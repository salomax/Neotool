-- Set search path to security schema
SET search_path TO security, public;

-- Create groups table
CREATE TABLE IF NOT EXISTS security.groups (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT groups_name_unique UNIQUE (name)
);

-- Create group_memberships table for user-group relationships
CREATE TABLE IF NOT EXISTS security.group_memberships (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES security.groups(id) ON DELETE CASCADE,
    membership_type VARCHAR(32) NOT NULL DEFAULT 'member', -- member, admin, owner
    valid_until TIMESTAMP, -- nullable, if null then no expiry
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT group_memberships_user_group_unique UNIQUE (user_id, group_id)
);

-- Create group_role_assignments table for group-level role assignments
CREATE TABLE IF NOT EXISTS security.group_role_assignments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    group_id UUID NOT NULL REFERENCES security.groups(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES security.roles(id) ON DELETE CASCADE,
    valid_from TIMESTAMP, -- nullable, if null then valid from creation
    valid_until TIMESTAMP, -- nullable, if null then no expiry
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_groups_name ON security.groups(name);

CREATE INDEX IF NOT EXISTS idx_group_memberships_user ON security.group_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_group_memberships_group ON security.group_memberships(group_id);
CREATE INDEX IF NOT EXISTS idx_group_memberships_user_group ON security.group_memberships(user_id, group_id);
CREATE INDEX IF NOT EXISTS idx_group_memberships_validity ON security.group_memberships(valid_until);

CREATE INDEX IF NOT EXISTS idx_group_role_assignments_group ON security.group_role_assignments(group_id);
CREATE INDEX IF NOT EXISTS idx_group_role_assignments_role ON security.group_role_assignments(role_id);
CREATE INDEX IF NOT EXISTS idx_group_role_assignments_group_role ON security.group_role_assignments(group_id, role_id);
CREATE INDEX IF NOT EXISTS idx_group_role_assignments_validity ON security.group_role_assignments(valid_from, valid_until);


