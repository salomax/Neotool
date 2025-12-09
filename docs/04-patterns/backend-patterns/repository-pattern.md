---
title: Repository Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [repository, micronaut-data, jpa, data-access, pattern, query-builder]
ai_optimized: true
search_keywords: [repository, @Repository, JpaRepository, Micronaut Data, Micronaut Query Builder, EntityManager, Criteria API, interface]
related:
  - 04-patterns/backend-patterns/entity-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Repository Pattern

> **Purpose**: Standard pattern for creating data access repositories using Micronaut Data JPA, ensuring consistent query methods and proper integration with services.

## Overview

Repositories provide data access abstraction using Micronaut Data JPA. They extend `JpaRepository` and use method naming conventions for automatic query generation.

## Core Requirements

### Required Annotations

Every repository MUST have:

1. `@Repository` - Marks the interface as a Micronaut Data repository
2. Extends `JpaRepository<Entity, ID>` - Provides CRUD operations

### Required Structure

```kotlin
@Repository
interface {EntityName}Repository : JpaRepository<{EntityName}Entity, {IDType}> {
    // Custom query methods
}
```

## Pattern Structure

### Basic Repository

```kotlin
package io.github.salomax.neotool.{module}.repo

import io.github.salomax.neotool.{module}.entity.{EntityName}Entity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface {EntityName}Repository : JpaRepository<{EntityName}Entity, UUID> {
    // Custom query methods here
}
```

### Complete Example: ProductRepository

```kotlin
package io.github.salomax.neotool.example.repo

import io.github.salomax.neotool.example.entity.ProductEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findBySku(sku: String): ProductEntity?
}
```

## Query Method Patterns

### Find by Single Field

```kotlin
fun findBy{FieldName}({fieldName}: {Type}): {EntityName}Entity?

// Examples:
fun findBySku(sku: String): ProductEntity?
fun findByEmail(email: String): CustomerEntity?
fun findByName(name: String): ProductEntity?
```

### Find by Multiple Fields

```kotlin
fun findBy{Field1}And{Field2}({field1}: {Type1}, {field2}: {Type2}): {EntityName}Entity?

// Example:
fun findByEmailAndStatus(email: String, status: CustomerStatus): CustomerEntity?
```

### Find All by Field

```kotlin
fun findAllBy{FieldName}({fieldName}: {Type}): List<{EntityName}Entity>

// Example:
fun findAllByStatus(status: CustomerStatus): List<CustomerEntity>
```

### Exists Checks

```kotlin
fun existsBy{FieldName}({fieldName}: {Type}): Boolean

// Example:
fun existsBySku(sku: String): Boolean
fun existsByEmail(email: String): Boolean
```

### Count Queries

```kotlin
fun countBy{FieldName}({fieldName}: {Type}): Long

// Example:
fun countByStatus(status: CustomerStatus): Long
```

## Return Type Patterns

### Single Entity (Nullable)

```kotlin
fun findBySku(sku: String): ProductEntity?  // ✅ Correct - may not exist
```

### List of Entities

```kotlin
fun findAllByStatus(status: CustomerStatus): List<CustomerEntity>  // ✅ Correct
```

### Boolean (Exists)

```kotlin
fun existsBySku(sku: String): Boolean  // ✅ Correct
```

### Long (Count)

```kotlin
fun countByStatus(status: CustomerStatus): Long  // ✅ Correct
```

## Common Errors and How to Avoid Them

### ❌ Error: Missing @Repository Annotation

```kotlin
// ❌ Incorrect - Micronaut won't recognize it
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findBySku(sku: String): ProductEntity?
}

// ✅ Correct
@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findBySku(sku: String): ProductEntity?
}
```

### ❌ Error: Wrong Generic Types

```kotlin
// ❌ Incorrect - Wrong entity type
@Repository
interface ProductRepository : JpaRepository<CustomerEntity, UUID>

// ✅ Correct - Matches entity
@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID>
```

### ❌ Error: Wrong ID Type

```kotlin
// ❌ Incorrect - Entity uses UUID but repository uses Int
@Repository
interface ProductRepository : JpaRepository<ProductEntity, Int>

// ✅ Correct - Matches entity ID type
@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID>
```

### ❌ Error: Non-Nullable Return for Single Entity

```kotlin
// ❌ Incorrect - Entity may not exist
fun findBySku(sku: String): ProductEntity

// ✅ Correct - Nullable return type
fun findBySku(sku: String): ProductEntity?
```

### ❌ Error: Wrong Method Naming

```kotlin
// ❌ Incorrect - Micronaut Data won't generate query
fun getBySku(sku: String): ProductEntity?

// ✅ Correct - Use "findBy" prefix
fun findBySku(sku: String): ProductEntity?
```

### ❌ Error: Missing Package Import

```kotlin
// ❌ Incorrect - Missing imports
interface ProductRepository : JpaRepository<ProductEntity, UUID>

// ✅ Correct - All imports present
import io.github.salomax.neotool.example.entity.ProductEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID>
```

## Quick Reference Checklist

### Standard Repository Checklist

When creating a standard repository, verify:

- [ ] `@Repository` annotation present
- [ ] Extends `JpaRepository<EntityType, IDType>`
- [ ] Entity type matches the entity class
- [ ] ID type matches entity ID type (UUID or Int)
- [ ] Package name follows `io.github.salomax.neotool.{module}.repo`
- [ ] Query methods use "findBy" prefix (not "getBy" or "queryBy")
- [ ] Single entity queries return nullable type (`Entity?`)
- [ ] List queries return `List<Entity>`
- [ ] Method names follow Micronaut Data conventions
- [ ] All required imports present
- [ ] **Only methods that Micronaut Data can auto-generate are included**

### Custom Repository Checklist

When creating a custom repository implementation, verify:

- [ ] Main repository interface (`{EntityName}Repository`) contains only auto-generatable methods
- [ ] Custom interface (`{EntityName}RepositoryCustom`) is separate and does NOT extend `JpaRepository`
- [ ] Custom interface does NOT have `@Repository` annotation
- [ ] Implementation class (`{EntityName}RepositoryImpl`) is annotated with `@Singleton`
- [ ] Implementation class implements the custom interface
- [ ] **Implementation uses Micronaut Query Builder (EntityManager with JPA Criteria API) as the standard approach**
- [ ] **All read-only query methods are annotated with `@ReadOnly`**
- [ ] Common predicate logic extracted into private helper methods
- [ ] Service layer injects both repositories when needed
- [ ] All required imports present (jakarta.persistence.*, io.micronaut.transaction.annotation.ReadOnly)

## Required Imports

```kotlin
import io.github.salomax.neotool.{module}.entity.{EntityName}Entity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID  // or Int, depending on ID type
```

## Micronaut Data Query Method Conventions

### Supported Keywords

- `findBy` - Find single entity
- `findAllBy` - Find multiple entities
- `existsBy` - Check existence
- `countBy` - Count entities
- `deleteBy` - Delete entities
- `And` - Combine conditions
- `Or` - OR conditions
- `Is` - Equality check (optional, `findByName` = `findByNameIs`)
- `Not` - Negation
- `In` - IN clause
- `Between` - Range check
- `LessThan`, `GreaterThan` - Comparisons
- `Like`, `Containing` - String matching
- `IgnoreCase` - Case-insensitive

### Examples

```kotlin
@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    // Basic find
    fun findBySku(sku: String): ProductEntity?
    
    // Multiple conditions
    fun findByNameAndStockGreaterThan(name: String, stock: Int): List<ProductEntity>
    
    // Exists check
    fun existsBySku(sku: String): Boolean
    
    // Count
    fun countByStockLessThan(stock: Int): Long
    
    // String matching
    fun findByNameContaining(name: String): List<ProductEntity>
    fun findByNameIgnoreCase(name: String): ProductEntity?
    
    // IN clause
    fun findBySkuIn(skus: List<String>): List<ProductEntity>
    
    // Range
    fun findByPriceCentsBetween(min: Long, max: Long): List<ProductEntity>
}
```

## Custom Repository Implementation Pattern

### When to Use Custom Implementation

Use a **separate custom interface** when you need:
- Complex queries that Micronaut Data cannot auto-generate
- Dynamic queries with conditional logic
- Queries that don't follow Micronaut Data naming conventions
- Cursor-based pagination with complex ordering

**IMPORTANT**: Never add methods to the main repository interface that Micronaut Data cannot auto-generate. This will cause KSP compilation errors.

### Standard Implementation Approach: Micronaut Query Builder

**STANDARD**: When the default auto-generated repository methods do not meet requirements, custom repository implementations MUST use **Micronaut Query Builder** as the standard approach. This is implemented using `EntityManager` with JPA Criteria API, which provides type-safe, dynamic query building capabilities.

The Micronaut Query Builder pattern:
- Uses `EntityManager` injected via constructor
- Builds queries using JPA Criteria API (`CriteriaBuilder`, `CriteriaQuery`, `Root`, `Predicate`)
- Provides type-safe query construction
- Supports dynamic query building with conditional logic
- Enables complex filtering, sorting, and pagination

#### Implementation Structure

```kotlin
@Singleton
class {EntityName}RepositoryImpl(
    private val entityManager: EntityManager,  // Injected EntityManager
) : {EntityName}RepositoryCustom {

    @ReadOnly
    override fun customQuery(...): List<{EntityName}Entity> {
        // 1. Get CriteriaBuilder from EntityManager
        val criteriaBuilder = entityManager.criteriaBuilder
        
        // 2. Create CriteriaQuery for the entity type
        val criteriaQuery = criteriaBuilder.createQuery({EntityName}Entity::class.java)
        
        // 3. Get Root entity reference
        val root = criteriaQuery.from({EntityName}Entity::class.java)
        
        // 4. Build predicates dynamically
        val predicates = mutableListOf<Predicate>()
        // ... add predicates based on conditions
        
        // 5. Apply WHERE clause
        if (predicates.isNotEmpty()) {
            criteriaQuery.where(*predicates.toTypedArray())
        }
        
        // 6. Apply ordering
        criteriaQuery.orderBy(...)
        
        // 7. Execute query
        val typedQuery = entityManager.createQuery(criteriaQuery)
        typedQuery.maxResults = limit
        return typedQuery.resultList
    }
}
```

### Pattern Structure

1. **Main Repository Interface** - Only contains methods Micronaut Data can auto-generate
2. **Custom Repository Interface** - Contains methods requiring custom implementation
3. **Custom Implementation Class** - Implements the custom interface using **Micronaut Query Builder** (EntityManager + JPA Criteria API)
4. **Service Layer** - Injects both repositories

### Example: Custom Search Implementation

#### Step 1: Main Repository (Auto-Generated Methods Only)

```kotlin
package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    // Only methods that Micronaut Data can auto-generate
    fun findByEmail(email: String): UserEntity?
    fun findByRememberMeToken(token: String): UserEntity?
    fun findByPasswordResetToken(token: String): UserEntity?
    fun findByIdIn(ids: List<UUID>): List<UserEntity>
}
```

#### Step 2: Custom Repository Interface

```kotlin
package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import java.util.UUID

/**
 * Custom query contract for {@link UserRepository}.
 * Declared separately so Micronaut Data can generate the default repository implementation
 * while these methods are provided via a manual bean.
 */
interface UserRepositoryCustom {
    /**
     * Search users by name or email with cursor-based pagination.
     * Performs case-insensitive partial matching on displayName and email fields.
     */
    fun searchByNameOrEmail(
        query: String?,
        first: Int,
        after: UUID?,
    ): List<UserEntity>

    /**
     * Count users matching the search query by name or email.
     */
    fun countByNameOrEmail(query: String?): Long
}
```

#### Step 3: Custom Implementation Class

```kotlin
package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.UUID

/**
 * Custom repository bean that exposes complex search/count queries.
 * Uses Micronaut Query Builder (EntityManager with JPA Criteria API) for building dynamic filters.
 * 
 * This is the standard implementation approach for custom repository queries when
 * auto-generated methods do not meet requirements.
 */
@Singleton
class UserRepositoryImpl(
    private val entityManager: EntityManager,
) : UserRepositoryCustom {

    @ReadOnly
    override fun searchByNameOrEmail(
        query: String?,
        first: Int,
        after: UUID?,
    ): List<UserEntity> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(UserEntity::class.java)
        val root = criteriaQuery.from(UserEntity::class.java)

        // Build WHERE clause predicates
        val predicates = mutableListOf<Predicate>()

        // Add search filter if query is provided
        if (query != null && query.isNotBlank()) {
            val likePattern = "%${query.lowercase()}%"
            val displayNamePath = root.get<String?>("displayName")
            val emailPath = root.get<String>("email")

            val displayNamePredicate = criteriaBuilder.like(
                criteriaBuilder.lower(
                    criteriaBuilder.coalesce(displayNamePath, criteriaBuilder.literal(""))
                ),
                likePattern
            )

            val emailPredicate = criteriaBuilder.like(
                criteriaBuilder.lower(emailPath),
                likePattern
            )

            predicates.add(criteriaBuilder.or(displayNamePredicate, emailPredicate))
        }

        // Add cursor pagination if after is provided
        if (after != null) {
            predicates.add(criteriaBuilder.greaterThan(root.get<UUID>("id"), after))
        }

        // Apply WHERE clause
        if (predicates.isNotEmpty()) {
            criteriaQuery.where(*predicates.toTypedArray())
        }

        // Build ordering
        val displayNamePath = root.get<String?>("displayName")
        val emailPath = root.get<String>("email")
        val coalesceExpression = criteriaBuilder.coalesce(displayNamePath, emailPath)
        criteriaQuery.orderBy(
            criteriaBuilder.asc(coalesceExpression),
            criteriaBuilder.asc(root.get<UUID>("id"))
        )

        // Execute query with limit
        val typedQuery: TypedQuery<UserEntity> = entityManager.createQuery(criteriaQuery)
        typedQuery.maxResults = first

        return typedQuery.resultList
    }

    @ReadOnly
    override fun countByNameOrEmail(query: String?): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val root = criteriaQuery.from(UserEntity::class.java)

        criteriaQuery.select(criteriaBuilder.count(root))

        // Add search filter if query is provided
        if (query != null && query.isNotBlank()) {
            val likePattern = "%${query.lowercase()}%"
            val displayNamePath = root.get<String?>("displayName")
            val emailPath = root.get<String>("email")

            val displayNamePredicate = criteriaBuilder.like(
                criteriaBuilder.lower(
                    criteriaBuilder.coalesce(displayNamePath, criteriaBuilder.literal(""))
                ),
                likePattern
            )

            val emailPredicate = criteriaBuilder.like(
                criteriaBuilder.lower(emailPath),
                likePattern
            )

            criteriaQuery.where(criteriaBuilder.or(displayNamePredicate, emailPredicate))
        }

        return entityManager.createQuery(criteriaQuery).singleResult
    }
}
```

#### Step 4: Service Layer Usage

```kotlin
@Singleton
class UserManagementService(
    private val userRepository: UserRepository,           // Auto-generated methods
    private val userSearchRepository: UserRepositoryCustom, // Custom methods
) {
    fun searchUsers(query: String?, first: Int, after: String?): Connection<User> {
        // Use custom repository for complex queries
        val entities = userSearchRepository.searchByNameOrEmail(query, first + 1, afterCursor)
        val totalCount = userSearchRepository.countByNameOrEmail(query)
        
        // Use standard repository for simple queries
        val user = userRepository.findByEmail(email)
        
        // ...
    }
}
```

### Naming Conventions

- **Main Repository**: `{EntityName}Repository` (e.g., `UserRepository`)
- **Custom Interface**: `{EntityName}RepositoryCustom` (e.g., `UserRepositoryCustom`)
- **Implementation Class**: `{EntityName}RepositoryImpl` (e.g., `UserRepositoryImpl`)

### @ReadOnly Annotation

**IMPORTANT**: Always add `@ReadOnly` annotation to read-only query methods in custom implementations.

The `@ReadOnly` annotation:
- Optimizes database connection usage for read-only operations
- Allows database connection pools to route queries to read replicas (if configured)
- Improves performance by indicating the query won't modify data
- Should be used on: searches, counts, finds, and any query that only reads data
- Should NOT be used on: save, delete, update, or any method that modifies data

```kotlin
import io.micronaut.transaction.annotation.ReadOnly

@ReadOnly
override fun searchByNameOrEmail(...): List<UserEntity> {
    // Read-only query implementation
}

@ReadOnly
override fun countByNameOrEmail(...): Long {
    // Read-only count query
}

// ❌ DON'T use @ReadOnly on write operations
override fun saveUser(...): UserEntity {
    // Write operation - no @ReadOnly
}
```

### Key Rules

1. ✅ **DO**: Keep auto-generatable methods in the main repository interface
2. ✅ **DO**: Put custom methods in a separate `{EntityName}RepositoryCustom` interface
3. ✅ **DO**: Implement the custom interface in a `@Singleton` class
4. ✅ **DO**: Use **Micronaut Query Builder** (EntityManager + JPA Criteria API) as the standard implementation approach
5. ✅ **DO**: Add `@ReadOnly` annotation to all read-only query methods (searches, counts, finds)
6. ✅ **DO**: Import `io.micronaut.transaction.annotation.ReadOnly` in implementation classes
7. ✅ **DO**: Inject both repositories in services that need both
8. ❌ **DON'T**: Add non-standard method names to the main repository interface
9. ❌ **DON'T**: Use `@Query` annotation on methods in custom interfaces (not needed)
10. ❌ **DON'T**: Extend `JpaRepository` in the custom interface
11. ❌ **DON'T**: Forget `@ReadOnly` on read-only queries (performance optimization)
12. ❌ **DON'T**: Use `@ReadOnly` on write operations (save, delete, update)

### Common Errors

#### ❌ Error: Adding Custom Methods to Main Repository

```kotlin
// ❌ INCORRECT - Micronaut Data will try to auto-generate and fail
@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun searchByNameOrEmail(query: String?, first: Int, after: UUID?): List<UserEntity>
    // Error: Cannot query entity on non-existent property: Name
}
```

#### ✅ Correct: Separate Custom Interface

```kotlin
// ✅ CORRECT - Main repository only has auto-generatable methods
@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): UserEntity?
}

// ✅ CORRECT - Custom methods in separate interface
interface UserRepositoryCustom {
    fun searchByNameOrEmail(query: String?, first: Int, after: UUID?): List<UserEntity>
}
```

## Related Documentation

- [Entity Pattern](./entity-pattern.md) - Entity structure
- [Service Pattern](./service-pattern.md) - Next layer: business logic
- [Kotlin Coding Standards](../05-standards/coding-standards/kotlin-standards.md) - General conventions
