---
title: GraphQL Mutation Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [graphql, mutation, apollo, cache, refetch, frontend]
ai_optimized: true
search_keywords: [graphql, mutation, apollo, refetchQueries, cache, list, table, update]
related:
  - 04-patterns/frontend-patterns/toast-notification-pattern.md
  - 04-patterns/api-patterns/graphql-federation.md
  - 05-standards/api-standards/graphql-standards.md
---

# GraphQL Mutation Pattern

> **Purpose**: Standard pattern for ensuring GraphQL mutations immediately update list/table queries in the UI, preventing stale data issues when users interact with sorted or filtered lists.

## Overview

When a user performs a mutation (create, update, delete, enable/disable) on an item that appears in a list or table, the UI must immediately reflect the change. This is especially critical when:
- The list is sorted or filtered
- The user changes sort order immediately after a mutation
- Multiple queries with different variables might be cached

## Core Requirement

**Rule**: All mutations that affect list/table data MUST use `refetchQueries` to ensure the list query is refetched with the current variables (including sort, filter, and pagination state).

**Rationale**: 
- Apollo Client caches queries by their variables
- A simple `refetch()` call may not update queries with different variable combinations
- Users may change sort/filter immediately after a mutation, causing stale cache to be served
- `refetchQueries` ensures the query is refetched with the exact current variables

## Pattern Structure

### Standard Mutation Implementation

```typescript
import { GetUsersDocument } from '@/lib/graphql/operations/authorization-management/queries.generated';
import { useEnableUserMutation, useDisableUserMutation } from '@/lib/graphql/operations/authorization-management/mutations.generated';

export function useUserManagement() {
  const [first, setFirst] = useState(10);
  const [after, setAfter] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [orderBy, setOrderBy] = useState<UserSortState>(null);

  // Convert sort state to GraphQL format
  const graphQLOrderBy = useMemo(() => toGraphQLOrderBy(orderBy), [orderBy]);

  // Query with current variables
  const { data, loading, error, refetch } = useGetUsersQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: graphQLOrderBy || undefined,
    },
  });

  const [enableUserMutation] = useEnableUserMutation();
  const [disableUserMutation] = useDisableUserMutation();

  // ✅ CORRECT: Use refetchQueries with current variables
  const enableUser = useCallback(async (userId: string) => {
    try {
      const result = await enableUserMutation({
        variables: { userId },
        refetchQueries: [
          {
            query: GetUsersDocument,
            variables: {
              first,
              after: after || undefined,
              query: searchQuery || undefined,
              orderBy: graphQLOrderBy || undefined,
            },
          },
        ],
      });

      // Optional: Also call refetch for immediate UI update
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error enabling user:', err);
      throw err;
    }
  }, [enableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);

  const disableUser = useCallback(async (userId: string) => {
    try {
      const result = await disableUserMutation({
        variables: { userId },
        refetchQueries: [
          {
            query: GetUsersDocument,
            variables: {
              first,
              after: after || undefined,
              query: searchQuery || undefined,
              orderBy: graphQLOrderBy || undefined,
            },
          },
        ],
      });

      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error disabling user:', err);
      throw err;
    }
  }, [disableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);

  return {
    users: data?.users?.nodes || [],
    enableUser,
    disableUser,
    // ... other return values
  };
}
```

### Key Components

1. **Import the Query Document**: Import the generated query document (e.g., `GetUsersDocument`)
2. **Include All Current Variables**: Pass all current query variables to `refetchQueries`
3. **Dependency Array**: Include all variables in the `useCallback` dependency array

## Common Errors and How to Avoid Them

### ❌ Error: Missing refetchQueries

```typescript
// ❌ INCORRECT - Mutation doesn't update list when sort changes
const disableUser = useCallback(async (userId: string) => {
  try {
    await disableUserMutation({
      variables: { userId },
    });
    refetch(); // Only refetches with current variables, may miss cached queries
  } catch (err) {
    throw err;
  }
}, [disableUserMutation, refetch]);
```

**Problem**: If user changes sort order immediately after mutation, Apollo may serve cached data for the new sort order before the refetch completes.

### ❌ Error: Missing Variables in refetchQueries

```typescript
// ❌ INCORRECT - Missing variables means query won't match current state
const disableUser = useCallback(async (userId: string) => {
  try {
    await disableUserMutation({
      variables: { userId },
      refetchQueries: [
        {
          query: GetUsersDocument,
          // Missing variables! Won't match current query state
        },
      ],
    });
  } catch (err) {
    throw err;
  }
}, [disableUserMutation]);
```

**Problem**: Without variables, Apollo can't match the exact query, so it may not refetch the correct cached query.

### ❌ Error: Missing Variables in Dependency Array

```typescript
// ❌ INCORRECT - Stale closure may use old variable values
const disableUser = useCallback(async (userId: string) => {
  try {
    await disableUserMutation({
      variables: { userId },
      refetchQueries: [
        {
          query: GetUsersDocument,
          variables: {
            first,
            after: after || undefined,
            query: searchQuery || undefined,
            orderBy: graphQLOrderBy || undefined,
          },
        },
      ],
    });
  } catch (err) {
    throw err;
  }
}, [disableUserMutation]); // Missing variables in dependencies!
```

**Problem**: The callback may capture stale values of `first`, `after`, `searchQuery`, or `orderBy`, causing the wrong query to be refetched.

### ✅ Correct Pattern

```typescript
// ✅ CORRECT - All variables included, dependencies complete
const disableUser = useCallback(async (userId: string) => {
  try {
    const result = await disableUserMutation({
      variables: { userId },
      refetchQueries: [
        {
          query: GetUsersDocument,
          variables: {
            first,
            after: after || undefined,
            query: searchQuery || undefined,
            orderBy: graphQLOrderBy || undefined,
          },
        },
      ],
    });

    // Optional: Also call refetch for immediate UI update
    if (result.data) {
      refetch();
    }
  } catch (err) {
    console.error('Error disabling user:', err);
    throw err;
  }
}, [disableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);
```

## Complete Example: User Management Hook

```typescript
"use client";

import { useState, useMemo, useCallback } from "react";
import {
  useGetUsersQuery,
  GetUsersDocument,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import {
  useEnableUserMutation,
  useDisableUserMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { extractErrorMessage } from '@/shared/utils/error';
import { toGraphQLOrderBy, type UserSortState, type UserOrderField } from '@/shared/utils/sorting';

export function useUserManagement() {
  // State management
  const [first, setFirst] = useState(10);
  const [after, setAfter] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [orderBy, setOrderBy] = useState<UserSortState>(null);

  // Convert sort state to GraphQL format
  const graphQLOrderBy = useMemo(() => toGraphQLOrderBy(orderBy), [orderBy]);

  // Query with current variables
  const { data: usersData, loading, error, refetch } = useGetUsersQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: graphQLOrderBy || undefined,
    },
  });

  // Mutations
  const [enableUserMutation, { loading: enableLoading }] = useEnableUserMutation();
  const [disableUserMutation, { loading: disableLoading }] = useDisableUserMutation();

  // ✅ CORRECT: Enable user with refetchQueries
  const enableUser = useCallback(async (userId: string) => {
    try {
      const result = await enableUserMutation({
        variables: { userId },
        refetchQueries: [
          {
            query: GetUsersDocument,
            variables: {
              first,
              after: after || undefined,
              query: searchQuery || undefined,
              orderBy: graphQLOrderBy || undefined,
            },
          },
        ],
      });

      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error enabling user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to enable user');
      throw new Error(errorMessage);
    }
  }, [enableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);

  // ✅ CORRECT: Disable user with refetchQueries
  const disableUser = useCallback(async (userId: string) => {
    try {
      const result = await disableUserMutation({
        variables: { userId },
        refetchQueries: [
          {
            query: GetUsersDocument,
            variables: {
              first,
              after: after || undefined,
              query: searchQuery || undefined,
              orderBy: graphQLOrderBy || undefined,
            },
          },
        ],
      });

      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error disabling user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to disable user');
      throw new Error(errorMessage);
    }
  }, [disableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);

  return {
    users: usersData?.users?.nodes || [],
    loading,
    enableUser,
    disableUser,
    enableLoading,
    disableLoading,
    error,
    refetch,
    // ... other return values
  };
}
```

## When to Use This Pattern

Use `refetchQueries` for mutations that affect:

1. **List/Table Queries**: Any mutation that changes data visible in a list or table
2. **Sorted Lists**: When the list can be sorted by different fields
3. **Filtered Lists**: When the list can be filtered or searched
4. **Paginated Lists**: When the list uses pagination
5. **Status Changes**: Enable/disable, activate/deactivate operations
6. **Create/Update/Delete**: Any CRUD operation that affects list visibility

## Quick Reference Checklist

When implementing mutations that affect lists, verify:

- [ ] Query document imported (e.g., `GetUsersDocument`)
- [ ] `refetchQueries` includes the query document
- [ ] All current query variables included in `refetchQueries.variables`
- [ ] All variables included in `useCallback` dependency array
- [ ] Variables match exactly the query variables (including `undefined` for optional params)
- [ ] Optional: `refetch()` called after successful mutation for immediate UI update
- [ ] Error handling implemented
- [ ] Toast notifications shown (see [Toast Notification Pattern](./toast-notification-pattern.md))

## Related Documentation

- [Toast Notification Pattern](./toast-notification-pattern.md) - User feedback for mutations
- [GraphQL Federation Pattern](../api-patterns/graphql-federation.md) - GraphQL architecture
- [GraphQL Standards](../../05-standards/api-standards/graphql-standards.md) - API standards

