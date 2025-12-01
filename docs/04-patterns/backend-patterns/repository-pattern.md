---
title: Repository Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [repository, micronaut-data, jpa, data-access, pattern]
ai_optimized: true
search_keywords: [repository, @Repository, JpaRepository, Micronaut Data, interface]
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

When creating a repository, verify:

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

## Related Documentation

- [Entity Pattern](./entity-pattern.md) - Entity structure
- [Service Pattern](./service-pattern.md) - Next layer: business logic
- [Kotlin Coding Standards](../05-standards/coding-standards/kotlin-standards.md) - General conventions
