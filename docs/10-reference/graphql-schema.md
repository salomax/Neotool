---
title: GraphQL Schema Reference
type: reference
category: graphql
status: current
version: 2.0.0
tags: [reference, graphql, schema]
ai_optimized: true
search_keywords: [graphql, schema, reference]
related:
  - 04-patterns/api-patterns/graphql-query-pattern.md
  - 05-standards/api-standards/graphql-standards.md
---

# GraphQL Schema Reference

> **Purpose**: Quick reference for GraphQL schema patterns.

## Entity Definition

```graphql
type Product @key(fields: "id") {
  id: ID!
  name: String!
  price: Float!
}
```

## Federation

Use `@key` directive for federated entities.

## Related Documentation

- [GraphQL Query Pattern](../04-patterns/api-patterns/graphql-query-pattern.md)
- [GraphQL Standards](../05-standards/api-standards/graphql-standards.md)

