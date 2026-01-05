---
title: Coding Standards
type: rule
category: coding
status: current
version: 2.0.0
tags: [coding, standards, style, naming, conventions]
ai_optimized: true
search_keywords: [coding, standards, style, naming, conventions, formatting]
related:
  - 05-standards/architecture-standards/layer-rules.md
---

# Coding Standards

> **Purpose**: Code style, naming conventions, and formatting rules.

## Naming Conventions

### Rule: Package Naming

**Rule**: Use reverse domain notation for packages: `io.github.salomax.neotool.{module}`

**Rationale**: Standard Java/Kotlin convention.

**Example**:
```kotlin
// ✅ Correct
package io.github.salomax.neotool.app.service
package io.github.salomax.neotool.security.entity

// ❌ Incorrect
package app.service
package neotool.security
```

### Rule: Class Naming

**Rule**: Use PascalCase for classes.

**Rationale**: Standard convention.

**Example**:
```kotlin
// ✅ Correct
class CustomerService
class ProductEntity

// ❌ Incorrect
class customerService
class product_entity
```

### Rule: Function Naming

**Rule**: Use camelCase for functions.

**Rationale**: Standard convention.

**Example**:
```kotlin
// ✅ Correct
fun createCustomer()
fun findById()

// ❌ Incorrect
fun CreateCustomer()
fun find_by_id()
```

### Rule: Variable Naming

**Rule**: Use camelCase for variables.

**Rationale**: Standard convention.

**Example**:
```kotlin
// ✅ Correct
val customerName = "John"
val orderId = UUID.randomUUID()

// ❌ Incorrect
val customer_name = "John"
val OrderId = UUID.randomUUID()
```

### Rule: Constant Naming

**Rule**: Use UPPER_SNAKE_CASE for constants.

**Rationale**: Standard convention.

**Example**:
```kotlin
// ✅ Correct
const val MAX_RETRY_COUNT = 3
const val DEFAULT_TIMEOUT = 5000

// ❌ Incorrect
const val maxRetryCount = 3
const val defaultTimeout = 5000
```

## File Organization

### Rule: One Class Per File

**Rule**: Each file should contain one primary class/interface.

**Rationale**: Better organization and navigation.

**Exception**: Companion objects, data classes, and small related classes may be in the same file.

### Rule: File Naming

**Rule**: File name must match class name.

**Rationale**: Standard convention.

**Example**:
```kotlin
// ✅ Correct
// File: CustomerService.kt
class CustomerService { ... }

// ❌ Incorrect
// File: customer_service.kt
class CustomerService { ... }
```

## Code Formatting

### Rule: Indentation

**Rule**: Use 4 spaces for indentation (not tabs).

**Rationale**: Consistent formatting.

### Rule: Line Length

**Rule**: Maximum line length is 120 characters.

**Rationale**: Readability.

**Exception**: Long URLs, file paths, or generated code may exceed.

### Rule: Imports

**Rule**: Organize imports in lexicographic (alphabetical) order: project imports, third-party imports, then standard library imports (`java.*`, `javax.*`, `kotlin.*`) at the end.

**Rationale**: Clear import organization following ktlint standards.

**Details**: See [Linting Standards](./linting-standards.md#import-ordering-pattern) for complete import ordering rules and examples.

## Domain-Entity Conversion

### Rule: Nullable ID Handling

**Rule**: When converting domain objects to entities, handle nullable IDs correctly. See [Domain-Entity Conversion Pattern](../../04-patterns/backend-patterns/domain-entity-conversion.md) for detailed rules.

**Rationale**: Prevents type mismatch errors and ensures correct ID generation.

**Quick Reference**:
- Domain objects use nullable IDs (`UUID?`, `Int?`) for new entities
- Entities use non-nullable IDs (`UUID`) for UUID-based entities
- Use `id ?: UUID.randomUUID()` when converting `UUID?` to `UUID`
- Pass `id` directly when both are nullable (`Int?` to `Int?`)

## Related Documentation

- [Linting Standards](./linting-standards.md)
- [Architecture Standards](../architecture-standards/layer-rules.md)
- [Domain-Entity Conversion Pattern](../../04-patterns/backend-patterns/domain-entity-conversion.md)

