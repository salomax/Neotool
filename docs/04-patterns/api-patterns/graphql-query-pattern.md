---
title: GraphQL Query Pattern
type: pattern
category: api
status: current
version: 1.0.0
tags: [graphql, query, resolver, api]
ai_optimized: true
search_keywords: [graphql, query, resolver, method naming, unified query]
related:
  - 04-patterns/backend-patterns/pagination-pattern.md
  - 04-patterns/backend-patterns/resolver-pattern.md
---

# GraphQL Query Pattern

> **Purpose**: Standard pattern for implementing GraphQL query resolvers with unified list/search operations and consistent method naming.

## Overview

GraphQL query resolvers should follow consistent naming conventions and implement unified query patterns that handle both list and search operations through optional filter parameters.

## Method Naming Best Practice

### Rule: Resolver Methods Match GraphQL Field Names

**Rule**: GraphQL resolver methods should be named to match their corresponding GraphQL query field names.

**Rationale**:
- Improves code readability and maintainability
- Makes it easier to find resolver implementations
- Follows GraphQL best practices
- Reduces cognitive load when navigating code

**Examples**:

```kotlin
// ✅ Correct - resolver method matches GraphQL field name
fun users(first: Int?, after: String?, query: String?): UserConnectionDTO

// ❌ Incorrect - resolver method doesn't match GraphQL field name
fun getUsers(first: Int?, after: String?, query: String?): UserConnectionDTO
```

**GraphQL Schema**:
```graphql
type Query {
  users(first: Int, after: String, query: String): UserConnection!
}
```

**Resolver Implementation**:
```kotlin
@Singleton
class UserManagementResolver(
    private val userManagementService: UserManagementService,
    private val mapper: UserManagementMapper,
) {
    // Method name matches GraphQL field name
    fun users(
        first: Int?,
        after: String?,
        query: String?,
    ): UserConnectionDTO {
        val pageSize = first ?: 20
        val connection = userManagementService.searchUsers(query, pageSize, after)
        return mapper.toUserConnectionDTO(connection)
    }
}
```

## Unified List/Search Pattern

### Overview

Instead of having separate `listXxx` and `searchXxx` methods, implement a single unified query method that accepts an optional filter parameter.

### Pattern

**GraphQL Schema**:
```graphql
type Query {
  # Unified query: query parameter is optional
  # When omitted or empty: returns all items (list behavior)
  # When provided: returns filtered items (search behavior)
  users(first: Int, after: String, query: String): UserConnection!
}
```

**Service Layer**:
```kotlin
@Singleton
class UserManagementService(
    private val userRepository: UserRepository,
) {
    /**
     * Unified search method that handles both list and search operations.
     * When query is null or empty, returns all users.
     * When query is provided, returns filtered users.
     * totalCount is always calculated.
     */
    fun searchUsers(
        query: String?,  // Optional - null/empty means list all
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<User> {
        // Normalize query: convert empty string to null
        val normalizedQuery = if (query.isNullOrBlank()) null else query
        
        // Always use search path (repository handles null query)
        val entities = userRepository.searchByNameOrEmail(normalizedQuery, pageSize + 1, afterCursor)
        
        // Always calculate totalCount
        val totalCount = userRepository.countSearchByNameOrEmail(normalizedQuery)
        
        return ConnectionBuilder.buildConnectionWithUuid(
            items = users,
            hasMore = hasMore,
            getId = { it.id },
            totalCount = totalCount,  // Always included
        )
    }
}
```

**Resolver Layer**:
```kotlin
@Singleton
class UserManagementResolver(
    private val userManagementService: UserManagementService,
    private val mapper: UserManagementMapper,
) {
    /**
     * Unified query - no branching logic needed.
     * Service layer handles both list and search cases.
     */
    fun users(
        first: Int?,
        after: String?,
        query: String?,
    ): UserConnectionDTO {
        val pageSize = first ?: 20
        val connection = userManagementService.searchUsers(query, pageSize, after)
        return mapper.toUserConnectionDTO(connection)
    }
}
```

### Benefits

- **Simpler code**: No branching logic in resolvers
- **Consistent behavior**: Same pagination logic for both list and search
- **Better UX**: Always provides totalCount
- **Easier to maintain**: Single code path to test and maintain

### Repository Implementation

Repository methods should handle null/empty query by conditionally including filter conditions:

```kotlin
@Query(
    value = """
    SELECT * FROM security.users
    WHERE (:query IS NULL OR :query = '' OR 
        (LOWER(COALESCE(display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%'))))
    AND (CAST(:after AS UUID) IS NULL OR id > CAST(:after AS UUID))
    ORDER BY COALESCE(display_name, email) ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun searchByNameOrEmail(
    query: String?,  // Nullable - null/empty means no filter
    first: Int,
    after: UUID?,
): List<UserEntity>
```

## Related Patterns

- [Pagination Pattern](../backend-patterns/pagination-pattern.md) - Cursor-based pagination implementation
- [Resolver Pattern](../backend-patterns/resolver-pattern.md) - GraphQL resolver structure

