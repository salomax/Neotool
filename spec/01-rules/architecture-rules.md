---
title: Architecture Rules
type: rule
category: architecture
status: current
version: 2.0.0
tags: [architecture, rules, constraints, patterns]
ai_optimized: true
search_keywords: [architecture, rules, constraints, layers, boundaries]
related:
  - 00-core/principles.md
  - 00-core/architecture.md
---

# Architecture Rules

> **Purpose**: Architecture constraints and layer boundary rules.

## Layer Rules

### Rule: Layer Dependencies

**Rule**: Dependencies must point inward: API → Service → Repository → Entity

**Rationale**: Clean architecture principle.

**Example**:
```kotlin
// ✅ Correct
// Resolver (API) depends on Service
class CustomerResolver(private val service: CustomerService)

// Service depends on Repository
class CustomerService(private val repository: CustomerRepository)

// ❌ Incorrect
// Service depends on Resolver (violates dependency rule)
class CustomerService(private val resolver: CustomerResolver)
```

### Rule: No Cross-Layer Dependencies

**Rule**: Layers cannot depend on outer layers.

**Rationale**: Maintains clean architecture.

**Exception**: None.

## Module Rules

### Rule: Module Boundaries

**Rule**: Modules must have clear boundaries and explicit dependencies.

**Rationale**: Prevents tight coupling.

### Rule: Common Module

**Rule**: Shared code must be in `common` module, not duplicated.

**Rationale**: Single source of truth.

## Related Documentation

- [Core Principles](../00-core/principles.md)
- [Architecture Overview](../00-core/architecture.md)

