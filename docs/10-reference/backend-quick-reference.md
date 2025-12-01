---
title: Backend Quick Reference
type: reference
category: backend
status: current
version: 1.0.0
tags: [reference, backend, kotlin, quick-reference, cheat-sheet]
ai_optimized: true
search_keywords: [reference, backend, kotlin, imports, annotations, patterns, quick]
related:
  - 04-patterns/backend-patterns/entity-pattern.md
  - 04-patterns/backend-patterns/repository-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 04-patterns/backend-patterns/resolver-pattern.md
---

# Backend Quick Reference

> **Purpose**: Quick reference guide for common backend patterns, imports, annotations, and common errors.

## Package Naming

**Rule**: `io.github.salomax.neotool.{module}.{layer}`

### Layers

- `entity` - JPA entities
- `repo` - Repositories
- `service` - Services
- `domain` - Domain objects
- `graphql.resolvers` - GraphQL resolvers
- `graphql.dto` - GraphQL DTOs
- `graphql.mapper` - GraphQL mappers

### Examples

```kotlin
package io.github.salomax.neotool.app.entity
package io.github.salomax.neotool.app.repo
package io.github.salomax.neotool.app.service
package io.github.salomax.neotool.app.domain
package io.github.salomax.neotool.app.graphql.resolvers
package io.github.salomax.neotool.app.graphql.dto
package io.github.salomax.neotool.app.graphql.mapper
```

## Common Imports by Layer

### Entity Imports

```kotlin
import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.{module}.domain.{DomainName}
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

### Repository Imports

```kotlin
import io.github.salomax.neotool.{module}.entity.{EntityName}Entity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID
```

### Service Imports

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

### Resolver Imports

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

### InputDTO Imports

```kotlin
import io.github.salomax.neotool.common.graphql.BaseInputDTO
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Email
```

## Annotation Checklist

### Entity Annotations

- [ ] `@Entity` - Marks as JPA entity
- [ ] `@Table(name = "...")` - Table name (snake_case)
- [ ] `@Id` - Primary key
- [ ] `@GeneratedValue(strategy = GenerationType.IDENTITY)` - ID generation
- [ ] `@Column` - Column properties
- [ ] `@Version` - Optimistic locking (REQUIRED)

### Repository Annotations

- [ ] `@Repository` - Micronaut Data repository

### Service Annotations

- [ ] `@Singleton` - Micronaut bean
- [ ] `@Transactional` - On write operations (create, update, delete)

### Resolver Annotations

- [ ] `@Singleton` - Micronaut bean

### InputDTO Annotations

- [ ] `@Introspected` - Micronaut introspection
- [ ] `@Serdeable` - Serialization support
- [ ] Bean Validation annotations (`@NotBlank`, `@Min`, `@Email`, etc.)

## Common Patterns

### Entity Pattern

```kotlin
@Entity
@Table(name = "{table_name}")
open class {EntityName}Entity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID?,
    @Column(nullable = false)
    open var name: String,
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): {DomainName} { ... }
}
```

### Repository Pattern

```kotlin
@Repository
interface {EntityName}Repository : JpaRepository<{EntityName}Entity, UUID> {
    fun findBy{Field}({field}: {Type}): {EntityName}Entity?
}
```

### Service Pattern

```kotlin
@Singleton
open class {EntityName}Service(
    private val repo: {EntityName}Repository,
) {
    fun list(): List<{DomainName}> { ... }
    fun get(id: UUID): {DomainName}? { ... }
    
    @Transactional
    open fun create(domain: {DomainName}): {DomainName} { ... }
    
    @Transactional
    open fun update(domain: {DomainName}): {DomainName} { ... }
    
    @Transactional
    open fun delete(id: UUID) { ... }
}
```

### Resolver Pattern

```kotlin
@Singleton
class {EntityName}Resolver(
    private val {entityName}Service: {EntityName}Service,
    validator: Validator,
    private val mapper: {EntityName}GraphQLMapper,
) : GenericCrudResolver<{DomainName}, {EntityName}InputDTO, UUID>() {
    override val validator: Validator = validator
    override val service: CrudService<{DomainName}, UUID> = {EntityName}CrudService({entityName}Service)
    
    override fun mapToInputDTO(input: Map<String, Any?>): {EntityName}InputDTO { ... }
    override fun mapToEntity(dto: {EntityName}InputDTO, id: UUID?): {DomainName} { ... }
}
```

## Domain-Entity Conversion Rules

### UUID-based Entities

**Domain → Entity**:
```kotlin
// Domain has UUID?, Entity has UUID?
id = this.id  // Direct assignment (both nullable)
```

**Entity → Domain**:
```kotlin
// Entity has UUID?, Domain has UUID?
id = this.id  // Direct assignment (both nullable)
```

**Note**: If entity uses non-nullable UUID, use `id ?: UUID.randomUUID()` in domain's `toEntity()`.

### Int-based Entities

**Domain → Entity**:
```kotlin
// Both have Int?
id = this.id  // Direct assignment
```

**Entity → Domain**:
```kotlin
// Both have Int?
id = this.id  // Direct assignment
```

## Common Errors and Quick Fixes

### Error: Missing @Entity Annotation

**Fix**: Add `@Entity` before class declaration

### Error: Missing @Version Field

**Fix**: Add `@Version open var version: Long = 0` to entity

### Error: Class Not Open

**Fix**: Change `class` to `open class` (required for JPA)

### Error: Properties Not Open

**Fix**: Change `var` to `open var` (required for JPA)

### Error: Missing @Singleton

**Fix**: Add `@Singleton` to service/resolver classes

### Error: Missing @Transactional

**Fix**: Add `@Transactional` to write operations (create, update, delete)

### Error: Method Not Open

**Fix**: Change `fun` to `open fun` for transactional methods

### Error: UUID? to UUID Conversion

**Fix**: Use `id ?: UUID.randomUUID()` when converting nullable to non-nullable

### Error: Missing @Repository

**Fix**: Add `@Repository` to repository interfaces

### Error: Wrong Package Name

**Fix**: Use `io.github.salomax.neotool.{module}.{layer}` format

### Error: Missing InputDTO Annotations

**Fix**: Add `@Introspected`, `@Serdeable`, and extend `BaseInputDTO`

### Error: Missing CrudService Adapter

**Fix**: Create adapter class implementing `CrudService<Domain, ID>`

## ktlint Common Issues

### Trailing Whitespace

**Fix**: Run `./gradlew ktlintFormat`

### Missing Blank Lines

**Fix**: Run `./gradlew ktlintFormat`

### Comments in Argument Lists

**Fix**: Move comments to separate line above argument

```kotlin
// ❌ Incorrect
classes("name", "*\$*", // comment
    "*Generated*")

// ✅ Correct
classes(
    "name",
    // comment
    "*\$*",
    "*Generated*",
)
```

### Multiline Expression Wrapping

**Fix**: Start multiline expressions on new line

```kotlin
// ❌ Incorrect
val result = service.create(entity)

// ✅ Correct
val result =
    service.create(entity)
```

### Missing Trailing Commas

**Fix**: Add trailing comma before closing parenthesis

```kotlin
// ❌ Incorrect
classes(
    "name",
    "value"
)

// ✅ Correct
classes(
    "name",
    "value",
)
```

## File Structure

```
service/kotlin/{module}/
├── src/main/kotlin/io/github/salomax/neotool/{module}/
│   ├── entity/
│   │   └── {EntityName}Entity.kt
│   ├── repo/
│   │   └── {EntityName}Repository.kt
│   ├── service/
│   │   └── {EntityName}Service.kt
│   ├── domain/
│   │   └── {DomainName}.kt
│   └── graphql/
│       ├── resolvers/
│       │   └── {EntityName}Resolver.kt
│       ├── dto/
│       │   └── {EntityName}InputDTO.kt
│       └── mapper/
│           └── {EntityName}GraphQLMapper.kt
```

## Related Documentation

- [Entity Pattern](../../04-patterns/backend-patterns/entity-pattern.md)
- [Repository Pattern](../../04-patterns/backend-patterns/repository-pattern.md)
- [Service Pattern](../../04-patterns/backend-patterns/service-pattern.md)
- [Resolver Pattern](../../04-patterns/backend-patterns/resolver-pattern.md)
- [Domain-Entity Conversion](../../04-patterns/backend-patterns/domain-entity-conversion.md)
- [Linting Standards](../../05-standards/coding-standards/linting-standards.md)
