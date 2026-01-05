---
title: Authentication
type: rule
category: security
status: current
version: 3.0.0
tags: [security, auth, authentication, jwt, oauth, rules]
ai_optimized: true
search_keywords: [security, authentication, jwt, oauth, token, validation, issuing, rules]
related:
  - 00-core/architecture.md
  - 03-features/security/authentication/README.md
---

# Authentication

> **Purpose**: Comprehensive authentication rules and JWT token management.

## Authentication Rules

### Rule: JWT Token Usage

**Rule**: Use JWT tokens for authentication (access tokens for API, refresh tokens for renewal).

**Rationale**: Stateless, scalable authentication.

### Rule: Token Expiration

**Rule**: Access tokens must be short-lived (default: 15 minutes), refresh tokens long-lived (default: 7 days).

**Rationale**: Security best practice.

## JWT Token Issuing

NeoTool uses **RS256** (RSA-SHA256) algorithm for JWT token signing. All tokens are issued by the security service using a private key and validated by other services using the corresponding public key.

### Token Issuance Flow

```mermaid
flowchart TD
    A[User Authenticates] --> B{Authentication Method?}
    B -->|Password| C[Validate Password]
    B -->|OAuth| D[Validate OAuth ID Token]
    B -->|Refresh Token| E[Validate Refresh Token]
    
    C --> F[Load User from Database]
    D --> G[Validate External IdP ID Token]
    G --> H[Extract User Claims]
    H --> I[Create/Find User]
    I --> F
    E --> F
    
    F --> J[Build AuthContext]
    J --> K[Load Roles & Permissions]
    K --> L[JwtTokenIssuer.generateAccessToken]
    
    L --> M[Get Private Key from KeyManager]
    M --> N[Build JWT Claims]
    N --> O[Sign with RS256]
    O --> P[Return Signed Token]
    
    style A fill:#e1f5ff
    style L fill:#fff4e1
    style M fill:#fff4e1
    style O fill:#fff4e1
    style P fill:#e8f5e9
```

### Token Structure

**Access Token Claims:**
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "type": "access",
  "permissions": ["security:user:view", "security:user:edit"],
  "iat": 1234567890,
  "exp": 1234568790,
  "iss": "neotool-security-service"
}
```

**Refresh Token Claims:**
```json
{
  "sub": "user-uuid",
  "type": "refresh",
  "iat": 1234567890,
  "exp": 1235172690,
  "iss": "neotool-security-service"
}
```

**Service Token Claims:**
```json
{
  "sub": "service-uuid",
  "type": "service",
  "aud": "target-service-id",
  "permissions": ["service:permission"],
  "user_id": "user-uuid",
  "user_permissions": ["user:permission"],
  "iat": 1234567890,
  "exp": 1234568790,
  "iss": "neotool-security-service"
}
```

### Key Components

1. **JwtTokenIssuer** (`service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt`)
   - Generates and signs JWT tokens
   - Uses RS256 algorithm with private key
   - Includes key ID (kid) in header for JWKS support

2. **KeyManager** (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/key/KeyManager.kt`)
   - Manages RSA key pairs (private/public)
   - Supports multiple backends with automatic fallback:
     - **Vault** (production) - Keys provisioned via Terraform
     - **File-based** (development) - Local file paths or environment variables
   - Caches keys for performance (5-minute TTL for Vault keys)
   - Automatically selects backend based on configuration and availability

3. **JwtConfig** (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/config/JwtConfig.kt`)
   - Configuration for token expiration times
   - Key paths and inline keys
   - Algorithm selection (RS256 only)

### Configuration

NeoTool supports multiple key management backends with automatic fallback:

1. **Vault (Production)** - Recommended for production environments
2. **File-based (Development)** - For local development
3. **Environment Variables** - Alternative for development

#### Development Configuration (File-based or Environment Variables)

**Option A: File Paths**
```bash
# Required for RS256 signing
JWT_PRIVATE_KEY_PATH=/path/to/private-key.pem
JWT_PUBLIC_KEY_PATH=/path/to/public-key.pem
JWT_KEY_ID=kid-1
```

**Option B: Inline Keys**
```bash
# Use inline keys (useful for Docker/containers)
JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n..."
JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\n..."
JWT_KEY_ID=kid-1
```

**Optional Configuration:**
```bash
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=900
JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=604800
```

#### Production Configuration (Vault)

**Important**: In production, keys should be stored in HashiCorp Vault and provisioned via Terraform. See [Vault Integration](#vault-integration-for-key-management) below.

**Vault Configuration:**
```bash
# Enable Vault
VAULT_ENABLED=true
VAULT_ADDRESS=http://vault:8200
VAULT_TOKEN=your-vault-token
VAULT_SECRET_PATH=secret/jwt/keys
JWT_KEY_ID=kid-1  # Must match key ID in Vault
```

The system automatically:
- Uses Vault if enabled and available
- Falls back to file-based keys if Vault is unavailable
- Caches keys for 5 minutes to reduce Vault API calls

### Vault Integration for Key Management

**Production Requirement**: JWT signing keys must be stored in HashiCorp Vault in production environments. Keys are provisioned as infrastructure using Terraform.

**📖 For complete Vault setup instructions, see [Vault Setup Guide](../../93-reference/vault-setup.md)**

The guide covers:
- Local development setup
- Creating secrets manually (CLI, UI, API)
- Creating secrets with Terraform
- Configuration and verification
- Troubleshooting
- Production considerations

**Quick Reference:**
- **Secret Path**: `secret/jwt/keys/{keyId}` (default: `secret/jwt/keys/kid-1`)
- **Secret Fields**: `private` (RSA private key) and `public` (RSA public key)
- **Engine**: KV v2 (Key-Value secrets engine version 2)
- **Key Format**: PEM format for both private and public keys

## JWT Token Validation

All incoming requests with JWT tokens are validated before processing. The validation process ensures token integrity, authenticity, and expiration.

### Token Validation Flow

```mermaid
flowchart TD
    A[Incoming Request] --> B[Extract Authorization Header]
    B --> C{Header Present?}
    C -->|No| D[401 Unauthorized]
    C -->|Yes| E[Extract Bearer Token]
    
    E --> F[RequestPrincipalProvider.fromToken]
    F --> G[JwtPrincipalDecoder.fromToken]
    
    G --> H[Parse Token Header]
    H --> I[Extract Key ID kid]
    I --> J[JwtTokenValidator.validateToken]
    
    J --> K[Get Public Key from KeyManager]
    K --> L{Key Found?}
    L -->|No| M[401: Public Key Missing]
    L -->|Yes| N[Verify RS256 Signature]
    
    N --> O{Signature Valid?}
    O -->|No| P[401: Invalid Signature]
    O -->|Yes| Q[Check Expiration]
    
    Q --> R{Token Expired?}
    R -->|Yes| S[401: Token Expired]
    R -->|No| T[Extract Claims]
    
    T --> U{Token Type?}
    U -->|access| V[Extract User ID & Permissions]
    U -->|service| W[Extract Service ID & Permissions]
    U -->|refresh| X[Extract User ID]
    
    V --> Y[Build RequestPrincipal]
    W --> Y
    X --> Y
    
    Y --> Z[Return RequestPrincipal]
    
    style A fill:#e1f5ff
    style J fill:#fff4e1
    style N fill:#fff4e1
    style Q fill:#fff4e1
    style Y fill:#e8f5e9
    style D fill:#ffebee
    style M fill:#ffebee
    style P fill:#ffebee
    style S fill:#ffebee
```

### Validation Steps

1. **Token Extraction**
   - Extract `Authorization: Bearer <token>` header
   - Validate header format

2. **Header Parsing**
   - Decode JWT header (base64url)
   - Extract key ID (`kid`) if present
   - Fallback to default key ID if not present

3. **Public Key Retrieval**
   - Get public key from KeyManager using key ID
   - Support for multiple keys via key ID rotation

4. **Signature Verification**
   - Verify RS256 signature using public key
   - Ensures token hasn't been tampered with

5. **Expiration Check**
   - Verify `exp` claim is in the future
   - Reject expired tokens

6. **Claims Extraction**
   - Extract user/service ID from `sub` claim
   - Extract permissions from `permissions` claim
   - Extract token type from `type` claim

7. **Principal Creation**
   - Build `RequestPrincipal` with extracted information
   - Cache in request context for authorization checks

### Key Components

1. **JwtTokenValidator** (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/jwt/JwtTokenValidator.kt`)
   - Validates JWT token signatures
   - Extracts claims from validated tokens
   - Supports access, refresh, and service tokens

2. **JwtPrincipalDecoder** (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/JwtPrincipalDecoder.kt`)
   - Converts validated tokens to RequestPrincipal
   - Handles user and service tokens differently
   - Extracts permissions for authorization

3. **RequestPrincipalProvider** (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/RequestPrincipalProvider.kt`)
   - Abstraction layer for token decoding
   - Delegates to configured TokenPrincipalDecoder
   - Used by AuthorizationInterceptor

### Error Handling

**AuthenticationRequiredException** is thrown for:
- Missing Authorization header
- Invalid token format
- Missing or invalid public key
- Invalid signature
- Expired token
- Missing required claims

All exceptions are automatically converted to HTTP 401 Unauthorized responses.

## OAuth Authentication Flow

When authenticating via OAuth (e.g., external IdP like Google, Microsoft, GitHub), there are **two distinct JWT tokens** involved:

### Two-JWT Flow

```mermaid
flowchart TD
    A[User Clicks Sign in with External IdP] --> B[External IdP Identity Services]
    B --> C[User Authenticates with External IdP]
    C --> D[External IdP Issues ID Token JWT]
    
    D --> E[Frontend Receives External IdP ID Token]
    E --> F[Frontend Sends to Backend]
    
    F --> G[Backend Validates External IdP ID Token]
    G --> H{Valid?}
    H -->|No| I[401: Invalid Token]
    H -->|Yes| J[Extract User Claims]
    
    J --> K[Create/Find User in Database]
    K --> L[Load Roles & Permissions]
    L --> M[Backend Issues Access Token JWT]
    
    M --> N[Backend Issues Refresh Token JWT]
    N --> O[Return Tokens to Frontend]
    
    O --> P[Frontend Stores Tokens]
    P --> Q[Use Access Token for API Calls]
    
    style D fill:#fff4e1
    style M fill:#e8f5e9
    style N fill:#e8f5e9
    style Q fill:#e1f5ff
```

### External IdP ID Token vs Application Access Token

| Aspect | External IdP ID Token | Application Access Token |
|--------|----------------------|-------------------------|
| **Issuer** | External IdP (e.g., `accounts.google.com`, `login.microsoftonline.com`) | NeoTool Security Service |
| **Purpose** | Prove identity with external IdP | Authorize API access |
| **Lifetime** | ~1 hour (varies by IdP) | 15 minutes |
| **Contains** | External IdP user info (email, name) | Your user ID, permissions |
| **Validation** | External IdP's public keys | Your public keys |
| **Usage** | One-time during login | Every API request |

### Why Two Tokens?

1. **Different Purposes**: External IdP's token authenticates with the IdP; your token authorizes access to your APIs
2. **Different Claims**: External IdP's token has IdP-specific claims; your token has your application's user ID and permissions
3. **Different Lifetimes**: External IdP tokens are longer-lived; your access tokens are short-lived for security
4. **Different Validation**: Your services can't validate external IdP tokens directly; they need your tokens with your user context

## Authorization Rules

### Rule: Input Validation

**Rule**: Validate all inputs at API boundaries.

**Rationale**: Prevents injection attacks.

### Rule: Parameterized Queries

**Rule**: Always use parameterized queries (never string concatenation).

**Rationale**: Prevents SQL injection.

### Rule: REST Endpoint Authorization

**Rule**: Use the `@RequiresAuthorization` annotation to enforce permission-based authorization on REST endpoints.

**Rationale**: Provides a unified authorization interface that works across GraphQL resolvers, REST controllers, and future gRPC services. The annotation-based approach reduces boilerplate and ensures consistent authorization enforcement.

**Implementation Pattern**:

1. **Annotate Controller Methods or Classes**:

```kotlin
package io.github.salomax.neotool.{module}.http

import io.github.salomax.neotool.security.http.RequiresAuthorization
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.util.UUID

@Controller("/api/{entityName}s")
class {EntityName}Controller {
    
    @Get("/{id}")
    @RequiresAuthorization("{module}:{entityName}:view")
    fun get{EntityName}(id: UUID): HttpResponse<{EntityName}> {
        // Method execution only proceeds if user has required permission
        // ...
    }
    
    @Post
    @RequiresAuthorization("{module}:{entityName}:save")
    fun create{EntityName}(input: {EntityName}Input): HttpResponse<{EntityName}> {
        // Method execution only proceeds if user has required permission
        // ...
    }
}
```

**Key Points**:
- `@RequiresAuthorization` can be applied to methods or classes
- When applied to a class, all methods in that class require the specified permission
- Permission string format: `"{module}:{entity}:{action}"` (e.g., `"security:user:view"`)
- The `AuthorizationInterceptor` automatically:
  - Extracts Bearer token from `Authorization` header
  - Validates token and creates `RequestPrincipal`
  - Checks authorization using `AuthorizationManager`
  - Throws `AuthenticationRequiredException` if token is missing/invalid
  - Throws `AuthorizationDeniedException` if permission is denied

2. **Exception Handling**:

Exceptions thrown by `AuthorizationInterceptor` are automatically converted to appropriate HTTP responses:

- `AuthenticationRequiredException` → HTTP 401 Unauthorized
- `AuthorizationDeniedException` → HTTP 403 Forbidden

These exceptions are handled by:
- `AuthenticationRequiredExceptionHandler` (HTTP 401)
- `AuthorizationDeniedExceptionHandler` (HTTP 403)

**Example - Class-Level Annotation**:

```kotlin
@Controller("/api/admin")
@RequiresAuthorization("security:admin")
class AdminController {
    // All methods in this controller require "security:admin" permission
    // Individual methods can override with method-level annotation
    
    @Get("/users")
    fun getUsers(): HttpResponse<List<User>> {
        // Requires "security:admin" permission
    }
    
    @Get("/settings")
    @RequiresAuthorization("security:settings:view")
    fun getSettings(): HttpResponse<Settings> {
        // Overrides class-level annotation, requires "security:settings:view" permission
    }
}
```

**Key Points**:
- Class-level annotation applies to all methods
- Method-level annotation overrides class-level annotation
- Use class-level annotation for controllers where all endpoints require the same permission
- Use method-level annotation for fine-grained control

3. **AuthorizationInterceptor Details**:

The `AuthorizationInterceptor`:
- Extracts Bearer token from `Authorization` header (format: `"Bearer <token>"`)
- Uses `RequestPrincipalProvider.fromToken()` to validate token and create principal
- Uses `AuthorizationManager.require()` to check permissions
- Supports both RBAC and ABAC authorization (via `AuthorizationService`)
- Caches principal in request context for performance

**Related Documentation**:
- [Resolver Pattern](../../04-patterns/backend-patterns/resolver-pattern.md) - For GraphQL authorization patterns
- [Architecture Overview](../../00-overview/architecture-overview.md)

