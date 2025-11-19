---
title: API Rules
type: rule
category: api
status: current
version: 2.0.0
tags: [api, graphql, rules, federation]
ai_optimized: true
search_keywords: [api, graphql, federation, rules, schema]
related:
  - 03-patterns/shared/graphql-federation.md
  - 03-patterns/backend/resolver-pattern.md
---

# API Rules

> **Purpose**: Rules for GraphQL API and federation patterns.

## GraphQL Schema Rules

### Rule: Schema Location

**Rule**: GraphQL schemas must be in `src/main/resources/graphql/schema.graphqls`

**Rationale**: Standard location for Micronaut.

### Rule: Federation Directives

**Rule**: Entities must use `@key` directive for federation.

**Rationale**: Enables federation.

**Example**:
```graphql
# ✅ Correct
type Customer @key(fields: "id") {
  id: ID!
  name: String!
}

# ❌ Incorrect
type Customer {
  id: ID!
  name: String!
}
```

## Resolver Rules

### Rule: Resolver Naming

**Rule**: Resolvers must be named `{Type}Resolver`.

**Rationale**: Clear naming convention.

**Example**:
```kotlin
// ✅ Correct
class CustomerResolver

// ❌ Incorrect
class CustomerHandler
class CustomerController
```

## Related Documentation

- [GraphQL Federation Pattern](../03-patterns/shared/graphql-federation.md)
- [Resolver Pattern](../03-patterns/backend/resolver-pattern.md)

