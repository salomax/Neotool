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
│   │   ├── ValidationService.kt
│   │   └── RateLimitService.kt
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
