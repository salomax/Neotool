---
title: Pagination Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [pagination, graphql, relay, cursor, backend]
ai_optimized: true
search_keywords: [pagination, relay, graphql, cursor, connection, pageinfo, edge]
related:
  - 04-patterns/backend-patterns/repository-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 04-patterns/api-patterns/graphql-federation.md
---

# Pagination Pattern

> **Purpose**: Standard pattern for implementing Relay GraphQL pagination in backend services using cursor-based pagination.

## Overview

Relay GraphQL pagination provides a consistent, efficient way to paginate through large datasets. This pattern uses cursor-based pagination, which is more efficient than offset-based pagination and works well with distributed systems.

### Benefits

- **Efficient**: Cursor-based pagination avoids performance issues with large offsets
- **Consistent**: Standard Relay format works across all GraphQL queries
- **Scalable**: Works well with distributed databases and caching
- **Reliable**: Cursors are opaque and don't break when data changes

## Relay Pagination Structure

### Connection Type

All paginated queries return a `Connection` type with the following structure:

```kotlin
data class Connection<T>(
    val edges: List<Edge<T>>,
    val nodes: List<T>,  // Convenience field (derived from edges)
    val pageInfo: PageInfo,
)
```

### Edge Type

Each item in the connection is wrapped in an `Edge`:

```kotlin
data class Edge<T>(
    val node: T,
    val cursor: String,  // Base64-encoded cursor
)
```

### PageInfo Type

Metadata about the pagination state:

```kotlin
data class PageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String? = null,  // Cursor of first item
    val endCursor: String? = null,     // Cursor of last item
)
```

## Cursor Encoding

### Rule: Use Entity ID as Cursor

**Rule**: Cursors MUST be encoded entity IDs (UUID or Int) as base64 strings.

**Rationale**:
- Simple and efficient
- Opaque to clients (implementation detail)
- Works with any ID type
- Easy to decode for repository queries

### UUID Cursor Encoding

For entities with UUID primary keys:

```kotlin
// Encode UUID to cursor
fun encodeCursor(id: UUID): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        id.toString().toByteArray()
    )
}

// Decode cursor to UUID
fun decodeCursor(cursor: String): UUID {
    val decoded = Base64.getUrlDecoder().decode(cursor)
    return UUID.fromString(String(decoded))
}
```

### Int Cursor Encoding

For entities with integer primary keys:

```kotlin
// Encode Int to cursor
fun encodeCursor(id: Int): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        id.toString().toByteArray()
    )
}

// Decode cursor to Int
fun decodeCursor(cursor: String): Int {
    val decoded = Base64.getUrlDecoder().decode(cursor)
    return String(decoded).toInt()
}
```

## Repository Layer Pattern

### Rule: Cursor-Based Pagination Queries

**Rule**: Repository methods MUST use cursor-based pagination with native SQL queries.

**Pattern**:

```kotlin
@Query(
    value = """
    SELECT * FROM {schema}.{table}
    WHERE (:after IS NULL OR id > :after)
    ORDER BY {sort_field} ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun findAll(first: Int, after: {ID_TYPE}?): List<{Entity}>
```

**Key Points**:
- Use `id > :after` for forward pagination (exclusive)
- Always include `id` as secondary sort for consistent ordering
- Use `LIMIT :first` to control page size
- Use native queries (JPQL doesn't support LIMIT)

### Example: User Repository

```kotlin
@Query(
    value = """
    SELECT * FROM security.users
    WHERE (:after IS NULL OR id > :after)
    ORDER BY COALESCE(display_name, email) ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun findAll(first: Int, after: UUID?): List<UserEntity>
```

### Example: Search Query

```kotlin
@Query(
    value = """
    SELECT * FROM security.users
    WHERE (LOWER(COALESCE(display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%')))
    AND (:after IS NULL OR id > :after)
    ORDER BY COALESCE(display_name, email) ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun searchByNameOrEmail(query: String, first: Int, after: UUID?): List<UserEntity>
```

## Service Layer Pattern

### Rule: Use Common Module Pagination Utilities

**Rule**: All services MUST use pagination utilities from `io.github.salomax.neotool.common.graphql.pagination`.

**Rationale**:
- Consistent pagination across all services
- Reusable code
- Single source of truth for pagination logic

### Pattern: Building Connections

```kotlin
import io.github.salomax.neotool.common.graphql.pagination.*

@Singleton
class UserManagementService(
    private val userRepository: UserRepository,
) {
    fun listUsers(first: Int, after: String?): Connection<User> {
        // Decode cursor
        val afterCursor = after?.let { decodeCursor(it) }
        
        // Query repository
        val entities = userRepository.findAll(first, afterCursor)
        
        // Convert to domain
        val users = entities.map { it.toDomain() }
        
        // Build connection
        return buildConnection(
            items = users,
            hasMore = users.size == first,  // If we got exactly 'first' items, there might be more
            encodeCursor = { encodeCursor(it.id!!) },
        )
    }
}
```

### Pattern: Checking for More Results

To determine `hasNextPage`, query with `limit + 1`:

```kotlin
fun listUsers(first: Int, after: String?): Connection<User> {
    val afterCursor = after?.let { decodeCursor(it) }
    
    // Query one extra item to check if there are more
    val entities = userRepository.findAll(first + 1, afterCursor)
    val hasMore = entities.size > first
    
    // Take only the requested number
    val users = entities.take(first).map { it.toDomain() }
    
    return buildConnection(
        items = users,
        hasMore = hasMore,
        encodeCursor = { encodeCursor(it.id!!) },
    )
}
```

## Common Module Utilities

### Location

All pagination utilities are located in:
```
service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/graphql/pagination/
```

### Available Functions

- `encodeCursor(id: UUID): String` - Encode UUID to cursor
- `decodeCursor(cursor: String): UUID` - Decode cursor to UUID
- `encodeCursor(id: Int): String` - Encode Int to cursor
- `decodeCursor(cursor: String): Int` - Decode cursor to Int
- `buildConnection<T>(items, hasMore, encodeCursor): Connection<T>` - Build connection from items

## GraphQL Schema Pattern

### Connection Type Definition

```graphql
type UserConnection {
    edges: [UserEdge!]!
    nodes: [User!]!  # Convenience field
    pageInfo: PageInfo!
}

type UserEdge {
    node: User!
    cursor: String!
}

type PageInfo {
    hasNextPage: Boolean!
    hasPreviousPage: Boolean!
    startCursor: String
    endCursor: String
}
```

### Query Pattern

```graphql
type Query {
    users(first: Int, after: String): UserConnection!
}
```

## Best Practices

### 1. Default Page Size

Always provide a reasonable default for `first`:

```kotlin
fun listUsers(first: Int = 20, after: String?): Connection<User>
```

### 2. Maximum Page Size

Enforce a maximum page size to prevent performance issues:

```kotlin
fun listUsers(first: Int = 20, after: String?): Connection<User> {
    val pageSize = minOf(first, MAX_PAGE_SIZE)
    // ... rest of implementation
}
```

### 3. Consistent Ordering

Always include ID as secondary sort for consistent pagination:

```sql
ORDER BY name ASC, id ASC
```

### 4. Error Handling

Handle invalid cursors gracefully:

```kotlin
fun listUsers(first: Int, after: String?): Connection<User> {
    val afterCursor = try {
        after?.let { decodeCursor(it) }
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid cursor: $after")
    }
    // ... rest of implementation
}
```

### 5. Empty Results

Handle empty results correctly:

```kotlin
if (users.isEmpty()) {
    return Connection(
        edges = emptyList(),
        nodes = emptyList(),
        pageInfo = PageInfo(
            hasNextPage = false,
            hasPreviousPage = false,
        ),
    )
}
```

## Examples

### Complete Service Example

```kotlin
@Singleton
class UserManagementService(
    private val userRepository: UserRepository,
) {
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }
    
    fun listUsers(first: Int = DEFAULT_PAGE_SIZE, after: String?): Connection<User> {
        val pageSize = minOf(first, MAX_PAGE_SIZE)
        
        val afterCursor = try {
            after?.let { decodeCursor(it) }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid cursor: $after")
        }
        
        // Query one extra to check for more results
        val entities = userRepository.findAll(pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize
        
        // Take only requested number
        val users = entities.take(pageSize).map { it.toDomain() }
        
        return buildConnection(
            items = users,
            hasMore = hasMore,
            encodeCursor = { encodeCursor(it.id!!) },
        )
    }
    
    fun searchUsers(query: String, first: Int = DEFAULT_PAGE_SIZE, after: String?): Connection<User> {
        val pageSize = minOf(first, MAX_PAGE_SIZE)
        
        val afterCursor = try {
            after?.let { decodeCursor(it) }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid cursor: $after")
        }
        
        val entities = userRepository.searchByNameOrEmail(query, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize
        
        val users = entities.take(pageSize).map { it.toDomain() }
        
        return buildConnection(
            items = users,
            hasMore = hasMore,
            encodeCursor = { encodeCursor(it.id!!) },
        )
    }
}
```

## Related Documentation

- [Repository Pattern](./repository-pattern.md) - Repository layer patterns
- [Service Pattern](./service-pattern.md) - Service layer patterns
- [GraphQL Federation](../api-patterns/graphql-federation.md) - GraphQL API patterns

