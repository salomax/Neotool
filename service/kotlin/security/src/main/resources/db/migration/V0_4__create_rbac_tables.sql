-- Set search path to security schema
SET search_path TO security, public;

-- Create role_assignments table for role assignments with temporal validity
CREATE TABLE IF NOT EXISTS security.role_assignments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES security.roles(id) ON DELETE CASCADE,
    valid_from TIMESTAMP, -- nullable, if null then valid from creation
    valid_until TIMESTAMP, -- nullable, if null then no expiry
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Add index on user_id and role_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_role_assignments_user_role ON security.role_assignments(user_id, role_id);

-- Add index on valid_from and valid_until for temporal validity checks
CREATE INDEX IF NOT EXISTS idx_role_assignments_validity ON security.role_assignments(valid_from, valid_until);


