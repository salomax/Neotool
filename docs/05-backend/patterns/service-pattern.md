---
title: Service Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [service, business-logic, domain, transaction, pattern]
ai_optimized: true
search_keywords: [service, @Singleton, @Transactional, domain, entity, toDomain, toEntity]
related:
  - 04-patterns/backend-patterns/repository-pattern.md
  - 04-patterns/backend-patterns/domain-entity-conversion.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Service Pattern

> **Purpose**: Standard pattern for creating business logic services, handling domain-entity conversion, transactions, and business rules.

## Overview

Services contain business logic and coordinate between repositories and domain objects. They handle domain-entity conversion, transactions, validation, and logging.

## Core Requirements

### Required Annotations

Every service MUST have:

1. `@Singleton` - Marks the class as a Micronaut singleton bean
2. `@Transactional` - On all write operations (create, update, delete)

### Required Structure

```kotlin
@Singleton
open class {EntityName}Service(
    private val repo: {EntityName}Repository,
) {
    // Read operations (no @Transactional)
    fun list(): List<{DomainName}>
    fun get(id: UUID): {DomainName}?
    
    // Write operations (with @Transactional)
    @Transactional
    open fun create(domain: {DomainName}): {DomainName}
    
    @Transactional
    open fun update(domain: {DomainName}): {DomainName}
    
    @Transactional
    open fun delete(id: UUID)
}
```

## Pattern Structure

### Complete Example: ProductService

```kotlin
package io.github.salomax.neotool.example.service

import io.github.salomax.neotool.common.logging.LoggingUtils.logAuditData
import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.repo.ProductRepository
import io.micronaut.http.server.exceptions.NotFoundException
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

@Singleton
open class ProductService(
    private val repo: ProductRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun list(): List<Product> {
        val entities = repo.findAll()
        val products = entities.map { it.toDomain() }
        logAuditData("SELECT_ALL", "ProductService", null, "count" to products.size)
        return products
    }

    fun get(id: UUID): Product? {
        val entity = repo.findById(id).orElse(null)
        val product = entity?.toDomain()
        if (product != null) {
            logAuditData("SELECT_BY_ID", "ProductService", id.toString())
            logger.debug { "Product found: ${product.name} (SKU: ${product.sku})" }
        } else {
            logAuditData("SELECT_BY_ID", "ProductService", id.toString(), "result" to "NOT_FOUND")
            logger.debug { "Product not found with ID: $id" }
        }
        return product
    }

    @Transactional
    open fun create(product: Product): Product {
        val entity = product.toEntity()
        val saved = repo.save(entity)
        val result = saved.toDomain()
        logAuditData("INSERT", "ProductService", result.id.toString(), "name" to result.name, "sku" to result.sku)
        logger.info { "Product created successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    @Transactional
    open fun update(product: Product): Product {
        val updatedEntity = product.toEntity()
        val saved = repo.update(updatedEntity)
        val result = saved.toDomain()
        logAuditData("UPDATE", "ProductService", result.id.toString(), "name" to result.name, "sku" to result.sku)
        logger.info { "Product updated successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    @Transactional
    open fun delete(id: UUID) {
        val found =
            repo.findById(id).orElseThrow {
                logger.warn { "Attempted to delete non-existent product with ID: $id" }
                NotFoundException()
            }
        repo.delete(found)
        logAuditData("DELETE", "ProductService", id.toString(), "name" to found.name, "sku" to found.sku)
        logger.info { "Product deleted successfully: ${found.name} (ID: $id)" }
    }
}
```

## Operation Patterns

### Pattern: List Operation (Read)

```kotlin
fun list(): List<{DomainName}> {
    val entities = repo.findAll()
    val domains = entities.map { it.toDomain() }
    logAuditData("SELECT_ALL", "{ServiceName}", null, "count" to domains.size)
    return domains
}
```

**Key Points**:
- No `@Transactional` (read-only)
- Convert entities to domain objects
- Log audit data

### Pattern: Get by ID (Read)

```kotlin
fun get(id: UUID): {DomainName}? {
    val entity = repo.findById(id).orElse(null)
    val domain = entity?.toDomain()
    if (domain != null) {
        logAuditData("SELECT_BY_ID", "{ServiceName}", id.toString())
        logger.debug { "{EntityName} found: ${domain.name}" }
    } else {
        logAuditData("SELECT_BY_ID", "{ServiceName}", id.toString(), "result" to "NOT_FOUND")
        logger.debug { "{EntityName} not found with ID: $id" }
    }
    return domain
}
```

**Key Points**:
- No `@Transactional` (read-only)
- Handle null case gracefully
- Return nullable domain object
- Log both success and not-found cases

### Pattern: Create Operation (Write)

```kotlin
@Transactional
open fun create(domain: {DomainName}): {DomainName} {
    val entity = domain.toEntity()
    val saved = repo.save(entity)
    val result = saved.toDomain()
    logAuditData("INSERT", "{ServiceName}", result.id.toString(), "name" to result.name)
    logger.info { "{EntityName} created successfully: ${result.name} (ID: ${result.id})" }
    return result
}
```

**Key Points**:
- `@Transactional` required
- Method must be `open` (for transaction proxy)
- Convert domain to entity
- Save entity
- Convert back to domain
- Log audit data

### Pattern: Update Operation (Write)

**Simple Update**:
```kotlin
@Transactional
open fun update(domain: {DomainName}): {DomainName} {
    val updatedEntity = domain.toEntity()
    val saved = repo.update(updatedEntity)
    val result = saved.toDomain()
    logAuditData("UPDATE", "{ServiceName}", result.id.toString(), "name" to result.name)
    logger.info { "{EntityName} updated successfully: ${result.name} (ID: ${result.id})" }
    return result
}
```

**Update with Optimistic Locking**:
```kotlin
@Transactional
open fun update(domain: {DomainName}): {DomainName} {
    logger.debug { "{ServiceName}.update - domain: $domain, version: ${domain.version}" }
    
    // Fetch existing entity to preserve version and other fields
    val existingEntity =
        repo.findById(domain.id!!).orElseThrow {
            logger.warn { "Attempted to update non-existent {entityName} with ID: ${domain.id}" }
            NotFoundException()
        }
    
    logger.debug { "{ServiceName}.update - existingEntity version: ${existingEntity.version}" }
    
    // Check optimistic locking
    if (existingEntity.version != domain.version) {
        throw org.hibernate.StaleObjectStateException(
            "{EntityName} with id ${domain.id} was modified by another user",
            domain.id,
        )
    }
    
    // Update only changed fields
    existingEntity.name = domain.name
    existingEntity.updatedAt = java.time.Instant.now()
    
    // Save (JPA will auto-increment version)
    val saved = repo.save(existingEntity)
    val result = saved.toDomain()
    logAuditData("UPDATE", "{ServiceName}", result.id.toString(), "name" to result.name)
    logger.info { "{EntityName} updated successfully: ${result.name} (ID: ${result.id})" }
    return result
}
```

**Key Points**:
- `@Transactional` required
- Method must be `open`
- Fetch existing entity first (for optimistic locking)
- Check version match
- Update fields on existing entity
- Update `updatedAt` timestamp
- Log audit data

### Pattern: Delete Operation (Write)

```kotlin
@Transactional
open fun delete(id: UUID) {
    val found =
        repo.findById(id).orElseThrow {
            logger.warn { "Attempted to delete non-existent {entityName} with ID: $id" }
            NotFoundException()
        }
    repo.delete(found)
    logAuditData("DELETE", "{ServiceName}", id.toString(), "name" to found.name)
    logger.info { "{EntityName} deleted successfully: ${found.name} (ID: $id)" }
}
```

**Key Points**:
- `@Transactional` required
- Method must be `open`
- Find entity first (for logging)
- Throw `NotFoundException` if not found
- Delete entity
- Log audit data

## Domain-Entity Conversion

### Pattern: Domain to Entity

```kotlin
// In domain object
fun toEntity(): {EntityName}Entity {
    return {EntityName}Entity(
        id = this.id,  // For UUID?: pass directly, for UUID use: id ?: UUID.randomUUID()
        name = this.name,
        // ... other fields
    )
}
```

**See**: [Domain-Entity Conversion Pattern](./domain-entity-conversion.md) for detailed rules.

### Pattern: Entity to Domain

```kotlin
// In entity
fun toDomain(): {DomainName} {
    return {DomainName}(
        id = this.id,  // UUID can be assigned to UUID?
        name = this.name,
        // ... other fields
    )
}
```

## Common Errors and How to Avoid Them

### ❌ Error: Missing @Singleton Annotation

```kotlin
// ❌ Incorrect - Not a bean, can't be injected
open class ProductService(
    private val repo: ProductRepository,
)

// ✅ Correct
@Singleton
open class ProductService(
    private val repo: ProductRepository,
)
```

### ❌ Error: Missing @Transactional on Write Operations

```kotlin
// ❌ Incorrect - No transaction, data inconsistency risk
open fun create(product: Product): Product {
    val entity = product.toEntity()
    val saved = repo.save(entity)
    return saved.toDomain()
}

// ✅ Correct
@Transactional
open fun create(product: Product): Product {
    val entity = product.toEntity()
    val saved = repo.save(entity)
    return saved.toDomain()
}
```

### ❌ Error: Final Methods (Not Open)

```kotlin
// ❌ Incorrect - Transaction proxy can't wrap final methods
@Transactional
fun create(product: Product): Product { ... }

// ✅ Correct
@Transactional
open fun create(product: Product): Product { ... }
```

### ❌ Error: Wrong Domain-Entity Conversion

```kotlin
// ❌ Incorrect - UUID? to UUID without handling null
@Transactional
open fun create(product: Product): Product {
    val entity = ProductEntity(
        id = product.id,  // Error: UUID? cannot be assigned to UUID
        // ...
    )
}

// ✅ Correct - Handle nullable ID (see domain-entity-conversion.md)
@Transactional
open fun create(product: Product): Product {
    val entity = product.toEntity()  // Domain handles conversion
    val saved = repo.save(entity)
    return saved.toDomain()
}
```

### ❌ Error: Not Converting Entity to Domain

```kotlin
// ❌ Incorrect - Returning entity instead of domain
fun get(id: UUID): ProductEntity? {
    return repo.findById(id).orElse(null)
}

// ✅ Correct - Convert to domain
fun get(id: UUID): Product? {
    val entity = repo.findById(id).orElse(null)
    return entity?.toDomain()
}
```

### ❌ Error: Missing Error Handling

```kotlin
// ❌ Incorrect - No error handling
@Transactional
open fun delete(id: UUID) {
    repo.deleteById(id)  // May throw if not found
}

// ✅ Correct - Handle not found
@Transactional
open fun delete(id: UUID) {
    val found = repo.findById(id).orElseThrow {
        logger.warn { "Attempted to delete non-existent product with ID: $id" }
        NotFoundException()
    }
    repo.delete(found)
}
```

## Quick Reference Checklist

When creating a service, verify:

- [ ] `@Singleton` annotation present
- [ ] Class is `open` (for transaction proxies)
- [ ] Repository injected via constructor
- [ ] Logger initialized: `private val logger = KotlinLogging.logger {}`
- [ ] Read operations (list, get) have NO `@Transactional`
- [ ] Write operations (create, update, delete) have `@Transactional`
- [ ] Write operation methods are `open`
- [ ] All operations convert entity ↔ domain correctly
- [ ] Error handling for not-found cases
- [ ] Audit logging with `logAuditData()`
- [ ] Debug/info logging for operations
- [ ] Package name follows `io.github.salomax.neotool.{module}.service`

## Required Imports

```kotlin
import io.github.salomax.neotool.common.logging.LoggingUtils.logAuditData
import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.repo.{EntityName}Repository
import io.micronaut.http.server.exceptions.NotFoundException
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID
```

## Related Documentation

- [Repository Pattern](./repository-pattern.md) - Data access layer
- [Domain-Entity Conversion Pattern](./domain-entity-conversion.md) - Conversion rules
- [Resolver Pattern](./resolver-pattern.md) - Next layer: GraphQL API
- [Kotlin Coding Standards](../05-standards/coding-standards/kotlin-standards.md) - General conventions
