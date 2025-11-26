---
title: Architecture Rules
type: rule
category: architecture
status: current
version: 2.0.0
tags: [architecture, rules, constraints, patterns]
ai_optimized: true
search_keywords: [architecture, rules, constraints, layers, boundaries]
related:
  - 00-core/principles.md
  - 00-core/architecture.md
---

# Architecture Rules

> **Purpose**: Architecture constraints and layer boundary rules.

## Layer Rules

### Rule: Layer Dependencies

**Rule**: Dependencies must point inward: API → Service → Repository → Entity

**Rationale**: Clean architecture principle.

**Example**:
```kotlin
// ✅ Correct
// Resolver (API) depends on Service
class CustomerResolver(private val service: CustomerService)

// Service depends on Repository
class CustomerService(private val repository: CustomerRepository)

// ❌ Incorrect
// Service depends on Resolver (violates dependency rule)
class CustomerService(private val resolver: CustomerResolver)
```

### Rule: No Cross-Layer Dependencies

**Rule**: Layers cannot depend on outer layers.

**Rationale**: Maintains clean architecture.

**Exception**: None.

## Module Rules

### Rule: Module Boundaries

**Rule**: Modules must have clear boundaries and explicit dependencies.

**Rationale**: Prevents tight coupling.

### Rule: Common Module

**Rule**: Shared code must be in `common` module, not duplicated.

**Rationale**: Single source of truth.

## Component Separation Rules

### Rule: Resolver and Mapper/Converter Separation

**Rule**: GraphQL resolvers and mapping/converter components must be in separate files. Resolvers must delegate mapping logic to dedicated mapper/converter classes.

**Rationale**: 
- Separation of concerns: Resolvers handle GraphQL-specific logic, mappers handle data transformation
- Better testability: Mappers can be tested independently without GraphQL context
- Maintainability: Mapping logic is isolated and easier to modify
- Reusability: Mappers can be reused across different resolvers or contexts

**Requirements**:
- Mappers/converters must be in a separate file from resolvers
- Mappers/converters should be in a `mapper` or `converter` package/directory
- Resolvers must inject and use mapper/converter instances
- Mappers/converters should be singleton beans when stateless
- All mapping logic (entity to DTO, DTO to entity, input map to DTO) must be in mappers

**Example**:
```kotlin
// ✅ Correct - Separate mapper file
// File: graphql/mapper/SecurityGraphQLMapper.kt
@Singleton
class SecurityGraphQLMapper {
    fun userToDTO(user: UserEntity): UserDTO {
        return UserDTO(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
        )
    }
}

// File: graphql/resolvers/SecurityAuthResolver.kt
@Singleton
class SecurityAuthResolver(
    private val authenticationService: AuthenticationService,
    private val mapper: SecurityGraphQLMapper,
) {
    fun signIn(input: Map<String, Any?>): GraphQLPayload<SignInPayloadDTO> {
        val user = authenticationService.authenticate(...)
        val payload = SignInPayloadDTO(
            token = token,
            user = mapper.userToDTO(user), // Delegates to mapper
        )
        return GraphQLPayloadFactory.success(payload)
    }
}

// ❌ Incorrect - Mapping logic in resolver
@Singleton
class SecurityAuthResolver(
    private val authenticationService: AuthenticationService,
) {
    fun signIn(input: Map<String, Any?>): GraphQLPayload<SignInPayloadDTO> {
        val user = authenticationService.authenticate(...)
        // Mapping logic should not be in resolver
        val userDTO = UserDTO(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
        )
        // ...
    }
}
```

**Testing Requirements**:
- Mappers/converters must have dedicated unit tests
- Mapper tests should cover all mapping scenarios (null handling, type conversions, etc.)
- Resolver tests should mock mappers to focus on resolver logic

## Related Documentation

- [Core Principles](../00-core/principles.md)
- [Architecture Overview](../00-core/architecture.md)

