---
title: Resolver Pattern
type: pattern
category: backend
status: current
version: 2.1.0
tags: [resolver, graphql, api, pattern]
ai_optimized: true
search_keywords: [resolver, graphql, @Singleton, GenericCrudResolver, mapper, InputDTO, DataLoader, relationship resolver, wiring factory, standalone resolver, error handling, validation, pagination]
related:
  - 04-patterns/backend-patterns/mapper-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 05-standards/api-standards/graphql-standards.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Resolver Pattern

> **Purpose**: Standard pattern for creating GraphQL resolvers, handling input validation, mapping, and error handling.

## Version 2.1.0 Changes

This version introduces strict error handling and validation rules:

- **Invalid Input Errors**: Resolvers MUST throw `IllegalArgumentException` for invalid input (malformed IDs, invalid permissions) instead of returning `null` or empty lists silently
- **Internal Failures**: Service/repository exceptions MUST be propagated as GraphQL errors, not masked as silent denials or empty results
- **Batch Resolvers**: MUST return one entry for each requested key, even if invalid (invalid IDs return empty list to maintain DataLoader contract)
- **Pagination Validation**: `first` parameter MUST be validated (min: 1, max: 100, default: 20)
- **orderBy Validation**: Fields and directions MUST be validated explicitly with clear error messages
- **No Direct Repository Access**: Resolvers MUST use service layer methods instead of accessing repositories directly

## Overview

Resolvers handle GraphQL operations (queries, mutations) and coordinate between GraphQL input, DTOs, domain objects, and services. There are two main resolver patterns:

1. **CRUD Resolvers** - Extend `GenericCrudResolver` for standard Create, Read, Update, Delete operations
2. **Standalone Resolvers** - Custom resolvers for complex operations, relationships, and specialized queries/mutations

## Resolver Types

### When to Use GenericCrudResolver

Use `GenericCrudResolver` when:
- ✅ You need standard CRUD operations (create, read, update, delete)
- ✅ Your operations follow the standard pattern
- ✅ You want automatic payload handling and validation

### When to Use Standalone Resolvers

Use standalone resolvers when:
- ✅ You need custom queries (pagination, search, filtering)
- ✅ You need custom mutations (enable/disable, assign/remove relationships)
- ✅ You need relationship resolvers (single and batch)
- ✅ Your operations don't fit the standard CRUD pattern
- ✅ You need complex business logic in the resolver layer

## Core Requirements

### Required Annotations

Every resolver MUST have:

1. `@Singleton` - Marks the class as a Micronaut singleton bean

### CRUD Resolver Requirements

CRUD resolvers MUST also:
1. Extend `GenericCrudResolver<Domain, InputDTO, ID>` - Provides CRUD operations
2. Have **InputDTO class** - GraphQL input DTO with validation
3. Have **Mapper class** - Converts between GraphQL input, DTO, and domain
4. Have **CrudService adapter** - Adapts service to `CrudService` interface

### Standalone Resolver Requirements

Standalone resolvers MUST have:
1. **Logger** - `private val logger = KotlinLogging.logger {}`
2. **Service injection** - Business logic services (NOT direct repository access)
3. **Mapper injection** - For DTO conversion
4. **Error handling** - Throw `IllegalArgumentException` for invalid input, propagate service exceptions

### Rule: No Direct Repository Access

**MUST** use service layer methods instead of accessing repositories directly. This ensures:
- Business rules are applied consistently
- Multi-tenant filters are enforced
- Audit logging is performed
- Domain validation is executed

```kotlin
// ❌ Incorrect - Direct repository access
fun group(id: String): GroupDTO? {
    val groupIdUuid = mapper.toGroupId(id)
    val entity = groupRepository.findById(groupIdUuid)  // Direct access
    return entity.map { it.toDomain() }
        .map { mapper.toGroupDTO(it) }
        .orElse(null)
}

// ✅ Correct - Use service layer
fun group(id: String): GroupDTO? {
    val groupIdUuid = try {
        mapper.toGroupId(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid group ID format: $id", e)
    }
    val group = groupManagementService.getGroupById(groupIdUuid)  // Service layer
    return group?.let { mapper.toGroupDTO(it) }
}
```

**Key Points**:
- ✅ Always use service layer methods (e.g., `service.get{Entity}ById()`, `service.get{Entity}{Relationship}()`)
- ✅ Services must provide methods for all resolver needs
- ❌ Do NOT inject repositories into resolvers
- ❌ Do NOT access repositories directly from resolvers

## Pattern Structure

### Complete Example: ProductResolver

```kotlin
package io.github.salomax.neotool.example.graphql.resolvers

import io.github.salomax.neotool.common.graphql.CrudService
import io.github.salomax.neotool.common.graphql.GenericCrudResolver
import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.graphql.dto.ProductInputDTO
import io.github.salomax.neotool.example.graphql.mapper.ProductGraphQLMapper
import io.github.salomax.neotool.example.service.ProductService
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.UUID

/**
 * Product resolver using the generic enhanced CRUD pattern with automatic payload handling.
 * Delegates mapping logic to ProductGraphQLMapper for separation of concerns.
 */
@Singleton
class ProductResolver(
    private val productService: ProductService,
    validator: Validator,
    private val mapper: ProductGraphQLMapper,
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    override val validator: Validator = validator
    override val service: CrudService<Product, UUID> = ProductCrudService(productService)

    override fun mapToInputDTO(input: Map<String, Any?>): ProductInputDTO {
        return mapper.mapToInputDTO(input)
    }

    override fun mapToEntity(
        dto: ProductInputDTO,
        id: UUID?,
    ): Product {
        return mapper.mapToEntity(dto, id)
    }
}

/**
 * Adapter to make ProductService compatible with CrudService interface
 */
class ProductCrudService(private val productService: ProductService) : CrudService<Product, UUID> {
    override fun create(entity: Product): Product = productService.create(entity)

    override fun update(entity: Product): Product? = productService.update(entity)

    override fun delete(id: UUID): Boolean {
        productService.delete(id)
        return true
    }

    override fun getById(id: UUID): Product? = productService.get(id)

    override fun list(): List<Product> = productService.list()
}
```

## Component Patterns

### Pattern: InputDTO Class

```kotlin
package io.github.salomax.neotool.{module}.graphql.dto

import io.github.salomax.neotool.common.graphql.BaseInputDTO
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min

@Introspected
@Serdeable
data class {EntityName}InputDTO(
    @field:NotBlank(message = "{field} must not be blank")
    var name: String = "",
    @field:Min(value = 0, message = "{field} must be >= 0")
    var priceCents: Long = 0,
    // ... other fields
) : BaseInputDTO()
```

**Key Points**:
- `@Introspected` - Required for Micronaut
- `@Serdeable` - Required for serialization
- Extends `BaseInputDTO`
- Bean Validation annotations (`@NotBlank`, `@Min`, `@Email`, etc.)
- Default values for optional fields
- Use `var` (not `val`) for validation to work

### Pattern: Mapper Class

> **Note**: For detailed mapper patterns including list handling in update operations, see the [Mapper Pattern](./mapper-pattern.md).

```kotlin
package io.github.salomax.neotool.{module}.graphql.mapper

import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}InputDTO
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class {EntityName}GraphQLMapper {
    fun mapToInputDTO(input: Map<String, Any?>): {EntityName}InputDTO {
        return {EntityName}InputDTO(
            name = extractField(input, "name"),
            priceCents = extractField(input, "priceCents", 0L),
            // ... other fields
        )
    }

    fun mapToEntity(
        dto: {EntityName}InputDTO,
        id: UUID? = null,
    ): {DomainName} {
        return {DomainName}(
            id = id,
            name = dto.name,
            priceCents = dto.priceCents,
            // ... other fields
        )
    }

    inline fun <reified T> extractField(
        input: Map<String, Any?>,
        name: String,
        defaultValue: T? = null,
    ): T {
        val value = input[name]
        if (value == null) {
            return defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }
        return if (value is T) {
            value
        } else {
            // Handle type conversions
            when {
                T::class == Long::class && value is Number -> value.toLong() as T
                T::class == Int::class && value is Number -> value.toInt() as T
                else -> defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
            }
        }
    }
}
```

**Key Points**:
- `@Singleton` annotation
- `mapToInputDTO()` - Converts GraphQL input map to DTO
- `mapToEntity()` - Converts DTO to domain object
- `extractField()` - Type-safe field extraction with defaults
- Handle type conversions (Int to Long, etc.)
- **Important**: For update operations with list fields, see [Mapper Pattern - List Handling](./mapper-pattern.md#critical-pattern-list-handling-in-update-operations)

### Pattern: Resolver Class

```kotlin
package io.github.salomax.neotool.{module}.graphql.resolvers

import io.github.salomax.neotool.common.graphql.CrudService
import io.github.salomax.neotool.common.graphql.GenericCrudResolver
import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}InputDTO
import io.github.salomax.neotool.{module}.graphql.mapper.{EntityName}GraphQLMapper
import io.github.salomax.neotool.{module}.service.{EntityName}Service
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.UUID

@Singleton
class {EntityName}Resolver(
    private val {entityName}Service: {EntityName}Service,
    validator: Validator,
    private val mapper: {EntityName}GraphQLMapper,
) : GenericCrudResolver<{DomainName}, {EntityName}InputDTO, UUID>() {
    override val validator: Validator = validator
    override val service: CrudService<{DomainName}, UUID> = {EntityName}CrudService({entityName}Service)

    override fun mapToInputDTO(input: Map<String, Any?>): {EntityName}InputDTO {
        return mapper.mapToInputDTO(input)
    }

    override fun mapToEntity(
        dto: {EntityName}InputDTO,
        id: UUID?,
    ): {DomainName} {
        return mapper.mapToEntity(dto, id)
    }
}
```

**Key Points**:
- `@Singleton` annotation
- Extends `GenericCrudResolver<Domain, InputDTO, ID>`
- Injects service, validator, and mapper
- Creates `CrudService` adapter
- Implements `mapToInputDTO()` and `mapToEntity()`

### Pattern: CrudService Adapter

```kotlin
class {EntityName}CrudService(
    private val {entityName}Service: {EntityName}Service,
) : CrudService<{DomainName}, UUID> {
    override fun create(entity: {DomainName}): {DomainName} = {entityName}Service.create(entity)

    override fun update(entity: {DomainName}): {DomainName}? = {entityName}Service.update(entity)

    override fun delete(id: UUID): Boolean {
        {entityName}Service.delete(id)
        return true
    }

    override fun getById(id: UUID): {DomainName}? = {entityName}Service.get(id)

    override fun list(): List<{DomainName}> = {entityName}Service.list()
}
```

**Key Points**:
- Adapts service to `CrudService` interface
- Delegates to service methods
- `delete()` returns `Boolean` (true on success)

## GraphQL Schema Integration

### Pattern: GraphQL Schema

```graphql
type {EntityName} @key(fields: "id") {
  id: ID!
  name: String!
  # ... other fields
  version: Int!
}

type Query {
  {entityName}(id: ID!): {EntityName}
  {entityName}s: [{EntityName}!]!
}

type Mutation {
  create{EntityName}(input: {EntityName}Input!): {EntityName}!
  update{EntityName}(id: ID!, input: {EntityName}Input!): {EntityName}!
  delete{EntityName}(id: ID!): Boolean!
}

input {EntityName}Input {
  name: String!
  # ... other fields
}
```

### Pattern: GraphQL Factory Wiring

Resolvers are automatically wired via Micronaut dependency injection. The GraphQL factory discovers all `@Singleton` resolvers.

## Standalone Resolver Pattern

For resolvers that don't extend `GenericCrudResolver`, use the standalone resolver pattern.

### Complete Example: UserManagementResolver

```kotlin
package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.service.UserManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for user management operations.
 * Provides queries for listing and searching users, mutations for enabling/disabling users,
 * and relationship resolvers for User.roles, User.groups, and User.permissions.
 */
@Singleton
class UserManagementResolver(
    private val userManagementService: UserManagementService,
    private val mapper: UserManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a single user by ID.
     *
     * @param id The user ID
     * @return UserDTO or null if not found
     * @throws IllegalArgumentException if user ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun user(id: String): UserDTO? {
        // Validate and convert user ID - throw IllegalArgumentException for invalid input
        val userIdUuid = try {
            mapper.toUserId(id)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format: $id", e)
        }

        // Use service layer instead of direct repository access
        val user = userManagementService.getUserById(userIdUuid)
        return user?.let { mapper.toUserDTO(it) }
    }

    /**
     * Unified query for users with optional pagination and search.
     *
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @param query Optional search query
     * @param orderBy Optional order by specification
     * @return UserConnectionDTO
     * @throws IllegalArgumentException if first is invalid or orderBy contains invalid fields/directions
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun users(
        first: Int?,
        after: String?,
        query: String?,
        orderBy: List<Map<String, Any?>>?,
    ): UserConnectionDTO {
        // Validate pagination parameter
        val pageSize = when {
            first == null -> PaginationConstants.DEFAULT_PAGE_SIZE
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first"
            )
            else -> first
        }

        // Validate orderBy - mapper will throw IllegalArgumentException for invalid fields/directions
        val orderByList = try {
            mapper.toUserOrderByList(orderBy)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
        }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection = userManagementService.searchUsers(query, pageSize, after, orderByList)
        return mapper.toUserConnectionDTO(connection)
    }

    /**
     * Enable a user account.
     */
    fun enableUser(userId: String): UserDTO {
        // Validate and convert user ID - throw IllegalArgumentException for invalid input
        val userIdUuid = try {
            mapper.toUserId(userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format: $userId", e)
        }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val user = userManagementService.enableUser(userIdUuid)
        return mapper.toUserDTO(user)
    }
}
```

**Key Points**:
- `@Singleton` annotation
- Logger initialized: `private val logger = KotlinLogging.logger {}`
- Service and mapper injected (NOT repositories)
- Throw `IllegalArgumentException` for invalid input (malformed IDs)
- Propagate service exceptions (they will be converted to GraphQL errors)
- Validate pagination `first` parameter (min: 1, max: 100, default: 20)
- Validate `orderBy` fields and directions explicitly
- Use service layer methods instead of direct repository access
- Use mapper for ID parsing and DTO conversion

## Custom Queries and Mutations

### Pattern: Custom Query with Pagination

```kotlin
/**
 * Unified query with optional pagination and search.
 * Returns Relay-style connection for pagination.
 */
fun {entityName}s(
    first: Int?,
    after: String?,
    query: String?,
    orderBy: List<Map<String, Any?>>?,
): {EntityName}ConnectionDTO {
    return try {
        val pageSize = first ?: 20
        val orderByList = mapper.to{EntityName}OrderByList(orderBy)
        val connection = {entityName}Service.search{EntityName}s(query, pageSize, after, orderByList)
        mapper.to{EntityName}ConnectionDTO(connection)
    } catch (e: Exception) {
        logger.error(e) { "Error listing {entityName}s" }
        throw e
    }
}
```

**Key Points**:
- Accept pagination parameters (`first`, `after`, `last`, `before`)
- Accept search/filter parameters (`query`)
- Accept sorting parameters (`orderBy`)
- Return Relay-style `ConnectionDTO` for pagination
- Use mapper to convert `orderBy` GraphQL input to service layer types
- Use mapper to convert service connection to DTO

### Pattern: Custom Mutation

```kotlin
/**
 * Custom mutation for business-specific operations.
 */
fun {action}{EntityName}(
    {entityName}Id: String,
    // ... other parameters
): {EntityName}DTO {
    return try {
        val {entityName}IdUuid = mapper.to{EntityName}Id({entityName}Id)
        val command = mapper.to{Action}{EntityName}Command({entityName}IdUuid, /* ... */)
        val {entityName} = {entityName}Service.{action}{EntityName}(command)
        mapper.to{EntityName}DTO({entityName})
    } catch (e: IllegalArgumentException) {
        logger.warn { "Invalid {entityName} ID: ${entityName}Id" }
        throw e
    } catch (e: Exception) {
        logger.error(e) { "Error {action}ing {entityName}: ${entityName}Id" }
        throw e
    }
}
```

**Key Points**:
- Use mapper to parse IDs (`mapper.to{EntityName}Id()`)
- Use mapper to convert to domain commands
- Call service layer with commands
- Use mapper to convert domain objects to DTOs
- Handle `IllegalArgumentException` separately (invalid input)
- Re-throw business exceptions

## Relationship Resolvers

Relationship resolvers handle GraphQL relationship fields (e.g., `User.roles`, `Group.members`).

### Pattern: Single Relationship Resolver

```kotlin
/**
 * Resolve User.roles relationship.
 * Returns all roles assigned to the user (direct and group-inherited).
 *
 * @param userId The user ID
 * @return List of RoleDTO
 * @throws IllegalArgumentException if user ID is invalid
 * @throws Exception if service fails (propagated as GraphQL error)
 */
fun resolveUserRoles(userId: String): List<RoleDTO> {
    // Validate and convert user ID - throw IllegalArgumentException for invalid input
    val userIdUuid = try {
        UUID.fromString(userId)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid user ID format: $userId", e)
    }

    // Call service - propagate any exceptions (will be converted to GraphQL errors)
    val roles = authorizationService.getUserRoles(userIdUuid)
    return roles.map { role ->
        RoleDTO(
            id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
            name = role.name,
        )
    }
}
```

**Key Points**:
- Accept parent entity ID as `String`
- Throw `IllegalArgumentException` for invalid IDs (malformed format)
- Call service layer to get relationships (NOT direct repository access)
- Propagate service exceptions (they will be converted to GraphQL errors)
- Convert domain objects to DTOs
- Return empty list only when service returns empty list (valid ID with no relationships)

### Pattern: Batch Relationship Resolver (for DataLoader)

### Rule: Batch Resolvers Must Return Entry for Each Key

**MUST** return one entry in the result map for each requested key, even if the ID is invalid. Invalid IDs should be included with an empty list to maintain the DataLoader contract. Do NOT filter out invalid IDs silently.

```kotlin
/**
 * Batch resolve User.roles relationship for multiple users.
 * Returns a map of user ID to list of roles assigned to that user.
 * Optimized to avoid N+1 queries.
 * Guarantees one entry in the result map for each requested user ID, even if invalid.
 *
 * @param userIds List of user IDs
 * @return Map of user ID to list of RoleDTO (empty list for invalid IDs)
 * @throws Exception if service fails (propagated as GraphQL error)
 */
fun resolveUserRolesBatch(userIds: List<String>): Map<String, List<RoleDTO>> {
    if (userIds.isEmpty()) {
        return emptyMap()
    }

    // Parse valid user IDs while preserving order and mapping original string to UUID
    // Invalid IDs are logged but included in result with empty list
    val validUserIdMap = mutableMapOf<String, UUID>()
    val userIdUuids = userIds.mapNotNull { userId ->
        try {
            val userIdUuid = UUID.fromString(userId)
            validUserIdMap[userId] = userIdUuid
            userIdUuid
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID in batch request: $userId" }
            null
        }
    }

    // Use service layer to batch load user roles (only for valid IDs)
    val userRolesMap = if (userIdUuids.isNotEmpty()) {
        authorizationService.getUserRolesBatch(userIdUuids)
    } else {
        emptyMap()
    }

    // Convert domain objects to DTOs and build result map preserving order of original input
    // Ensure every requested user ID has an entry (empty list for invalid IDs)
    val result = linkedMapOf<String, List<RoleDTO>>()
    for (userId in userIds) {
        val userIdUuid = validUserIdMap[userId]
        if (userIdUuid != null) {
            val roles = userRolesMap[userIdUuid] ?: emptyList()
            val roleDTOs = roles.map { role ->
                RoleDTO(
                    id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                    name = role.name,
                )
            }
            result[userId] = roleDTOs
        } else {
            // Invalid ID: add entry with empty list to maintain DataLoader contract
            result[userId] = emptyList()
        }
    }

    return result
}
```

**Key Points**:
- ✅ Accept list of parent entity IDs
- ✅ Parse and validate IDs, log warnings for invalid ones
- ✅ **Critical**: Include invalid IDs in result with empty list (maintains DataLoader contract)
- ✅ Preserve order of input IDs (use `linkedMapOf`)
- ✅ Use service layer batch method to load all relationships in one query
- ✅ Convert domain objects to DTOs
- ✅ Return map: `Map<String, List<DTO>>` (ID -> list of related DTOs)
- ✅ Propagate service exceptions (they will be converted to GraphQL errors)
- ❌ Do NOT filter out invalid IDs silently (breaks DataLoader contract)
- ❌ Do NOT catch and mask service exceptions

## DataLoader Pattern

DataLoader prevents N+1 query problems by batching relationship queries. See [GraphQL Standards - Performance Rules](../05-standards/api-standards/graphql-standards.md#rule-avoid-n1-query-problems) for when to use DataLoader.

### Step 1: Create Batch Resolver Method

Implement a batch resolver method in your resolver (see [Batch Relationship Resolver](#pattern-batch-relationship-resolver-for-dataloader) above).

### Step 2: Create DataLoader Object

```kotlin
package io.github.salomax.neotool.{module}.graphql.dataloader

import io.github.salomax.neotool.{module}.graphql.dto.{RelatedDTO}
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading {entityName} {relationship}.
 * This prevents N+1 queries when multiple {entityName}s request their {relationship}.
 */
object {EntityName}{Relationship}DataLoader {
    const val KEY = "{entityName}{relationship}"

    fun create({entityName}ManagementResolver: {EntityName}ManagementResolver): DataLoader<String, List<{RelatedDTO}>> {
        return DataLoaderFactory.newDataLoader<String, List<{RelatedDTO}>>(
            { {entityName}Ids ->
                val batchResult = {entityName}ManagementResolver.resolve{EntityName}{Relationship}Batch({entityName}Ids)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    {entityName}Ids.map { {entityName}Id -> batchResult[{entityName}Id] ?: emptyList() }
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
```

**Key Points**:
- Object with `KEY` constant for registration
- `create()` method accepts resolver instance
- Returns `DataLoader<String, List<DTO>>` (ID -> list of related DTOs)
- Calls batch resolver method
- Preserves order: map input IDs to results in same order
- Returns empty list for missing IDs

### Step 3: Register DataLoader in DataLoaderRegistryFactory

```kotlin
package io.github.salomax.neotool.{module}.graphql.dataloader

import io.github.salomax.neotool.common.graphql.DataLoaderRegistryFactory
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.DataLoaderRegistry

/**
 * Factory for creating DataLoader registries in the {module} module.
 * Registers all {module}-related DataLoaders.
 */
@Singleton
@Named("{module}")
class {Module}DataLoaderRegistryFactory(
    private val {entityName}ManagementResolver: {EntityName}ManagementResolver,
) : DataLoaderRegistryFactory {
    override fun createDataLoaderRegistry(): DataLoaderRegistry {
        val registry = DataLoaderRegistry()
        registry.register(
            {EntityName}{Relationship}DataLoader.KEY,
            {EntityName}{Relationship}DataLoader.create({entityName}ManagementResolver),
        )
        // Register other DataLoaders...
        return registry
    }
}
```

**Key Points**:
- `@Singleton` and `@Named("{module}")` annotations
- Implements `DataLoaderRegistryFactory`
- Creates `DataLoaderRegistry` in `createDataLoaderRegistry()`
- Registers all DataLoaders using their `KEY` constants

### Step 4: Wire DataLoader in Wiring Factory

```kotlin
// In your GraphQLWiringFactory
override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
    return builder
        .type("User") { type ->
            type.dataFetcher(
                "roles",
                createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<UserDTO>()
                    if (user == null) {
                        emptyList()
                    } else {
                        val dataLoader = env.getDataLoader<List<RoleDTO>>(UserRolesDataLoader.KEY)
                        dataLoader.load(user.id)
                    }
                },
            )
        }
}
```

**Key Points**:
- Override `registerCustomTypeResolvers()` in your `GraphQLWiringFactory`
- Use `createValidatedDataFetcher` for data fetchers
- Get source object from `env.getSource<DTO>()`
- Get DataLoader using `env.getDataLoader<ReturnType>(DataLoader.KEY)`
- Call `dataLoader.load(id)` to queue the request
- Return empty list if source is null

## Wiring Factory Integration

Resolvers must be wired into GraphQL via a `GraphQLWiringFactory`.

### Pattern: Wiring Factory Structure

```kotlin
package io.github.salomax.neotool.{module}.graphql

import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import jakarta.inject.Singleton
import graphql.schema.idl.TypeRuntimeWiring

@Singleton
class {Module}WiringFactory(
    private val {entityName}ManagementResolver: {EntityName}ManagementResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("{entityName}Management", {entityName}ManagementResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "{entityName}",
                createValidatedDataFetcher { env ->
                    val id = getRequiredString(env, "id")
                    {entityName}ManagementResolver.{entityName}(id)
                },
            )
            .dataFetcher(
                "{entityName}s",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                    {entityName}ManagementResolver.{entityName}s(first, after, query, orderBy)
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "enable{EntityName}",
                createValidatedDataFetcher { env ->
                    val {entityName}Id = getRequiredString(env, "{entityName}Id")
                    {entityName}ManagementResolver.enable{EntityName}({entityName}Id)
                },
            )
    }

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type  // No subscriptions yet
    }
}
```

**Key Points**:
- `@Singleton` annotation
- Extends `GraphQLWiringFactory`
- Register resolvers in `init` block for cross-module access
- Override `registerQueryResolvers()` for queries
- Override `registerMutationResolvers()` for mutations
- Use `createValidatedDataFetcher` for all data fetchers
- Use `getRequiredString()` for required string arguments
- Use `env.getArgument<T>()` for optional arguments

## Authorization Patterns

GraphQL resolvers can enforce authorization using `RequestPrincipalProvider` and `AuthorizationManager`. These patterns provide a unified authorization interface that works across GraphQL resolvers, REST controllers, and future gRPC services.

### Pattern: RequestPrincipalProvider

`RequestPrincipalProvider` extracts and validates request principals from GraphQL context or JWT tokens. It caches the principal in GraphQL context to avoid revalidation in subsequent data fetchers.

**Key Features**:
- Extracts token from GraphQL context (stored by `GraphQLControllerBase`)
- Validates token and extracts user ID and permissions
- Caches principal in GraphQL context for performance
- Supports both GraphQL context and direct token validation (for REST/gRPC)

### Pattern: AuthorizationManager

`AuthorizationManager` provides a unified interface for authorization checks. It enriches subject attributes with permissions from the token to support ABAC evaluation while maintaining RBAC as the primary authorization mechanism.

**Usage in Wiring Factory**:

```kotlin
package io.github.salomax.neotool.{module}.graphql

import io.github.salomax.neotool.security.service.AuthorizationManager
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import graphql.schema.DataFetchingEnvironment

@Singleton
class {Module}WiringFactory(
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationManager: AuthorizationManager,
    // ... other dependencies
) : GraphQLWiringFactory() {
    
    /**
     * Helper function to enforce permission checks before executing a data fetcher block.
     * This helper can be reused for any GraphQL operation that requires authorization.
     * It extracts the principal from GraphQL context, validates permissions, and executes
     * the block only if authorization succeeds.
     *
     * Exceptions thrown by this method (AuthenticationRequiredException and AuthorizationDeniedException)
     * are automatically converted to user-friendly GraphQL error messages by SecurityGraphQLExceptionHandler:
     * - AuthenticationRequiredException → "Authentication required"
     * - AuthorizationDeniedException → "Permission denied: <action>"
     *
     * @param env The GraphQL DataFetchingEnvironment
     * @param action The permission/action to check (e.g., "security:user:view")
     * @param block The block to execute if authorization succeeds
     * @return The result of executing the block
     * @throws AuthenticationRequiredException if no valid token is present (converted to GraphQL error)
     * @throws AuthorizationDeniedException if permission is denied (converted to GraphQL error)
     */
    private fun <T> withPermission(
        env: DataFetchingEnvironment,
        action: String,
        block: () -> T,
    ): T {
        val principal = requestPrincipalProvider.fromGraphQl(env)
        authorizationManager.require(principal, action)
        return block()
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "{entityName}",
                createValidatedDataFetcher { env ->
                    withPermission(env, "{module}:{entityName}:view") {
                        val id = getRequiredString(env, "id")
                        {entityName}ManagementResolver.{entityName}(id)
                    }
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "create{EntityName}",
                createValidatedDataFetcher { env ->
                    withPermission(env, "{module}:{entityName}:save") {
                        val input = env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                        {entityName}ManagementResolver.create{EntityName}(input)
                    }
                },
            )
    }
}
```

**Key Points**:
- Inject `RequestPrincipalProvider` and `AuthorizationManager` in wiring factory
- Create `withPermission()` helper function to enforce authorization
- Wrap data fetchers with `withPermission()` for protected operations
- Exceptions are automatically converted to user-friendly GraphQL errors
- Principal is cached in GraphQL context to avoid revalidation

### Pattern: Authorization in Resolvers

For resolvers that need to check authorization directly (e.g., conditional authorization based on resource ownership):

```kotlin
@Singleton
class {EntityName}ManagementResolver(
    private val {entityName}Service: {EntityName}Service,
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationManager: AuthorizationManager,
    private val mapper: {EntityName}ManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    fun update{EntityName}(
        env: DataFetchingEnvironment,
        id: String,
        input: Map<String, Any?>,
    ): {EntityName}DTO {
        // Extract principal for authorization
        val principal = requestPrincipalProvider.fromGraphQl(env)
        
        // Check general permission
        authorizationManager.require(principal, "{module}:{entityName}:save")
        
        // Optionally check resource-specific permission
        val {entityName}IdUuid = mapper.to{EntityName}Id(id)
        val existing{EntityName} = {entityName}Service.get{EntityName}ById({entityName}IdUuid)
            ?: throw NotFoundException()
        
        // Check if user owns the resource or has admin permission
        if (existing{EntityName}.ownerId != principal.userId) {
            authorizationManager.require(principal, "{module}:{entityName}:admin")
        }
        
        // Proceed with update
        val command = mapper.toUpdate{EntityName}Command(id, input)
        val {entityName} = {entityName}Service.update{EntityName}(command)
        return mapper.to{EntityName}DTO({entityName})
    }
}
```

**Key Points**:
- Inject `RequestPrincipalProvider` and `AuthorizationManager` in resolver
- Extract principal using `requestPrincipalProvider.fromGraphQl(env)`
- Use `authorizationManager.require()` to enforce permissions
- Can check multiple permissions for complex authorization logic
- Exceptions are automatically converted to GraphQL errors

### Pattern: SecurityGraphQLExceptionHandler

`SecurityGraphQLExceptionHandler` converts security-related exceptions to user-friendly GraphQL error messages:

- `AuthenticationRequiredException` → "Authentication required"
- `AuthorizationDeniedException` → "Permission denied: <action>"

This handler is automatically registered in the GraphQL execution pipeline and ensures that sensitive information (like stack traces) is not exposed to clients.

**Key Points**:
- Automatically converts security exceptions to GraphQL errors
- No stack traces or sensitive information exposed
- Errors returned in GraphQL response's `errors` array
- Delegates to next handler for non-security exceptions

### Pattern: Extracting Arguments

```kotlin
// Required string argument
val id = getRequiredString(env, "id")

// Optional arguments
val first = env.getArgument<Int?>("first")
val after = env.getArgument<String?>("after")
val query = env.getArgument<String?>("query")
val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")

// Map input (for mutations)
val inputMap = env.getArgument<Map<String, Any?>>("input")
    ?: throw IllegalArgumentException("input is required")
```

## Error Handling Patterns

### Rule: Invalid Input Errors

**MUST** throw `IllegalArgumentException` with clear messages for invalid input (malformed IDs, invalid permissions, etc.). Do NOT return `null` or empty lists silently, as this masks errors and can lead to incorrect authorization decisions.

```kotlin
fun {entityName}(id: String): {EntityName}DTO? {
    // Validate and convert ID - throw IllegalArgumentException for invalid input
    val {entityName}IdUuid = try {
        mapper.to{EntityName}Id(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid {entityName} ID format: $id", e)
    }

    // Use service layer instead of direct repository access
    val {entityName} = {entityName}Service.get{EntityName}ById({entityName}IdUuid)
    return {entityName}?.let { mapper.to{EntityName}DTO(it) }
}
```

**Key Points**:
- ✅ Throw `IllegalArgumentException` for invalid input (malformed IDs, invalid formats)
- ✅ Include clear error messages indicating what was invalid
- ✅ Use service layer methods instead of direct repository access
- ✅ Return `null` only for valid IDs that don't exist (not found)
- ❌ Do NOT return `null` for invalid input formats
- ❌ Do NOT catch and mask `IllegalArgumentException` for input validation

### Rule: Internal Failures

**MUST** propagate exceptions from services/repositories as GraphQL errors. Do NOT catch and convert to `null`/empty lists, as this masks failures and can incorrectly revoke permissions.

```kotlin
fun checkPermission(
    userId: String,
    permission: String,
    resourceId: String? = null,
): AuthorizationResultDTO {
    // Validate inputs - throw IllegalArgumentException for invalid input
    val sanitizedUserId = sanitizeInput(userId, "userId")
    val sanitizedPermission = sanitizeAndValidatePermission(permission)
    
    val userIdUuid = try {
        UUID.fromString(sanitizedUserId)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid user ID format: $userId", e)
    }

    // Call service - propagate any exceptions (will be converted to GraphQL errors)
    val result = authorizationService.checkPermission(
        userId = userIdUuid,
        permission = sanitizedPermission,
        resourceType = extractResourceType(sanitizedPermission),
        resourceId = resourceIdUuid,
    )

    return mapper.toAuthorizationResultDTO(result)
}
```

**Key Points**:
- ✅ Propagate exceptions from services (they will be converted to GraphQL errors automatically)
- ✅ Only return `allowed=false` when `AuthorizationService` actually denies access
- ❌ Do NOT catch service exceptions and return `null`/empty lists
- ❌ Do NOT mask internal failures as silent denials

### Pattern: Query Error Handling

```kotlin
fun {entityName}(id: String): {EntityName}DTO? {
    // Validate and convert ID - throw IllegalArgumentException for invalid input
    val {entityName}IdUuid = try {
        mapper.to{EntityName}Id(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid {entityName} ID format: $id", e)
    }

    // Use service layer instead of direct repository access
    val {entityName} = {entityName}Service.get{EntityName}ById({entityName}IdUuid)
    return {entityName}?.let { mapper.to{EntityName}DTO(it) }
}
```

**Key Points**:
- Throw `IllegalArgumentException` for invalid IDs (malformed format)
- Return `null` only for valid IDs that don't exist (not found)
- Use service layer methods instead of direct repository access
- Exceptions from services are automatically converted to GraphQL errors

### Pattern: Mutation Error Handling

```kotlin
fun enable{EntityName}({entityName}Id: String): {EntityName}DTO {
    return try {
        val {entityName}IdUuid = mapper.to{EntityName}Id({entityName}Id)
        val {entityName} = {entityName}Service.enable{EntityName}({entityName}IdUuid)
        mapper.to{EntityName}DTO({entityName})
    } catch (e: IllegalArgumentException) {
        logger.warn { "Invalid {entityName} ID or {entityName} not found: ${entityName}Id" }
        throw e  // Re-throw validation errors
    } catch (e: Exception) {
        logger.error(e) { "Error enabling {entityName}: ${entityName}Id" }
        throw e  // Re-throw business errors
    }
}
```

**Key Points**:
- Throw exceptions for business logic errors
- Log warnings for invalid input
- Log errors for unexpected exceptions
- Re-throw exceptions (mutations should fail on error)

### Pattern: Relationship Resolver Error Handling

```kotlin
fun resolve{EntityName}{Relationship}({entityName}Id: String): List<{RelatedDTO}> {
    // Validate and convert ID - throw IllegalArgumentException for invalid input
    val {entityName}IdUuid = try {
        UUID.fromString({entityName}Id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid {entityName} ID format: ${entityName}Id", e)
    }

    // Call service - propagate any exceptions (will be converted to GraphQL errors)
    val {related} = {entityName}Service.get{EntityName}{Relationship}({entityName}IdUuid)
    return {related}.map { mapper.to{Related}DTO(it) }
}
```

**Key Points**:
- Throw `IllegalArgumentException` for invalid IDs (malformed format)
- Propagate service exceptions (they will be converted to GraphQL errors)
- Use service layer methods instead of direct repository access
- Return empty list only when service returns empty list (valid ID with no relationships)

## DTO Conversion

### When to Convert to DTOs

Convert domain objects to DTOs in resolvers when:
- ✅ Returning data to GraphQL (all resolver return types should be DTOs)
- ✅ Handling relationships (relationship resolvers return DTOs)
- ✅ Batch operations (batch resolvers return maps of DTOs)

### Pattern: DTO Conversion in Resolver

```kotlin
// Single entity
fun {entityName}(id: String): {EntityName}DTO? {
    val domain = {entityName}Service.get(id)
    return domain?.let { mapper.to{EntityName}DTO(it) }
}

// List of entities
fun {entityName}s(): List<{EntityName}DTO> {
    val domains = {entityName}Service.list()
    return domains.map { mapper.to{EntityName}DTO(it) }
}

// Relationship
fun resolve{EntityName}{Relationship}({entityName}Id: String): List<{RelatedDTO}> {
    val domains = {entityName}Service.get{Relationship}({entityName}Id)
    return domains.map { mapper.to{Related}DTO(it) }
}
```

**Key Points**:
- Always convert domain objects to DTOs before returning
- Use mapper for conversion (separation of concerns)
- Handle nulls appropriately (use `?.let` or `mapNotNull`)

## Logger Usage

### Pattern: Logger Initialization

```kotlin
@Singleton
class {EntityName}ManagementResolver(
    // ... dependencies
) {
    private val logger = KotlinLogging.logger {}
    
    // ... methods
}
```

**Key Points**:
- Initialize logger as `private val logger = KotlinLogging.logger {}`
- Use `logger.warn {}` for invalid input
- Use `logger.error(e) {}` for unexpected exceptions
- Use `logger.debug {}` for debugging information

### Pattern: Logging in Resolvers

```kotlin
// Warning for invalid input
catch (e: IllegalArgumentException) {
    logger.warn { "Invalid {entityName} ID: $id" }
    null
}

// Error for unexpected exceptions
catch (e: Exception) {
    logger.error(e) { "Error getting {entityName}: $id" }
    null
}
```

## Input Handling Variations

### Pattern: Direct InputDTO Parameters

Some resolvers accept `InputDTO` directly instead of `Map<String, Any?>`:

```kotlin
/**
 * Create a new group.
 */
fun createGroup(input: CreateGroupInputDTO): GroupDTO {
    return try {
        val command = mapper.toCreateGroupCommand(input)
        val group = groupManagementService.createGroup(command)
        mapper.toGroupDTO(group)
    } catch (e: Exception) {
        logger.error(e) { "Error creating group" }
        throw e
    }
}
```

**When to Use Direct InputDTO**:
- ✅ When using `createMutationDataFetcher` in wiring factory
- ✅ When input is already converted to DTO in wiring factory
- ✅ For simpler, more type-safe code

**When to Use Map<String, Any?>**:
- ✅ When using `GenericCrudResolver` (required by base class)
- ✅ When you need to extract fields manually
- ✅ For maximum flexibility

### Pattern: Input Conversion in Wiring Factory

```kotlin
.dataFetcher(
    "createGroup",
    createMutationDataFetcher<GroupDTO>("createGroup") { input ->
        try {
            val dto = groupManagementMapper.mapToCreateGroupInputDTO(input)
            val result = groupManagementResolver.createGroup(dto)
            GraphQLPayloadFactory.success(result)
        } catch (e: Exception) {
            GraphQLPayloadFactory.error(e)
        }
    },
)
```

## ID Parsing and Validation

### Pattern: ID Parsing with Mapper

```kotlin
// In mapper
fun toUserId(id: String): UUID {
    return try {
        UUID.fromString(id.trim())
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid user ID format: '$id'")
    }
}

// In resolver
fun user(id: String): UserDTO? {
    return try {
        val userIdUuid = mapper.toUserId(id)  // Use mapper for parsing
        // ...
    } catch (e: IllegalArgumentException) {
        logger.warn { "Invalid user ID: $id" }
        null
    }
}
```

**Key Points**:
- Use mapper methods for ID parsing (`mapper.to{EntityName}Id()`)
- Trim input strings
- Throw `IllegalArgumentException` for invalid formats
- Handle in resolver with try-catch (return null for queries, throw for mutations)

## Validation Rules

### Rule: orderBy Validation

**MUST** validate `orderBy` parameters explicitly. The mapper should validate:
- Field names are allowed (e.g., "NAME", "EMAIL", "ID")
- Directions are valid ("ASC" or "DESC")
- Throw `IllegalArgumentException` with clear messages for invalid fields or directions

```kotlin
// In mapper
fun to{EntityName}OrderByList(orderBy: List<Map<String, Any?>>?): List<{EntityName}OrderBy>? {
    if (orderBy == null || orderBy.isEmpty()) {
        return null
    }

    return orderBy.map { orderByMap ->
        val fieldStr = orderByMap["field"] as? String
            ?: throw IllegalArgumentException("orderBy field is required")
        val directionStr = orderByMap["direction"] as? String
            ?: throw IllegalArgumentException("orderBy direction is required")

        val field = when (fieldStr) {
            "NAME" -> {EntityName}OrderField.NAME
            "EMAIL" -> {EntityName}OrderField.EMAIL
            else -> throw IllegalArgumentException("Invalid {EntityName}OrderField: $fieldStr. Allowed: NAME, EMAIL")
        }

        val direction = when (directionStr) {
            "ASC" -> OrderDirection.ASC
            "DESC" -> OrderDirection.DESC
            else -> throw IllegalArgumentException("Invalid OrderDirection: $directionStr. Allowed: ASC, DESC")
        }

        {EntityName}OrderBy(field, direction)
    }
}

// In resolver
fun {entityName}s(
    first: Int?,
    after: String?,
    query: String?,
    orderBy: List<Map<String, Any?>>?,
): {EntityName}ConnectionDTO {
    // Validate orderBy - mapper will throw IllegalArgumentException for invalid fields/directions
    val orderByList = try {
        mapper.to{EntityName}OrderByList(orderBy)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
    }
    
    // ... rest of implementation
}
```

**Key Points**:
- ✅ Validate field names against allowed enum values
- ✅ Validate directions ("ASC" or "DESC" only)
- ✅ Throw `IllegalArgumentException` with clear messages
- ✅ Include list of allowed values in error message
- ✅ Wrap mapper validation in try-catch to provide context

**Note**: Future implementation will use typed input (ENUM + direction) in the GraphQL schema. Until then, explicit validation in the mapper is required.

## Pagination Patterns

### Pattern: Relay-Style Connection

Resolvers return `ConnectionDTO` for Relay-style pagination:

```kotlin
data class {EntityName}ConnectionDTO(
    val edges: List<{EntityName}EdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int,
)

data class {EntityName}EdgeDTO(
    val node: {EntityName}DTO,
    val cursor: String,
)

data class PageInfoDTO(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)
```

**Key Points**:
- Use `ConnectionDTO` for paginated queries
- Include `edges` (list of `EdgeDTO`)
- Include `pageInfo` (pagination metadata)
- Include `totalCount` (total number of items)
- Use mapper to convert service layer connections to DTOs

### Pattern: Pagination Query

### Rule: Pagination Parameter Validation

**MUST** validate the `first` pagination parameter:
- Default: `PaginationConstants.DEFAULT_PAGE_SIZE` (20)
- Minimum: 1
- Maximum: `PaginationConstants.MAX_PAGE_SIZE` (100)
- Throw `IllegalArgumentException` for invalid values (negative, zero, or exceeding maximum)

```kotlin
fun {entityName}s(
    first: Int?,
    after: String?,
    query: String?,
    orderBy: List<Map<String, Any?>>?,
): {EntityName}ConnectionDTO {
    // Validate pagination parameter
    val pageSize = when {
        first == null -> PaginationConstants.DEFAULT_PAGE_SIZE
        first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
        first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
            "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first"
        )
        else -> first
    }

    // Validate orderBy - mapper will throw IllegalArgumentException for invalid fields/directions
    val orderByList = try {
        mapper.to{EntityName}OrderByList(orderBy)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
    }

    // Call service - propagate any exceptions (will be converted to GraphQL errors)
    val connection = {entityName}Service.search{EntityName}s(query, pageSize, after, orderByList)
    return mapper.to{EntityName}ConnectionDTO(connection)
}
```

**Key Points**:
- ✅ Validate `first` parameter: min 1, max 100, default 20
- ✅ Throw `IllegalArgumentException` for invalid pagination values
- ✅ Validate `orderBy` fields and directions explicitly
- ✅ Use `PaginationConstants.DEFAULT_PAGE_SIZE` and `PaginationConstants.MAX_PAGE_SIZE`
- ✅ Propagate service exceptions (they will be converted to GraphQL errors)
- ❌ Do NOT accept negative or zero values for `first`
- ❌ Do NOT accept values exceeding `MAX_PAGE_SIZE`

## Resolver Registry

Resolvers can be registered in `GraphQLResolverRegistry` for cross-module access.

### Pattern: Registering Resolvers

```kotlin
@Singleton
class {Module}WiringFactory(
    private val {entityName}ManagementResolver: {EntityName}ManagementResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("{entityName}Management", {entityName}ManagementResolver)
    }
}
```

**Key Points**:
- Register resolvers in `init` block of wiring factory
- Use descriptive names (e.g., `"userManagement"`, `"groupManagement"`)
- Enables other modules to access resolvers via registry

## Common Errors and How to Avoid Them

### ❌ Error: Missing @Singleton Annotation

```kotlin
// ❌ Incorrect - Not a bean, can't be injected
class ProductResolver(...) : GenericCrudResolver<Product, ProductInputDTO, UUID>()

// ✅ Correct
@Singleton
class ProductResolver(...) : GenericCrudResolver<Product, ProductInputDTO, UUID>()
```

### ❌ Error: Wrong Generic Types

```kotlin
// ❌ Incorrect - Wrong domain type
@Singleton
class ProductResolver(...) : GenericCrudResolver<Customer, ProductInputDTO, UUID>()

// ✅ Correct - Match domain type
@Singleton
class ProductResolver(...) : GenericCrudResolver<Product, ProductInputDTO, UUID>()
```

### ❌ Error: Missing Validator Injection

```kotlin
// ❌ Incorrect - Validator not injected
@Singleton
class ProductResolver(
    private val productService: ProductService,
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    // Missing validator
}

// ✅ Correct - Validator injected
@Singleton
class ProductResolver(
    private val productService: ProductService,
    validator: Validator,
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    override val validator: Validator = validator
}
```

### ❌ Error: Missing CrudService Adapter

```kotlin
// ❌ Incorrect - No service adapter
@Singleton
class ProductResolver(...) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    override val service: CrudService<Product, UUID> = ???  // Missing
}

// ✅ Correct - Create adapter
@Singleton
class ProductResolver(...) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    override val service: CrudService<Product, UUID> = ProductCrudService(productService)
}
```

### ❌ Error: Missing InputDTO Annotations

```kotlin
// ❌ Incorrect - Missing required annotations
data class ProductInputDTO(
    var name: String = "",
)

// ✅ Correct - All annotations present
@Introspected
@Serdeable
data class ProductInputDTO(
    @field:NotBlank(message = "name must not be blank")
    var name: String = "",
) : BaseInputDTO()
```

### ❌ Error: Wrong Package Structure

```kotlin
// ❌ Incorrect - Wrong package
package io.github.salomax.neotool.resolver

// ✅ Correct - Follow module structure
package io.github.salomax.neotool.{module}.graphql.resolvers
```

## Quick Reference Checklist

### CRUD Resolver Checklist

When creating a CRUD resolver, verify:

- [ ] `@Singleton` annotation on resolver class
- [ ] Extends `GenericCrudResolver<Domain, InputDTO, ID>`
- [ ] Validator injected and assigned to `override val validator`
- [ ] Service injected
- [ ] Mapper injected
- [ ] `CrudService` adapter created and assigned to `override val service`
- [ ] `mapToInputDTO()` implemented (delegates to mapper)
- [ ] `mapToEntity()` implemented (delegates to mapper)
- [ ] InputDTO class has `@Introspected` and `@Serdeable`
- [ ] InputDTO extends `BaseInputDTO`
- [ ] InputDTO has Bean Validation annotations
- [ ] Mapper class has `@Singleton`
- [ ] Mapper implements `mapToInputDTO()` and `mapToEntity()`
- [ ] Package names follow `io.github.salomax.neotool.{module}.graphql.{component}`

### Standalone Resolver Checklist

When creating a standalone resolver, verify:

- [ ] `@Singleton` annotation on resolver class
- [ ] Logger initialized: `private val logger = KotlinLogging.logger {}`
- [ ] Service(s) injected (NOT repositories)
- [ ] Mapper injected
- [ ] Throw `IllegalArgumentException` for invalid input (malformed IDs, invalid formats)
- [ ] Propagate service exceptions (they will be converted to GraphQL errors)
- [ ] Return `null` only for valid IDs that don't exist (not found)
- [ ] Validate pagination `first` parameter (min: 1, max: 100, default: 20)
- [ ] Validate `orderBy` fields and directions explicitly
- [ ] Use service layer methods instead of direct repository access
- [ ] Use mapper for ID parsing (`mapper.to{EntityName}Id()`)
- [ ] Use mapper for DTO conversion (`mapper.to{EntityName}DTO()`)
- [ ] Log warnings for invalid IDs in batch requests
- [ ] Package names follow `io.github.salomax.neotool.{module}.graphql.resolvers`

### Relationship Resolver Checklist

When creating relationship resolvers, verify:

- [ ] Single resolver method: `resolve{EntityName}{Relationship}(id: String): List<DTO>`
- [ ] Batch resolver method: `resolve{EntityName}{Relationship}Batch(ids: List<String>): Map<String, List<DTO>>`
- [ ] Single resolver throws `IllegalArgumentException` for invalid IDs
- [ ] Single resolver propagates service exceptions
- [ ] Batch resolver preserves input order (use `linkedMapOf`)
- [ ] Batch resolver includes invalid IDs in result with empty list (maintains DataLoader contract)
- [ ] Batch resolver logs warnings for invalid IDs
- [ ] Batch resolver propagates service exceptions (they will be converted to GraphQL errors)
- [ ] Both resolvers use service layer methods (NOT direct repository access)

### DataLoader Checklist

When creating DataLoaders, verify:

- [ ] DataLoader object with `KEY` constant
- [ ] `create()` method accepts resolver instance
- [ ] DataLoader calls batch resolver method
- [ ] DataLoader preserves order of input IDs
- [ ] DataLoader registered in `DataLoaderRegistryFactory`
- [ ] `DataLoaderRegistryFactory` has `@Singleton` and `@Named("{module}")`
- [ ] DataLoader wired in `GraphQLWiringFactory.registerCustomTypeResolvers()`
- [ ] Data fetcher uses `env.getDataLoader<ReturnType>(DataLoader.KEY)`
- [ ] Data fetcher calls `dataLoader.load(id)`

### Wiring Factory Checklist

When creating a wiring factory, verify:

- [ ] `@Singleton` annotation
- [ ] Extends `GraphQLWiringFactory`
- [ ] Resolvers registered in `init` block (for cross-module access)
- [ ] `registerQueryResolvers()` implemented
- [ ] `registerMutationResolvers()` implemented
- [ ] `registerSubscriptionResolvers()` implemented (can return `type` if no subscriptions)
- [ ] All data fetchers use `createValidatedDataFetcher`
- [ ] Required arguments use `getRequiredString(env, "argName")`
- [ ] Optional arguments use `env.getArgument<T?>("argName")`

## Required Imports

### CRUD Resolver

```kotlin
import io.github.salomax.neotool.common.graphql.CrudService
import io.github.salomax.neotool.common.graphql.GenericCrudResolver
import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}InputDTO
import io.github.salomax.neotool.{module}.graphql.mapper.{EntityName}GraphQLMapper
import io.github.salomax.neotool.{module}.service.{EntityName}Service
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.UUID
```

### Standalone Resolver

```kotlin
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}DTO
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}ConnectionDTO
import io.github.salomax.neotool.{module}.graphql.mapper.{EntityName}ManagementMapper
import io.github.salomax.neotool.{module}.service.{EntityName}ManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID
```

### DataLoader

```kotlin
import io.github.salomax.neotool.{module}.graphql.dto.{RelatedDTO}
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture
```

### DataLoaderRegistryFactory

```kotlin
import io.github.salomax.neotool.common.graphql.DataLoaderRegistryFactory
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.DataLoaderRegistry
```

### Wiring Factory

```kotlin
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import jakarta.inject.Singleton
```

### InputDTO

```kotlin
import io.github.salomax.neotool.common.graphql.BaseInputDTO
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min
```

### Mapper

```kotlin
import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}InputDTO
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}DTO
import jakarta.inject.Singleton
import java.util.UUID
```

## Related Documentation

- [Mapper Pattern](./mapper-pattern.md) - Detailed mapper patterns including list handling in update operations
- [Service Pattern](./service-pattern.md) - Business logic layer
- [GraphQL Standards](../05-standards/api-standards/graphql-standards.md) - GraphQL API rules including DataLoader requirements
- [Kotlin Coding Standards](../05-standards/coding-standards/kotlin-standards.md) - General conventions
- [Pagination Pattern](./pagination-pattern.md) - Relay-style pagination patterns
