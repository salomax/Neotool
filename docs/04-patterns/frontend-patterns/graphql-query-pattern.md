---
title: GraphQL Query Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [graphql, query, apollo, cache, pagination, relay, frontend]
ai_optimized: true
search_keywords: [graphql, query, apollo, cache, pagination, relay, relayStylePagination, InMemoryCache]
related:
  - 04-patterns/frontend-patterns/graphql-mutation-pattern.md
  - 04-patterns/backend-patterns/pagination-pattern.md
  - 05-standards/api-standards/graphql-standards.md
---

# GraphQL Query Pattern

> **Purpose**: Standard patterns for implementing GraphQL queries in the frontend using Apollo Client, including proper cache configuration for Relay-style pagination.

## Overview

This document covers best practices for:
- Configuring Apollo Client cache for Relay-style pagination
- Handling paginated queries with proper cache merging
- Avoiding cache warnings and data loss issues

## Apollo Client Cache Configuration

### Rule: Use relayStylePagination for Relay Connections

**Rule**: All Relay-style connection fields (queries that return `*Connection` types) MUST use Apollo Client's `relayStylePagination` helper in the cache configuration.

**Rationale**:
- Connection types don't have `id` fields, so Apollo Client can't automatically normalize them
- Without proper cache configuration, Apollo Client will warn about potential cache data loss
- `relayStylePagination` properly handles:
  - `keyArgs`: Excludes pagination args (`first`, `after`) and includes filter/sort args (`query`, `orderBy`)
  - `merge`: Properly merges paginated results when loading more pages
  - Cache separation: Different queries/filters are cached separately

**Example**:

```typescript
// ✅ CORRECT: Using relayStylePagination
import { ApolloClient, InMemoryCache } from '@apollo/client';
import { relayStylePagination } from '@apollo/client/utilities';

const apolloClient = new ApolloClient({
  cache: new InMemoryCache({
    typePolicies: {
      Query: {
        fields: {
          // Include filter/sort args in keyArgs, exclude pagination args
          users: relayStylePagination(['query', 'orderBy']),
          groups: relayStylePagination(['query', 'orderBy']),
          roles: relayStylePagination(['query', 'orderBy']),
          permissions: relayStylePagination(['query']),
        },
      },
    },
  }),
});
```

**Configuration Details**:

1. **keyArgs Parameter**: Specifies which arguments should be part of the cache key
   - **Include**: Arguments that define the dataset (`query`, `orderBy`, `filter`, etc.)
   - **Exclude**: Pagination arguments (`first`, `after`, `before`, `last`)
   - This ensures different queries/filters are cached separately, while pagination results are merged

2. **Automatic Merge**: `relayStylePagination` automatically:
   - Merges edges when loading more pages (appending with `after`, prepending with `before`)
   - Replaces data when no cursor is provided (new query/filter)
   - Handles out-of-order pagination requests

**❌ Incorrect Approaches**:

```typescript
// ❌ INCORRECT: Simple replace merge - doesn't handle pagination properly
cache: new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {
        users: {
          merge(existing, incoming) {
            return incoming; // Always replaces, doesn't merge pages
          },
        },
      },
    },
  },
});

// ❌ INCORRECT: No cache configuration - causes warnings
cache: new InMemoryCache(), // Will show cache warnings for connections
```

**Problems with incorrect approaches**:
- Simple replace merge doesn't properly merge paginated results when loading more pages
- No configuration causes Apollo Client warnings about cache data loss
- Different query variables aren't properly separated in cache

## GraphQL Query Structure

### Relay Connection Pattern

All paginated queries follow the Relay connection pattern:

```graphql
query GetUsers($first: Int, $after: String, $query: String, $orderBy: [UserOrderByInput!]) {
  users(first: $first, after: $after, query: $query, orderBy: $orderBy) {
    edges {
      node {
        id
        email
        displayName
      }
      cursor
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
    totalCount
  }
}
```

### Query Variables

**Pagination Variables** (excluded from cache key):
- `first`: Number of items to fetch
- `after`: Cursor for forward pagination
- `before`: Cursor for backward pagination (if supported)
- `last`: Number of items for backward pagination (if supported)

**Filter/Sort Variables** (included in cache key):
- `query`: Search/filter string
- `orderBy`: Sort configuration
- `filter`: Additional filter parameters (if any)

## Implementation Example

### Complete Apollo Client Setup

```typescript
// web/src/lib/graphql/client.ts
import { ApolloClient, InMemoryCache, HttpLink, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { relayStylePagination } from '@apollo/client/utilities';

const httpLink = new HttpLink({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
});

const authLink = setContext((_, { headers }) => {
  let token: string | null = null;
  if (typeof window !== 'undefined') {
    token = localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
  }
  
  return {
    headers: {
      ...headers,
      ...(token && { authorization: `Bearer ${token}` }),
    },
  };
});

const apolloClient = new ApolloClient({
  link: from([authLink, httpLink]),
  cache: new InMemoryCache({
    typePolicies: {
      Query: {
        fields: {
          // Relay-style pagination for all connection fields
          users: relayStylePagination(['query', 'orderBy']),
          groups: relayStylePagination(['query', 'orderBy']),
          roles: relayStylePagination(['query', 'orderBy']),
          permissions: relayStylePagination(['query']),
        },
      },
    },
  }),
});

export { apolloClient };
```

### Using Queries in Components

```typescript
import { useGetUsersQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';

function UserList() {
  const [first, setFirst] = useState(10);
  const [after, setAfter] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [orderBy, setOrderBy] = useState<UserSortState>(null);

  const { data, loading, error } = useGetUsersQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: orderBy || undefined,
    },
  });

  // Apollo Client automatically handles:
  // - Caching queries with different variables separately
  // - Merging paginated results when loading more pages
  // - Avoiding duplicate requests for cached data

  const users = data?.users?.edges?.map(edge => edge.node) || [];
  
  return (
    <div>
      {users.map(user => (
        <div key={user.id}>{user.email}</div>
      ))}
    </div>
  );
}
```

## Cache Behavior

### How relayStylePagination Works

1. **Cache Key Generation**:
   - Cache key includes: field name + filter/sort args (`query`, `orderBy`)
   - Cache key excludes: pagination args (`first`, `after`)
   - Example: `users({"query": "john", "orderBy": [...]})` vs `users({"query": "jane", "orderBy": [...]})`

2. **Data Merging**:
   - When `after` cursor is provided: Appends new edges to existing edges
   - When `before` cursor is provided: Prepends new edges to existing edges
   - When no cursor: Replaces existing data (new query/filter)

3. **Cache Separation**:
   - Different search queries are cached separately
   - Different sort orders are cached separately
   - Pagination results are merged within the same cache entry

### Example Cache Behavior

```typescript
// Query 1: users(query: "john", first: 10)
// Cache: users({"query": "john"}) = { edges: [...10 items...], pageInfo: {...} }

// Query 2: users(query: "john", first: 10, after: "cursor1")
// Cache: users({"query": "john"}) = { edges: [...20 items merged...], pageInfo: {...} }
// ✅ Correctly merged with previous query

// Query 3: users(query: "jane", first: 10)
// Cache: users({"query": "jane"}) = { edges: [...10 items...], pageInfo: {...} }
// ✅ Separate cache entry for different query

// Query 4: users(query: "john", orderBy: [{field: NAME, direction: DESC}], first: 10)
// Cache: users({"query": "john", "orderBy": [...]}) = { edges: [...10 items...], pageInfo: {...} }
// ✅ Separate cache entry for different sort order
```

## Common Issues and Solutions

### Issue: Cache Warning

**Warning**:
```
Cache data may be lost when replacing the users field of a Query object.
```

**Solution**: Use `relayStylePagination` as described above.

### Issue: Pagination Not Merging

**Problem**: Loading more pages doesn't append to existing results.

**Solution**: Ensure `relayStylePagination` is configured with correct `keyArgs` (excluding pagination args).

### Issue: Different Queries Sharing Cache

**Problem**: Different search queries show the same cached data.

**Solution**: Include filter/sort args in `keyArgs` parameter of `relayStylePagination`.

## Best Practices

1. **Always Configure Connection Fields**: Every `*Connection` field should use `relayStylePagination`
2. **Include Filter/Sort Args**: Add all arguments that define the dataset to `keyArgs`
3. **Exclude Pagination Args**: Never include `first`, `after`, `before`, `last` in `keyArgs`
4. **Test Cache Behavior**: Verify that different queries are cached separately and pagination merges correctly
5. **Monitor Warnings**: Check browser console for Apollo Client cache warnings

## Quick Reference Checklist

When setting up Apollo Client cache for Relay pagination:

- [ ] Import `relayStylePagination` from `@apollo/client/utilities`
- [ ] Configure all connection fields in `typePolicies.Query.fields`
- [ ] Include filter/sort args in `keyArgs` (e.g., `['query', 'orderBy']`)
- [ ] Exclude pagination args from `keyArgs` (never include `first`, `after`)
- [ ] Test that different queries are cached separately
- [ ] Test that pagination merges correctly when loading more pages
- [ ] Verify no cache warnings in browser console

## Related Documentation

- [GraphQL Mutation Pattern](./graphql-mutation-pattern.md) - Mutation patterns and cache updates
- [Pagination Pattern](../backend-patterns/pagination-pattern.md) - Backend pagination implementation
- [GraphQL Standards](../../05-standards/api-standards/graphql-standards.md) - API standards
