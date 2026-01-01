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

### JWT Key and Token Flows

#### 1. Key Creation Flow

The security service automatically generates RSA key pairs on first startup. This flow ensures only one pod generates keys even when multiple pods start simultaneously.

```mermaid
flowchart TD
    A[Application Startup] --> B{JwtKeyInitializer.onStartup}
    B --> C{autoGenerateKeys enabled?}
    C -->|No| Z[Skip - Keys Pre-provisioned]
    C -->|Yes| D[Get KeyManager from Factory]
    D --> E[Check if Keys Exist]
    E -->|Keys Found| Z
    E -->|Keys Missing| F{Vault Enabled?}
    F -->|Yes| G[Acquire Vault Lock]
    F -->|No| H[Acquire File Lock]
    G --> I{Lock Acquired?}
    I -->|No| J[Retry with Delay]
    J --> I
    I -->|Yes| K[Double-Check Keys Exist]
    H --> K
    K -->|Keys Found| L[Release Lock & Skip]
    K -->|Keys Missing| M[Generate RSA Key Pair]
    M --> N[Store Keys via WritableKeyManager]
    N --> O{Storage Success?}
    O -->|Yes| P[Release Lock]
    O -->|No| Q[Log Error]
    P --> R[Ready to Issue Tokens]
    L --> R
    Q --> R
    
    style A fill:#e1f5ff
    style M fill:#fff4e1
    style N fill:#fff4e1
    style R fill:#e8f5e9
    style Q fill:#ffebee
```

**Step-by-Step Process:**

1. **Application Startup Event** (`JwtKeyInitializer.onStartup()`)
   - **Class**: `JwtKeyInitializer`
   - **Method**: `onStartup(StartupEvent)`
   - **Rule**: Runs automatically when Micronaut application starts
   - **Why**: Ensures keys are available before any token operations

2. **Check Auto-Generation Configuration** (`jwtConfig.autoGenerateKeys`)
   - **Class**: `JwtConfig`
   - **Property**: `autoGenerateKeys: Boolean`
   - **Rule**: If `false`, skip generation (keys must be provisioned via Vault or file system)
   - **Why**: Allows production environments to disable auto-generation for security compliance

3. **Get Key Manager** (`keyManagerFactory.getKeyManager()`)
   - **Class**: `KeyManagerFactory`
   - **Method**: `getKeyManager(): KeyManager`
   - **Rule**: Returns `VaultKeyManager` if Vault enabled, else `FileKeyManager`
   - **Why**: Abstraction allows different storage backends without code changes

4. **Check Keys Exist** (`keyManager.getPrivateKey(keyId)`)
   - **Class**: `KeyManager` interface
   - **Method**: `getPrivateKey(keyId: String): PrivateKey?`
   - **Rule**: Returns `null` if keys don't exist
   - **Why**: Idempotent check - safe to run on every startup

5. **Acquire Distributed Lock** (`vaultClient.acquireLock()` or `fileLock.withLock()`)
   - **Class**: `VaultClient` or `ReentrantLock`
   - **Method**: `acquireLock(lockPath, ttlSeconds)` or `withLock { }`
   - **Rule**: Prevents race conditions when multiple pods start simultaneously
   - **Why**: Ensures only one pod generates keys, others wait and verify

6. **Double-Check Keys After Lock** (`keyManager.getPrivateKey(keyId)`)
   - **Class**: `KeyManager`
   - **Rule**: Another pod may have created keys while waiting for lock
   - **Why**: Prevents duplicate key generation (double-checked locking pattern)

7. **Generate RSA Key Pair** (`JwtKeyGenerator.generateRsaKeyPair()`)
   - **Class**: `JwtKeyGenerator`
   - **Method**: `generateRsaKeyPair(keySize: Int): KeyPair`
   - **Rule**: Uses `KeyPairGenerator` with `SecureRandom`, minimum 2048 bits
   - **Why**: Cryptographically secure key generation for signing tokens

8. **Store Key Pair** (`writableKeyManager.storeKeyPair()`)
   - **Class**: `WritableKeyManager` interface
   - **Method**: `storeKeyPair(keyId, privateKey, publicKey): Boolean`
   - **Rule**: Stores both keys atomically in Vault or files
   - **Why**: Ensures keys are stored together, prevents partial state

9. **Release Lock** (`vaultClient.releaseLock()` or automatic)
   - **Class**: `VaultClient` or `ReentrantLock`
   - **Rule**: Always release in `finally` block, even on errors
   - **Why**: Prevents deadlocks if pod crashes during generation

**Key Classes:**
- `JwtKeyInitializer` - Orchestrates key generation on startup
- `JwtKeyGenerator` - Generates RSA key pairs
- `KeyManagerFactory` - Selects appropriate key manager
- `VaultKeyManager` / `FileKeyManager` - Store keys in different backends
- `VaultClient` - Provides distributed locking via Vault

#### 2. Token Issuance Flow

When a user authenticates (password or OAuth), the security service issues a JWT token signed with the private key.

```mermaid
flowchart TD
    A[User Authentication] --> B{Authentication Method}
    B -->|Password| C[AuthenticationService.authenticate]
    B -->|OAuth| D[OAuthProvider.validateAndExtractClaims]
    C --> E{Valid Credentials?}
    D --> F{Valid Token?}
    E -->|No| G[Return null]
    F -->|No| G
    E -->|Yes| H[Load User from DB]
    F -->|Yes| I[Create/Find User by Email]
    H --> J[Check Principal Enabled]
    I --> J
    J -->|Disabled| G
    J -->|Enabled| K[Build AuthContext]
    K --> L[Get User Permissions]
    L --> M[JwtTokenIssuer.generateAccessToken]
    M --> N[Get Signing Key from KeyManager]
    N --> O{Algorithm Type?}
    O -->|RS256| P[Get Private Key]
    O -->|HS256| Q[Get Secret Key]
    O -->|AUTO| R{Private Key Available?}
    R -->|Yes| P
    R -->|No| Q
    P --> S[Build JWT Claims]
    Q --> S
    S --> T[Add Key ID to Header]
    T --> U[Sign Token with Key]
    U --> V[Return Signed JWT Token]
    
    style A fill:#e1f5ff
    style M fill:#fff4e1
    style S fill:#fff4e1
    style U fill:#fff4e1
    style V fill:#e8f5e9
    style G fill:#ffebee
```

**Step-by-Step Process:**

1. **User Authentication** (`AuthenticationService.authenticate()` or OAuth)
   - **Class**: `AuthenticationService`
   - **Method**: `authenticate(email, password): UserEntity?`
   - **Rule**: Validates credentials, checks principal enabled status
   - **Why**: Ensures only authenticated, enabled users receive tokens

2. **Build Authentication Context** (`AuthContextFactory.build()`)
   - **Class**: `AuthContextFactory`
   - **Method**: `build(user: UserEntity): AuthContext`
   - **Rule**: Loads user roles and permissions from database
   - **Why**: Normalizes authentication data regardless of auth method (password/OAuth)

3. **Generate Access Token** (`JwtTokenIssuer.generateAccessToken()`)
   - **Class**: `JwtTokenIssuer`
   - **Method**: `generateAccessToken(authContext: AuthContext): String`
   - **Rule**: Only security module can issue tokens
   - **Why**: Centralizes token issuance, ensures consistent token format

4. **Build JWT Claims** (`Jwts.builder()`)
   - **Class**: `JwtTokenIssuer`
   - **Method**: `generateAccessToken(userId, email, permissions)`
   - **Rule**: Includes standard claims: `sub`, `iat`, `exp`, `iss`, `type`, `permissions`
   - **Why**: Standard JWT claims enable validation and authorization

5. **Get Signing Key** (`keyManager.getPrivateKey()` or `getSecretKey()`)
   - **Class**: `KeyManager`
   - **Method**: `getPrivateKey(keyId)` or `getSecret(keyId)`
   - **Rule**: Uses configured `keyId` or default
   - **Why**: Supports key rotation by allowing multiple active keys

6. **Determine Algorithm** (`getAlgorithm()`)
   - **Class**: `JwtTokenIssuer`
   - **Method**: `getAlgorithm(): JwtAlgorithm`
   - **Rule**: Prefers RS256 if private key available, falls back to HS256
   - **Why**: RS256 is more secure (asymmetric), HS256 is simpler (symmetric)

7. **Add Key ID to Header** (`builder.header().add("kid", keyId)`)
   - **Class**: `JwtTokenIssuer`
   - **Method**: `signBuilder()`
   - **Rule**: Includes `kid` in JWT header if configured
   - **Why**: Enables validators to select correct public key for verification

8. **Sign Token** (`builder.signWith(privateKey/secretKey)`)
   - **Class**: `JwtTokenIssuer`
   - **Method**: `signBuilder()`
   - **Rule**: Uses RS256 with private key or HS256 with secret
   - **Why**: Cryptographic signature proves token authenticity and prevents tampering

9. **Compact Token** (`builder.compact()`)
   - **Class**: `io.jsonwebtoken.Jwts`
   - **Rule**: Encodes JWT as base64url string
   - **Why**: Standard JWT format for transmission in HTTP headers

**Key Classes:**
- `AuthenticationService` - Validates user credentials
- `JwtTokenIssuer` - Signs and issues JWT tokens
- `KeyManager` - Provides signing keys
- `AuthContextFactory` - Builds normalized auth context
- `JwtKeyGenerator` - Generates key pairs (used during key creation)

#### 3. Token Validation Flow (Multi-Issuer)

When a request arrives, the system validates the JWT token. It supports tokens from the internal security service and external IdPs (like Google OAuth).

```mermaid
flowchart TD
    A[Incoming Request] --> B[Extract JWT from Header]
    B --> C[JwtPrincipalDecoder.fromToken]
    C --> D[Parse Token Header]
    D --> E[Extract Issuer Claim]
    E --> F{Issuer Type?}
    F -->|neotool-security-service| G[JwtTokenValidator.validateToken]
    F -->|accounts.google.com| H[GoogleOAuthProvider.validateAndExtractClaims]
    F -->|Unknown| I[Try Internal Validator]
    I --> G
    G --> J[Extract Key ID from Header]
    J --> K[Get Public Key from KeyManager]
    K --> L{Key Found?}
    L -->|No| M[Try Secret Key]
    L -->|Yes| N[Verify RS256 Signature]
    M --> O{Secret Found?}
    O -->|No| P[Validation Failed]
    O -->|Yes| Q[Verify HS256 Signature]
    N --> R{Signature Valid?}
    Q --> R
    R -->|No| P
    R -->|Yes| S[Check Expiration]
    S --> T{Token Expired?}
    T -->|Yes| P
    T -->|No| U[Extract Claims]
    H --> V[Parse Google ID Token]
    V --> W[Verify Audience]
    W --> X{Audience Valid?}
    X -->|No| P
    X -->|Yes| Y[Verify Issuer]
    Y --> Z{Issuer Valid?}
    Z -->|No| P
    Z -->|Yes| AA[Check Expiration]
    AA --> AB{Token Expired?}
    AB -->|Yes| P
    AB -->|No| AC[Extract User Claims]
    U --> AD{Token Type?}
    AC --> AE[Create/Find User]
    AE --> AF[Build AuthContext]
    AF --> AG[Issue Internal Token]
    AG --> AD
    AD -->|service| AH[Extract Service ID]
    AD -->|access| AI[Extract User ID]
    AH --> AJ[Build RequestPrincipal]
    AI --> AJ
    AJ --> AK[Return RequestPrincipal]
    P --> AL[Throw AuthenticationRequiredException]
    
    style A fill:#e1f5ff
    style G fill:#fff4e1
    style H fill:#fff4e1
    style N fill:#fff4e1
    style Q fill:#fff4e1
    style AK fill:#e8f5e9
    style P fill:#ffebee
    style AL fill:#ffebee
```

**Step-by-Step Process:**

1. **Extract Token from Request** (`JwtPrincipalDecoder.fromToken()`)
   - **Class**: `JwtPrincipalDecoder`
   - **Method**: `fromToken(token: String): RequestPrincipal`
   - **Rule**: Token extracted from `Authorization: Bearer <token>` header
   - **Why**: Standard HTTP authentication header format

2. **Parse Token Header** (`token.split(".")`)
   - **Class**: `JwtTokenValidator`
   - **Method**: `validateToken(token: String)`
   - **Rule**: JWT has 3 parts: header.payload.signature
   - **Why**: Extract `kid` (key ID) and `iss` (issuer) from header

3. **Identify Issuer** (`claims.issuer` or `payload.issuer`)
   - **Class**: `JwtTokenValidator` or `GoogleOAuthProvider`
   - **Rule**: 
     - `"neotool-security-service"` → Internal token
     - `"accounts.google.com"` → Google OAuth token
     - Unknown → Try internal validator first
   - **Why**: Routes to correct validator based on issuer

4. **Internal Token Validation** (`JwtTokenValidator.validateToken()`)
   - **Class**: `JwtTokenValidator`
   - **Method**: `validateToken(token: String): Claims?`
   - **Rule**: Tries RS256 first, falls back to HS256 if AUTO mode
   - **Why**: Supports both algorithms, prefers more secure RS256

5. **Get Verification Key** (`keyManager.getPublicKey(keyId)` or `getSecret(keyId)`)
   - **Class**: `KeyManager`
   - **Method**: `getPublicKey(keyId)` for RS256, `getSecret(keyId)` for HS256
   - **Rule**: Uses `kid` from header, or default key if not present
   - **Why**: Supports key rotation by selecting correct key

6. **Verify Signature** (`Jwts.parser().verifyWith(key).parseSignedClaims()`)
   - **Class**: `JwtTokenValidator`
   - **Method**: `validateToken()`
   - **Rule**: Cryptographic verification of signature
   - **Why**: Ensures token wasn't tampered with and was signed by trusted issuer

7. **Check Expiration** (`claims.expiration`)
   - **Class**: `JwtTokenValidator`
   - **Rule**: Token must not be expired (`exp > now`)
   - **Why**: Prevents use of stale tokens, enforces token lifetime

8. **External Token Validation** (`GoogleOAuthProvider.validateAndExtractClaims()`)
   - **Class**: `GoogleOAuthProvider`
   - **Method**: `validateAndExtractClaims(idToken: String): OAuthUserClaims?`
   - **Rule**: Validates Google ID token format, audience, issuer, expiration
   - **Why**: External IdP tokens need different validation than internal tokens

9. **Verify Audience** (`tokenAudienceList.contains(clientId)`)
   - **Class**: `GoogleOAuthProvider`
   - **Rule**: Token audience must match configured Google Client ID
   - **Why**: Ensures token was issued for this application, not another

10. **Verify Issuer** (`tokenIssuer == expectedIssuer`)
   - **Class**: `GoogleOAuthProvider`
   - **Rule**: Token issuer must be `"accounts.google.com"` or `"https://accounts.google.com"`
   - **Why**: Prevents token forgery, ensures token from trusted Google

11. **Extract Claims** (`claims.subject`, `claims["permissions"]`, etc.)
   - **Class**: `JwtTokenValidator` or `GoogleOAuthProvider`
   - **Rule**: Extract user ID, permissions, email from validated token
   - **Why**: Claims contain authorization information needed for request processing

12. **Build RequestPrincipal** (`JwtPrincipalDecoder.fromToken()`)
   - **Class**: `JwtPrincipalDecoder`
   - **Method**: `fromToken()`
   - **Rule**: Creates `RequestPrincipal` with user/service ID and permissions
   - **Why**: Normalized security context for authorization checks

13. **Handle Service Tokens** (`jwtTokenValidator.isServiceToken()`)
   - **Class**: `JwtPrincipalDecoder`
   - **Rule**: Service tokens have `type="service"` claim
   - **Why**: Service-to-service authentication needs different handling than user tokens

**Key Classes:**
- `JwtPrincipalDecoder` - Entry point for token validation, builds RequestPrincipal
- `JwtTokenValidator` - Validates internal security service tokens
- `GoogleOAuthProvider` - Validates Google OAuth ID tokens
- `KeyManager` - Provides public keys/secrets for signature verification
- `OAuthProviderRegistry` - Manages multiple OAuth providers

**Validation Rules:**

1. **Internal Tokens** (`iss="neotool-security-service"`):
   - Must have valid signature (RS256 or HS256)
   - Must not be expired
   - Must have `sub` (subject/user ID) claim
   - May have `permissions` claim for authorization

2. **Google OAuth Tokens** (`iss="accounts.google.com"`):
   - Must have valid Google signature (validated by Google library)
   - Must have correct `aud` (audience) matching Client ID
   - Must have correct `iss` (issuer)
   - Must not be expired
   - Must have `email` claim

3. **Service Tokens** (`type="service"`):
   - Same validation as internal tokens
   - Must have `aud` (target audience) claim
   - May have `user_id` and `user_permissions` for user context propagation

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
- Store JWT keys in Vault (production)

❌ **DON'T:**
- Store tokens in localStorage (XSS risk)
- Use tokens without expiration
- Share tokens between users
- Expose token secrets in code
- Allow token reuse after logout
- Store private keys in environment variables (use Vault)

### 2.1. Vault Integration for Key Management

The system supports HashiCorp Vault for secure storage of JWT signing keys and secrets. This is the recommended approach for production environments.

#### Configuration

Enable Vault in `application.yml`:

```yaml
vault:
  enabled: ${VAULT_ENABLED:false}
  address: ${VAULT_ADDRESS:http://localhost:8200}
  token: ${VAULT_TOKEN:}
  secret-path: ${VAULT_SECRET_PATH:secret/jwt/keys}
  engine-version: ${VAULT_ENGINE_VERSION:2}
  connection-timeout: ${VAULT_CONNECTION_TIMEOUT:5000}
  read-timeout: ${VAULT_READ_TIMEOUT:5000}
```

#### Vault Secret Structure

Keys should be stored in Vault with this structure:

```
secret/jwt/keys/{keyId}/
  ├── private: "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
  ├── public: "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
  └── secret: "base64-encoded-secret" (optional, for HS256 fallback)
```

Example for key ID `kid-1`:
- Path: `secret/jwt/keys/kid-1`
- Keys: `private`, `public`, `secret`

#### Development vs Production

**Development:**
- Vault disabled by default (`vault.enabled: false`)
- Uses file-based or environment variable keys
- No Vault server required

**Production:**
- Enable Vault (`VAULT_ENABLED=true`)
- Provide Vault token via environment variable or service account
- Keys automatically fetched from Vault with 5-minute caching
- Falls back to file-based if Vault unavailable

#### Automatic Key Generation on First Startup

The security service automatically generates RSA key pairs on first startup if keys don't exist.

**How it works:**
1. On startup, the service checks if keys exist for the configured `key-id`
2. If keys don't exist and `jwt.auto-generate-keys=true` (default), keys are automatically generated
3. Uses distributed locking (Vault Lock API or file locks) to prevent race conditions when multiple pods start simultaneously
4. Keys are stored in Vault (if enabled) or local files (if Vault disabled)

**Configuration:**
```yaml
jwt:
  auto-generate-keys: ${JWT_AUTO_GENERATE_KEYS:true}
  key-size: ${JWT_KEY_SIZE:4096}
  key-id: ${JWT_KEY_ID:kid-1}
  lock-ttl-seconds: ${JWT_LOCK_TTL_SECONDS:60}
  lock-retry-attempts: ${JWT_LOCK_RETRY_ATTEMPTS:3}
  lock-retry-delay-seconds: ${JWT_LOCK_RETRY_DELAY_SECONDS:2}
```

**Distributed Locking:**
- **With Vault**: Uses Vault KV with check-and-set (CAS) for distributed locking
  - Lock path: `sys/locks/jwt-key-init/{keyId}`
  - TTL prevents deadlocks if pod crashes during key generation
  - Only one pod generates keys, others wait and verify keys exist
- **Without Vault**: Uses file-based locking (ReentrantLock)

**Race Condition Handling:**
When multiple pods start simultaneously:
1. All pods check if keys exist → NO
2. All pods attempt to acquire lock
3. One pod acquires lock, others wait
4. Lock holder double-checks keys (still missing)
5. Lock holder generates and stores keys
6. Lock holder releases lock
7. Other pods acquire lock, double-check keys → EXIST
8. Other pods skip generation

**Disabling Auto-Generation:**
Set `jwt.auto-generate-keys=false` for production environments where keys are provisioned through Vault or file system:
```yaml
jwt:
  auto-generate-keys: false
```

#### Key Rotation (Future)

The architecture supports key rotation:
- Multiple active key versions
- Key ID (kid) in JWT header identifies signing key
- Validators fetch correct public key by kid
- Old keys remain valid until expiration

#### Security Considerations

✅ **DO:**
- Store Vault token securely (environment variable or K8s service account)
- Use Vault policies to restrict key access
- Enable Vault audit logging
- Rotate Vault tokens regularly
- Use separate Vault paths per environment

❌ **DON'T:**
- Commit Vault tokens to code
- Use root Vault tokens
- Grant broad Vault access policies
- Store keys in both Vault and files simultaneously

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


