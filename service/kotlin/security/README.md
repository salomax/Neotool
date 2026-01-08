# Security Service

Authentication and authorization service for Neotool platform. Provides OAuth 2.0, JWT-based authentication, role-based access control (RBAC), attribute-based access control (ABAC), group management, and comprehensive audit logging.

## Quick Start

### Local Development

```bash
# Start PostgreSQL via docker-compose (from root)
docker-compose up -d postgres

# Run migration
./gradlew :security:flywayMigrate

# Start service
./gradlew :security:run
```

Service available at: http://localhost:8080
GraphiQL: http://localhost:8080/graphiql
JWKS Endpoint: http://localhost:8080/.well-known/jwks.json
Metrics: http://localhost:8080/prometheus

## Configuration

All configuration is done via environment variables or [application.yml](src/main/resources/application.yml).

### JWT Settings

The service uses **RS256 (RSA-SHA256)** for JWT signing. You can provide keys in two ways:

#### Option 1: Inline Keys (Development)

Set keys directly as environment variables (PEM or base64 format):

```bash
JWT_PRIVATE_KEY=-----BEGIN PRIVATE KEY----- MIIJQwIBADA... -----END PRIVATE KEY-----
JWT_PUBLIC_KEY=-----BEGIN PUBLIC KEY----- MIICIjANBgkq... -----END PUBLIC KEY-----
```

See [.env.local](.env.local) for a complete example.

#### Option 2: File Paths (Production)

Store keys in files and reference them:

```bash
JWT_PRIVATE_KEY_PATH=/path/to/private-key.pem
JWT_PUBLIC_KEY_PATH=/path/to/public-key.pem
```

#### Option 3: HashiCorp Vault (Production)

Enable Vault integration for secure key storage:

```bash
VAULT_ENABLED=true
VAULT_ADDRESS=http://localhost:8200
VAULT_TOKEN=myroot
VAULT_SECRET_PATH=secret/jwt/keys
JWT_KEY_ID=kid-1
```

Keys will be fetched from Vault at: `{VAULT_SECRET_PATH}/{JWT_KEY_ID}/private` and `{VAULT_SECRET_PATH}/{JWT_KEY_ID}/public`

#### JWT Configuration Variables

- `JWT_ALGORITHM`: Algorithm (default: `RS256`, **only supported algorithm**)
- `JWT_KEY_ID`: Key ID for JWKS (default: `kid-1`)
- `JWT_JWKS_ENABLED`: Enable JWKS endpoint (default: `true`)
- `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS`: Access token TTL (default: `900` / 15min)
- `JWT_REFRESH_TOKEN_EXPIRATION_SECONDS`: Refresh token TTL (default: `604800` / 7 days)

### Generating RSA Key Pairs

```bash
# Generate private key (4096-bit RSA)
openssl genrsa -out private-key.pem 4096

# Extract public key
openssl rsa -in private-key.pem -pubout -out public-key.pem

# View keys (for inline environment variables)
cat private-key.pem
cat public-key.pem
```

### OAuth Settings

#### Google OAuth

1. **Create OAuth 2.0 credentials** in [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Application type: Web application
   - Authorized redirect URIs: `http://localhost:3000/auth/google/callback` (adjust for your frontend)
2. **Copy Client ID** and set environment variables:

```bash
GOOGLE_CLIENT_ID=573386613456-xxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
GOOGLE_ISSUER=https://accounts.google.com
```

See [.env.local](.env.local) for example values.

### Vault Settings

- `VAULT_ENABLED`: Enable Vault integration (default: `false`)
- `VAULT_ADDRESS`: Vault server URL (e.g., `http://localhost:8200`)
- `VAULT_TOKEN`: Authentication token (use AppRole or Kubernetes auth in production)
- `VAULT_SECRET_PATH`: Key storage path prefix (default: `secret/jwt/keys`)
- `VAULT_ENGINE_VERSION`: KV engine version (default: `2`)
- `VAULT_CONNECTION_TIMEOUT`: Connection timeout in ms (default: `5000`)
- `VAULT_READ_TIMEOUT`: Read timeout in ms (default: `5000`)

### Email Settings

- `EMAIL_FROM`: From address for emails (default: `noreply@neotool.com`)
- `FRONTEND_URL`: Frontend URL for reset links (default: `http://localhost:3000`)

**Note:** Currently uses `MockEmailService` which logs emails to console for testing.

### Database Settings

- `POSTGRES_HOST`: Database host (default: `localhost`)
- `POSTGRES_PORT`: Database port (default: `6432`)
- `POSTGRES_DB`: Database name (default: `neotool_db`)
- `POSTGRES_USER`: Database user (default: `neotool`)
- `POSTGRES_PASSWORD`: Database password (default: `neotool`)

### HikariCP Connection Pool Settings (Optional)

- `HIKARI_MAX_POOL_SIZE`: Maximum pool size (default: `20`)
- `HIKARI_MIN_IDLE`: Minimum idle connections (default: `5`)
- `HIKARI_CONNECTION_TIMEOUT`: Connection timeout in ms (default: `30000`)
- `HIKARI_IDLE_TIMEOUT`: Idle timeout in ms (default: `600000`)
- `HIKARI_MAX_LIFETIME`: Max connection lifetime in ms (default: `1800000`)
- `HIKARI_LEAK_DETECTION`: Leak detection threshold in ms (default: `60000`, `0` to disable)

## Architecture

- **Config Layer**: JWT, OAuth, Vault, Email configuration
- **Domain Layer**: Pure Kotlin domain objects (DDD)
  - `domain/rbac/` - Role-Based Access Control models
  - `domain/abac/` - Attribute-Based Access Control models
  - `domain/audit/` - Authorization audit models
- **Model Layer**: JPA entities for persistence
- **Repository Layer**: Data access with Spring Data JPA
  - Standard repositories with custom implementations
  - Specialized repositories (PrincipalRepository, RefreshTokenRepository)
- **Service Layer**: Business logic
  - `service/authentication/` - Password hashing (Argon2id), JWT generation
  - `service/authorization/` - RBAC/ABAC evaluation, permission checking
  - `service/jwt/` - Token issuance and validation
  - `service/oauth/` - OAuth provider integration (Google)
  - `service/email/` - Password reset emails
  - `service/management/` - User, role, group, permission CRUD
  - `service/rate/` - Rate limiting for brute-force protection
- **GraphQL Layer**: API resolvers and mappers with DataLoaders for N+1 query prevention
- **HTTP Layer**: REST controllers (JWKS, OAuth token, service registration), authorization interceptors

## Project Structure

```
security/
├── src/main/kotlin/io/github/salomax/neotool/security/
│   ├── Application.kt              # Main entry point
│   ├── config/                     # Configuration
│   │   ├── JwtConfig.kt
│   │   ├── OAuthConfig.kt
│   │   ├── VaultConfig.kt
│   │   └── EmailConfig.kt
│   ├── domain/                     # Domain objects (DDD)
│   │   ├── rbac/                   # Role-Based Access Control
│   │   │   ├── User.kt
│   │   │   ├── Role.kt
│   │   │   ├── Permission.kt
│   │   │   ├── Group.kt
│   │   │   └── GroupRoleAssignment.kt
│   │   ├── abac/                   # Attribute-Based Access Control
│   │   │   ├── AbacPolicy.kt
│   │   │   └── AbacPolicyVersion.kt
│   │   └── audit/
│   │       └── AuthorizationAuditLog.kt
│   ├── model/                      # JPA entities
│   │   ├── UserEntity.kt
│   │   ├── RoleEntity.kt
│   │   ├── PermissionEntity.kt
│   │   ├── GroupEntity.kt
│   │   ├── AbacPolicyEntity.kt
│   │   └── ...
│   ├── repo/                       # Data access
│   │   ├── UserRepository.kt
│   │   ├── RoleRepository.kt
│   │   ├── PermissionRepository.kt
│   │   ├── GroupRepository.kt
│   │   ├── PrincipalRepository.kt
│   │   ├── RefreshTokenRepository.kt
│   │   └── ...
│   ├── service/                    # Business logic
│   │   ├── authentication/
│   │   │   └── AuthenticationService.kt
│   │   ├── authorization/
│   │   │   ├── AuthorizationService.kt
│   │   │   ├── PermissionService.kt
│   │   │   ├── AbacEvaluationService.kt
│   │   │   └── AuthorizationAuditService.kt
│   │   ├── jwt/
│   │   │   ├── JwtTokenIssuer.kt
│   │   │   └── RefreshTokenService.kt
│   │   ├── oauth/
│   │   │   ├── OAuthService.kt
│   │   │   ├── GoogleOAuthProvider.kt
│   │   │   └── OAuthProviderRegistry.kt
│   │   ├── email/
│   │   │   └── EmailService.kt
│   │   ├── management/
│   │   │   ├── UserManagementService.kt
│   │   │   ├── RoleManagementService.kt
│   │   │   ├── GroupManagementService.kt
│   │   │   ├── PermissionManagementService.kt
│   │   │   └── ServicePrincipalService.kt
│   │   └── rate/
│   │       └── RateLimitService.kt
│   ├── graphql/                    # GraphQL API
│   │   ├── SecurityGraphQLFactory.kt
│   │   ├── SecuritySchemaRegistryFactory.kt
│   │   ├── resolver/
│   │   │   ├── AuthorizationResolver.kt
│   │   │   ├── UserManagementResolver.kt
│   │   │   ├── RoleManagementResolver.kt
│   │   │   ├── GroupManagementResolver.kt
│   │   │   └── PermissionManagementResolver.kt
│   │   ├── mapper/
│   │   │   ├── SecurityGraphQLMapper.kt
│   │   │   ├── AuthorizationMapper.kt
│   │   │   └── ...
│   │   ├── dto/                    # GraphQL DTOs
│   │   └── dataloader/             # Batch loading for N+1 prevention
│   │       ├── UserGroupsDataLoader.kt
│   │       ├── GroupMembersDataLoader.kt
│   │       ├── RolePermissionsDataLoader.kt
│   │       └── ...
│   ├── http/                       # HTTP layer
│   │   ├── JwksController.kt
│   │   ├── OAuthTokenController.kt
│   │   ├── ServiceRegistrationController.kt
│   │   ├── AuthorizationInterceptor.kt
│   │   ├── RequiresAuthorization.kt
│   │   └── exception/
│   │       ├── AuthenticationRequiredExceptionHandler.kt
│   │       └── AuthorizationDeniedExceptionHandler.kt
│   ├── key/                        # Key management
│   │   └── SecurityKeyManagerFactory.kt
│   └── vault/                      # Vault integration
│       └── VaultKeyManager.kt
└── src/main/resources/
    ├── application.yml             # Configuration
    ├── db/migration/               # Flyway migrations
    │   ├── V0_0__create_uuidv7_function.sql
    │   ├── V0_1__init.sql
    │   ├── V0_2__add_password_support.sql
    │   ├── V0_3__add_password_reset_flow.sql
    │   ├── V0_4__create_group_tables.sql
    │   ├── V0_5__create_abac_tables.sql
    │   ├── V0_6__add_authorization_audit_log.sql
    │   ├── V0_7__add_indexes_for_performance.sql
    │   ├── V0_8__add_version_and_timestamp_tracking.sql
    │   ├── V0_9__add_user_status_tracking.sql
    │   ├── V0_10__setup_default_administrators_group.sql
    │   ├── V0_11__add_principals_table.sql
    │   ├── V0_12__add_assets_permissions.sql
    │   ├── V0_13__add_refresh_tokens_table.sql
    │   └── V0_15__add_service_credentials_table.sql
    ├── graphql/                    # GraphQL schemas
    │   └── schema.graphqls
    └── email-templates/            # Email templates
        ├── password-reset-en.html
        └── password-reset-pt.html
```

## Testing

```bash
# Unit tests
./gradlew :security:testUnit

# Integration tests (requires Docker for Testcontainers)
./gradlew :security:testIntegration

# All tests
./gradlew :security:test
```

## API Documentation

### GraphQL Mutations

#### signUp
Create a new user account with email/password.

```graphql
mutation {
  signUp(input: {
    email: "user@example.com"
    password: "SecureP@ssw0rd"
    displayName: "John Doe"
  }) {
    user {
      id
      email
      displayName
    }
    accessToken
    refreshToken
  }
}
```

#### signIn
Authenticate with email/password.

```graphql
mutation {
  signIn(input: {
    email: "user@example.com"
    password: "SecureP@ssw0rd"
  }) {
    user {
      id
      email
      displayName
      roles {
        id
        name
      }
      groups {
        id
        name
      }
    }
    accessToken
    refreshToken
  }
}
```

#### signInWithOAuth
Authenticate with OAuth provider (Google).

```graphql
mutation {
  signInWithOAuth(input: {
    provider: GOOGLE
    idToken: "eyJhbGciOiJSUzI1NiIsImtpZCI..."
  }) {
    user {
      id
      email
      displayName
    }
    accessToken
    refreshToken
  }
}
```

#### refreshAccessToken
Refresh access token using refresh token.

```graphql
mutation {
  refreshAccessToken(refreshToken: "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...") {
    accessToken
    refreshToken
  }
}
```

#### requestPasswordReset
Request password reset email.

```graphql
mutation {
  requestPasswordReset(email: "user@example.com") {
    success
    message
  }
}
```

#### resetPassword
Reset password using token from email.

```graphql
mutation {
  resetPassword(input: {
    token: "reset-token-from-email"
    newPassword: "NewSecureP@ssw0rd"
  }) {
    success
    message
  }
}
```

#### createGroup
Create a new group.

```graphql
mutation {
  createGroup(input: {
    name: "Engineering Team"
    description: "Backend engineering team"
  }) {
    group {
      id
      name
      description
    }
  }
}
```

#### assignGroupToUser
Add user to group.

```graphql
mutation {
  assignGroupToUser(input: {
    userId: "user-id"
    groupId: "group-id"
    membershipType: MEMBER
  }) {
    success
  }
}
```

#### assignRoleToGroup
Assign role to group.

```graphql
mutation {
  assignRoleToGroup(input: {
    groupId: "group-id"
    roleId: "role-id"
  }) {
    success
  }
}
```

#### createRole
Create a new role.

```graphql
mutation {
  createRole(input: {
    name: "asset-manager"
    description: "Can manage assets"
  }) {
    role {
      id
      name
    }
  }
}
```

#### assignPermissionToRole
Assign permission to role.

```graphql
mutation {
  assignPermissionToRole(input: {
    roleId: "role-id"
    permissionId: "permission-id"
  }) {
    success
  }
}
```

### GraphQL Queries

#### currentUser
Get currently authenticated user.

```graphql
query {
  currentUser {
    id
    email
    displayName
    avatarUrl
    enabled
    createdAt
    roles {
      id
      name
    }
    groups {
      id
      name
    }
    permissions {
      id
      name
    }
  }
}
```

#### checkPermission
Check if user has permission for resource.

```graphql
query {
  checkPermission(
    userId: "user-id"
    permission: "asset:upload"
    resourceId: "resource-id"
  ) {
    allowed
    reason
  }
}
```

#### user
Get user by ID.

```graphql
query {
  user(id: "user-id") {
    id
    email
    displayName
    roles {
      name
    }
  }
}
```

#### users
List users with pagination.

```graphql
query {
  users(
    filter: {
      enabled: true
    }
    first: 10
  ) {
    edges {
      node {
        id
        email
        displayName
      }
      cursor
    }
    pageInfo {
      hasNextPage
      endCursor
    }
    totalCount
  }
}
```

#### groups
List groups with pagination.

```graphql
query {
  groups(first: 10) {
    edges {
      node {
        id
        name
        description
        members {
          id
          displayName
        }
        roles {
          name
        }
      }
    }
  }
}
```

#### roles
List roles.

```graphql
query {
  roles {
    id
    name
    description
    permissions {
      id
      name
    }
  }
}
```

#### permissions
List permissions.

```graphql
query {
  permissions {
    id
    name
    description
  }
}
```

### REST Endpoints

#### JWKS Endpoint
Public key distribution for JWT validation.

```bash
curl http://localhost:8080/.well-known/jwks.json
```

Response:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "kid-1",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

#### OAuth Token Endpoint
Exchange OAuth code for tokens (if using authorization code flow).

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "google",
    "code": "authorization-code"
  }'
```

## Key Features

### Authentication

- **Password Hashing**: Argon2id (OWASP recommended, GPU/ASIC resistant)
- **JWT Tokens**: RS256 (RSA-SHA256) asymmetric signing
  - Access tokens: Short-lived (15min default)
  - Refresh tokens: Long-lived (7 days default)
- **OAuth 2.0**: Google ID token validation (extensible for other providers)
- **Key Management**: File-based, inline, or HashiCorp Vault integration
- **JWKS Endpoint**: Public key distribution for token validation
- **Rate Limiting**: Brute-force protection for password reset

### Authorization

- **RBAC (Role-Based Access Control)**:
  - Users → Groups → Roles → Permissions
  - Hierarchical permission model (e.g., `security:user:view`)
- **ABAC (Attribute-Based Access Control)**:
  - Policy-based evaluation with conditions
  - Versioning and audit trail
- **Hybrid Mode**: Combined RBAC + ABAC evaluation
- **Service Principals**: Service-to-service authentication with separate permission model
- **Authorization Interceptor**: Automatic enforcement via `@RequiresAuthorization` annotation

### Group Management

- User grouping with membership types (MEMBER, ADMIN, OWNER)
- Time-bound memberships (valid_from, valid_until)
- Group-level role assignments
- Default "Administrators" group pre-configured

### Audit & Compliance

- Authorization decision logging
- ABAC policy versioning
- User action tracking
- Detailed error logging

### Email Service

- Password reset email templates (HTML)
- i18n support (English, Portuguese)
- MockEmailService for development (logs to console)

### Session Management

- Refresh token rotation
- Token revocation capability
- Remember-me tokens

## Security Best Practices

### Password Security
- **Argon2id** hashing with random salts per user
- Minimum password requirements enforced at application level
- Password reset flow with token expiry and single-use tokens
- Rate limiting on password reset attempts

### Token Security
- **RS256** asymmetric signing (public key for validation, private key for signing)
- Short-lived access tokens (15min) to minimize exposure window
- Refresh token rotation to detect token theft
- JWKS endpoint for public key distribution

### OAuth Validation
- Client ID verification against configured providers
- Issuer validation (must match expected issuer)
- Token signature verification using provider's public keys

### Input Validation
- Email format validation
- Password strength requirements
- GraphQL input sanitization
- SQL injection prevention via parameterized queries

### Rate Limiting
- Brute-force protection on authentication endpoints
- Password reset attempt tracking per email

### Vault Integration
- Secure key storage with HashiCorp Vault
- Automatic key rotation support
- Token-based authentication (use AppRole or Kubernetes auth in production)

## Permission Hierarchy

Permissions follow a hierarchical naming convention: `domain:resource:action`

**Examples:**
- `security:user:view` - View users
- `security:user:create` - Create users
- `security:user:update` - Update users
- `security:user:delete` - Delete users
- `security:role:assign` - Assign roles
- `asset:upload` - Upload assets
- `asset:delete` - Delete assets

**Pre-configured Permissions** (see [V0_12__add_assets_permissions.sql](src/main/resources/db/migration/V0_12__add_assets_permissions.sql)):
- Asset management: `asset:upload`, `asset:view`, `asset:delete`, `asset:manage`

## Development Workflow

1. **Database Migration**: Create/modify `db/migration/*.sql` files
2. **Domain Objects**: Update pure Kotlin domain models in `domain/`
3. **JPA Entities**: Map domain to database in `model/`
4. **Repository**: Add data access methods in `repo/`
5. **Service Layer**: Implement business logic in `service/`
6. **GraphQL Schema**: Define API in `graphql/schema.graphqls`
7. **Resolvers**: Implement GraphQL resolvers in `graphql/resolver/`
8. **Mappers**: Add DTO mappings in `graphql/mapper/`
9. **Tests**: Write unit and integration tests
10. **Validation**: Run `./gradlew :security:test`

## Troubleshooting

### Database Migration Issues

```bash
# Check Flyway status
./gradlew :security:flywayInfo

# Repair failed migration
./gradlew :security:flywayRepair

# Baseline existing database
./gradlew :security:flywayBaseline
```

### JWT Key Issues

**Problem**: `JWT signing failed` or `Invalid JWT signature`

**Solution**:
1. Verify keys are valid PEM format
2. Ensure private key is used for signing, public key for validation
3. Check key ID matches in JWKS response
4. Validate Vault connection if using Vault integration

```bash
# Test Vault connection
curl -H "X-Vault-Token: myroot" http://localhost:8200/v1/secret/data/jwt/keys/kid-1

# Validate key format
openssl rsa -in private-key.pem -check
openssl rsa -pubin -in public-key.pem -text
```

### OAuth Issues

**Problem**: `Invalid OAuth token` or `OAuth validation failed`

**Solution**:
1. Verify `GOOGLE_CLIENT_ID` matches your OAuth app
2. Check `GOOGLE_ISSUER` is `https://accounts.google.com`
3. Ensure ID token is not expired (tokens expire after 1 hour)
4. Verify OAuth app has correct redirect URIs configured

### GraphQL Schema Issues

```bash
# View GraphiQL
open http://localhost:8080/graphiql

# Check schema introspection
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ __schema { types { name } } }"}'
```

### Connection Pool Issues

**Problem**: `Connection timeout` or `Too many connections`

**Solution**:
1. Adjust HikariCP pool settings in `application.yml`
2. Check PostgreSQL `max_connections` setting
3. Monitor connection leaks with leak detection

```bash
# Check active connections in PostgreSQL
docker exec -it postgres psql -U neotool -d neotool_db \
  -c "SELECT count(*) FROM pg_stat_activity WHERE datname='neotool_db';"
```

## References

- [Micronaut Documentation](https://docs.micronaut.io/)
- [Argon2 Password Hashing](https://github.com/password4j/password4j)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)
- [HashiCorp Vault](https://www.vaultproject.io/)
- [Google OAuth 2.0](https://developers.google.com/identity/protocols/oauth2)
- [GraphQL Relay Specification](https://relay.dev/graphql/connections.htm)

## Contributing

Follow the [Neotool Development Workflow](../../../docs/06-workflows/feature-development.md).

## License

See root LICENSE file.

## TODO

### High Priority (MVP)
- [ ] **Real Email Service** - Replace MockEmailService with SMTP/SendGrid/SES integration
- [ ] **Password Strength Validation** - Add configurable password requirements (min length, complexity, common password check)
- [ ] **Account Lockout** - Implement account lockout after N failed login attempts
- [ ] **Email Verification** - Add email verification flow for new signups
- [ ] **Session Management UI** - Add GraphQL mutations for listing/revoking active sessions
- [ ] **Multi-Factor Authentication (MFA)** - Add TOTP-based MFA support

### Medium Priority
- [ ] **OAuth Provider Expansion** - Add support for GitHub, Microsoft, Facebook OAuth
- [ ] **API Key Authentication** - Add API key support for service-to-service auth (alternative to service principals)
- [ ] **Permission Wildcards** - Support wildcard permissions (e.g., `asset:*` for all asset operations)
- [ ] **Role Hierarchy** - Add role inheritance (e.g., `admin` inherits from `user`)
- [ ] **Group Nesting** - Support nested groups (groups within groups)
- [ ] **ABAC Policy UI** - Add GraphQL mutations for managing ABAC policies
- [ ] **Audit Log Query API** - Expose authorization audit logs via GraphQL
- [ ] **Token Blacklist** - Add token revocation/blacklist for logout

### GraphQL API Enhancements
- [ ] **Batch Operations** - Add batch mutations for bulk user/role/group operations
- [ ] **Advanced Filters** - Add more filtering options to `users`, `groups`, `roles` queries
- [ ] **Permission Search** - Add search/autocomplete for permissions
- [ ] **Role Templates** - Pre-defined role templates for common use cases

### Observability & Operations
- [ ] **Structured Logging** - Add structured JSON logging for better log analysis
- [ ] **Distributed Tracing** - Add OpenTelemetry traces for request tracking
- [ ] **Grafana Dashboards** - Create Grafana dashboards for auth metrics
- [ ] **Alerting** - Set up alerts for failed logins, token validation failures, etc.
- [ ] **Health Checks** - Add detailed health checks (database, Vault, OAuth providers)

### Future Features (Post-MVP)
- [ ] **WebAuthn/Passkeys** - Add passwordless authentication support
- [ ] **SAML 2.0** - Enterprise SSO integration
- [ ] **LDAP/Active Directory** - Enterprise directory integration
- [ ] **GDPR Compliance Tooling** - Add user data export/deletion workflows
- [ ] **Rate Limiting per User** - Add per-user rate limits (not just global)
- [ ] **Session Timeout** - Add configurable session timeout with warnings
- [ ] **Security Headers** - Add security headers (CSP, HSTS, etc.) to HTTP responses

### Out of Scope (For Now)
- **HS256 Algorithm Support** - Only RS256 is supported (asymmetric keys required)
- **Custom OAuth Providers** - Currently limited to Google (extensible architecture in place)
