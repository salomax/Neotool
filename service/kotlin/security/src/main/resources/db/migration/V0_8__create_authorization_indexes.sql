-- Set search path to security schema
SET search_path TO security, public;

-- Create performance indexes for authorization queries
-- Note: Some indexes may already exist from previous migrations, using IF NOT EXISTS for idempotency

-- Indexes for role_assignments table
CREATE INDEX IF NOT EXISTS idx_role_assignments_user_role ON security.role_assignments(user_id, role_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_user_scope ON security.role_assignments(user_id, scope_type, scope_id);

-- Indexes for group_memberships table
CREATE INDEX IF NOT EXISTS idx_group_memberships_user_group ON security.group_memberships(user_id, group_id);

-- Indexes for group_role_assignments table
CREATE INDEX IF NOT EXISTS idx_group_role_assignments_group_role ON security.group_role_assignments(group_id, role_id);

-- Indexes for authorization_audit_logs table
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_user_timestamp ON security.authorization_audit_logs(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_resource ON security.authorization_audit_logs(resource_type, resource_id);


