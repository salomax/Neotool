# Assets Service Specification

## Overview
- Purpose: provide upload, storage, and delivery of binary assets (primarily images and documents) via an S3-compatible store (Cloudflare R2 in prod, MinIO in dev) fronted by CDN.
- Scope: backend service/API (GraphQL-only) to issue upload URLs, track metadata, and expose delivery URLs. Clients upload directly to storage; the service never proxies the binary.
- Visibility model: namespaces drive visibility (PUBLIC vs PRIVATE) and storage key templates; PUBLIC assets get stable CDN URLs, PRIVATE assets use presigned download URLs with TTL.
- Out of scope (current impl): variants/transcoding, malware scanning, overwrite semantics, multi-bucket sharding.

## Goals
- Simple dev experience: same code path for local (MinIO) and prod (R2/S3) by switching env vars.
- Strong cacheability: stable object keys and variant naming; CDN-friendly headers.
- Future-ready security: ready to swap public URLs for signed/HMAC Worker without breaking contract.
- Operationally robust: observability, idempotency, lifecycle cleanup, and safe overwrite rules.

## Functional Requirements

### API Surface (GraphQL-only, current implementation)
- Mutations:
  - `createAssetUpload(input: CreateAssetUploadInput!)` -> `Asset` with `uploadUrl`, `uploadExpiresAt`, `storageKey`, `visibility`, `status`.
  - `confirmAssetUpload(input: ConfirmAssetUploadInput!)` -> `Asset` with updated `status` and URLs.
  - `deleteAsset(assetId: ID!)` -> Boolean.
- Query: `asset(id: ID!): Asset`.
- Inputs:
  - `CreateAssetUploadInput`: `namespace`, `filename`, `mimeType`, `sizeBytes`, optional `idempotencyKey`.
  - `ConfirmAssetUploadInput`: `assetId`, optional `checksum`.
- Upload is direct to storage using the presigned PUT URL returned by `createAssetUpload`.
- URLs:
  - PUBLIC assets: `publicUrl` populated from `publicBaseUrl + storageKey`.
  - PRIVATE assets: `downloadUrl(ttlSeconds: Int = 3600)` field generates a presigned GET URL; `publicUrl` is null.

### Storage & Key Scheme
- Single bucket per environment (configurable): `asset.storage.bucket`.
- Object key format is namespace-driven and templated: see `asset-config.yml` with placeholders `{namespace}`, `{ownerId}`, `{assetId}`; examples:
  - `user-profiles/{ownerId}/{assetId}`
  - `group-assets/{assetId}`
  - `attachments/{ownerId}/{assetId}`
- Keys are immutable; overwrites are not supported once `READY`.
- Namespace controls visibility: PUBLIC (CDN URL) vs PRIVATE (presigned download URL).
- Persisted fields (current impl): `id`, `owner_id`, `namespace`, `storage_key`, `storage_region`, `storage_bucket`, `mime_type`, `size_bytes`, `checksum`, `original_filename`, `upload_url`, `upload_expires_at`, `status`, `visibility`, `idempotency_key`, timestamps, `version`.

### Upload Flow (current impl)
- Client calls `createAssetUpload(namespace, filename, mimeType, sizeBytes, idempotencyKey?)`.
  - Validates MIME and size against namespace rules from `asset-config.yml`.
  - Stores `PENDING` asset with temporary key, then rewrites the key using the namespace template `{namespace}/{ownerId?}/{assetId}`.
  - Generates a presigned PUT URL (TTL from namespace `uploadTtlSeconds` or global default) and returns it in `uploadUrl`.
- Client PUTs the file directly to storage using that URL (no auth headers required beyond the presign).
- Client calls `confirmAssetUpload(assetId, checksum?)`.
  - Service verifies ownership, status, TTL, checks object exists, fetches metadata, and updates status to `READY`, clearing `uploadUrl`.
  - For PRIVATE assets, consumers should ask for `downloadUrl(ttlSeconds)`; for PUBLIC assets, use `publicUrl`.
- On expired or missing uploads, confirmation fails and the asset should be retried (status set to `FAILED` by cleanup jobs or error paths).

### Delivery & URLs
- `publicUrl` = `STORAGE_PUBLIC_BASE_URL` + `storageKey` (variant-specific). Returned in API responses; not persisted as absolute string unless caching for analytics.
- Cache headers: originals/variants served with long-lived `Cache-Control` (e.g., `public, max-age=31536000, immutable`); clients handle purges via versioned keys (no overwrite) or explicit purge API (optional).
- Future signed URLs: same metadata/keys; resolver swaps `publicUrl` generation to include HMAC/exp or Worker route.

### Variants & Processing (Future Implementation)
- **Status:** Not included in MVP; placeholder in schema for future use.
- Variants set: `original` mandatory; derived variants configurable per resource type (e.g., `thumb` 256px, `medium` 1024px, `full` 2048px; formats webp/avif/jpg).
- Strategy: on-demand (image proxy/Worker) or async (queue + worker) configurable. Must record planned variants in metadata to avoid missing assets.
- Variant naming deterministic: `{key}/{variant}.{ext}`; `variant` part stable for cache hits.
- If variant generation fails, mark variant as `FAILED` with reason; keep original intact.

### Validation & Governance

#### Content Validation (MVP)
- **MIME type validation:** verify magic bytes match declared `mimeType` to prevent MIME spoofing attacks.
  - Supported types configurable via `ASSET_ALLOWED_MIME_TYPES` env var (e.g., `image/jpeg,image/png,image/webp,image/gif`).
  - Reject uploads where file signature doesn't match declared type.
- **Image dimension validation:** configurable per `resourceType` via validation rules in DB or config.
  - Example: profile images must be 100x100 to 5000x5000 pixels; reject images outside bounds.
  - Reject images >25000x25000 (decompression bomb protection).
  - Reject images <1x1 (invalid).
- **Size limits:** enforce `STORAGE_MAX_UPLOAD_BYTES` globally; allow per-resourceType/namespace overrides in validation config.
  - Example config: `{ "profile_image": { "maxBytes": 5242880, "maxWidth": 2048, "maxHeight": 2048, "allowedMimes": ["image/jpeg", "image/png", "image/webp"] } }`.

#### Rate Limiting & Quotas (MVP)
- **Per-owner rate limits:** configurable limits per service/user.
  - Default: 100 upload requests/hour, 1GB total size/hour per owner.
  - Burst allowance: 10 uploads within 60s for UX-sensitive flows.
  - Return `429 Too Many Requests` with `Retry-After` header when exceeded.
- **Per-namespace quotas:** soft limits on object count and total bytes.
  - Emit warning events at 80% usage; hard block at 100%.
  - Quotas configurable in DB per namespace or via env defaults.
- **Path-based quotas:** track total size per `{namespace}/{resourceType}` path for finer control.

#### Malware Scanning (Future - Enterprise Plan)
- **Status:** Deferred to Enterprise Plan; integrate Cloudflare's Built-in Malicious Uploads Detection.
- Scan threshold: files >1MB or high-risk MIME types (e.g., executables if allowed).
- On detection: set status to `FAILED`, log incident, notify owner.

#### Ownership & Deletion
- Ownership: every asset linked to namespace (default `public`) and owner; optional resource binding.
- **Deletion strategy:** use Cloudflare R2 Object Lifecycle Rules for automated cleanup instead of soft delete.
  - On delete request: immediately mark status as `DELETED` in metadata.
  - Move object to `/deleted/{namespace}/{resourceType}/{assetId}/` prefix via copy+delete or use R2 lifecycle policy to expire after N days.
  - Alternative: skip soft delete entirely and hard delete immediately; rely on audit logs for compliance.
  - Decision: **hard delete recommended** for simplicity; metadata retains `deletedAt` timestamp for audit trail.

### Observability & Operations
- Metrics: upload requests, signed URL issuance, upload success/failure, size distribution, variant generation success/failure, CDN cache hit/miss (if available), latency percentiles, 4xx/5xx.
- Logs: structured logs for upload issuance, confirmation, errors, access denials; include assetId/namespace.
- Traces: span for `createAssetUpload`, variant generation, storage head/put.
- Alerts: high 5xx on upload issuance, rising failed uploads, variant failure rate, low CDN hit ratio (if instrumented).
- Scheduled jobs: clean expired `PENDING_UPLOAD`; optionally purge soft-deleted after grace period.

### Security & Compliance

#### Authentication & Authorization (MVP)
- AuthN/AuthZ: only authenticated principals can request uploads; enforce permissions per namespace/resource type.
  - Internal services/jobs: use service credentials to request uploads/deletes (current use case: job downloads external image and publishes to CDN).
  - Future end-user uploads: business service mediates the request, validates file intent/type/size, and calls `createAssetUpload`; upload URLs remain scoped to one object with short TTL and can later switch to signed delivery URLs.
- Input validation: strict mime/size; disallow overwrite unless flagged; reject path traversal in keys.
- Data protection: no secrets in object keys; avoid PII in filenames. Optional encryption at rest delegated to R2 settings.

#### CORS Configuration (MVP)
- **Bucket CORS policy:** configure R2 bucket to allow direct uploads from permitted origins.
  - Allowed origins: configurable via `ASSET_CORS_ALLOWED_ORIGINS` env var (e.g., `https://app.example.com,https://admin.example.com`).
  - Allowed methods: `PUT`, `HEAD`, `OPTIONS`.
  - Allowed headers: `Content-Type`, `Content-Length`, `x-amz-*`, custom checksum headers.
  - Credentials: omit for public uploads; enable when signed URLs implemented.
  - Preflight cache: `Access-Control-Max-Age: 86400`.
- MinIO dev config: ensure CORS rules match production R2 config for local testing.

#### Auditability (MVP)
- **Audit logging:** structured logs for all asset lifecycle events.
  - Log on upload request: `ownerId`, `namespace`, `resourceType`, `resourceId`, `assetId`, `mimeType`, `expectedSize`.
  - Log on status transitions: `PENDING_UPLOAD -> READY/FAILED/DELETED` with timestamp and reason.
  - Log on delete/purge: include `deletedBy` actor and `deletedAt` timestamp.
- **Retention:** audit logs retained per project standards (90 days hot, 2 years cold if available).

#### Compliance (Future Implementation)
- **GDPR/LGPD tooling:** deferred to future; requires investigation.
  - Right to access: provide `exportOwnerAssets(ownerId)` mutation returning manifest JSON.
  - Right to erasure: `purgeAsset` with `gdprDelete: true` bypasses soft-delete grace period.
  - Data residency: bucket region configurable per namespace; metadata includes `storageRegion`.
- **Data classification:** avoid storing sensitive PII in asset metadata; use opaque IDs for `ownerId`/`resourceId`.

### Local Development & Testing
- Emulate storage with MinIO via docker-compose; console on :9001 for manual bucket creation/upload without AWS CLI.
- Config for dev: `STORAGE_ENDPOINT=http://localhost:9000`, `STORAGE_BUCKET=assets`, `STORAGE_FORCE_PATH_STYLE=true`, `STORAGE_ACCESS_KEY=minio`, `STORAGE_SECRET=minio123`, `STORAGE_PUBLIC_BASE_URL=http://localhost:9000/assets/` (or proxy domain).
- Provide sample seed script to create bucket, configure CORS, and upload sample object; keep parity with prod by only swapping endpoints/creds.
- **Testing strategy:** follow project testing standards.
  - Unit tests: storage client, URL resolver, validation logic, rate limiter (coverage target per project spec).
  - Integration tests: MinIO stack; test full upload flow, CORS preflight, hard delete, quota enforcement.
  - Contract tests: GraphQL schema stability; validate upload URL -> PUT -> confirm -> fetch metadata -> GET object via public URL; verify cache headers.

## Non-Functional Requirements

### Performance & Scalability (MVP)
- Durability: rely on storage provider durability (R2/S3 11 nines); avoid destructive overwrites.
- Scalability: horizontally scalable stateless API; storage IO scalable via provider; no local disk reliance.
- Consistency: metadata writes strongly consistent (DB); storage eventual consistency tolerated with confirmation check; `READY` only after confirmed presence.
- Portability: storage client must be S3-compatible; no provider-specific lock-in in code paths (Worker/signed URL adapter pluggable).

### Cost Control (MVP)
- Prefer cache hits via stable keys; avoid purge churn.
- Use Cloudflare R2 Object Lifecycle Rules to expire stale `PENDING_UPLOAD` objects (e.g., delete objects with prefix `/pending/` older than 7 days).
- Monitor storage usage per namespace; emit cost attribution metrics.

### Backup & Disaster Recovery (Future Implementation)
- **Status:** Deferred; depends on Cloudflare R2 plan features.
- Metadata backup: daily DB snapshots with retention per project standards.
- Object replication: optional cross-region replication if R2 supports (investigate during Enterprise Plan adoption).
- Recovery playbook: restore metadata from backup; reconcile missing objects via R2 listing.

### SLIs & SLOs (Future Implementation)
- **Status:** Deferred; implement monitoring dashboards post-MVP.
- Target SLIs:
  - Upload issuance latency: p95 <200ms, p99 <500ms.
  - Upload success rate: >99% (excluding client abandons).
  - Public URL availability: >99.95% (CDN-dependent).
- Error budget: track 5xx rate; 0.1% monthly budget.

## API Contract (GraphQL)

### Mutations
- **`createAssetUpload(input: CreateAssetUploadInput!): Asset!`**
  - Input fields: `namespace!`, `filename!`, `mimeType!`, `sizeBytes!`, `idempotencyKey`.
  - Returns: full `Asset` object including `uploadUrl`, `uploadExpiresAt`, `status`, `visibility`, `storageKey`.
  - Errors: `VALIDATION_ERROR`, `STATE_ERROR`, `STORAGE_UNAVAILABLE`, plus auth errors via wiring permissions.

- **`confirmAssetUpload(input: ConfirmAssetUploadInput!): Asset!`**
  - Input fields: `assetId!`, optional `checksum`.
  - Returns: updated `Asset`; for PUBLIC assets `publicUrl` is populated, for PRIVATE assets use `downloadUrl(ttlSeconds)` field.

- **`deleteAsset(assetId: ID!): Boolean!`**
  - Hard-deletes metadata row and attempts to delete object from storage. Returns `true` on success, `false` if not found/unauthorized.

### Queries
- **`asset(id: ID!): Asset`**
  - Returns asset if found and authorized (PUBLIC assets bypass owner check; PRIVATE requires ownership).

### Error Handling
- Errors follow project GraphQL standards: structured error types with codes.
- Asset module maps: `VALIDATION_ERROR`, `STATE_ERROR`, `STORAGE_UNAVAILABLE`; optimistic locking delegated to common handler.

## Client Integration (Service-to-Service)
1. Choose namespace based on business rules; namespaces are defined in `asset-config.yml` with visibility, allowed MIME types, size limits, and key templates.
2. Call `createAssetUpload` with `namespace`, `filename`, `mimeType`, `sizeBytes`, and a client-generated `idempotencyKey` to dedupe retries within 24h.
3. Perform `PUT` to the returned `uploadUrl` with `Content-Type` set to the declared MIME type; no auth headers are needed beyond the presign.
4. Call `confirmAssetUpload` with the returned `assetId`; optionally include a checksum (stored but not validated against multipart ETags yet).
5. Use the returned URLs:
   - PUBLIC: use `publicUrl` (stable CDN URL derived from `storageKey`).
   - PRIVATE: call `downloadUrl(ttlSeconds)` in the GraphQL response shape to fetch a presigned GET URL per request.
6. Handle failures: if confirmation fails due to expiry, retry by issuing a new `createAssetUpload` (idempotency prevents dupes) and uploading again.

## Data Model (Logical)

### Table: `assets` (schema `assets`)
**Columns (current implementation):**
- `id` (UUID v7, PK, db-generated)
- `owner_id` (VARCHAR, NOT NULL)
- `namespace` (VARCHAR, NOT NULL)
- `storage_key` (VARCHAR, NOT NULL, UNIQUE)
- `storage_region` (VARCHAR, NOT NULL)
- `storage_bucket` (VARCHAR, NOT NULL)
- `mime_type` (VARCHAR, NOT NULL)
- `size_bytes` (BIGINT, NULLABLE)
- `checksum` (VARCHAR, NULLABLE)
- `original_filename` (VARCHAR, NULLABLE)
- `upload_url` (TEXT, NULLABLE)
- `upload_expires_at` (TIMESTAMPTZ, NULLABLE)
- `public_url` (TEXT, NULLABLE; deprecated, derived at runtime)
- `status` (VARCHAR ENUM: `PENDING`, `READY`, `FAILED`, `DELETED`)
- `visibility` (VARCHAR ENUM: `PUBLIC`, `PRIVATE`)
- `idempotency_key` (VARCHAR, NULLABLE)
- `created_at` (TIMESTAMPTZ, NOT NULL, default NOW())
- `updated_at` (TIMESTAMPTZ, NOT NULL, default NOW(), trigger-updated)
- `deleted_at` (TIMESTAMPTZ, NULLABLE)
- `version` (BIGINT, optimistic locking)

**Indexes / queries:**
- `storage_key` unique lookup.
- `owner_id` and `namespace` lookups for listing and quotas.
- `status` + `timestamps` for cleanup of stale PENDING/FAILED assets.

**Notes:**
- Visibility is derived from namespace config; storage keys come from namespace templates.
- `public_url` is no longer persisted; `publicUrl` is generated from `storageKey` at read time.

## Key Workflows

### 1. Upload Issuance (createAssetUpload)
```
Client -> GraphQL: createAssetUpload(input: {
  namespace, filename, mimeType, sizeBytes, idempotencyKey?
})
Service:
  1. Check idempotencyKey for same owner (24h window); return existing asset if found
  2. Load namespace config (visibility, keyTemplate, limits) from asset-config.yml
  3. Validate MIME and size against namespace rules
  4. Insert PENDING asset with temporary key, then compute final storage_key from template ({namespace}/{ownerId?}/{assetId})
  5. Generate presigned PUT URL (TTL from namespace or global setting)
  6. Persist uploadUrl and return Asset with uploadUrl + uploadExpiresAt
```

### 2. Upload Confirmation (confirmAssetUpload)
```
Client -> storage: PUT binary using uploadUrl (direct)
Client -> GraphQL: confirmAssetUpload(input: { assetId, checksum? })
Service:
  1. Verify owner matches and status is PENDING, and upload URL not expired
  2. HEAD object to ensure it exists; fetch metadata (size, ETag, content-type)
  3. Persist checksum if provided (ETag comparison for simple uploads only)
  4. Update status to READY, set sizeBytes, clear uploadUrl
  5. Return Asset with:
     - publicUrl when visibility = PUBLIC
     - downloadUrl resolver for PRIVATE (requires ttlSeconds argument)
```

### 3. Hard Delete
```
Client -> GraphQL: deleteAsset(assetId)
Service:
  1. Check permissions (owner or admin)
  2. Delete object from R2 (ignore 404 if already gone)
  3. Update metadata: status = DELETED, deleted_at = NOW(), deleted_by = actor
  4. Log audit event
  5. Return: { success: true, deletedAt }
```

### 4. Cleanup Jobs (Scheduled)
**Job 1: Expire Stale Pending Uploads (runs every 1h)**
```
Service:
  1. Query assets with status = PENDING_UPLOAD AND created_at < NOW() - 24h
  2. For each: update status = FAILED, error_reason = 'Upload expired'
  3. Optional: delete objects from R2 using lifecycle policy (R2 auto-deletes prefix /pending/ after 7d)
```

**Job 2: Purge Deleted Assets (runs daily)**
```
Service:
  1. Query assets with status = DELETED AND deleted_at < NOW() - 90d
  2. For each: hard delete metadata row (or move to archive table)
  3. Objects already deleted from R2; metadata cleanup only
```

### 5. Variant Generation (Future)
*Deferred to post-MVP; workflow placeholder:*
```
Trigger: on asset status -> READY
Service:
  1. Read variants_planned for resourceType
  2. Enqueue variant generation jobs (async workers or on-demand proxy)
  3. Workers: fetch original, resize/convert, upload variant to R2
  4. Update variants_ready on success; log error_reason on failure
```

## Configuration

### Storage (Required)
- `STORAGE_ENDPOINT` (string): S3-compatible endpoint (e.g., `https://<account-id>.r2.cloudflarestorage.com` for R2, `http://localhost:9000` for MinIO).
- `STORAGE_REGION` (string): AWS region or equivalent (e.g., `auto` for R2, `us-east-1` for S3).
- `STORAGE_BUCKET` (string): bucket name (e.g., `neotool-assets-prod`).
- `STORAGE_ACCESS_KEY` (string): access key ID.
- `STORAGE_SECRET` (string): secret access key.
- `STORAGE_PUBLIC_BASE_URL` (string): public CDN URL prefix (e.g., `https://cdn.neotool.com/assets/`).
- `STORAGE_FORCE_PATH_STYLE` (bool, default `false`): use path-style URLs for MinIO (`true`) vs. virtual-hosted for R2/S3 (`false`).
- `STORAGE_UPLOAD_TTL_SECONDS` (int, default `900`): pre-signed URL expiry (15min recommended).

### Validation & Limits (Required)
- `STORAGE_MAX_UPLOAD_BYTES` (int, default `10485760`): global max upload size (10MB default).
- `ASSET_ALLOWED_MIME_TYPES` (string, comma-separated): allowed MIME types (e.g., `image/jpeg,image/png,image/webp,image/gif`).
- `ASSET_VALIDATION_RULES_JSON` (JSON string, optional): per-resourceType validation rules.
  - Example: `{ "profile_image": { "maxBytes": 5242880, "maxWidth": 2048, "maxHeight": 2048, "minWidth": 100, "minHeight": 100, "allowedMimes": ["image/jpeg", "image/png", "image/webp"] }, "document": { "maxBytes": 52428800, "allowedMimes": ["application/pdf"] } }`

### Rate Limiting & Quotas (Required)
- `ASSET_RATE_LIMIT_REQUESTS_PER_HOUR` (int, default `100`): max upload requests per owner per hour.
- `ASSET_RATE_LIMIT_BYTES_PER_HOUR` (int, default `1073741824`): max total bytes per owner per hour (1GB default).
- `ASSET_RATE_LIMIT_BURST` (int, default `10`): burst allowance within 60s.
- `ASSET_NAMESPACE_QUOTA_BYTES` (int, optional): soft limit per namespace; warn at 80%.

### CORS (Required for browser uploads)
- `ASSET_CORS_ALLOWED_ORIGINS` (string, comma-separated): allowed origins for CORS (e.g., `https://app.neotool.com,https://admin.neotool.com`).
- Apply to R2 bucket CORS policy; ensure MinIO dev config matches.

### Feature Flags (Optional)
- `ASSET_ENABLE_SIGNED_URLS` (bool, default `false`): swap public URLs for signed/HMAC URLs (future).
- `ASSET_ENABLE_VARIANT_GENERATION` (bool, default `false`): enable automatic variant generation (future).
- `ASSET_ALLOW_OVERWRITE` (bool, default `false`): allow overwriting existing `READY` assets.
- `ASSET_ENABLE_BACKGROUND_CONFIRMATION` (bool, default `true`): enable background polling for upload confirmation.

### Cleanup & Lifecycle (Optional)
- `ASSET_CLEANUP_FAILED_PENDING_HOURS` (int, default `24`): hours before marking stale `PENDING_UPLOAD` as `FAILED`.
- `ASSET_PURGE_DELETED_DAYS` (int, default `90`): days before hard-deleting `DELETED` metadata.
- Configure R2 Object Lifecycle Rules separately for object expiry.

### Observability (Optional)
- `OTEL_*`: standard OpenTelemetry env vars for tracing.
- `METRICS_ENABLED` (bool, default `true`): enable Prometheus metrics export.
- `LOG_LEVEL` (string, default `info`): log verbosity.

## Migration & Rollout

### Phase 1: MVP (Public URLs, Direct Upload)
- Deploy service with MinIO dev stack for local testing.
- Configure R2 bucket with CORS policy for production.
- Implement core upload flow: `createAssetUpload`, background confirmation, hard delete.
- Enable magic bytes validation and rate limiting.
- Deploy to staging; run integration tests.

### Phase 2: Production Rollout
- Configure R2 production bucket + Cloudflare CDN.
- Migrate existing assets (if any): backfill `storageKey` and `publicBaseUrl` using migration script.
- Roll out to internal services first (jobs downloading external images).
- Monitor metrics: upload success rate, latency, rate limit hits.
- Gradually enable for end-user flows (profile images, etc.).

### Phase 3: Future Enhancements
- **Signed URLs:** implement HMAC Worker or R2 signed URLs; swap resolver without API contract change.
- **Variant generation:** add on-demand image proxy or async workers; backfill `variants_planned` for existing assets.
- **Malware scanning:** integrate Cloudflare Enterprise detection; add to confirmation workflow.
- **GDPR tooling:** implement `exportOwnerAssets` and enhanced purge workflows.
- **SLO dashboards:** build Grafana dashboards for latency, error budget tracking.

### Rollback Plan
- Service failure: traffic can bypass asset service; fall back to legacy storage URLs if cached.
- R2 unavailable: circuit breaker returns 503; clients retry with exponential backoff.
- Schema migration issues: metadata changes backward-compatible; can roll back without data loss.

---

## Implementation Checklist (MVP)

### Infrastructure
- [ ] Provision Cloudflare R2 bucket (prod) with CORS policy
- [ ] Configure R2 access keys and region settings
- [ ] Set up MinIO via docker-compose for local dev
- [ ] Configure CDN public base URL
- [ ] Create R2 Object Lifecycle Rules for cleanup (optional)

### Database Schema
- [ ] Create `asset` table with all columns per data model
- [ ] Create enums: `asset_status`, `asset_resource_type`
- [ ] Add indexes: namespace/id, owner/created_at, status/created_at, idempotency_key
- [ ] Add constraints: FK on owner_id, check constraints on size/deleted_at
- [ ] Write migration script with rollback plan

### Core Service Logic
- [ ] Implement S3-compatible storage client (R2/MinIO abstraction)
- [ ] Implement pre-signed PUT URL generation
- [ ] Implement magic bytes validation for MIME types
- [ ] Implement image dimension validation (decompression bomb protection)
- [ ] Implement per-resourceType validation rules (config loader)
- [ ] Implement rate limiting (per-owner requests/hour, bytes/hour, burst)
- [ ] Implement per-namespace quota tracking (soft limits, warnings)
- [ ] Implement idempotency key deduplication (24h window)

### GraphQL API
- [ ] Define schema: `createAssetUpload`, `confirmAssetUpload`, `deleteAsset`, `asset`, `assets`
- [ ] Implement `createAssetUpload` resolver with full validation
- [ ] Implement `confirmAssetUpload` resolver with R2 HEAD check
- [ ] Implement `deleteAsset` resolver with hard delete
- [ ] Implement `asset` query resolver
- [ ] Implement `assets` query resolver (with filters for admin)
- [ ] Add error codes: VALIDATION_ERROR, RATE_LIMIT_EXCEEDED, UNAUTHORIZED, STORAGE_UNAVAILABLE

### Background Jobs
- [ ] Implement background confirmation polling job (every 1min)
- [ ] Implement stale PENDING_UPLOAD cleanup job (every 1h)
- [ ] Implement DELETED metadata purge job (daily, configurable retention)

### Security & Compliance
- [ ] Implement AuthN/AuthZ checks per namespace/resourceType
- [ ] Implement path traversal prevention in storage keys
- [ ] Configure CORS on R2 bucket to match allowed origins
- [ ] Implement audit logging for all lifecycle events (create, confirm, delete)
- [ ] Add structured logs with assetId, ownerId, namespace, resourceType

### Observability
- [ ] Add metrics: upload_requests_total, upload_success_total, upload_failed_total, upload_duration_seconds
- [ ] Add metrics: rate_limit_exceeded_total, quota_warning_total, storage_errors_total
- [ ] Add OpenTelemetry traces for createAssetUpload, confirmAssetUpload, storage operations
- [ ] Configure alerting for high 5xx rate, rising failures, storage unavailable
- [ ] Create basic dashboard (upload success rate, p95 latency, quota usage)

### Testing
- [ ] Unit tests: storage client, validation logic, rate limiter, URL resolver (target coverage per project spec)
- [ ] Integration tests: full upload flow with MinIO (issue URL -> PUT -> confirm -> fetch)
- [ ] Integration tests: CORS preflight, rate limiting, quota enforcement, hard delete
- [ ] Contract tests: GraphQL schema validation, error response formats
- [ ] Load tests: 100 concurrent uploads, validate latency and error rate

### Documentation
- [ ] Update API documentation with GraphQL schema examples
- [ ] Write developer integration guide (how to use from frontend/backend)
- [ ] Document error codes and retry strategies
- [ ] Create runbook for common operational issues
- [ ] Document MinIO dev setup in project README

### Future Enhancements (TODO - Post-MVP)
- [ ] **Variant generation:** on-demand proxy or async workers
- [ ] **Signed URLs:** HMAC Worker or R2 signed URL support
- [ ] **Malware scanning:** integrate Cloudflare Enterprise detection
- [ ] **GDPR tooling:** exportOwnerAssets, enhanced purge workflows
- [ ] **SLO monitoring:** Grafana dashboards for error budget tracking
- [ ] **Backup strategy:** investigate R2 cross-region replication
- [ ] **Cost attribution:** detailed per-namespace storage cost reports

---

## Quick Reference

### Example Upload Flow (Client-Side)
```typescript
// 1. Request upload URL
const { data } = await graphql(`
  mutation {
    createAssetUpload(input: {
      namespace: "user-profiles"
      filename: "avatar.jpg"
      mimeType: "image/jpeg"
      sizeBytes: 204800
      idempotencyKey: "unique-client-uuid"
    }) {
      id
      uploadUrl
      uploadExpiresAt
      status
      visibility
      storageKey
    }
  }
`)

// 2. Upload file directly to R2
const response = await fetch(data.createAssetUpload.uploadUrl, {
  method: 'PUT',
  headers: { 'Content-Type': 'image/jpeg' },
  body: fileBlob
})

// 3. Confirm upload (or rely on background polling)
await graphql(`
  mutation {
    confirmAssetUpload(input: { assetId: "${data.createAssetUpload.id}" }) {
      id
      status
      publicUrl        // populated when visibility = PUBLIC
      downloadUrl      // provide ttlSeconds arg when visibility = PRIVATE
    }
  }
`)

// 4. Use public URL
console.log(data.confirmAssetUpload.publicUrl)
// => https://cdn.neotool.com/assets/public/profile_image/01HQXY.../original
```

### Example Validation Rules Config
```json
{
  "profile_image": {
    "maxBytes": 5242880,
    "maxWidth": 2048,
    "maxHeight": 2048,
    "minWidth": 100,
    "minHeight": 100,
    "allowedMimes": ["image/jpeg", "image/png", "image/webp"]
  },
  "post_image": {
    "maxBytes": 10485760,
    "maxWidth": 4096,
    "maxHeight": 4096,
    "allowedMimes": ["image/jpeg", "image/png", "image/webp", "image/gif"]
  },
  "document": {
    "maxBytes": 52428800,
    "allowedMimes": ["application/pdf"]
  }
}
```

### R2 CORS Policy Example
```json
[
  {
    "AllowedOrigins": ["https://app.neotool.com", "https://admin.neotool.com"],
    "AllowedMethods": ["PUT", "HEAD", "OPTIONS"],
    "AllowedHeaders": ["Content-Type", "Content-Length", "x-amz-*"],
    "MaxAgeSeconds": 86400
  }
]
```
