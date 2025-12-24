# Assets Service Specification

## Overview
- Purpose: provide upload, storage, and delivery of binary assets (primarily images) via an S3-compatible store (Cloudflare R2 in prod, MinIO in dev) fronted by CDN. Public URLs now; signed URLs later without contract change.
- Scope: backend service/API (GraphQL-only) to issue upload URLs, track metadata, and expose delivery URLs. No frontend UI beyond existing consumers.
- Out of scope (for now): per-tenant billing, client-side image manipulation, video/transcoding, DRM, automated variant generation.
- Future roadmap: malware scanning (Enterprise Plan with Cloudflare's detection), advanced backup strategies, GDPR/LGPD compliance tooling, SLO monitoring dashboards.

## Goals
- Simple dev experience: same code path for local (MinIO) and prod (R2/S3) by switching env vars.
- Strong cacheability: stable object keys and variant naming; CDN-friendly headers.
- Future-ready security: ready to swap public URLs for signed/HMAC Worker without breaking contract.
- Operationally robust: observability, idempotency, lifecycle cleanup, and safe overwrite rules.

## Functional Requirements

### API Surface (GraphQL-only)
- Provide `createAssetUpload` GraphQL mutation that returns: `key`, `uploadUrl` (pre-signed PUT), `publicUrl`, `expiresAt`, `contentType`, `contentLengthLimit`.
  - Mutation accepts optional `idempotencyKey` (UUID) for deduplication within 24h window; returns existing asset if key matches and status != FAILED.
- Provide `asset` query for metadata + resolved `publicUrl` (or signed URL once enabled).
- Optional list/search query for internal tooling: filter by owner, namespace, status, type, createdAt.
- Upload is direct from client/job to storage; service never proxies the binary.
- Expose health/readiness endpoints consistent with service standards (non-GraphQL).

### Storage & Key Scheme
- Single bucket per environment (configurable): `STORAGE_BUCKET`.
- Object key format: `{namespace}/{resourceType}/{assetId}/{variant}` with default namespace `public` (tenant-less today); original upload uses `original` variant; derived variants use fixed names (e.g., `thumb`, `medium`, `full`).
- Keys are immutable; no overwrite allowed once `READY` unless explicitly flagged (`allowOverwrite` false by default).
- Store checksum (e.g., SHA-256) supplied by client or computed server-side for integrity/dedupe.
- Enforce max object size per environment; reject pre-sign requests over limit.

- Persist: `id` (UUIDv7), `namespace` (default `public`), `ownerId` (user/service), `resourceType` (enum), `resourceId` (optional FK to domain resource), `status` (`PENDING_UPLOAD`, `READY`, `FAILED`, `DELETED`), `mimeType`, `size` (optional until head), `checksum`, `storageKey`, `publicBaseUrl`, `variants` (available + planned), `createdAt`, `updatedAt`, `deletedAt` (soft delete), `lastAccessedAt` (optional).
- Status transitions: `PENDING_UPLOAD` -> `READY` on successful head/confirm; `FAILED` on error; `DELETED` on soft delete; hard delete/purge tracked separately.
- Immutability: metadata is append-only for history-sensitive fields (owner, namespace, resource binding) unless explicitly allowed via admin path.

### Upload Flow
- Client calls `createAssetUpload` with `mimeType`, `expectedSize`, `resourceType/resourceId`, optional `namespace` (defaults to `public`), optional `checksum`.
- Service validates mime/size/namespace quotas, allocates `assetId`, writes metadata as `PENDING_UPLOAD`, issues pre-signed PUT to storage endpoint with short TTL.
- Client PUTs binary to storage (direct).
- Service confirms upload: either via client callback (`confirmAssetUpload`), background poll/head, or event from storage if available. On success, updates status to `READY`, records size and checksum (if computed).
- On failure/expiration, status -> `FAILED`; upload URL expires automatically; stale PENDING cleaned by scheduled job.

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

## API Contract (GraphQL First)

### Mutations
- **`createAssetUpload(input: CreateAssetUploadInput!): CreateAssetUploadPayload`**
  - Input: `{ mimeType!, expectedSize!, resourceType!, resourceId, namespace, checksum, idempotencyKey }`
  - Payload: `{ asset { id, key, status, mimeType, plannedVariants, publicUrl, uploadUrl, uploadExpiresAt } }`
  - Errors: `VALIDATION_ERROR` (invalid mime/size/quota), `RATE_LIMIT_EXCEEDED`, `UNAUTHORIZED`, `STORAGE_UNAVAILABLE`.

- **`confirmAssetUpload(id: ID!, size: Int, checksum: String): ConfirmAssetUploadPayload`**
  - Optional explicit confirmation path; alternative to background polling.
  - Updates status to `READY` if object exists; `FAILED` otherwise.

- **`deleteAsset(id: ID!): DeleteAssetPayload`**
  - Hard delete: immediately removes object from R2 and marks metadata as `DELETED`.
  - Returns success boolean and `deletedAt` timestamp.

- **`purgeAsset(id: ID!): PurgeAssetPayload`**
  - Admin-only; for compliance/GDPR (future implementation).
  - Bypasses grace periods; immediately purges metadata and object.

### Queries
- **`asset(id: ID!): Asset`**
  - Returns: `{ id, namespace, ownerId, resourceType, resourceId, status, mimeType, sizeBytes, checksum, storageKey, publicUrl, variants, createdAt, updatedAt, deletedAt }`

- **`assets(filter: AssetFilter, limit: Int, offset: Int): AssetsConnection`**
  - Filter: `{ namespace, ownerId, resourceType, resourceId, status, createdAfter, createdBefore }`
  - For internal tooling/admin dashboards.

### Error Handling
- Errors follow project GraphQL standards: structured error types with codes.
- Common codes: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `RATE_LIMIT_EXCEEDED` (429), `STORAGE_UNAVAILABLE` (502/503).

## Data Model (Logical)

### Table: `asset` (schema `app`)
**Columns:**
- `id` (UUID v7, PK): unique asset identifier; time-sortable.
- `namespace` (VARCHAR, NOT NULL, default `'public'`): logical grouping; future multi-tenancy.
- `owner_id` (UUID, NOT NULL): FK to user/service who owns the asset.
- `resource_type` (ENUM `asset_resource_type`, NOT NULL): type of resource (e.g., `profile_image`, `post_image`, `document`).
- `resource_id` (UUID, NULLABLE): optional FK to domain resource (e.g., user.id, post.id).
- `storage_key` (VARCHAR, NOT NULL, UNIQUE): full object key in R2 bucket (e.g., `public/profile_image/01HQXY.../original`).
- `public_base_url` (VARCHAR, NOT NULL): base CDN URL (e.g., `https://cdn.example.com/assets/`).
- `mime_type` (VARCHAR, NOT NULL): validated MIME type.
- `size_bytes` (BIGINT, NULLABLE): actual size after upload; NULL until confirmed.
- `checksum` (VARCHAR, NULLABLE): SHA-256 or client-provided checksum for integrity.
- `status` (ENUM `asset_status`, NOT NULL): `PENDING_UPLOAD`, `READY`, `FAILED`, `DELETED`.
- `variants_planned` (JSONB, NULLABLE): planned variants config (future); e.g., `["thumb", "medium"]`.
- `variants_ready` (JSONB, NULLABLE): successfully generated variants (future); e.g., `["thumb"]`.
- `error_reason` (TEXT, NULLABLE): reason for `FAILED` status.
- `idempotency_key` (UUID, NULLABLE, UNIQUE): client-provided key for deduplication (24h window).
- `created_at` (TIMESTAMPTZ, NOT NULL, default NOW()).
- `updated_at` (TIMESTAMPTZ, NOT NULL, default NOW(), auto-update on change).
- `deleted_at` (TIMESTAMPTZ, NULLABLE): soft delete timestamp (if hard delete not used, set on delete).
- `deleted_by` (UUID, NULLABLE): actor who deleted the asset.

**Indexes:**
- `(namespace, id)`: primary access pattern.
- `(namespace, resource_type, resource_id)`: query assets by resource.
- `(owner_id, created_at)`: query assets by owner.
- `(status, created_at)`: cleanup jobs for expired `PENDING_UPLOAD`.
- `(idempotency_key)`: fast lookup for deduplication (partial index where NOT NULL).

**Enums:**
- `asset_status`: `PENDING_UPLOAD`, `READY`, `FAILED`, `DELETED`.
- `asset_resource_type`: `profile_image`, `post_image`, `document`, `attachment` (extend as needed).

**Constraints:**
- Foreign key `owner_id` -> `app.user.id` or `app.service.id` (depending on auth model).
- Check constraint: `size_bytes >= 0` when NOT NULL.
- Check constraint: `deleted_at IS NULL OR status = 'DELETED'`.

## Key Workflows

### 1. Upload Issuance
```
Client -> GraphQL: createAssetUpload(mimeType, expectedSize, resourceType, ...)
Service:
  1. Check idempotencyKey; return existing if duplicate within 24h
  2. Validate MIME type against allowed list (magic bytes check deferred to confirmation)
  3. Validate expectedSize against quota (per-owner, per-namespace, per-resourceType)
  4. Check rate limits (per-owner); reject if exceeded
  5. Allocate assetId (UUIDv7)
  6. Compute storage_key: {namespace}/{resourceType}/{assetId}/original
  7. Insert metadata with status = PENDING_UPLOAD
  8. Generate pre-signed PUT URL (TTL: 15min, headers: Content-Type, Content-Length)
  9. Return: { assetId, uploadUrl, publicUrl, expiresAt }
```

### 2. Upload Confirmation
**Option A: Client Callback**
```
Client -> R2: PUT binary to uploadUrl (direct)
Client -> GraphQL: confirmAssetUpload(assetId, size, checksum)
Service:
  1. HEAD object in R2 to verify existence
  2. Validate size matches expectedSize (tolerance ±1%)
  3. Optional: compute checksum if not provided and compare
  4. Update metadata: status = READY, size_bytes = actual, checksum
  5. Return: { asset { status, publicUrl } }
```

**Option B: Background Polling**
```
Service (scheduled every 1min):
  1. Query assets with status = PENDING_UPLOAD AND created_at > 15min ago
  2. For each: HEAD object in R2
  3. If exists: update to READY (same validation as Option A)
  4. If not exists and created_at > 1h: update to FAILED (expired)
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
      mimeType: "image/jpeg"
      expectedSize: 2048000
      resourceType: PROFILE_IMAGE
      resourceId: "user-123"
      idempotencyKey: "unique-client-uuid"
    }) {
      asset {
        id
        uploadUrl
        publicUrl
        uploadExpiresAt
      }
    }
  }
`)

// 2. Upload file directly to R2
const response = await fetch(data.createAssetUpload.asset.uploadUrl, {
  method: 'PUT',
  headers: { 'Content-Type': 'image/jpeg' },
  body: fileBlob
})

// 3. Confirm upload (or rely on background polling)
await graphql(`
  mutation {
    confirmAssetUpload(id: "${data.createAssetUpload.asset.id}") {
      asset { status publicUrl }
    }
  }
`)

// 4. Use public URL
console.log(data.createAssetUpload.asset.publicUrl)
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
