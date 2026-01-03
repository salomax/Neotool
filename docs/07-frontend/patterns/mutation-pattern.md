---
title: Mutation Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [mutation, hooks, refetch, race-condition, pattern, frontend]
ai_optimized: true
search_keywords: [mutation, useMutationWithRefetch, race condition, refetch, apollo, graphql]
related:
  - 04-patterns/frontend-patterns/graphql-mutation-pattern.md
  - 04-patterns/frontend-patterns/management-pattern.md
  - 04-patterns/frontend-patterns/toast-notification-pattern.md
---

# Mutation Pattern

> **Purpose**: Standard pattern for executing GraphQL mutations with race condition prevention and automatic refetch.

## Overview

The Mutation Pattern provides a reusable hook (`useMutationWithRefetch`) for executing mutations safely with:
- Race condition prevention (prevents duplicate mutations)
- Automatic refetch after successful mutation
- Consistent error handling

## Core Requirements

### Required Usage

Every mutation that affects list/table data MUST:

1. **Use `useMutationWithRefetch`** - For consistent mutation handling
2. **Include refetchQueries** - To update list queries after mutation
3. **Prevent race conditions** - Use unique mutation keys
4. **Handle errors** - Show user-friendly error messages

## Pattern Structure

### Hook: useMutationWithRefetch

```typescript
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { GetUsersDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";

function useUserManagement() {
  const { data, refetch } = useGetUsersQuery({
    variables: { first, after, query, orderBy },
  });

  const { executeMutation } = useMutationWithRefetch({
    refetchQuery: GetUsersDocument,
    refetchVariables: { first, after, query, orderBy },
    onRefetch: refetch,
    errorMessage: 'Failed to update user',
  });

  const [enableUserMutation] = useEnableUserMutation();

  const enableUser = useCallback(async (userId: string) => {
    await executeMutation(
      enableUserMutation,
      { userId },
      userId // Unique mutation key
    );
  }, [executeMutation, enableUserMutation]);
}
```

### Features

#### 1. Race Condition Prevention

The hook automatically prevents duplicate mutations using a unique mutation key:

```typescript
// ✅ Correct - Unique key per mutation
const enableUser = async (userId: string) => {
  await executeMutation(enableUserMutation, { userId }, userId);
};

// ✅ Correct - Composite key for relationship mutations
const assignGroupToUser = async (userId: string, groupId: string) => {
  await executeMutation(
    assignGroupMutation,
    { userId, groupId },
    `assign-group-${userId}-${groupId}`
  );
};
```

#### 2. Automatic Refetch

The hook automatically refetches queries after successful mutation:

```typescript
const { executeMutation } = useMutationWithRefetch({
  refetchQuery: GetUsersDocument,
  refetchVariables: { first, after, query, orderBy },
  onRefetch: refetch, // Optional: additional refetch callback
});
```

#### 3. Error Handling

The hook provides consistent error handling:

```typescript
const { executeMutation } = useMutationWithRefetch({
  errorMessage: 'Failed to enable user', // Default error message
});

try {
  await enableUser(userId);
  toast.success('User enabled');
} catch (err) {
  // Error is already extracted and formatted
  toast.error(err.message);
}
```

## Complete Example

```typescript
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { useToast } from "@/shared/providers";
import { GetUsersDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useEnableUserMutation, useDisableUserMutation } from "@/lib/graphql/operations/authorization-management/mutations.generated";

function useUserManagement() {
  const toast = useToast();
  const { data, refetch } = useGetUsersQuery({
    variables: { first, after, query, orderBy },
  });

  // Setup mutation hook
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery: GetUsersDocument,
    refetchVariables: { first, after, query, orderBy },
    onRefetch: refetch,
    errorMessage: 'Failed to update user',
  });

  // Mutation hooks
  const [enableUserMutation] = useEnableUserMutation();
  const [disableUserMutation] = useDisableUserMutation();

  // Enable user
  const enableUser = useCallback(async (userId: string) => {
    try {
      await executeMutation(enableUserMutation, { userId }, userId);
      toast.success('User enabled successfully');
    } catch (err) {
      toast.error(err.message);
      throw err; // Re-throw for component error handling
    }
  }, [executeMutation, enableUserMutation, toast]);

  // Disable user
  const disableUser = useCallback(async (userId: string) => {
    try {
      await executeMutation(disableUserMutation, { userId }, userId);
      toast.success('User disabled successfully');
    } catch (err) {
      toast.error(err.message);
      throw err;
    }
  }, [executeMutation, disableUserMutation, toast]);

  return {
    enableUser,
    disableUser,
    // ... other return values
  };
}
```

## Mutation Key Patterns

### Single Entity Mutations

Use the entity ID as the mutation key:

```typescript
// Enable/disable user
await executeMutation(enableUserMutation, { userId }, userId);

// Delete item
await executeMutation(deleteItemMutation, { itemId }, itemId);
```

### Relationship Mutations

Use composite keys for relationship mutations:

```typescript
// Assign group to user
await executeMutation(
  assignGroupMutation,
  { userId, groupId },
  `assign-group-${userId}-${groupId}`
);

// Remove role from user
await executeMutation(
  removeRoleMutation,
  { userId, roleId },
  `remove-role-${userId}-${roleId}`
);
```

### Bulk Mutations

Use operation-specific keys for bulk mutations:

```typescript
// Bulk enable users
await executeMutation(
  bulkEnableMutation,
  { userIds },
  `bulk-enable-${userIds.join('-')}`
);
```

## Common Patterns

### Pattern: Mutation with Toast Notification

```typescript
const enableUser = useCallback(async (userId: string) => {
  try {
    await executeMutation(enableUserMutation, { userId }, userId);
    toast.success(t('userManagement.toast.userEnabled'));
  } catch (err) {
    toast.error(err.message);
    throw err;
  }
}, [executeMutation, enableUserMutation, toast, t]);
```

### Pattern: Mutation with Loading State

```typescript
const [enableUserMutation, { loading: enableLoading }] = useEnableUserMutation();

const enableUser = useCallback(async (userId: string) => {
  if (enableLoading) return; // Prevent if already loading
  
  await executeMutation(enableUserMutation, { userId }, userId);
}, [executeMutation, enableUserMutation, enableLoading]);
```

### Pattern: Conditional Refetch

```typescript
const { executeMutation } = useMutationWithRefetch({
  refetchQuery: GetUsersDocument,
  refetchVariables: { first, after, query, orderBy },
  onRefetch: () => {
    // Only refetch if certain conditions are met
    if (shouldRefetch) {
      refetch();
    }
  },
});
```

## Anti-Patterns

### ❌ Error: Not Using Mutation Hook

```typescript
// ❌ Incorrect - No race condition prevention, no automatic refetch
const enableUser = async (userId: string) => {
  await enableUserMutation({ variables: { userId } });
};
```

### ❌ Error: Duplicate Mutation Keys

```typescript
// ❌ Incorrect - Same key for different operations
await executeMutation(enableUserMutation, { userId }, 'user-operation');
await executeMutation(disableUserMutation, { userId }, 'user-operation');
```

### ❌ Error: Missing Refetch Variables

```typescript
// ❌ Incorrect - Missing current query variables
const { executeMutation } = useMutationWithRefetch({
  refetchQuery: GetUsersDocument,
  // Missing refetchVariables - will refetch with wrong variables
});
```

## Quick Reference Checklist

When implementing mutations, verify:

- [ ] `useMutationWithRefetch` imported and used
- [ ] Unique mutation key provided for each mutation
- [ ] `refetchQuery` includes the query document
- [ ] `refetchVariables` includes all current query variables
- [ ] Error handling implemented
- [ ] Toast notifications shown (see [Toast Notification Pattern](./toast-notification-pattern.md))
- [ ] Race condition prevention working (test by clicking rapidly)
- [ ] Refetch happens after successful mutation
- [ ] Loading states handled correctly

## Related Documentation

- [GraphQL Mutation Pattern](./graphql-mutation-pattern.md) - GraphQL mutation patterns with refetchQueries
- [Management Pattern](./management-pattern.md) - Complete management module pattern
- [Toast Notification Pattern](./toast-notification-pattern.md) - User feedback for mutations

