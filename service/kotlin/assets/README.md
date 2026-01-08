# Assets Service

Asset management service for Neotool platform. Handles upload, storage, and delivery of binary assets (images, documents) via S3-compatible storage (Cloudflare R2 in prod, MinIO in dev).

## Quick Start

### Local Development with MinIO

```bash
# Start MinIO via docker-compose (from root)
docker-compose up -d minio

# Run migration
./gradlew :assets:flywayMigrate

# Start service
./gradlew :assets:run
```

Service available at: http://localhost:8083
GraphiQL: http://localhost:8083/graphiql

## Configuration

All configuration is done via environment variables or `application.yml`.

### JWT Authentication

The assets service validates JWT tokens but does not issue them. Public keys for validation are fetched from the security service via JWKS.

**Required Configuration:**
- `JWT_JWKS_URL`: URL to the security service's JWKS endpoint (required)
  - Example: `http://security-service:8080/.well-known/jwks.json`
  - The service will automatically fetch and cache public keys from this endpoint
- `JWT_KEY_ID`: Key identifier (default: `kid-1`)

**Note:** Validating services do not need direct access to Vault. The security service manages private keys and exposes public keys via the JWKS endpoint. Only the security service requires Vault access for key management.

### Storage Settings

- `STORAGE_ENDPOINT`: S3-compatible endpoint (default: `http://localhost:9000` for MinIO)
- `STORAGE_REGION`: AWS region (default: `us-east-1`)
- `STORAGE_BUCKET`: Bucket name (default: `neotool-assets`)
- `STORAGE_ACCESS_KEY`: Access key (default: `minioadmin`)
- `STORAGE_SECRET`: Secret key (default: `minioadmin`)
- `STORAGE_PUBLIC_BASE_URL`: Public CDN URL (default: `http://localhost:9000/neotool-assets`)
- `STORAGE_FORCE_PATH_STYLE`: Use path-style URLs for MinIO (default: `true`)
- `STORAGE_UPLOAD_TTL_SECONDS`: Pre-signed URL expiry (default: `900` / 15min)

### Validation Settings

- `STORAGE_MAX_UPLOAD_BYTES`: Global max upload size (default: `10485760` / 10MB)
- `ASSET_ALLOWED_MIME_TYPES`: Comma-separated MIME types (default: `image/jpeg,image/png,image/webp,image/gif`)

### Rate Limiting

- `ASSET_RATE_LIMIT_REQUESTS_PER_HOUR`: Max requests per owner/hour (default: `100`)
- `ASSET_RATE_LIMIT_BYTES_PER_HOUR`: Max bytes per owner/hour (default: `1073741824` / 1GB)
- `ASSET_RATE_LIMIT_BURST`: Burst allowance (default: `10`)

## Architecture

- **Domain Layer**: Pure Kotlin domain objects (DDD)
- **Entity Layer**: JPA entities for persistence
- **Repository Layer**: Data access with Spring Data JPA
- **Service Layer**: Business logic, S3 client, validation, rate limiting
- **GraphQL Layer**: API resolvers
- **Job Layer**: Scheduled tasks (confirmation, cleanup)

## Project Structure

```
assets/
├── src/main/kotlin/io/github/salomax/neotool/assets/
│   ├── Application.kt              # Main entry point
│   ├── domain/                     # Domain objects (DDD)
│   │   ├── Asset.kt
│   │   ├── AssetStatus.kt
│   │   └── AssetResourceType.kt
│   ├── entity/                     # JPA entities
│   │   └── AssetEntity.kt
│   ├── repository/                 # Data access
│   │   └── AssetRepository.kt
│   ├── service/                    # Business logic
│   │   ├── AssetService.kt
│   │   ├── StorageService.kt
│   │   └── ValidationService.kt
│   ├── storage/                    # S3/R2 client
│   │   ├── S3StorageClient.kt
│   │   └── StorageConfig.kt
│   ├── graphql/                    # GraphQL API
│   │   ├── AssetGraphQLFactory.kt
│   │   ├── AssetWiringFactory.kt
│   │   └── resolvers/
│   │       ├── AssetQueryResolver.kt
│   │       └── AssetMutationResolver.kt
│   ├── job/                        # Background jobs
│   │   ├── UploadConfirmationJob.kt
│   │   ├── CleanupPendingJob.kt
│   │   └── PurgeDeletedJob.kt
│   └── config/                     # Configuration
│       ├── AssetConfig.kt
│       └── StorageProperties.kt
└── src/main/resources/
    ├── application.yml             # Configuration
    ├── db/migration/               # Flyway migrations
    │   └── V1_1__create_assets_table.sql
    └── graphql/                    # GraphQL schemas
        └── assets.graphqls
```

## Testing

```bash
# Unit tests
./gradlew :assets:test

# Integration tests (requires MinIO running)
./gradlew :assets:integrationTest

# All tests
./gradlew :assets:check
```

## API Documentation

### GraphQL Mutations

#### createAssetUpload
Request a pre-signed URL for uploading an asset.

```graphql
mutation {
  createAssetUpload(input: {
    mimeType: "image/jpeg"
    expectedSize: 2048000
    resourceType: PROFILE_IMAGE
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
```

#### confirmAssetUpload
Confirm an upload has completed (optional if background polling enabled).

```graphql
mutation {
  confirmAssetUpload(id: "asset-id", size: 2048000, checksum: "sha256-hash") {
    asset {
      status
      publicUrl
    }
  }
}
```

#### deleteAsset
Delete an asset (hard delete).

```graphql
mutation {
  deleteAsset(id: "asset-id") {
    success
    deletedAt
  }
}
```

### GraphQL Queries

#### asset
Get asset metadata by ID.

```graphql
query {
  asset(id: "asset-id") {
    id
    status
    mimeType
    sizeBytes
    publicUrl
    createdAt
  }
}
```

#### assets
List assets with filters (admin/internal use).

```graphql
query {
  assets(
    filter: {
      ownerId: "user-123"
      status: READY
    }
    limit: 10
  ) {
    edges {
      node {
        id
        publicUrl
      }
    }
  }
}
```

## References

- [Asset Service Specification](../../../docs/03-features/assets/asset-service/README.md)
- [ADR-0007: Asset Service with Cloudflare R2](../../../docs/09-adr/0007-asset-service-cloudflare-r2.md)
- [Technical Decisions](../../../docs/03-features/assets/asset-service/DECISIONS.md)

## Development Workflow

1. **Database Migration**: Create/modify `db/migration/*.sql` files
2. **Domain Objects**: Update pure Kotlin domain models in `domain/`
3. **JPA Entities**: Map domain to database in `entity/`
4. **Repository**: Add data access methods in `repository/`
5. **Service Layer**: Implement business logic in `service/`
6. **GraphQL Schema**: Define API in `graphql/*.graphqls`
7. **Resolvers**: Implement GraphQL resolvers in `graphql/resolvers/`
8. **Tests**: Write unit and integration tests
9. **Validation**: Run `./gradlew :assets:check`

## Troubleshooting

### MinIO Connection Issues

```bash
# Check MinIO is running
docker ps | grep minio

# Check MinIO console
open http://localhost:9001
# Login: minioadmin / minioadmin

# Create bucket if not exists
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/neotool-assets
```

### Database Migration Issues

```bash
# Check Flyway status
./gradlew :assets:flywayInfo

# Repair failed migration
./gradlew :assets:flywayRepair

# Baseline existing database
./gradlew :assets:flywayBaseline
```

### GraphQL Schema Issues

```bash
# Regenerate GraphQL schema
./gradlew :assets:generateGraphQL

# View GraphiQL
open http://localhost:8083/graphiql
```

## Contributing

Follow the [Neotool Development Workflow](../../../docs/06-workflows/feature-development.md).

## License

See root LICENSE file.

## Answers to Your Questions

### 1. Magic Bytes Validation
**Status:** Not implemented  
**Implementation approach:**
- Validate during the confirmation phase (after upload), not during issuance
- Download the first 512 bytes from R2/MinIO during `confirmAssetUpload`
- Compare magic bytes against the declared MIME type
- Set status to `FAILED` with `error_reason = "MIME type mismatch"` if mismatch

**Reference:** DECISIONS.md § "Magic Bytes Validation: On Upload Issuance vs. Confirmation"

### 2. Integration Tests with MinIO
**Status:** Testcontainers is configured, but no MinIO-specific container found

**Current state:**
- `build.gradle.kts` includes `testcontainers` dependencies
- No MinIO-specific testcontainer module found in the codebase

**Solution:** Testcontainers doesn't have a dedicated MinIO module, but you can use a generic container:

```kotlin
// Example approach (not implemented yet)
val minioContainer = GenericContainer("minio/minio:latest")
    .withCommand("server", "/data")
    .withExposedPorts(9000, 9001)
    .withEnv("MINIO_ROOT_USER", "minioadmin")
    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
```

Alternatively, use the S3-compatible LocalStack container which has better testcontainers support.

### 3. Standardized GraphQL Error Codes
**Status:** Partially defined in the spec and partially implemented

**Found in codebase:**
- `GraphQLPayload` in `common` module defines:
  - `VALIDATION_ERROR`
  - `INVALID_INPUT`
  - `NOT_FOUND`
  - `INTERNAL_ERROR`
- Asset service has `AssetGraphQLExceptionHandler` with:
  - `VALIDATION_ERROR`
  - `STATE_ERROR`

**Spec requirements (from README.md):**
- `VALIDATION_ERROR` (400)
- `UNAUTHORIZED` (401)
- `FORBIDDEN` (403)
- `NOT_FOUND` (404)
- `RATE_LIMIT_EXCEEDED` (429)
- `STORAGE_UNAVAILABLE` (502/503)

**Gap:** Missing `RATE_LIMIT_EXCEEDED` and `STORAGE_UNAVAILABLE` error codes. The asset service should align with the common `GraphQLPayload` pattern and add the missing codes.

---

## Proposed README Updates

Add a TODO section to the README with the tasks you listed. Here's the section to add:

```markdown:service/kotlin/assets/README.md
<code_block_to_apply_changes_from>
## TODO

### High Priority (MVP)
- [ ] **Magic Bytes Validation** - Validate file magic bytes match declared MIME type during confirmation phase
- [ ] **Integration Tests with MinIO** - Add comprehensive integration tests using Testcontainers with MinIO/S3-compatible storage
- [ ] **Background Jobs** - Implement scheduled jobs:
  - [ ] `UploadConfirmationJob` - Background polling for PENDING uploads (every 1min)
  - [ ] `CleanupPendingJob` - Mark stale PENDING uploads as FAILED (every 1h)
  - [ ] `PurgeDeletedJob` - Hard delete DELETED metadata after retention (daily)
- [ ] **Image Dimension Validation** - Add configurable dimension validation per resourceType (min/max width/height, decompression bomb protection)

### Medium Priority
- [ ] **Circuit Breaker for R2 Client** - Implement circuit breaker pattern for R2 storage operations (prevent cascading failures)
- [ ] **Per-Namespace Quota Tracking** - Track and enforce quotas per namespace (soft limits, warnings at 80%, hard block at 100%)
- [ ] **Per-Namespace Bytes/Hour Rate Limiting** - Add per-namespace bytes/hour rate limiting with 429 responses
- [ ] **Burst Allowance in Rate Limiting** - Implement burst allowance (10 uploads within 60s) for UX-sensitive flows
- [ ] **Path-Based Quotas** - Track total size per `{namespace}/{resourceType}` path
- [ ] **Audit Logging** - Structured audit logs for all asset lifecycle events (create, confirm, delete, status transitions)
- [ ] **CORS Validation/Health Check** - Add service startup check for CORS configuration (optional health check)

### GraphQL API Enhancements
- [ ] **`assets` Query with Filters** - Implement unified `assets` query with `AssetFilter` input and `AssetsConnection` (Relay pagination)
- [ ] **`purgeAsset` Mutation** - Admin-only mutation for GDPR compliance (bypasses grace periods, immediate purge)

### Observability & Operations
- [ ] **Observability / SLO Monitoring** - Add Prometheus metrics, OpenTelemetry traces, Grafana dashboards, and alerting
- [ ] **Hard Delete Implementation** - Align delete behavior with DECISIONS.md (hard delete immediately, retain metadata with `deletedAt`)

### Future Features (Post-MVP)
- [ ] **Signed URLs** - Swap public URLs for signed/HMAC URLs without contract change
- [ ] **Malware Scanning** - Integrate Cloudflare Enterprise Built-in Malicious Uploads Detection
- [ ] **GDPR/LGPD Compliance Tooling** - Implement `exportOwnerAssets` mutation and enhanced purge workflows
- [ ] **Backup & Disaster Recovery** - Cross-region replication strategy and recovery playbook

### Out of Scope (For Now)
- **Per-ResourceType Validation Rules** - Deferred; using namespace-level validation instead
