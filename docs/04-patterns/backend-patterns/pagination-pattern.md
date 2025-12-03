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
    WHERE (:after IS NULL OR id > CAST(:after AS {ID_TYPE}))
    ORDER BY {sort_field} ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun findAll(first: Int, after: {ID_TYPE}?): List<{Entity}>
```

**Key Points**:
- Use `id > CAST(:after AS {ID_TYPE})` for forward pagination (exclusive)
- **CRITICAL**: Always cast nullable cursor parameters to their type (UUID or INTEGER)
- Always include `id` as secondary sort for consistent ordering
- Use `LIMIT :first` to control page size
- Use native queries (JPQL doesn't support LIMIT)

### PostgreSQL Type Inference Requirement

**IMPORTANT**: When using nullable parameters in SQL comparisons, PostgreSQL requires explicit type casting.

**Problem**: PostgreSQL cannot determine the data type of a parameter when it's NULL. If you use `WHERE (:after IS NULL OR id > :after)` without casting, PostgreSQL will fail with:
```
ERROR: could not determine data type of parameter $1
```

**Solution**: Always cast nullable cursor parameters to their explicit type:
- For UUID: `CAST(:after AS UUID)`
- For Integer: `CAST(:after AS INTEGER)`

**Correct Pattern**:
```kotlin
// For UUID entities
WHERE (:after IS NULL OR id > CAST(:after AS UUID))

// For Integer entities
WHERE (:after IS NULL OR id > CAST(:after AS INTEGER))
```

**Why Tests Don't Catch This**: Unit tests that mock repositories never execute actual SQL, so they won't catch PostgreSQL compilation errors. Integration tests with a real database are required to catch SQL-level issues.

### Example: User Repository (UUID)

```kotlin
@Query(
    value = """
    SELECT * FROM security.users
    WHERE (:after IS NULL OR id > CAST(:after AS UUID))
    ORDER BY COALESCE(display_name, email) ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun findAll(first: Int, after: UUID?): List<UserEntity>
```

### Example: Search Query (UUID)

```kotlin
@Query(
    value = """
    SELECT * FROM security.users
    WHERE (LOWER(COALESCE(display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%')))
    AND (:after IS NULL OR id > CAST(:after AS UUID))
    ORDER BY COALESCE(display_name, email) ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun searchByNameOrEmail(query: String, first: Int, after: UUID?): List<UserEntity>
```

### Example: Role Repository (Integer)

```kotlin
@Query(
    value = """
    SELECT * FROM security.roles
    WHERE (:after IS NULL OR id > CAST(:after AS INTEGER))
    ORDER BY name ASC, id ASC
    LIMIT :first
    """,
    nativeQuery = true,
)
fun findAll(first: Int, after: Int?): List<RoleEntity>
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

### 6. Testing Repository Methods

**IMPORTANT**: Unit tests that mock repositories will NOT catch SQL compilation errors.

**Unit Tests** (mocked repositories):
- Test service layer logic
- Test cursor encoding/decoding
- Test pagination logic
- **Will NOT catch**: PostgreSQL type inference errors, SQL syntax errors, query compilation issues

**Integration Tests** (real database):
- Test actual SQL execution
- Test query compilation
- Test type casting requirements
- **Required for**: Catching SQL-level errors like parameter type inference issues

**Recommendation**: Always write integration tests for repository methods that use native SQL queries to catch PostgreSQL-specific issues early.

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

## Total Count for Unified List/Search Operations

### Overview

For unified list/search operations, `totalCount` is always calculated and provided to clients. This allows clients to display information like "Showing 20 of 150 results" for both list (all items) and search (filtered items) operations.

### Rule: Always Calculate Total Count

**Rule**: `totalCount` should be implemented for all paginated queries, whether they are list operations (no query filter) or search operations (with query filter).

**Rationale**:
- Provides consistent behavior and better UX (users always see "X of Y results")
- When query is null/empty: `totalCount` = total count of all items in the database
- When query is provided: `totalCount` = total count of filtered items matching the query
- Repository search methods handle null/empty query by ignoring the filter condition

### Implementation Pattern

#### 1. Add Count Method to Repository

Add a count method that matches the search query's WHERE clause:

```kotlin
@Query(
    value = """
    SELECT COUNT(*) FROM security.users
    WHERE (LOWER(COALESCE(display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%')))
    """,
    nativeQuery = true,
)
fun countSearchByNameOrEmail(query: String): Long
```

**Key Points**:
- Use the same WHERE clause as the search query (without pagination)
- Return `Long` (database count type)
- Use native SQL queries for consistency

#### 2. Update Service to Fetch Count

In the search service method, fetch the count and pass it to ConnectionBuilder:

```kotlin
fun searchUsers(
    query: String,
    first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
    after: String?,
): Connection<User> {
    // ... existing pagination logic ...
    
    // Get total count of matching users
    val totalCount = userRepository.countSearchByNameOrEmail(query)
    
    return ConnectionBuilder.buildConnectionWithUuid(
        items = users,
        hasMore = hasMore,
        getId = { it.id },
        totalCount = totalCount,  // Pass totalCount here
    )
}
```

#### 3. Update GraphQL Schema

Add `totalCount` field to Connection types:

```graphql
type UserConnection {
  edges: [UserEdge!]!
  nodes: [User!]!
  pageInfo: PageInfo!
  totalCount: Int  # Optional, only present for search operations
}
```

**Note**: GraphQL uses `Int` (not `Long`) for counts. Convert in the mapper.

#### 4. Update DTOs and Mappers

Add `totalCount: Int?` to Connection DTOs and convert from `Long` to `Int` in mappers:

```kotlin
data class UserConnectionDTO(
    val edges: List<UserEdgeDTO>,
    val nodes: List<UserDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,  // Optional field
)

// In mapper
fun toUserConnectionDTO(connection: Connection<User>): UserConnectionDTO {
    return UserConnectionDTO(
        edges = edges,
        nodes = nodes,
        pageInfo = pageInfo,
        totalCount = connection.totalCount?.toInt(),  // Convert Long to Int
    )
}
```

### Example: Complete Search Implementation

```kotlin
// Repository
@Query(
    value = """
    SELECT COUNT(*) FROM security.users
    WHERE (LOWER(COALESCE(display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%')))
    """,
    nativeQuery = true,
)
fun countSearchByNameOrEmail(query: String): Long

// Service
fun searchUsers(
    query: String,
    first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
    after: String?,
): Connection<User> {
    val pageSize = minOf(first, PaginationConstants.MAX_PAGE_SIZE)
    val afterCursor = after?.let { CursorEncoder.decodeCursorToUuid(it) }
    
    val entities = userRepository.searchByNameOrEmail(query, pageSize + 1, afterCursor)
    val hasMore = entities.size > pageSize
    val totalCount = userRepository.countSearchByNameOrEmail(query)  // Fetch count
    
    val users = entities.take(pageSize).map { it.toDomain() }
    
    return ConnectionBuilder.buildConnectionWithUuid(
        items = users,
        hasMore = hasMore,
        getId = { it.id },
        totalCount = totalCount,  // Include in connection
    )
}
```

### Best Practices

1. **Separate Queries**: Count query is separate from data query (two database calls)
2. **Performance**: Count queries are typically fast with proper indexes
3. **Optional Field**: `totalCount` is optional in Connection type for backward compatibility
4. **Search Only**: Only include `totalCount` for search operations, not list operations
5. **Type Conversion**: Convert `Long` (database) to `Int` (GraphQL) in mappers

## Related Documentation

- [Repository Pattern](./repository-pattern.md) - Repository layer patterns
- [Service Pattern](./service-pattern.md) - Service layer patterns
- [GraphQL Federation](../api-patterns/graphql-federation.md) - GraphQL API patterns

