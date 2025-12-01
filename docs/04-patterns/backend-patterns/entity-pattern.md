---
title: Entity Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [entity, jpa, kotlin, database, pattern]
ai_optimized: true
search_keywords: [entity, jpa, @Entity, @Table, @Id, @Version, BaseEntity]
related:
  - 04-patterns/backend-patterns/domain-entity-conversion.md
  - 05-standards/database-standards/schema-standards.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Entity Pattern

> **Purpose**: Standard pattern for creating JPA entities in NeoTool, ensuring consistency, type safety, and proper database mapping.

## Overview

Entities represent database tables in the JPA layer. They must follow specific patterns for ID generation, versioning, timestamps, and domain conversion.

## Core Requirements

### Required Annotations

Every entity MUST have:

1. `@Entity` - Marks the class as a JPA entity
2. `@Table(name = "...")` - Specifies the database table name (snake_case)
3. `@Id` - Marks the primary key field
4. `@Version` - Enables optimistic locking (REQUIRED)
5. `@Column` - Specifies column properties (nullable, unique, name)

### Required Fields

Every entity MUST have:

1. **ID field** - Primary key (UUID or Int)
2. **Version field** - For optimistic locking (`@Version`)
3. **Timestamps** - `createdAt` and `updatedAt` (Instant type)
4. **toDomain() method** - Converts entity to domain object

## Pattern Structure

### UUID-based Entity (Recommended)

```kotlin
package io.github.salomax.neotool.{module}.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.{module}.domain.{EntityName}
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "{table_name}")
open class {EntityName}Entity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID?,
    @Column(nullable = false)
    open var name: String,
    // ... other fields
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): {EntityName} {
        return {EntityName}(
            id = this.id,
            name = this.name,
            // ... other fields
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
```

### Complete Example: ProductEntity

```kotlin
package io.github.salomax.neotool.example.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.example.domain.Product
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
open class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID?,
    @Column(nullable = false)
    open var name: String,
    @Column(nullable = false, unique = true)
    open var sku: String,
    @Column(name = "price_cents", nullable = false)
    open var priceCents: Long = 0,
    @Column(nullable = false)
    open var stock: Int = 0,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): Product {
        return Product(
            id = this.id,
            name = this.name,
            sku = this.sku,
            priceCents = this.priceCents,
            stock = this.stock,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
```

## Field Patterns

### ID Field

**UUID-based (Recommended)**:
```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(columnDefinition = "uuid")
override val id: UUID?,
```

**Int-based (Auto-generated)**:
```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
override val id: Int? = null,
```

### String Fields

```kotlin
@Column(nullable = false)
open var name: String,

@Column(nullable = false, unique = true)
open var email: String,
```

### Numeric Fields

```kotlin
@Column(name = "price_cents", nullable = false)
open var priceCents: Long = 0,

@Column(nullable = false)
open var stock: Int = 0,
```

### Enum Fields

```kotlin
@Enumerated(EnumType.STRING)
@Column(nullable = false)
open var status: CustomerStatus = CustomerStatus.ACTIVE,
```

### Timestamp Fields

```kotlin
@Column(name = "created_at", nullable = false)
open var createdAt: Instant = Instant.now(),

@Column(name = "updated_at", nullable = false)
open var updatedAt: Instant = Instant.now(),
```

### Version Field (REQUIRED)

```kotlin
@Version
open var version: Long = 0,
```

## Common Patterns

### Pattern: Extending BaseEntity

**Rule**: All entities MUST extend `BaseEntity<T>` where T is the ID type.

**Rationale**: Provides consistent equals/hashCode and common functionality.

```kotlin
open class ProductEntity(
    // ... fields
) : BaseEntity<UUID?>(id)
```

### Pattern: Open Classes

**Rule**: Entity classes MUST be `open` (not `final`).

**Rationale**: JPA requires open classes for proxy generation.

```kotlin
open class ProductEntity(...)  // ✅ Correct
class ProductEntity(...)      // ❌ Incorrect - JPA won't work
```

### Pattern: Open Properties

**Rule**: Entity properties MUST be `open var` (not `val` or `final`).

**Rationale**: JPA needs to modify properties for lazy loading and updates.

```kotlin
open var name: String  // ✅ Correct
val name: String       // ❌ Incorrect - can't be updated
var name: String       // ❌ Incorrect - not open, JPA proxy issues
```

### Pattern: Column Naming

**Rule**: Use snake_case for database column names, camelCase for Kotlin properties.

```kotlin
@Column(name = "price_cents")  // Database column
open var priceCents: Long      // Kotlin property
```

## Common Errors and How to Avoid Them

### ❌ Error: Missing @Entity Annotation

```kotlin
// ❌ Incorrect
@Table(name = "products")
class ProductEntity(...)

// ✅ Correct
@Entity
@Table(name = "products")
open class ProductEntity(...)
```

### ❌ Error: Missing @Version Field

```kotlin
// ❌ Incorrect - No optimistic locking
@Entity
@Table(name = "products")
open class ProductEntity(
    @Id
    override val id: UUID?,
    // Missing @Version
)

// ✅ Correct
@Entity
@Table(name = "products")
open class ProductEntity(
    @Id
    override val id: UUID?,
    @Version
    open var version: Long = 0,
)
```

### ❌ Error: Wrong ID Type

```kotlin
// ❌ Incorrect - UUID? in entity but domain expects UUID
open class ProductEntity(
    @Id
    override val id: UUID,  // Non-nullable
)

// ✅ Correct - Match domain pattern
open class ProductEntity(
    @Id
    override val id: UUID?,  // Nullable for new entities
)
```

### ❌ Error: Missing BaseEntity Extension

```kotlin
// ❌ Incorrect
@Entity
open class ProductEntity(
    @Id
    val id: UUID?,
)

// ✅ Correct
@Entity
open class ProductEntity(
    @Id
    override val id: UUID?,
) : BaseEntity<UUID?>(id)
```

### ❌ Error: Final Class or Properties

```kotlin
// ❌ Incorrect - JPA can't create proxies
class ProductEntity(...)
val name: String

// ✅ Correct
open class ProductEntity(...)
open var name: String
```

### ❌ Error: Missing toDomain() Method

```kotlin
// ❌ Incorrect - No conversion method
open class ProductEntity(...) {
    // Missing toDomain()
}

// ✅ Correct
open class ProductEntity(...) {
    fun toDomain(): Product {
        return Product(
            id = this.id,
            // ... map all fields
        )
    }
}
```

## Quick Reference Checklist

When creating an entity, verify:

- [ ] `@Entity` annotation present
- [ ] `@Table(name = "...")` with snake_case table name
- [ ] Extends `BaseEntity<T>` where T is ID type
- [ ] Class is `open` (not final)
- [ ] All properties are `open var` (not `val` or final)
- [ ] `@Id` annotation on ID field
- [ ] `@GeneratedValue(strategy = GenerationType.IDENTITY)` on ID
- [ ] `@Version` field present for optimistic locking
- [ ] `createdAt` and `updatedAt` timestamp fields
- [ ] `toDomain()` method implemented
- [ ] Package name follows `io.github.salomax.neotool.{module}.entity`
- [ ] Column names use snake_case in `@Column(name = "...")`
- [ ] Nullable fields marked with `nullable = true` in `@Column`
- [ ] Unique fields marked with `unique = true` in `@Column`

## Required Imports

```kotlin
import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.{module}.domain.{EntityName}
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
```

## Related Documentation

- [Domain-Entity Conversion Pattern](./domain-entity-conversion.md) - Converting between domain and entity
- [Database Standards](../05-standards/database-standards/schema-standards.md) - Database schema rules
- [Kotlin Coding Standards](../05-standards/coding-standards/kotlin-standards.md) - General Kotlin conventions
- [Repository Pattern](./repository-pattern.md) - Next layer: data access
