# Security Feature - Comprehensive Documentation

> **Enterprise-grade authentication, authorization, and user management for NeoTool**

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Core Components](#core-components)
4. [Architecture](#architecture)
5. [Implementation Guide](#implementation-guide)
6. [Security Best Practices](#security-best-practices)
7. [Testing Strategy](#testing-strategy)
8. [Troubleshooting](#troubleshooting)
9. [Future Roadmap](#future-roadmap)
10. [Related Documentation](#related-documentation)

---

## Overview

The NeoTool Security Module provides a complete, production-ready security infrastructure that combines authentication (AuthN), authorization (AuthZ), and user management into a cohesive system designed for enterprise applications.

### Design Philosophy

- **Security by Default**: All endpoints and resources are protected unless explicitly made public
- **Zero Trust Architecture**: Every request is authenticated and authorized
- **Defense in Depth**: Multiple layers of security controls
- **Principle of Least Privilege**: Users and services get minimum necessary permissions
- **Audit Everything**: Comprehensive logging of all security-relevant events
- **Performance First**: Optimized for high-throughput, low-latency operations

### Key Capabilities

| Component | Description | Key Features |
|-----------|-------------|--------------|
| **Authentication** | Who you are | Password auth, OAuth 2.0, JWT tokens, MFA-ready |
| **Authorization** | What you can do | RBAC, ABAC, resource-level access, service-to-service |
| **User Management** | Managing identities | CRUD operations, roles, groups, enable/disable |
| **Security Infrastructure** | Supporting systems | Principals, audit logs, rate limiting, token management |

---

## Quick Start

### For Frontend Developers

```tsx
// 1. Use PermissionGate to control visibility
<PermissionGate require="security:user:create">
  <CreateUserButton />
</PermissionGate>

// 2. Check permissions programmatically
const { hasPermission } = useAuthorization();
if (hasPermission("security:user:delete")) {
  // Show delete button
}
```

### For Administrators

```graphql
# 1. Assign a role to a user
mutation {
  assignRoleToUser(userId: "user-id", roleId: "admin-role-id")
}

# 2. Enable a user
mutation {
  enableUser(userId: "user-id")
}

# 3. Disable a user
mutation {
  disableUser(userId: "user-id")
}
```

---

## Core Components

### 1. Authentication

User identity verification through multiple methods.

**[📖 Full Authentication Documentation](./authentication/README.md)**

**Capabilities:**
- Password-based authentication (Argon2id hashing)
- OAuth 2.0 (Google, extensible to others)
- JWT access and refresh tokens
- Remember Me functionality
- Password reset with rate limiting
- User registration and email verification

**Key Services:**
- `AuthenticationService` - Core authentication logic
- `JwtService` - Token generation and validation
- `OAuthProviderRegistry` - OAuth provider management
- `PasswordResetService` - Password recovery flows

### 2. Authorization

Access control determining what authenticated users can do.

**[📖 Full Authorization Documentation](./authorization/README.md)**

**Capabilities:**
- Role-Based Access Control (RBAC)
- Attribute-Based Access Control (ABAC)
- Hybrid RBAC+ABAC evaluation
- Resource-level permissions
- Service-to-service authorization
- Temporary access grants
- Deny-by-default security model

**Key Services:**
- `AuthorizationService` - Permission checks
- `AuthorizationManager` - High-level authorization interface
- `AbacEvaluationService` - Policy evaluation
- `AuthorizationAuditService` - Decision logging

### 3. User Management

Managing user accounts, roles, and groups.

**Capabilities:**
- User CRUD operations
- Role and permission management
- Group management with inheritance
- User enable/disable
- Profile updates
- GraphQL API with Relay pagination
- Batch operations for performance

**Key Services:**
- `UserManagementService` - User operations
- `RoleManagementService` - Role operations
- `GroupManagementService` - Group operations

### 4. Security Infrastructure

Supporting systems that enable the security module.

**Components:**
- **Principals**: Unified identity for users and services
- **Audit Logging**: Comprehensive security event tracking
- **Rate Limiting**: Protection against brute force attacks
- **Token Management**: Lifecycle management for tokens
- **Request Context**: Security context propagation

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Layer                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐           │
│  │  Web App   │  │ Mobile App │  │  Service   │           │
│  │  (React)   │  │  (RN/Expo) │  │ (Internal) │           │
│  └────────────┘  └────────────┘  └────────────┘           │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼ JWT Token / API Key
┌─────────────────────────────────────────────────────────────┐
│              GraphQL Gateway / REST API                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │        Authentication Middleware                     │  │
│  │  - Token validation                                  │  │
│  │  - Principal extraction                              │  │
│  │  - Request context setup                             │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼ RequestPrincipal
┌─────────────────────────────────────────────────────────────┐
│                  Security Module                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Authn Service│  │ Authz Service│  │User Mgmt     │     │
│  │              │  │              │  │Service       │     │
│  │- Password    │  │- RBAC        │  │              │     │
│  │- OAuth       │  │- ABAC        │  │- Users       │     │
│  │- JWT         │  │- Policies    │  │- Roles       │     │
│  │- Reset PW    │  │- Audit       │  │- Groups      │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼ SQL Queries
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │ Users    │ │ Roles    │ │Principals│ │  Audit   │      │
│  │ Table    │ │ Table    │ │ Table    │ │  Logs    │      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │UserRoles │ │ Perms    │ │  ABAC    │                    │
│  │ Table    │ │ Table    │ │ Policies │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

### Request Flow

```
┌─────────┐
│ Client  │
│ Request │
└────┬────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 1. Token Validation                 │
│    - Extract JWT from header        │
│    - Validate signature & expiry    │
│    - Extract user ID                │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 2. Principal Resolution             │
│    - Load user from database        │
│    - Check enabled status           │
│    - Create RequestPrincipal        │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 3. Authorization Check              │
│    a. RBAC Evaluation               │
│       - Get user roles              │
│       - Check permission            │
│    b. ABAC Evaluation (if RBAC ✓)  │
│       - Build attributes            │
│       - Evaluate policies           │
│    c. Final Decision                │
│       - Combine RBAC + ABAC         │
│       - Log audit entry             │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 4. Business Logic Execution         │
│    - Process request                │
│    - Return response                │
└─────────────────────────────────────┘
```

### Data Model

```
User ──┬──> Principal (enabled/disabled status)
       │
       ├──> UserRole ──> Role ──> RolePermission ──> Permission
       │
       └──> GroupMembership ──> Group ──> GroupRole ──> Role

Service ──> Principal (enabled/disabled status)
        └──> PrincipalPermission ──> Permission

Authorization Check ──> AuthorizationAuditLog
```

---

## Implementation Guide

### How to Authenticate a User

See: [AuthenticationService.kt](../../../service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthenticationService.kt)

```kotlin
// 1. Authenticate a user with password
val user = authenticationService.authenticate(email, password)

// 2. Generate access and refresh tokens
val accessToken = authenticationService.generateAccessToken(user)
val refreshToken = authenticationService.generateRefreshToken(user)

// 3. Validate a token
val userId = jwtService.validateToken(accessToken)
```

### How to Check Authorization

See: [AuthorizationManager.kt](../../../service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthorizationManager.kt)

```kotlin
// Check if user has permission (returns boolean)
val hasPermission = authorizationManager.check(
    principal = requestPrincipal,
    action = "security:user:view"
)

// Require permission (throws exception if denied)
authorizationManager.require(
    principal = requestPrincipal,
    action = "security:user:update",
    resourceId = userId
)
```

### How to Get User Permissions

See: [AuthorizationService.kt](../../../service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthorizationService.kt)

```kotlin
// Get all permissions for a user
val permissions = authorizationService.getUserPermissions(userId)

// Get permissions for a service principal
val servicePermissions = authorizationService.getServicePermissions(serviceId)
```

### Backend Implementation

#### 1. Add Security to Your Service

```kotlin
@Singleton
class MyService(
    private val authorizationManager: AuthorizationManager,
) {
    fun performAction(principal: RequestPrincipal, resourceId: UUID) {
        // Require permission before executing
        authorizationManager.require(
            principal = principal,
            action = "myservice:resource:update",
            resourceId = resourceId
        )

        // Business logic here
    }
}
```

#### 2. Secure GraphQL Resolvers

```kotlin
@GraphQLMutation
fun updateResource(
    @GraphQLContext principal: RequestPrincipal,
    resourceId: String,
    input: UpdateResourceInput
): Resource {
    authorizationManager.require(
        principal = principal,
        action = "myservice:resource:update",
        resourceId = UUID.fromString(resourceId)
    )

    return resourceService.update(resourceId, input)
}
```

#### 3. Secure HTTP Endpoints

```kotlin
@Controller("/api/resources")
class ResourceController(
    private val authorizationManager: AuthorizationManager,
) {
    @Get("/{id}")
    @RequiresAuthorization(permission = "myservice:resource:view")
    fun getResource(id: String): Resource {
        // Method is automatically protected by interceptor
        return resourceService.get(id)
    }
}
```

### Frontend Implementation

#### 1. Wrap Protected UI Components

```tsx
import { PermissionGate } from '@/shared/components/authorization';

function ResourceList() {
  return (
    <div>
      <PermissionGate require="myservice:resource:create">
        <CreateResourceButton />
      </PermissionGate>

      <PermissionGate require="myservice:resource:view">
        <ResourceTable />
      </PermissionGate>
    </div>
  );
}
```

#### 2. Check Permissions Programmatically

```tsx
import { useAuthorization } from '@/shared/providers/AuthorizationProvider';

function ResourceActions({ resourceId }: { resourceId: string }) {
  const { hasPermission } = useAuthorization();

  return (
    <div>
      {hasPermission("myservice:resource:update") && (
        <EditButton resourceId={resourceId} />
      )}

      {hasPermission("myservice:resource:delete") && (
        <DeleteButton resourceId={resourceId} />
      )}
    </div>
  );
}
```

### Database Schema

#### Core Tables

```sql
-- Users and authentication
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Principal (unified identity for users and services)
CREATE TABLE principals (
    id UUID PRIMARY KEY,
    principal_type VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(principal_type, external_id)
);

-- Roles and permissions
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Relationships
CREATE TABLE user_roles (
    user_id UUID REFERENCES users(id),
    role_id UUID REFERENCES roles(id),
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    PRIMARY KEY(user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id UUID REFERENCES roles(id),
    permission_id UUID REFERENCES permissions(id),
    PRIMARY KEY(role_id, permission_id)
);

-- ABAC policies
CREATE TABLE abac_policies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    effect VARCHAR(10) NOT NULL,
    condition JSONB NOT NULL,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL
);
```

---

## Security Best Practices

### 1. Password Security

✅ **DO:**
- Use Argon2id for password hashing (already implemented)
- Enforce password complexity requirements
- Implement rate limiting on login attempts
- Use secure password reset flows with time-limited tokens
- Never log or expose password hashes

❌ **DON'T:**
- Store passwords in plain text
- Use weak hashing algorithms (MD5, SHA1, BCrypt)
- Allow unlimited login attempts
- Send passwords via email
- Display password hints

### 2. Token Security

✅ **DO:**
- Use short-lived access tokens (15 minutes)
- Use longer-lived refresh tokens (7 days)
- Implement token rotation on refresh
- Store tokens securely (httpOnly cookies or secure storage)
- Invalidate tokens on logout
- Use strong JWT secrets (min 32 bytes)

❌ **DON'T:**
- Store tokens in localStorage (XSS risk)
- Use tokens without expiration
- Share tokens between users
- Expose token secrets in code
- Allow token reuse after logout

### 3. Authorization Security

✅ **DO:**
- Use deny-by-default (require explicit grants)
- Check permissions at every layer (UI, API, service)
- Log all authorization decisions
- Use resource-level permissions for sensitive data
- Implement separation of duties
- Regular permission audits

❌ **DON'T:**
- Trust client-side permission checks alone
- Grant broad wildcard permissions
- Skip authorization checks for "admin" users
- Assume authentication implies authorization
- Hard-code permissions in code

### 4. Service-to-Service Security

✅ **DO:**
- Use separate service principals
- Implement mTLS for service communication
- Scope service permissions tightly
- Rotate service credentials regularly
- Monitor service access patterns

❌ **DON'T:**
- Share user credentials for services
- Use overly permissive service accounts
- Skip authentication for internal services
- Hard-code service credentials

### 5. Audit and Monitoring

✅ **DO:**
- Log all authentication attempts
- Log all authorization decisions
- Monitor for suspicious patterns
- Alert on repeated failures
- Retain logs for compliance periods
- Protect audit logs from tampering

❌ **DON'T:**
- Log sensitive data (passwords, tokens)
- Ignore failed authentication attempts
- Delete audit logs prematurely
- Make audit logs writable by application

---

## Testing Strategy

### Unit Tests

```bash
# Run all security unit tests
./gradlew :security:test

# Run with coverage
./gradlew :security:test :security:koverXmlReport

# Coverage requirements:
# - Overall: 90%
# - Security services: 100%
```

### Integration Tests

```bash
# Run integration tests (requires PostgreSQL)
./gradlew :security:testIntegration

# Test real authentication flows
# Test authorization with database
# Test token generation and validation
```

### E2E Tests

```bash
# Run frontend E2E tests
cd web && npm run test:e2e -- security

# Test login/logout flows
# Test permission-based UI rendering
# Test authorization failures
```

### Manual Testing

See individual component documentation for manual testing procedures:
- [Authentication Testing](./authentication/README.md#testing)
- [Authorization Testing](./authorization/README.md#testing-guide)

---

## Troubleshooting

### Common Issues

#### Issue: "User cannot login"

**Symptoms:** Login fails with valid credentials

**Possible Causes:**
1. User is disabled → Check `principals` table
2. Password hash mismatch → Verify Argon2id configuration
3. Token generation fails → Check JWT configuration
4. Database connection issues → Verify database health

**Solution:**
```sql
-- Check user status
SELECT u.id, u.email, p.enabled
FROM users u
LEFT JOIN principals p ON p.external_id = u.id::text AND p.principal_type = 'USER'
WHERE u.email = 'user@example.com';

-- Enable user if disabled
UPDATE principals
SET enabled = true
WHERE principal_type = 'USER' AND external_id = 'user-id';
```

#### Issue: "Authorization denied despite having role"

**Symptoms:** User has role but permission check fails

**Possible Causes:**
1. Role doesn't have the permission
2. Role binding expired
3. ABAC policy denying access
4. Permission name mismatch

**Solution:**
```graphql
# Check user's permissions
query {
  getUserPermissions(userId: "user-id") {
    id
    name
  }
}

# Check user's roles
query {
  getUserRoles(userId: "user-id") {
    id
    name
  }
}
```

#### Issue: "Token expired too quickly"

**Symptoms:** Access tokens expire faster than expected

**Possible Causes:**
1. JWT configuration incorrect
2. Clock skew between services
3. Token not refreshed properly

**Solution:**
```kotlin
// Check JWT configuration
@ConfigurationProperties("jwt")
class JwtConfig {
    var accessTokenExpirationSeconds: Long = 900  // 15 minutes
    var refreshTokenExpirationSeconds: Long = 604800  // 7 days
}
```

### Debug Mode

Enable debug logging for security components:

```yaml
# application.yml
logger:
  levels:
    io.github.salomax.neotool.security: DEBUG
```

---

## Future Roadmap

### TODO

- [ ] Resource-Level ACLs
  - Per-resource access control lists
  - Resource sharing between users
  - Granular resource permissions
  - Share expiration and revocation

- [ ] Multi-Factor Authentication (MFA)
  - TOTP (Google Authenticator, Authy)
  - SMS-based OTP
  - Backup codes
  - MFA enrollment flows

- [ ] Session Management
  - Active session tracking
  - Remote session termination
  - Concurrent session limits
  - Session activity monitoring

- [ ] Enhanced Password Security
  - Passwordless authentication (WebAuthn)
  - Password breach detection
  - Password history enforcement

- [ ] ABAC Policy Management UI
  - Create and edit policies through UI
  - Policy testing/simulation
  - Policy versioning

- [ ] Performance Optimizations
  - Redis-based permission caching
  - Permission pre-computation
  - Batch permission checks API
  - Query optimization

- [ ] Authorization Analytics
  - Permission usage analytics
  - Access pattern analysis
  - Authorization insights dashboard

- [ ] External Identity Integration
  - SAML support
  - LDAP integration
  - Active Directory integration

---

## Related Documentation

### Feature Documentation
- [Authentication](./authentication/README.md) - User authentication flows and methods
- [Authorization](./authorization/README.md) - RBAC, ABAC, and access control
- [Feature Files](./authentication/) - Gherkin specifications
- [Authorization Feature Files](./authorization/) - Gherkin specifications

### Architecture Documentation
- [Interservice Security ADR](../../09-adr/0008-interservice-security-migration-plan.md)
- [Security Service Architecture](../../08-service/security/)
- [Frontend Security Patterns](../../04-patterns/frontend-patterns/)

### API Documentation
- GraphQL Schema: `contracts/graphql/security/`
- OpenAPI Spec: `contracts/openapi/security/`

### Code Documentation
- Backend: `service/kotlin/security/src/main/`
- Frontend: `web/src/shared/components/authorization/`
- Tests: `service/kotlin/security/src/test/`

---

## Contributing

When contributing to the security module:

1. **Security First**: All changes must maintain or improve security
2. **Test Coverage**: Maintain 100% coverage for security services
3. **Audit Logging**: All security events must be logged
4. **Documentation**: Update docs with code changes
5. **Code Review**: Security changes require additional review
6. **Backward Compatibility**: Don't break existing auth flows

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable names
- Add comprehensive comments for complex logic
- Write self-documenting code
- Keep functions small and focused

### Testing Requirements

- Unit tests for all new code
- Integration tests for flows
- E2E tests for user-facing features
- Security tests for vulnerabilities
- Performance tests for hot paths

---

## Support

For issues, questions, or feature requests, please open an issue on the GitHub project: https://github.com/salomax/neotool


