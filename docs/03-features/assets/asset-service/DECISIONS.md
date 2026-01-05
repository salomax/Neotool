# Asset Service - Technical Decisions & Trade-offs

## Overview
This document captures key architectural decisions, trade-offs, and rationale for the Asset Service implementation.

---

## Decision Log

### 1. Hard Delete vs. Soft Delete

**Decision:** Use **hard delete** with metadata retention.

**Rationale:**
- **Simplicity:** Soft delete with R2 Object Lifecycle Rules requires managing `/deleted/` prefixes or complex policies.
- **Cost control:** Immediate deletion reduces storage costs; no stale objects.
- **Compliance:** Metadata retains `deletedAt` and `deletedBy` for audit trail; sufficient for most compliance needs.
- **Future flexibility:** Can add soft delete with grace period later if GDPR "right to erasure" requires it.

**Trade-offs:**
- ✅ Pro: Simpler implementation, lower storage costs, clear lifecycle.
- ❌ Con: No recovery window for accidental deletes (mitigate with confirmation dialogs in UI).
- ❌ Con: GDPR "right to erasure" bypass requires separate purge workflow (deferred to future).

**Alternative Considered:** Soft delete with copy to `/deleted/` prefix + R2 lifecycle expiry after 30 days.
- Rejected: adds complexity, cost, and requires additional R2 API calls (copy + delete).

---

### 2. Upload Confirmation: Client Callback vs. Background Polling

**Decision:** Support **both** (client callback preferred; background polling as fallback).

**Rationale:**
- **Client callback:** Faster confirmation, better UX (immediate feedback).
- **Background polling:** Resilient to client failures (network drops, browser close).
- **Flexibility:** Different clients can choose based on use case (e.g., server-side jobs always use polling).

**Implementation:**
- Client calls `confirmAssetUpload` after successful PUT (optional).
- Background job polls `PENDING_UPLOAD` assets every 1min as fallback.
- Avoid duplicate confirmation: status transition is idempotent.

**Trade-offs:**
- ✅ Pro: Best of both worlds (fast + resilient).
- ❌ Con: Slightly more complex (two confirmation paths).
- ❌ Con: Background polling adds DB query load (mitigate with indexed query on `status + created_at`).

---

### 3. Magic Bytes Validation: On Upload Issuance vs. Confirmation

**Decision:** Defer magic bytes validation to **confirmation phase**.

**Rationale:**
- **Upload issuance:** Client hasn't uploaded yet; no bytes to inspect.
- **Confirmation:** Service can download first N bytes from R2 to validate magic bytes match declared MIME.
- **Performance:** Avoids requiring client to upload twice (once for validation, once for storage).

**Implementation:**
- On `confirmAssetUpload` or background polling: HEAD object, GET first 512 bytes, validate magic bytes.
- If mismatch: set status to `FAILED`, error_reason = "MIME type mismatch".

**Trade-offs:**
- ✅ Pro: Accurate validation (based on actual file content).
- ❌ Con: Validation happens after upload (client already spent bandwidth).
- ❌ Con: Adds latency to confirmation (mitigate: validation runs async after status -> READY).

**Alternative Considered:** Validate during upload issuance by requiring client to send first 512 bytes.
- Rejected: poor UX, requires additional client logic, breaks direct-to-R2 simplicity.

---

### 4. Rate Limiting Strategy: Per-Owner vs. Per-IP

**Decision:** **Per-owner** rate limiting (owner_id from auth token).

**Rationale:**
- **Internal services:** IP-based limits don't work (all requests from same service IP).
- **Accountability:** Owner-based limits tie to authenticated user/service; better for audit and quota enforcement.
- **Flexibility:** Different owners can have different limits (e.g., premium users, internal jobs).

**Implementation:**
- Extract `ownerId` from auth token/context.
- Use sliding window rate limiter (Redis or in-memory with distributed cache sync).
- Track: requests/hour, bytes/hour, burst allowance.

**Trade-offs:**
- ✅ Pro: Works for both user and service contexts; fair per-actor limits.
- ❌ Con: Requires auth; can't rate-limit unauthenticated requests (mitigate: reject unauthed at API gateway).
- ❌ Con: Distributed rate limiting requires shared state (Redis or cache sync).

**Alternative Considered:** Per-IP rate limiting.
- Rejected: doesn't work for services; easy to bypass with proxies.

---

### 5. Idempotency Key: Required vs. Optional

**Decision:** **Optional** `idempotencyKey` in `createAssetUpload`.

**Rationale:**
- **Flexibility:** Not all clients need idempotency (e.g., one-off manual uploads).
- **Simplicity:** Clients can omit if they handle retries at application level.
- **Safety:** When provided, prevents duplicate uploads on retry (e.g., network timeout -> retry -> same asset).

**Implementation:**
- If `idempotencyKey` provided: check for existing asset with same key created within 24h.
- If exists and status != FAILED: return existing asset.
- If exists and status == FAILED: allow new upload (client retrying failed upload).

**Trade-offs:**
- ✅ Pro: Flexible; clients choose when to use.
- ❌ Con: Clients must generate UUIDs (minor burden).
- ❌ Con: Requires unique constraint on `idempotency_key` column.

---

### 6. Namespace Design: Multi-Tenant vs. Single `public`

**Decision:** Start with single **`public`** namespace; design schema for future multi-tenancy.

**Rationale:**
- **MVP simplicity:** Current use case is tenant-less (shared CDN for all users).
- **Future-ready:** Schema includes `namespace` column; can add tenant-specific namespaces later without migration.
- **Quota enforcement:** Even with single namespace, can enforce quotas per `ownerId` or `resourceType`.

**Implementation:**
- Default namespace = `'public'`.
- Storage key includes namespace: `public/profile_image/01HQXY.../original`.
- Future: add `tenant_id` column, use namespace per tenant, enforce per-tenant quotas.

**Trade-offs:**
- ✅ Pro: Simple MVP; easy to add tenants later.
- ❌ Con: All assets share same namespace today (collision risk if `resourceType` not granular).

---

### 7. Variant Generation: Deferred to Post-MVP

**Decision:** **Not implemented in MVP**; schema includes placeholders (`variants_planned`, `variants_ready`).

**Rationale:**
- **Scope control:** Variant generation adds significant complexity (image processing, workers, retry logic).
- **Use case priority:** MVP serves original images only; variants needed for optimization later.
- **Architectural flexibility:** Can choose between on-demand (Cloudflare Image Resizing) or async (workers + queue) post-MVP.

**Future Implementation Options:**
1. **On-demand proxy:** Cloudflare Image Resizing Worker; generate variants on first request, cache in CDN.
2. **Async workers:** On asset -> READY, enqueue variant jobs; workers fetch original, resize, upload variants.

**Trade-offs:**
- ✅ Pro: Faster MVP delivery; avoid premature optimization.
- ❌ Con: No optimized images for mobile/web (clients download full originals).

---

### 8. CORS Configuration: Service-Managed vs. Manual

**Decision:** **Manual R2 bucket CORS policy** configured via Cloudflare dashboard/API.

**Rationale:**
- **Separation of concerns:** Service doesn't manage infrastructure (R2 config is IaC or manual setup).
- **Security:** CORS policy is infrastructure-level; should match environment (staging, prod).
- **MinIO parity:** Local dev requires matching CORS in MinIO config.

**Implementation:**
- Document required CORS policy in spec.
- Provide example JSON for R2 dashboard.
- Add validation: service startup checks CORS (optional health check).

**Trade-offs:**
- ✅ Pro: Clear separation; infra team owns bucket config.
- ❌ Con: Manual setup step (mitigate: IaC script or documentation).

---

### 9. Error Handling: Retry Strategy for R2 Unavailable

**Decision:** Use **circuit breaker** pattern for R2 client; return 503 on open circuit.

**Rationale:**
- **Resilience:** If R2 is down, avoid hammering it with retries; fail fast.
- **User experience:** Return clear error (503 Service Unavailable) so clients can retry with backoff.
- **Observability:** Circuit state (closed, open, half-open) exposes R2 health.

**Implementation:**
- Wrap R2 client calls (pre-signed URL generation, HEAD, DELETE) with circuit breaker.
- Circuit opens after N consecutive failures (e.g., 5); stays open for M seconds (e.g., 60s).
- Half-open: allow 1 request to test recovery; close if successful.

**Trade-offs:**
- ✅ Pro: Prevents cascading failures; clear error signals.
- ❌ Con: May reject requests even if R2 recovers quickly (mitigate: short open duration).

---

### 10. Testing Strategy: Integration Tests with MinIO vs. Mocked R2

**Decision:** Use **real MinIO** in integration tests; mock R2 in unit tests.

**Rationale:**
- **Integration tests:** Validate full upload flow (CORS, pre-signed URLs, object storage) with real S3-compatible backend.
- **Unit tests:** Validate business logic (rate limiting, validation, state machine) with mocked storage client.
- **Confidence:** MinIO tests catch S3 API quirks (path-style URLs, CORS preflight, etc.).

**Implementation:**
- Docker Compose with MinIO service for integration tests.
- Seed script creates bucket, sets CORS, uploads fixtures.
- Tests run against MinIO endpoint; validate object exists, headers correct, etc.

**Trade-offs:**
- ✅ Pro: High confidence; tests real S3 behavior.
- ❌ Con: Slower tests (Docker startup, network latency).
- ❌ Con: Requires Docker in CI (acceptable for most CI/CD).

---

## Open Questions (Require Future Investigation)

### 1. GDPR/LGPD Compliance Tooling
- **Question:** What specific requirements exist for "right to erasure" and "right to access"?
- **Impact:** May need soft delete with grace period, audit export, data residency controls.
- **Action:** Defer to future; design schema to support (add `data_classification`, `storage_region` columns).

### 2. Backup & Disaster Recovery
- **Question:** Does Cloudflare R2 plan support cross-region replication or versioning?
- **Impact:** If no native replication, may need custom backup solution (periodic sync to S3/GCS).
- **Action:** Investigate R2 capabilities; plan backup strategy for Enterprise Plan.

### 3. SLO Targets
- **Question:** What are acceptable latency/availability targets for asset service?
- **Impact:** Determines error budget, monitoring granularity, alerting thresholds.
- **Action:** Establish baseline metrics post-MVP; set SLOs based on user impact.

### 4. Cost Attribution per Namespace/Tenant
- **Question:** Do we need per-tenant cost tracking for billing?
- **Impact:** Requires tracking storage bytes, CDN bandwidth, request counts per namespace.
- **Action:** Add namespace-level metrics; defer detailed billing to future.

---

## Risks & Mitigations

### Risk 1: R2 Rate Limiting (Cloudflare Imposed)
- **Risk:** Cloudflare may rate-limit R2 API calls (unknown limits).
- **Mitigation:** Monitor 429 responses from R2; implement exponential backoff; add circuit breaker.

### Risk 2: CDN Cache Invalidation
- **Risk:** Immutable keys mean old versions cached indefinitely; no purge mechanism.
- **Mitigation:** Use versioned keys (include timestamp or hash in key); avoid overwrites.

### Risk 3: Storage Costs Growth
- **Risk:** Uncontrolled uploads could exhaust R2 storage budget.
- **Mitigation:** Enforce quotas per namespace; monitor usage; set up billing alerts; use lifecycle policies for cleanup.

### Risk 4: Magic Bytes Validation Bypass
- **Risk:** Malicious clients could upload polyglot files (valid JPEG + embedded script).
- **Mitigation:** Validate magic bytes + file structure; add malware scanning in Enterprise Plan.

### Risk 5: Thundering Herd on Variant Generation
- **Risk:** If 1000 assets uploaded simultaneously, variant workers overwhelmed.
- **Mitigation:** Queue with concurrency limit; add backpressure; prioritize variants (thumb first, full later).

---

## Summary

This document captures the key decisions made during asset service design. All decisions prioritize:
1. **MVP simplicity:** Defer complex features (variants, malware scanning, GDPR) to post-MVP.
2. **Security:** Magic bytes validation, rate limiting, CORS, audit logging.
3. **Resilience:** Circuit breaker for R2, idempotency, background polling fallback.
4. **Future-ready:** Schema supports multi-tenancy, variants, compliance without major rewrites.

For questions or proposed changes to these decisions, open a discussion in the team channel or create an ADR (Architecture Decision Record).
