---
title: Resolver Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [resolver, graphql, api, pattern]
ai_optimized: true
search_keywords: [resolver, graphql, @Singleton, GenericCrudResolver, mapper, InputDTO]
related:
  - 04-patterns/backend-patterns/mapper-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 05-standards/api-standards/graphql-standards.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Resolver Pattern

> **Purpose**: Standard pattern for creating GraphQL resolvers, handling input validation, mapping, and error handling.

## Overview

Resolvers handle GraphQL operations (queries, mutations) and coordinate between GraphQL input, DTOs, domain objects, and services. They use `GenericCrudResolver` for automatic payload handling and validation.

## Core Requirements

### Required Annotations

Every resolver MUST have:

1. `@Singleton` - Marks the class as a Micronaut singleton bean
2. Extends `GenericCrudResolver<Domain, InputDTO, ID>` - Provides CRUD operations

### Required Components

1. **Resolver class** - Extends `GenericCrudResolver`
2. **InputDTO class** - GraphQL input DTO with validation
3. **Mapper class** - Converts between GraphQL input, DTO, and domain
4. **CrudService adapter** - Adapts service to `CrudService` interface

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

When creating a resolver, verify:

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

## Required Imports

### Resolver

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
import jakarta.inject.Singleton
import java.util.UUID
```

## Related Documentation

- [Mapper Pattern](./mapper-pattern.md) - Detailed mapper patterns including list handling in update operations
- [Service Pattern](./service-pattern.md) - Business logic layer
- [GraphQL Standards](../05-standards/api-standards/graphql-standards.md) - GraphQL API rules
- [Kotlin Coding Standards](../05-standards/coding-standards/kotlin-standards.md) - General conventions
