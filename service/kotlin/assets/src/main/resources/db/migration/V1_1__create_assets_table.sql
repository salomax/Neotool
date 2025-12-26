-- Asset Service - Assets Table Migration
-- Version: 1.1
-- Description: Creates the assets table with audit fields and indexes
-- Reference: docs/03-features/assets/asset-service/README.md

-- Create assets schema if not exists
CREATE SCHEMA IF NOT EXISTS assets;

-- Set search path to assets schema
SET search_path TO assets, public;

-- Install UUID v7 extension for time-sortable UUIDs (must be in public schema)
-- Note: Extension may not be available in test environments
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_uuidv7 WITH SCHEMA public;
EXCEPTION WHEN OTHERS THEN
    -- Extension not available, will use gen_random_uuid() as fallback
    RAISE NOTICE 'pg_uuidv7 extension not available, using gen_random_uuid() as fallback';
END $$;

-- Create function that uses uuidv7 if available, otherwise gen_random_uuid()
CREATE OR REPLACE FUNCTION assets.generate_uuid() RETURNS UUID AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_uuidv7') THEN
        RETURN uuidv7();
    ELSE
        RETURN gen_random_uuid();
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create assets table
CREATE TABLE IF NOT EXISTS assets.assets (
    -- Primary key (UUID v7 - time-sortable, database-generated, or UUID v4 fallback)
    id UUID PRIMARY KEY DEFAULT assets.generate_uuid(),

    -- Ownership & namespace
    owner_id VARCHAR(255) NOT NULL,
    namespace VARCHAR(100) NOT NULL,

    -- Resource association
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,

    -- Storage metadata
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    storage_region VARCHAR(50) NOT NULL,
    storage_bucket VARCHAR(100) NOT NULL,

    -- File metadata
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT,
    checksum VARCHAR(255),
    original_filename VARCHAR(500),

    -- Upload metadata
    upload_url TEXT,
    upload_expires_at TIMESTAMP WITH TIME ZONE,
    public_url TEXT, -- Deprecated: Generated dynamically from storage_key, not stored

    -- Status tracking
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Idempotency
    idempotency_key VARCHAR(255),

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,

    -- Optimistic locking
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT assets_status_check CHECK (status IN ('PENDING', 'READY', 'FAILED', 'DELETED')),
    CONSTRAINT assets_size_positive CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT assets_upload_expires_future CHECK (upload_expires_at IS NULL OR upload_expires_at > created_at)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_assets_owner_id ON assets.assets(owner_id);
CREATE INDEX IF NOT EXISTS idx_assets_namespace ON assets.assets(namespace);
CREATE INDEX IF NOT EXISTS idx_assets_resource ON assets.assets(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_assets_status ON assets.assets(status);
CREATE INDEX IF NOT EXISTS idx_assets_created_at ON assets.assets(created_at);
CREATE INDEX IF NOT EXISTS idx_assets_idempotency_key ON assets.assets(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_assets_owner_status ON assets.assets(owner_id, status);
CREATE INDEX IF NOT EXISTS idx_assets_namespace_status ON assets.assets(namespace, status);

-- Unique constraint for active idempotency (excludes FAILED status)
-- Note: 24-hour window enforcement is handled at application level
CREATE UNIQUE INDEX IF NOT EXISTS idx_assets_idempotency_unique
    ON assets.assets(owner_id, idempotency_key)
    WHERE status != 'FAILED' AND idempotency_key IS NOT NULL;

-- Comments for documentation
COMMENT ON TABLE assets.assets IS 'Stores asset metadata for uploaded files (images, documents, etc.)';
COMMENT ON COLUMN assets.assets.id IS 'UUID v7 primary key (time-sortable, database-generated)';
COMMENT ON COLUMN assets.assets.owner_id IS 'User ID or system ID that owns this asset';
COMMENT ON COLUMN assets.assets.namespace IS 'Logical grouping (e.g., user-profiles, group-assets)';
COMMENT ON COLUMN assets.assets.resource_type IS 'Type of resource (e.g., PROFILE_IMAGE, GROUP_LOGO)';
COMMENT ON COLUMN assets.assets.resource_id IS 'ID of the resource this asset is attached to';
COMMENT ON COLUMN assets.assets.storage_key IS 'Unique key in S3/R2 storage';
COMMENT ON COLUMN assets.assets.storage_region IS 'Storage region (e.g., us-east-1)';
COMMENT ON COLUMN assets.assets.storage_bucket IS 'Storage bucket name';
COMMENT ON COLUMN assets.assets.mime_type IS 'MIME type (e.g., image/jpeg, application/pdf)';
COMMENT ON COLUMN assets.assets.size_bytes IS 'File size in bytes (null until confirmed)';
COMMENT ON COLUMN assets.assets.checksum IS 'SHA-256 checksum for integrity verification';
COMMENT ON COLUMN assets.assets.original_filename IS 'Original filename from client upload';
COMMENT ON COLUMN assets.assets.upload_url IS 'Pre-signed upload URL (temporary)';
COMMENT ON COLUMN assets.assets.upload_expires_at IS 'When the upload URL expires';
COMMENT ON COLUMN assets.assets.public_url IS 'Public CDN URL (deprecated - generated dynamically from storage_key, not stored)';
COMMENT ON COLUMN assets.assets.status IS 'Upload status: PENDING, READY, FAILED, DELETED';
COMMENT ON COLUMN assets.assets.idempotency_key IS 'Client-provided key to prevent duplicate uploads';
COMMENT ON COLUMN assets.assets.created_at IS 'Timestamp when asset record was created';
COMMENT ON COLUMN assets.assets.updated_at IS 'Timestamp when asset record was last updated';
COMMENT ON COLUMN assets.assets.deleted_at IS 'Timestamp when asset was soft deleted (null if active)';
COMMENT ON COLUMN assets.assets.version IS 'Version number for optimistic locking';

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION assets.update_assets_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_assets_updated_at
    BEFORE UPDATE ON assets.assets
    FOR EACH ROW
    EXECUTE FUNCTION assets.update_assets_updated_at();
