---
id: service-kotlin-managing-jpa-entity
title: Service — Managing a JPA Entity (Kotlin + Micronaut + Postgres)
area: service
version: 0.1
tags: [kotlin, micronaut, jpa, postgres, flyway, repository, testing]
last_reviewed: 2025-10-15
---

# Managing JPA Entities in Kotlin Service

This guide covers the complete lifecycle of managing JPA entities in our Kotlin service using Micronaut, PostgreSQL, and Flyway for database migrations.

## Architecture Overview

Our JPA implementation follows a clean architecture pattern with clear separation of concerns:

- **Entity Layer**: JPA entities that map to database tables
- **Domain Layer**: Pure Kotlin data classes representing business objects
- **Repository Layer**: Data access interfaces using Micronaut Data
- **Service Layer**: Business logic orchestration
- **Migration Layer**: Database schema management with Flyway

## Entity Structure

### Base Entity Pattern

All entities extend `BaseEntity<T>` which provides:

```kotlin
abstract class BaseEntity<T>(
    open val id: T
) {
    // No-arg constructor for JPA
    constructor() : this(null as T)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BaseEntity<*>
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

### Entity Implementation

Here's a complete example of a JPA entity:

```kotlin
@Entity
@Table(name = "customers")
open class CustomerEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "uuid")
  override val id: UUID?,

  @Column(nullable = false)
  open var name: String,

  @Column(nullable = false, unique = true)
  open var email: String,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  open var status: CustomerStatus = CustomerStatus.ACTIVE,

  @Column(name = "created_at", nullable = false)
  open var createdAt: Instant = Instant.now(),

  @Column(name = "updated_at", nullable = false)
  open var updatedAt: Instant = Instant.now(),

  @Version
  open var version: Long = 0
) : BaseEntity<UUID?>(id) {
    fun toDomain(): Customer {
        return Customer(
            id = this.id,
            name = this.name,
            email = this.email,
            status = this.status,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version
        )
    }
}
```

## Key JPA Annotations and Patterns

### Required Annotations

1. **@Entity**: Marks the class as a JPA entity
2. **@Table(name = "table_name")**: Specifies the database table name
3. **@Id**: Marks the primary key field
4. **@GeneratedValue(strategy = GenerationType.IDENTITY)**: Auto-generates ID values
5. **@Column**: Configures column properties

### Column Configuration

```kotlin
@Column(
    nullable = false,           // NOT NULL constraint
    unique = true,              // UNIQUE constraint
    name = "custom_name",       // Custom column name
    columnDefinition = "uuid"   // Custom column type
)
```

### Optimistic Locking

```kotlin
@Version
open var version: Long = 0
```

### Enums

```kotlin
@Enumerated(EnumType.STRING)
@Column(nullable = false)
open var status: CustomerStatus = CustomerStatus.ACTIVE
```

### Timestamps

```kotlin
@Column(name = "created_at", nullable = false)
open var createdAt: Instant = Instant.now(),

@Column(name = "updated_at", nullable = false)
open var updatedAt: Instant = Instant.now()
```

## Domain Mapping

### Entity to Domain Conversion

Each entity should have a `toDomain()` method:

```kotlin
fun toDomain(): Customer {
    return Customer(
        id = this.id,
        name = this.name,
        email = this.email,
        status = this.status,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        version = this.version
    )
}
```

### Domain to Entity Conversion

Domain classes should have a `toEntity()` method:

```kotlin
data class Customer(
    val id: UUID? = null,
    val name: String,
    val email: String,
    val status: CustomerStatus = CustomerStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0
) {
    fun toEntity(): CustomerEntity {
        return CustomerEntity(
            id = this.id,
            name = this.name,
            email = this.email,
            status = this.status,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version
        )
    }
}
```

## Repository Layer

### Repository Interface

```kotlin
@Repository
interface CustomerRepository : JpaRepository<CustomerEntity, UUID> {
    fun findByEmail(email: String): CustomerEntity?
    fun findByStatus(status: CustomerStatus): List<CustomerEntity>
    fun findByNameContainingIgnoreCase(name: String): List<CustomerEntity>
}
```

### Custom Query Methods

Micronaut Data supports various query method patterns:

- `findBy{Property}`: Find by exact property match
- `findBy{Property}Containing`: Find by substring match
- `findBy{Property}IgnoreCase`: Case-insensitive search
- `findBy{Property}In`: Find by property in collection
- `countBy{Property}`: Count by property
- `existsBy{Property}`: Check existence by property

## Database Migrations

### Flyway Migration Files

Create migration files in `src/main/resources/db/migration/`:

```sql
-- V1_1__create_products_customers.sql
CREATE TABLE IF NOT EXISTS products (
    id              uuid DEFAULT uuidv7() PRIMARY KEY,
    name            TEXT NOT NULL,
    sku             TEXT UNIQUE NOT NULL,
    price_cents     BIGINT NOT NULL DEFAULT 0,
    stock           INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS customers (
    id              uuid DEFAULT uuidv7() PRIMARY KEY,
    name            TEXT NOT NULL,
    email           TEXT UNIQUE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
```

### Migration Naming Convention

- Format: `V{version}__{description}.sql`
- Version: Sequential number (V1, V2, V3...)
- Description: Brief description of the change

## Configuration

### Application Configuration

```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:neotool_db}
    driverClassName: org.postgresql.Driver
    username: ${POSTGRES_USER:neotool}
    password: ${POSTGRES_PASSWORD:neotool}
    dialect: POSTGRES

jpa:
  default:
    properties:
      hibernate:
        hbm2ddl:
          auto: none  # Use Flyway instead of Hibernate DDL

flyway:
  datasources:
    default:
      enabled: true
```

### Build Configuration

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")
}
```

## Testing

### Test Data Builders

Create test data builders for consistent test data:

```kotlin
object TestDataBuilders {
    fun customer(
        id: UUID? = null,
        name: String = "Test Customer",
        email: String = "test@example.com",
        status: CustomerStatus = CustomerStatus.ACTIVE,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Customer = Customer(
        id = id,
        name = name,
        email = email,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
```

### Integration Testing

Use Testcontainers for database integration tests:

```kotlin
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest : TestPropertyProvider {
    
    override fun getProperties(): MutableMap<String, String> {
        val postgres = PostgresTestContainer.container
        return PostgresTestContainer.micronautProps()
    }
}
```

## Best Practices

### Entity Design

1. **Use `open` classes**: Required for JPA proxy generation
2. **Provide default values**: For non-nullable fields with defaults
3. **Use `Instant` for timestamps**: Better than `LocalDateTime` for UTC handling
4. **Implement `toDomain()` method**: For clean separation of concerns
5. **Use optimistic locking**: Add `@Version` field for concurrent access

### Naming Conventions

1. **Table names**: Use snake_case (e.g., `customers`, `order_items`)
2. **Column names**: Use snake_case (e.g., `created_at`, `updated_at`)
3. **Entity classes**: Use PascalCase with `Entity` suffix
4. **Repository interfaces**: Use PascalCase with `Repository` suffix

### Performance Considerations

1. **Use `@Column(columnDefinition = "uuid")`**: For proper UUID handling
2. **Add indexes**: For frequently queried columns
3. **Use `@Version`**: For optimistic locking instead of pessimistic locking
4. **Lazy loading**: Use `@OneToMany(fetch = FetchType.LAZY)` for collections

### Error Handling

1. **Unique constraints**: Handle `DataIntegrityViolationException` for duplicate entries
2. **Optimistic locking**: Handle `OptimisticLockException` for concurrent modifications
3. **Validation**: Use Bean Validation annotations for input validation

## Common Patterns

### Soft Delete Pattern

```kotlin
@Column(name = "deleted_at")
open var deletedAt: Instant? = null

fun isDeleted(): Boolean = deletedAt != null
```

### Audit Fields Pattern

```kotlin
@Column(name = "created_by")
open var createdBy: String? = null

@Column(name = "updated_by")
open var updatedBy: String? = null
```

### Status Enum Pattern

```kotlin
enum class CustomerStatus {
    ACTIVE, INACTIVE, SUSPENDED, DELETED
}
```

## Troubleshooting

### Common Issues

1. **LazyInitializationException**: Ensure transactions are properly managed
2. **Entity not found**: Check if entity is properly annotated and scanned
3. **Migration failures**: Verify SQL syntax and version numbering
4. **Constraint violations**: Check unique constraints and foreign keys

### Debugging Tips

1. Enable SQL logging: `logging.level.org.hibernate.SQL=DEBUG`
2. Use `@Transactional` for read operations if needed
3. Check entity scanning configuration
4. Verify database connection and permissions

## Dependencies

### Required Dependencies

```kotlin
implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
implementation("io.micronaut.flyway:micronaut-flyway")
implementation("org.postgresql:postgresql")
implementation("org.flywaydb:flyway-database-postgresql")
implementation("org.hibernate.orm:hibernate-core")
```

### Test Dependencies

```kotlin
testImplementation("org.testcontainers:postgresql")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("io.micronaut.test:micronaut-test-junit5")
```

This documentation provides a comprehensive guide for managing JPA entities in our Kotlin service, covering everything from basic entity creation to advanced patterns and troubleshooting.