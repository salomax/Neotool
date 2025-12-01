---
title: CRUD Example
type: example
category: backend
status: current
version: 1.0.0
tags: [example, backend, crud, entity, repository, service, resolver]
ai_optimized: true
search_keywords: [example, crud, complete, flow, entity, repository, service, resolver]
related:
  - 04-patterns/backend-patterns/entity-pattern.md
  - 04-patterns/backend-patterns/repository-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 04-patterns/backend-patterns/resolver-pattern.md
---

# CRUD Example

> **Purpose**: Complete working example showing the full backend stack from Entity to Resolver.

## Overview

This example demonstrates a complete CRUD implementation following NeoTool patterns. It shows:
- Entity with JPA annotations
- Repository with custom queries
- Service with business logic
- Domain object with conversion methods
- GraphQL resolver with DTO and mapper
- Complete flow from API to database

## Reference Implementation

The complete working implementation can be found in:
- `service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/`

This includes:
- **Product** - Complete CRUD example
- **Customer** - Complete CRUD example with optimistic locking

## File Structure

```
service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/
├── entity/
│   ├── ProductEntity.kt
│   └── CustomerEntity.kt
├── repo/
│   └── Repositories.kt (ProductRepository, CustomerRepository)
├── service/
│   └── Services.kt (ProductService, CustomerService)
├── domain/
│   ├── Product.kt
│   └── Customer.kt
└── graphql/
    ├── resolvers/
    │   ├── ProductResolver.kt
    │   └── CustomerResolver.kt
    ├── dto/
    │   └── Inputs.kt (ProductInputDTO, CustomerInputDTO)
    └── mapper/
        ├── ProductGraphQLMapper.kt
        └── CustomerGraphQLMapper.kt
```

## Key Patterns Demonstrated

### 1. Entity Pattern
- `@Entity`, `@Table`, `@Id`, `@Version` annotations
- Extends `BaseEntity<UUID?>`
- `open class` and `open var` properties
- `toDomain()` method

### 2. Repository Pattern
- `@Repository` annotation
- Extends `JpaRepository<Entity, UUID>`
- Custom query methods (`findBySku`, `findByEmail`)

### 3. Service Pattern
- `@Singleton` annotation
- `@Transactional` on write operations
- `open class` and `open fun` for transactions
- Domain-entity conversion
- Audit logging

### 4. Resolver Pattern
- `@Singleton` annotation
- Extends `GenericCrudResolver<Domain, InputDTO, UUID>`
- `CrudService` adapter
- Mapper for input conversion

### 5. Domain-Entity Conversion
- Domain objects use nullable IDs (`UUID?`)
- Entities use nullable IDs (`UUID?`) for UUID-based entities
- Direct assignment (both nullable)

## Usage

When implementing a new feature:

1. **Reference these examples** as the source of truth
2. **Copy the structure** from Product or Customer
3. **Follow the patterns** exactly as shown
4. **Use the code templates** from `docs/08-templates/code/` as starting points
5. **Verify against patterns** in `docs/04-patterns/backend-patterns/`

## Related Documentation

- [Entity Pattern](../../04-patterns/backend-patterns/entity-pattern.md)
- [Repository Pattern](../../04-patterns/backend-patterns/repository-pattern.md)
- [Service Pattern](../../04-patterns/backend-patterns/service-pattern.md)
- [Resolver Pattern](../../04-patterns/backend-patterns/resolver-pattern.md)
- [Domain-Entity Conversion](../../04-patterns/backend-patterns/domain-entity-conversion.md)
- [Backend Quick Reference](../../10-reference/backend-quick-reference.md)
