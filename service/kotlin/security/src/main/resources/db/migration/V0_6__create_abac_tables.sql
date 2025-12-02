-- Set search path to security schema
SET search_path TO security, public;

-- Create abac_policies table for Attribute-Based Access Control policies
CREATE TABLE IF NOT EXISTS security.abac_policies (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    effect VARCHAR(16) NOT NULL, -- allow or deny
    condition TEXT NOT NULL, -- JSON or expression string for policy evaluation
    version INT NOT NULL DEFAULT 1, -- policy version number
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT abac_policies_name_unique UNIQUE (name)
);

-- Create abac_policy_versions table for policy versioning and audit trail
CREATE TABLE IF NOT EXISTS security.abac_policy_versions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    policy_id UUID NOT NULL REFERENCES security.abac_policies(id) ON DELETE CASCADE,
    version INT NOT NULL,
    effect VARCHAR(16) NOT NULL, -- allow or deny
    condition TEXT NOT NULL, -- JSON or expression string for policy evaluation
    is_active BOOLEAN NOT NULL DEFAULT false, -- only one version per policy should be active
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES security.users(id) ON DELETE SET NULL, -- who created this version
    CONSTRAINT abac_policy_versions_policy_version_unique UNIQUE (policy_id, version)
);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_abac_policies_name ON security.abac_policies(name);
CREATE INDEX IF NOT EXISTS idx_abac_policies_active ON security.abac_policies(is_active);
CREATE INDEX IF NOT EXISTS idx_abac_policies_effect ON security.abac_policies(effect);

CREATE INDEX IF NOT EXISTS idx_abac_policy_versions_policy ON security.abac_policy_versions(policy_id);
CREATE INDEX IF NOT EXISTS idx_abac_policy_versions_policy_version ON security.abac_policy_versions(policy_id, version);
CREATE INDEX IF NOT EXISTS idx_abac_policy_versions_active ON security.abac_policy_versions(is_active);


