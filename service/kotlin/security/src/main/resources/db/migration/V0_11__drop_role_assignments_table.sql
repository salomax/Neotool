-- Set search path to security schema
SET search_path TO security, public;

-- Drop indexes first
DROP INDEX IF EXISTS security.idx_role_assignments_validity;
DROP INDEX IF EXISTS security.idx_role_assignments_user_role;

-- Drop the role_assignments table
DROP TABLE IF EXISTS security.role_assignments;
