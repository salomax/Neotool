---
title: Database Rules
type: rule
category: database
status: current
version: 2.0.0
tags: [database, schema, migration, rules, constraints]
ai_optimized: true
search_keywords: [database, schema, migration, postgresql, flyway, jpa, entity]
related:
  - 04-patterns/backend-patterns/uuid-v7-pattern.md
  - 04-patterns/backend-patterns/domain-entity-conversion.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Database Rules

> **Purpose**: Explicit rules for database schema, migrations, and entity management.

## Schema Organization

### Rule: All Tables Must Be in a Schema

**Rule**: ANY TABLE MUST BE INCLUDED IN A SCHEMA. NEVER IN PUBLIC.

**Rationale**: 
- Prevents namespace pollution
- Enables better organization by module/service
- Allows schema-level permissions
- Supports multi-tenancy patterns

**Example**:
```sql
-- ✅ Correct
CREATE TABLE app.customers (...);
CREATE TABLE security.users (...);

-- ❌ Incorrect
CREATE TABLE customers (...);
CREATE TABLE public.customers (...);
```

**Exception**: None. This rule is mandatory.

### Rule: Schema Naming Convention

**Rule**: Use module/service name as schema name (e.g., `app`, `security`, `assistant`).

**Rationale**: Clear organization by domain/module.

**Example**:
```sql
-- ✅ Correct
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS security;

-- ❌ Incorrect
CREATE SCHEMA IF NOT EXISTS my_schema;
CREATE SCHEMA IF NOT EXISTS schema1;
```

### Rule: Entity Schema Annotation

**Rule**: JPA entities MUST specify the schema in `@Table` annotation.

**Rationale**: Ensures entities map to correct schema.

**Example**:
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

## Migration Rules

### Rule: Flyway Migration Naming

**Rule**: Migration files must follow format: `V{version}__{description}.sql`

**Rationale**: Ensures proper ordering and clarity.

**Example**:
```sql
-- ✅ Correct
V1_1__create_customers_table.sql
V1_2__add_email_index.sql

-- ❌ Incorrect
V1__customers.sql
migration_1.sql
```

### Rule: Schema in Migrations

**Rule**: All migration files must set search path and use explicit schema qualification.

**Rationale**: Ensures migrations work correctly regardless of default schema.

**Example**:
```sql
-- ✅ Correct
SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS app.customers (...);

-- ❌ Incorrect
CREATE TABLE customers (...);
```

### Rule: Migration Idempotency

**Rule**: Migrations must be idempotent (safe to run multiple times).

**Rationale**: Prevents errors on re-runs.

**Example**:
```sql
-- ✅ Correct
CREATE TABLE IF NOT EXISTS app.customers (...);
CREATE INDEX IF NOT EXISTS idx_customers_email ON app.customers(email);

-- ❌ Incorrect
CREATE TABLE app.customers (...);
CREATE INDEX idx_customers_email ON app.customers(email);
```

## Entity Rules

### Rule: Base Entity Extension

**Rule**: All entities must extend `BaseEntity<T>`.

**Rationale**: Provides consistent ID handling and equals/hashCode.

**Example**:
```kotlin
// ✅ Correct
@Entity
@Table(name = "customers", schema = "app")
open class CustomerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: UUID?,
    ...
) : BaseEntity<UUID?>(id)

// ❌ Incorrect
@Entity
@Table(name = "customers", schema = "app")
open class CustomerEntity(
    @Id
    val id: UUID?,
    ...
)
```

### Rule: Open Classes for Entities

**Rule**: Entity classes must be `open` (not `final`).

**Rationale**: Required for JPA proxy generation.

**Example**:
```kotlin
// ✅ Correct
open class CustomerEntity(...)

// ❌ Incorrect
class CustomerEntity(...)
final class CustomerEntity(...)
```

### Rule: Optimistic Locking

**Rule**: All entities must include `@Version` field for optimistic locking.

**Rationale**: Prevents concurrent modification issues.

**Example**:
```kotlin
// ✅ Correct
@Version
open var version: Long = 0

// ❌ Incorrect
// Missing @Version field
```

### Rule: Timestamp Fields

**Rule**: All entities must include `createdAt` and `updatedAt` timestamp fields.

**Rationale**: Enables audit trails and debugging.

**Example**:
```kotlin
// ✅ Correct
@Column(name = "created_at", nullable = false)
open var createdAt: Instant = Instant.now(),

@Column(name = "updated_at", nullable = false)
open var updatedAt: Instant = Instant.now()

// ❌ Incorrect
// Missing timestamp fields
```

### Rule: Domain Mapping

**Rule**: All entities must implement `toDomain()` method.

**Rationale**: Clean separation between entity and domain layers.

**Example**:
```kotlin
// ✅ Correct
fun toDomain(): Customer {
    return Customer(
        id = this.id,
        name = this.name,
        ...
    )
}

// ❌ Incorrect
// Missing toDomain() method
```

## Column Rules

### Rule: Column Naming

**Rule**: Use snake_case for column names.

**Rationale**: Database convention and readability.

**Example**:
```kotlin
// ✅ Correct
@Column(name = "created_at")
@Column(name = "user_id")

// ❌ Incorrect
@Column(name = "createdAt")
@Column(name = "userId")
```

### Rule: Nullable Constraints

**Rule**: Explicitly specify `nullable = false` for required fields.

**Rationale**: Clear intent and database constraints.

**Example**:
```kotlin
// ✅ Correct
@Column(nullable = false)
open var name: String

// ❌ Incorrect
@Column
open var name: String
```

### Rule: UUID Column Definition

**Rule**: Use `columnDefinition = "uuid"` for UUID columns.

**Rationale**: Ensures proper PostgreSQL UUID type.

**Example**:
```kotlin
// ✅ Correct
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(columnDefinition = "uuid")
override val id: UUID?

// ❌ Incorrect
@Id
@Column
override val id: UUID?
```

### Rule: UUID v7 for Primary Keys

**Rule**: All UUID primary keys MUST use UUID v7 (timestamp-based), generated by the database.

**Rationale**: 
- Better database performance (index locality)
- Natural sorting by creation time
- Time-based querying capabilities
- Consistent UUID format across the system

**Requirements**:
- Migration must install `pg_uuidv7` extension
- Table must use `DEFAULT uuidv7()` for UUID primary keys
- Entity must use nullable `UUID?` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Entity ID default must be `null` (not `UUID.randomUUID()`)
- Domain objects must pass `null` for new entities

**Example**:
```sql
-- ✅ Correct - Migration
CREATE EXTENSION IF NOT EXISTS pg_uuidv7;
CREATE TABLE IF NOT EXISTS app.users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- other columns
);
```

```kotlin
// ✅ Correct - Entity
@Entity
@Table(name = "users", schema = "app")
open class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,  // Nullable, DB generates v7
    // ...
) : BaseEntity<UUID?>(id)
```

**See**: [UUID v7 Pattern](../../04-patterns/backend-patterns/uuid-v7-pattern.md) for complete pattern details.

## Enum Rules

### Rule: Enum Storage

**Rule**: Store enums as `EnumType.STRING`, not `EnumType.ORDINAL`.

**Rationale**: More readable and maintainable.

**Example**:
```kotlin
// ✅ Correct
@Enumerated(EnumType.STRING)
@Column(nullable = false)
open var status: CustomerStatus = CustomerStatus.ACTIVE

// ❌ Incorrect
@Enumerated(EnumType.ORDINAL)
@Column(nullable = false)
open var status: CustomerStatus = CustomerStatus.ACTIVE
```

## Repository Rules

### Rule: Repository Interface

**Rule**: Repositories must extend `JpaRepository<Entity, ID>` or `CrudRepository<Entity, ID>`.

**Rationale**: Provides standard CRUD operations.

**Example**:
```kotlin
// ✅ Correct
@Repository
interface CustomerRepository : JpaRepository<CustomerEntity, UUID>

// ❌ Incorrect
@Repository
interface CustomerRepository
```

### Rule: Query Method Naming

**Rule**: Use Micronaut Data query method naming conventions.

**Rationale**: Automatic query generation.

**Example**:
```kotlin
// ✅ Correct
fun findByEmail(email: String): CustomerEntity?
fun findByStatus(status: CustomerStatus): List<CustomerEntity>
fun findByNameContainingIgnoreCase(name: String): List<CustomerEntity>

// ❌ Incorrect
fun getByEmail(email: String): CustomerEntity?
fun queryByStatus(status: CustomerStatus): List<CustomerEntity>
```

## Index Rules

### Rule: Index Naming

**Rule**: Use format `idx_{table}_{columns}` for index names.

**Rationale**: Consistent and clear naming.

**Example**:
```sql
-- ✅ Correct
CREATE INDEX idx_customers_email ON app.customers(email);
CREATE INDEX idx_orders_customer_id ON app.orders(customer_id);

-- ❌ Incorrect
CREATE INDEX email_idx ON app.customers(email);
CREATE INDEX idx1 ON app.orders(customer_id);
```

### Rule: Index on Foreign Keys

**Rule**: Create indexes on all foreign key columns.

**Rationale**: Improves join performance.

**Example**:
```sql
-- ✅ Correct
CREATE TABLE app.orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app.customers(id)
);
CREATE INDEX idx_orders_customer_id ON app.orders(customer_id);

-- ❌ Incorrect
-- Missing index on customer_id
```

## Related Documentation

- [Entity Pattern](../03-patterns/backend/entity-pattern.md)
- [Repository Pattern](../03-patterns/backend/repository-pattern.md)
- [Coding Standards](./coding-standards.md)

