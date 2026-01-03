---
title: Security Rules
type: rule
category: security
status: current
version: 2.0.0
tags: [security, auth, authorization, rules]
ai_optimized: true
search_keywords: [security, authentication, authorization, jwt, rules]
related:
  - 00-core/architecture.md
---

# Security Rules

> **Purpose**: Security and authentication rules.

## Authentication Rules

### Rule: JWT Token Usage

**Rule**: Use JWT tokens for authentication (access tokens for API, refresh tokens for renewal).

**Rationale**: Stateless, scalable authentication.

### Rule: Token Expiration

**Rule**: Access tokens must be short-lived (default: 15 minutes), refresh tokens long-lived (default: 7 days).

**Rationale**: Security best practice.

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

