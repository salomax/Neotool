---
title: Domain Model Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [domain, model, entity, repository, database, sql, jpa, pattern, consolidated]
ai_optimized: true
search_keywords: [domain, model, entity, repository, database, schema, migration, jpa, sql, uuid, conversion]
related:
  - 04-patterns/backend-patterns/entity-pattern.md
  - 04-patterns/backend-patterns/repository-pattern.md
  - 04-patterns/backend-patterns/domain-entity-conversion.md
  - 04-patterns/backend-patterns/uuid-v7-pattern.md
  - 05-standards/database-standards/schema-standards.md
---

# Domain Model Pattern

> **Purpose**: Consolidated quick reference guide for all SQL modeling patterns in NeoTool. This document summarizes database schema, entity, domain conversion, and repository patterns for rapid implementation reference.

## Overview

This consolidated guide covers the complete domain modeling flow from database schema to repository layer:

1. **Database Schema** → Migration files with schema organization
2. **JPA Entity** → Database table mapping with annotations
3. **Domain Object** → Business logic representation
4. **Repository** → Data access layer

For detailed explanations, see the related pattern documents linked above.

## Complete Flow Example

### 1. Database Migration

```sql
-- V1_1__create_products_table.sql
SET search_path TO app, public;

-- Install UUID v7 extension (PostgreSQL < 18 only)
-- Note: PostgreSQL 18+ has uuidv7() built-in, no extension needed
CREATE EXTENSION IF NOT EXISTS pg_uuidv7;

-- Create schema if needed
CREATE SCHEMA IF NOT EXISTS app;

-- Create table with UUID v7 primary key
CREATE TABLE IF NOT EXISTS app.products (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(255) NOT NULL UNIQUE,
    price_cents BIGINT NOT NULL DEFAULT 0,
    stock INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_products_sku ON app.products(sku);
```

### 2. Domain Object

```kotlin
package io.github.salomax.neotool.example.domain

import java.time.Instant
import java.util.UUID

data class Product(
    val id: UUID? = null,  // Nullable for new entities
    val name: String,
    val sku: String,
    val priceCents: Long = 0,
    val stock: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): ProductEntity {
        return ProductEntity(
            id = this.id,  // Pass null for UUID v7, let DB generate
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

### 3. JPA Entity

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
@Table(name = "products", schema = "app")
open class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,  // Nullable, DB generates UUID v7
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

### 4. Repository

```kotlin
package io.github.salomax.neotool.example.repo

import io.github.salomax.neotool.example.entity.ProductEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findBySku(sku: String): ProductEntity?
    fun findAllByStockGreaterThan(stock: Int): List<ProductEntity>
    fun existsBySku(sku: String): Boolean
}
```

## Schema Organization Rules

### Rule: All Tables Must Be in a Schema

**MANDATORY**: ANY TABLE MUST BE INCLUDED IN A SCHEMA. NEVER IN PUBLIC.

```sql
-- ✅ Correct
CREATE TABLE app.customers (...);
CREATE TABLE security.users (...);

-- ❌ Incorrect
CREATE TABLE customers (...);
CREATE TABLE public.customers (...);
```

### Rule: Schema Naming

Use module/service name as schema name (e.g., `app`, `security`, `assistant`).

### Rule: Entity Schema Annotation

JPA entities MUST specify the schema in `@Table` annotation:

```kotlin
// ✅ Correct
@Entity
@Table(name = "customers", schema = "app")
open class CustomerEntity(...)

// ❌ Incorrect
@Entity
@Table(name = "customers")
open class CustomerEntity(...)
```

## Migration Patterns

### Migration Naming

Format: `V{version}__{description}.sql`

```sql
-- ✅ Correct
V1_1__create_customers_table.sql
V1_2__add_email_index.sql

-- ❌ Incorrect
V1__customers.sql
migration_1.sql
```

### Migration Structure

```sql
-- ✅ Correct - Always include:
SET search_path TO {schema}, public;

-- Install UUID v7 extension (PostgreSQL < 18 only)
-- Note: PostgreSQL 18+ has uuidv7() built-in, no extension needed
CREATE EXTENSION IF NOT EXISTS pg_uuidv7;  -- For PostgreSQL < 18

CREATE TABLE IF NOT EXISTS {schema}.{table} (
    -- columns
);

CREATE INDEX IF NOT EXISTS idx_{table}_{column} ON {schema}.{table}({column});
```

### Migration Idempotency

All migrations MUST be idempotent (safe to run multiple times):

```sql
-- ✅ Correct
CREATE TABLE IF NOT EXISTS app.customers (...);
CREATE INDEX IF NOT EXISTS idx_customers_email ON app.customers(email);

-- ❌ Incorrect
CREATE TABLE app.customers (...);
CREATE INDEX idx_customers_email ON app.customers(email);
```

## Entity Patterns

### Required Annotations

Every entity MUST have:

1. `@Entity` - Marks the class as a JPA entity
2. `@Table(name = "...", schema = "...")` - Specifies table and schema
3. `@Id` - Marks the primary key field
4. `@Version` - Enables optimistic locking (REQUIRED)
5. `@Column` - Specifies column properties

### Required Fields

Every entity MUST have:

1. **ID field** - Primary key (UUID v7 or Int)
2. **Version field** - `@Version open var version: Long = 0`
3. **Timestamps** - `createdAt` and `updatedAt` (Instant type)
4. **toDomain() method** - Converts entity to domain object

### Class Requirements

- **MUST be `open`** (not final) - JPA requires open classes for proxy generation
- **MUST extend `BaseEntity<T>`** - Provides consistent equals/hashCode
- **Properties MUST be `open var`** (not `val` or final) - JPA needs to modify properties

### Field Patterns

#### UUID v7 Primary Key (Recommended)

```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(columnDefinition = "uuid")
override val id: UUID? = null,  // Nullable, DB generates UUID v7
```

**Migration**:
```sql
CREATE EXTENSION IF NOT EXISTS pg_uuidv7;
CREATE TABLE IF NOT EXISTS app.products (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- other columns
);
```

#### Int-based Primary Key

```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
override val id: Int? = null,  // Nullable, DB auto-increments
```

#### String Fields

```kotlin
@Column(nullable = false)
open var name: String,

@Column(nullable = false, unique = true)
open var email: String,
```

#### Numeric Fields

```kotlin
@Column(name = "price_cents", nullable = false)
open var priceCents: Long = 0,

@Column(nullable = false)
open var stock: Int = 0,
```

#### Enum Fields

```kotlin
@Enumerated(EnumType.STRING)  // Always use STRING, not ORDINAL
@Column(nullable = false)
open var status: CustomerStatus = CustomerStatus.ACTIVE,
```

#### Timestamp Fields

```kotlin
@Column(name = "created_at", nullable = false)
open var createdAt: Instant = Instant.now(),

@Column(name = "updated_at", nullable = false)
open var updatedAt: Instant = Instant.now(),
```

#### Version Field (REQUIRED)

```kotlin
@Version
open var version: Long = 0,
```

### Column Naming Convention

- **Database columns**: snake_case (`created_at`, `user_id`)
- **Kotlin properties**: camelCase (`createdAt`, `userId`)

```kotlin
@Column(name = "price_cents")  // Database column
open var priceCents: Long      // Kotlin property
```

## Domain-Entity Conversion

### Core Principle

- **Domain objects**: Use nullable IDs (`UUID?` or `Int?`) for new entities
- **JPA entities**: Use nullable IDs (`UUID?` or `Int?`) with database generation

### UUID v7 Conversion Pattern

**Domain → Entity**: Pass `null`, let database generate UUID v7

```kotlin
// Domain Object
data class User(
    val id: UUID? = null,  // Nullable for new entities
    val email: String,
) {
    fun toEntity(): UserEntity {
        return UserEntity(
            id = this.id,  // Pass null, DB generates UUID v7
            email = this.email,
        )
    }
}
```

**Entity → Domain**: Pass ID directly (UUID? → UUID? is safe)

```kotlin
// Entity
open class UserEntity(
    override val id: UUID? = null,
    // ...
) {
    fun toDomain(): User {
        return User(
            id = this.id,  // UUID? → UUID? is safe
            email = this.email,
        )
    }
}
```

### Int-based Conversion Pattern

**Both use nullable `Int?`** - Pass directly:

```kotlin
// Domain → Entity
fun toEntity(): RoleEntity {
    return RoleEntity(
        id = this.id,  // Both nullable, no conversion needed
        name = this.name,
    )
}

// Entity → Domain
fun toDomain(): Role {
    return Role(
        id = this.id,  // Both nullable, direct assignment
        name = this.name,
    )
}
```

## Repository Patterns

### Standard Repository

```kotlin
@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    // Only methods Micronaut Data can auto-generate
    fun findBySku(sku: String): ProductEntity?
    fun findAllByStatus(status: ProductStatus): List<ProductEntity>
    fun existsBySku(sku: String): Boolean
    fun countByStockLessThan(stock: Int): Long
}
```

### Query Method Patterns

#### Find by Single Field
```kotlin
fun findBy{FieldName}({fieldName}: {Type}): {EntityName}Entity?
// Example: fun findBySku(sku: String): ProductEntity?
```

#### Find by Multiple Fields
```kotlin
fun findBy{Field1}And{Field2}({field1}: {Type1}, {field2}: {Type2}): {EntityName}Entity?
// Example: fun findByEmailAndStatus(email: String, status: CustomerStatus): CustomerEntity?
```

#### Find All by Field
```kotlin
fun findAllBy{FieldName}({fieldName}: {Type}): List<{EntityName}Entity>
// Example: fun findAllByStatus(status: CustomerStatus): List<CustomerEntity>
```

#### Exists Checks
```kotlin
fun existsBy{FieldName}({fieldName}: {Type}): Boolean
// Example: fun existsBySku(sku: String): Boolean
```

#### Count Queries
```kotlin
fun countBy{FieldName}({fieldName}: {Type}): Long
// Example: fun countByStatus(status: CustomerStatus): Long
```

### Custom Repository Pattern

For complex queries, use a separate custom interface:

```kotlin
// 1. Main Repository (auto-generated methods only)
@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): UserEntity?
}

// 2. Custom Interface (complex queries)
interface UserRepositoryCustom {
    fun searchByNameOrEmail(query: String?, first: Int, after: UUID?): List<UserEntity>
}

// 3. Custom Implementation (uses EntityManager + JPA Criteria API)
@Singleton
class UserRepositoryImpl(
    private val entityManager: EntityManager,
) : UserRepositoryCustom {
    @ReadOnly
    override fun searchByNameOrEmail(...): List<UserEntity> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(UserEntity::class.java)
        val root = criteriaQuery.from(UserEntity::class.java)
        // ... build dynamic query
        return entityManager.createQuery(criteriaQuery).resultList
    }
}
```

**Key Rules**:
- Main repository: Only auto-generatable methods
- Custom interface: Does NOT extend `JpaRepository`, no `@Repository` annotation
- Implementation: `@Singleton`, uses EntityManager + JPA Criteria API
- Read-only methods: Annotate with `@ReadOnly`

## Index Rules

### Index Naming

Format: `idx_{table}_{columns}`

```sql
-- ✅ Correct
CREATE INDEX idx_customers_email ON app.customers(email);
CREATE INDEX idx_orders_customer_id ON app.orders(customer_id);

-- ❌ Incorrect
CREATE INDEX email_idx ON app.customers(email);
CREATE INDEX idx1 ON app.orders(customer_id);
```

### Index on Foreign Keys

**Rule**: Create indexes on all foreign key columns.

```sql
-- ✅ Correct
CREATE TABLE app.orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app.customers(id)
);
CREATE INDEX idx_orders_customer_id ON app.orders(customer_id);
```

## Implementation Checklist

### Database Migration Checklist

- [ ] Migration file named: `V{version}__{description}.sql`
- [ ] Sets search path: `SET search_path TO {schema}, public;`
- [ ] **PostgreSQL 18+**: No extension needed, `uuidv7()` is built-in
- [ ] **PostgreSQL < 18**: Installs `pg_uuidv7` extension if using UUID v7
- [ ] Creates schema if needed: `CREATE SCHEMA IF NOT EXISTS {schema};`
- [ ] Table in named schema (not `public`): `CREATE TABLE IF NOT EXISTS {schema}.{table}`
- [ ] UUID v7 primary key: `id UUID PRIMARY KEY DEFAULT uuidv7()`
- [ ] All operations use `IF NOT EXISTS` (idempotent)
- [ ] Indexes on foreign keys: `CREATE INDEX IF NOT EXISTS idx_{table}_{column}`
- [ ] Index naming follows `idx_{table}_{columns}` format

### Entity Checklist

- [ ] `@Entity` annotation present
- [ ] `@Table(name = "...", schema = "...")` with snake_case table name and schema
- [ ] Extends `BaseEntity<T>` where T is ID type
- [ ] Class is `open` (not final)
- [ ] All properties are `open var` (not `val` or final)
- [ ] `@Id` annotation on ID field
- [ ] `@GeneratedValue(strategy = GenerationType.IDENTITY)` on ID
- [ ] UUID v7: `id: UUID? = null` (nullable, DB generates)
- [ ] `@Version` field present for optimistic locking
- [ ] `createdAt` and `updatedAt` timestamp fields
- [ ] `toDomain()` method implemented
- [ ] Package name follows `io.github.salomax.neotool.{module}.entity`
- [ ] Column names use snake_case in `@Column(name = "...")`
- [ ] Nullable fields marked with `nullable = true` in `@Column`
- [ ] Unique fields marked with `unique = true` in `@Column`
- [ ] Enums use `@Enumerated(EnumType.STRING)`

### Domain Object Checklist

- [ ] ID type is nullable (`UUID?` or `Int?`)
- [ ] `toEntity()` method implemented
- [ ] UUID v7: Passes `null` for new entities (let DB generate)
- [ ] Int-based: Passes `id` directly (both nullable)

### Repository Checklist

- [ ] `@Repository` annotation present
- [ ] Extends `JpaRepository<EntityType, IDType>`
- [ ] Entity type matches the entity class
- [ ] ID type matches entity ID type (UUID or Int)
- [ ] Package name follows `io.github.salomax.neotool.{module}.repo`
- [ ] Query methods use "findBy" prefix (not "getBy" or "queryBy")
- [ ] Single entity queries return nullable type (`Entity?`)
- [ ] List queries return `List<Entity>`
- [ ] Method names follow Micronaut Data conventions
- [ ] Only auto-generatable methods in main repository
- [ ] Custom queries in separate `{EntityName}RepositoryCustom` interface
- [ ] Custom implementation uses EntityManager + JPA Criteria API
- [ ] Read-only methods annotated with `@ReadOnly`

## Common Errors

### ❌ Missing Schema in Entity

```kotlin
// ❌ Incorrect
@Entity
@Table(name = "customers")
open class CustomerEntity(...)

// ✅ Correct
@Entity
@Table(name = "customers", schema = "app")
open class CustomerEntity(...)
```

### ❌ Missing @Version Field

```kotlin
// ❌ Incorrect - No optimistic locking
@Entity
@Table(name = "products", schema = "app")
open class ProductEntity(
    @Id
    override val id: UUID?,
    // Missing @Version
)

// ✅ Correct
@Entity
@Table(name = "products", schema = "app")
open class ProductEntity(
    @Id
    override val id: UUID?,
    @Version
    open var version: Long = 0,
)
```

### ❌ Final Class or Properties

```kotlin
// ❌ Incorrect - JPA can't create proxies
class ProductEntity(...)
val name: String

// ✅ Correct
open class ProductEntity(...)
open var name: String
```

### ❌ Wrong UUID Generation

```kotlin
// ❌ Incorrect - Application generates UUID v4
open class UserEntity(
    override val id: UUID = UUID.randomUUID(),  // Wrong!
)

// ✅ Correct - Database generates UUID v7
open class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,  // DB generates UUID v7
)
```

### ❌ Missing toDomain() Method

```kotlin
// ❌ Incorrect
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

## Quick Reference: Complete Example

### Migration
```sql
SET search_path TO app, public;
CREATE EXTENSION IF NOT EXISTS pg_uuidv7;
CREATE TABLE IF NOT EXISTS app.products (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(255) NOT NULL UNIQUE,
    price_cents BIGINT NOT NULL DEFAULT 0,
    stock INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_products_sku ON app.products(sku);
```

### Entity
```kotlin
@Entity
@Table(name = "products", schema = "app")
open class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
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
    fun toDomain(): Product = Product(
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
```

### Domain
```kotlin
data class Product(
    val id: UUID? = null,
    val name: String,
    val sku: String,
    val priceCents: Long = 0,
    val stock: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): ProductEntity = ProductEntity(
        id = this.id,  // null for new entities, DB generates UUID v7
        name = this.name,
        sku = this.sku,
        priceCents = this.priceCents,
        stock = this.stock,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        version = this.version,
    )
}
```

### Repository
```kotlin
@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findBySku(sku: String): ProductEntity?
    fun existsBySku(sku: String): Boolean
}
```

## Related Documentation

For detailed explanations and advanced patterns, see:

- [Entity Pattern](./entity-pattern.md) - Complete entity structure and field patterns
- [Repository Pattern](./repository-pattern.md) - Complete repository patterns and custom implementations
- [Domain-Entity Conversion Pattern](./domain-entity-conversion.md) - Detailed conversion rules
- [UUID v7 Pattern](./uuid-v7-pattern.md) - Complete UUID v7 implementation guide
- [Database Standards](../05-standards/database-standards/schema-standards.md) - Complete database rules and standards
