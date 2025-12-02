-- Set search path to security schema
SET search_path TO security, public;

-- Create authorization_audit_logs table for comprehensive audit trail
CREATE TABLE IF NOT EXISTS security.authorization_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    groups JSONB, -- array of group IDs the user belongs to at time of check
    roles JSONB, -- array of role IDs the user has at time of check
    requested_action VARCHAR(255) NOT NULL, -- e.g., "transaction:update"
    resource_type VARCHAR(128), -- e.g., "transaction", "profile", "project"
    resource_id UUID, -- nullable, specific resource if applicable
    rbac_result VARCHAR(16) NOT NULL, -- allowed or denied
    abac_result VARCHAR(16), -- allowed, denied, or not_evaluated (nullable if RBAC denied)
    final_decision VARCHAR(16) NOT NULL, -- allowed or denied
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB, -- additional context like IP address, user agent, etc.
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add indexes for performance and querying
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_user ON security.authorization_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_timestamp ON security.authorization_audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_user_timestamp ON security.authorization_audit_logs(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_resource ON security.authorization_audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_decision ON security.authorization_audit_logs(final_decision);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_action ON security.authorization_audit_logs(requested_action);

-- Add GIN index on JSONB columns for efficient querying
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_groups_gin ON security.authorization_audit_logs USING GIN (groups);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_roles_gin ON security.authorization_audit_logs USING GIN (roles);
CREATE INDEX IF NOT EXISTS idx_authorization_audit_logs_metadata_gin ON security.authorization_audit_logs USING GIN (metadata);


