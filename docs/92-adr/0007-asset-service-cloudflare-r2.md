---
title: ADR-0007 Asset Service with Cloudflare R2
type: adr
category: architecture
status: accepted
version: 1.0.0
tags: [assets, storage, cdn, cloudflare-r2, s3, graphql]
related:
  - docs/03-features/assets/asset-service/README.md
  - docs/03-features/assets/asset-service/decisions.md
  - adr/0003-kotlin-micronaut-backend.md
  - adr/0005-postgresql-database.md
---

# ADR-0007: Asset Service with Cloudflare R2

## Status
Accepted

## Context

Neotool requires a robust, scalable solution for managing binary assets (primarily images, with future support for documents and other file types). The system needs to support:

- **Direct client uploads**: Browser/mobile clients upload directly to storage without proxying through backend (reduces server load, improves UX)
- **CDN delivery**: Fast, globally distributed asset delivery with long-lived caching
- **Metadata tracking**: Store asset ownership, resource bindings, status, and audit trail in application database
- **Security**: Content validation, rate limiting, quota enforcement, audit logging
- **Cost efficiency**: Minimize egress costs, storage costs, and operational overhead
- **Developer experience**: Identical code path for local dev (MinIO) and production (Cloudflare R2)
- **Future-ready**: Support for signed URLs, variant generation (thumbnails, resizing), and malware scanning without breaking API contract

Current use cases:
1. **Internal jobs**: Services download external images and publish to CDN for consistent delivery
2. **User uploads** (future): Profile pictures, post images, document attachments

## Decision

We will implement a **GraphQL-based Asset Service** using:

1. **Storage Backend**: **Cloudflare R2** (S3-compatible) for production, **MinIO** for local development
2. **Delivery**: Cloudflare CDN with public URLs (immutable keys); future support for signed URLs
3. **Upload Flow**: Pre-signed PUT URLs for direct client-to-storage uploads
4. **Metadata**: PostgreSQL for asset metadata, status tracking, and audit trail
5. **API**: GraphQL-only interface (no REST) with mutations for upload/delete, queries for retrieval
6. **Validation**: Magic bytes validation, image dimension checks, rate limiting, quota enforcement
7. **Deletion Strategy**: Hard delete with metadata retention (audit log preserved)

### Key Architectural Choices

#### 1. Cloudflare R2 over AWS S3
- **Zero egress fees**: R2 charges only for storage and operations, not data transfer
- **S3-compatible API**: Drop-in replacement for S3 SDK; portable to other providers
- **Integrated CDN**: Native Cloudflare CDN integration with edge caching
- **Cost efficiency**: ~10x cheaper than S3 for CDN-heavy workloads

#### 2. Direct Upload with Pre-signed URLs
- **Client flow**: Request upload URL → PUT binary directly to R2 → Confirm upload
- **Benefits**: Reduces backend load, improves upload latency, scales horizontally
- **Trade-off**: Validation happens post-upload (client spends bandwidth before validation)

#### 3. Hard Delete over Soft Delete
- **Decision**: Immediately delete objects from R2; retain metadata with `deletedAt` timestamp
- **Rationale**: Simplifies lifecycle management, reduces storage costs, avoids complex R2 lifecycle policies
- **Audit trail**: Metadata retains `deletedAt`, `deletedBy` for compliance

#### 4. GraphQL-Only API
- **Consistency**: Aligns with Neotool's API-first approach (contracts repo)
- **Type safety**: Strong typing for asset metadata, mutations, and errors
- **Flexibility**: Easy to extend schema for future features (variants, signed URLs)

#### 5. Background Confirmation + Client Callback
- **Dual approach**: Client can call `confirmAssetUpload` for immediate feedback OR rely on background polling (every 1min)
- **Resilience**: Background polling recovers from client failures (network drop, browser close)

## Consequences

### Positive

- **Cost savings**: R2 zero egress fees save ~$90/TB vs. S3 ($0.09/GB S3 egress)
- **Performance**: CDN-backed delivery with long-lived caching (`max-age=31536000`)
- **Scalability**: Stateless API, horizontally scalable; storage scales via R2
- **Developer UX**: MinIO for local dev provides full S3 API parity (CORS, pre-signed URLs, lifecycle)
- **Security**: Magic bytes validation prevents MIME spoofing; rate limiting prevents abuse
- **Auditability**: Structured logs for all lifecycle events (create, confirm, delete)
- **Future-ready**: Schema supports variants, signed URLs, multi-tenancy without breaking changes

### Negative

- **Vendor lock-in (mitigated)**: R2-specific features (e.g., Cloudflare Workers) require abstraction layer; mitigated by S3-compatible API
- **Validation timing**: Magic bytes validation happens post-upload (client bandwidth spent before rejection)
- **Background polling load**: 1min polling adds DB query load; mitigated with indexed queries on `status + created_at`
- **No native events**: R2 lacks S3 Event Notifications; requires background polling for upload confirmation
- **Manual CORS setup**: R2 bucket CORS policy must be manually configured (not service-managed)

### Risks

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **R2 rate limiting** (Cloudflare-imposed) | Upload failures, 429 errors | Circuit breaker, exponential backoff, monitoring |
| **Storage cost growth** | Budget overrun | Namespace quotas, lifecycle policies, billing alerts |
| **CDN cache poisoning** | Serving stale/wrong assets | Immutable keys (include timestamp/hash), no overwrites |
| **Magic bytes bypass** (polyglot files) | Malicious uploads | Validate file structure, add malware scanning (Enterprise) |
| **Thundering herd** on variant generation | Worker overload | Queue with concurrency limit, backpressure (future) |

## Implementation Plan

### Phase 1: MVP (Public URLs, Direct Upload)
**Status**: Current phase

**Scope**:
- Core upload flow: `createAssetUpload`, `confirmAssetUpload`, `deleteAsset`
- Magic bytes validation, image dimension checks
- Rate limiting (per-owner: 100 req/hour, 1GB/hour)
- Quota enforcement (per-namespace, per-resourceType)
- Hard delete with audit logging
- MinIO dev stack + R2 production

**Deliverables**:
- [ ] PostgreSQL schema: `asset` table, enums, indexes
- [ ] S3-compatible storage client (R2/MinIO abstraction)
- [ ] GraphQL mutations/queries
- [ ] Background confirmation polling job (1min)
- [ ] Cleanup jobs (stale PENDING, purge DELETED)
- [ ] Integration tests with MinIO
- [ ] R2 bucket + CORS config

### Phase 2: Production Rollout
**Scope**:
- Deploy to staging with R2 bucket
- Roll out to internal services (jobs)
- Monitor metrics (upload success rate, latency, quota hits)
- Gradually enable for end-user flows

### Phase 3: Future Enhancements (Post-MVP)
**Deferred** (documented for future):
- **Signed URLs**: HMAC Worker or R2 signed URL support
- **Variant generation**: On-demand image proxy (Cloudflare Image Resizing) or async workers
- **Malware scanning**: Cloudflare Enterprise Built-in Detection
- **GDPR tooling**: `exportOwnerAssets`, enhanced purge workflows
- **SLO monitoring**: Grafana dashboards, error budget tracking
- **Backup strategy**: Cross-region replication (investigate R2 support)

## Alternatives Considered

### Alternative 1: AWS S3 + CloudFront
- **Pros**: Mature ecosystem, S3 Event Notifications, native lifecycle policies
- **Cons**: High egress costs ($0.09/GB), complex CloudFront setup, more expensive
- **Verdict**: **Rejected** - R2 is 10x cheaper for CDN-heavy workloads

### Alternative 2: Self-hosted MinIO + CDN
- **Pros**: Full control, no vendor lock-in, free egress
- **Cons**: Operational overhead (HA, backups, scaling), CDN integration complexity, no built-in CDN
- **Verdict**: **Rejected** - Operational burden outweighs cost savings for startup scale

### Alternative 3: Cloudinary / Imgix (SaaS)
- **Pros**: Managed service, built-in variant generation, image optimization
- **Cons**: High cost per GB, vendor lock-in, limited control over storage/CDN
- **Verdict**: **Rejected** - Too expensive for high-volume use cases; R2 + future Workers gives similar capabilities

### Alternative 4: Backend-proxied uploads
- **Pros**: Easier validation (validate before upload), simpler client logic
- **Cons**: Backend bottleneck, high server load, poor UX (slow uploads)
- **Verdict**: **Rejected** - Doesn't scale; direct upload is industry standard

### Alternative 5: Soft delete with R2 Lifecycle Rules
- **Pros**: Recovery window for accidental deletes (30d grace period)
- **Cons**: Complex R2 lifecycle config (move to `/deleted/` prefix), higher storage costs, more API calls
- **Verdict**: **Rejected** for MVP - Hard delete is simpler; can add later if GDPR requires

## Decision Drivers

1. **Cost efficiency**: R2 zero egress fees critical for CDN-heavy workload
2. **Developer velocity**: MinIO dev parity enables local testing without cloud dependencies
3. **Scalability**: Direct uploads + CDN delivery scale to millions of assets
4. **Security**: Magic bytes, rate limiting, quotas prevent abuse
5. **Future-ready**: S3-compatible API keeps options open (can migrate to S3/GCS if needed)
6. **Simplicity**: GraphQL-only API reduces surface area; hard delete avoids lifecycle complexity

## Validation Metrics

**Success criteria** (post-MVP):
- Upload success rate: >99% (excluding client-side abandons)
- p95 latency for upload issuance: <200ms
- p95 latency for confirmation: <500ms
- Rate limit hit rate: <1% of requests
- Storage cost: <$0.02/GB/month (R2 pricing)
- CDN cache hit ratio: >90% (after warmup)

**Monitoring**:
- Metrics: `upload_requests_total`, `upload_success_total`, `upload_failed_total`, `rate_limit_exceeded_total`
- Alerts: 5xx rate >0.1%, upload failure rate >1%, R2 unavailable
- Dashboards: Upload success rate, latency percentiles, quota usage per namespace

## References

- [Asset Service Specification](../03-features/assets/asset-service/README.md)
- [Technical Decisions](../03-features/assets/asset-service/decisions.md)
- [Cloudflare R2 Documentation](https://developers.cloudflare.com/r2/)
- [ADR-0003: Kotlin/Micronaut Backend](./0003-kotlin-micronaut-backend.md)
- [ADR-0005: PostgreSQL Database](./0005-postgresql-database.md)

## Open Questions

1. **GDPR/LGPD compliance**: Does "right to erasure" require soft delete? (Investigate post-MVP)
2. **R2 replication**: Does R2 support cross-region replication for backup? (Check Enterprise plan)
3. **SLO targets**: What are acceptable latency/availability targets based on user impact? (Establish baseline post-MVP)
4. **Cost attribution**: Do we need per-tenant cost tracking for billing? (Defer to multi-tenancy phase)

---

**Last Updated**: 2025-12-23
**Author**: Neotool Team
**Reviewers**: [To be added]
