---
title: Optimistic Toggle Pattern
category: frontend
tags: [hooks, components, optimistic-updates, mutations]
related:
  - ../frontend-patterns/graphql-mutation-pattern.md
  - ../../04-patterns/frontend-patterns/management-pattern.md
---

# Optimistic Toggle Pattern

This document describes the reusable pattern for implementing toggles with optimistic updates, preventing UI blinking during mutations.

## Problem

When implementing status toggles (enable/disable) with async mutations, the UI can "blink" or flicker during the mutation because:

1. The component waits for server response before updating
2. During refetch, data may temporarily be unavailable
3. All toggles may be disabled when one is being updated

## Solution

Use the `useOptimisticUpdate` hook to:
- Immediately update UI when user performs action
- Track update state independently per component
- Automatically sync with server state
- Revert on error

## Implementation

### Hook: `useOptimisticUpdate`

**Location**: `web/src/shared/hooks/mutations/useOptimisticUpdate.ts`

```tsx
import { useOptimisticUpdate } from "@/shared/hooks/mutations";

const {
  optimisticValue,
  isUpdating,
  executeUpdate,
} = useOptimisticUpdate({
  value: enabled,
});

await executeUpdate(newValue, () => onToggle(newValue));
```

### Component: `StatusToggle`

**Location**: `web/src/shared/components/ui/StatusToggle.tsx`

Generic toggle component with optimistic updates built-in.

```tsx
<StatusToggle
  value={user.enabled}
  onChange={(enabled) => updateUser(user.id, enabled)}
  enabledTooltip="Disable user"
  disabledTooltip="Enable user"
/>
```

## Example: UserStatusToggle

### Before (Manual Implementation)

```tsx
// ~135 lines with manual state management
const [optimisticEnabled, setOptimisticEnabled] = useState(enabled);
const [isToggling, setIsToggling] = useState(false);
const previousEnabledRef = useRef(enabled);
const isTogglingRef = useRef(false);

// Complex useEffect for syncing
useEffect(() => {
  if (!isTogglingRef.current && enabled !== optimisticEnabled) {
    setOptimisticEnabled(enabled);
    previousEnabledRef.current = enabled;
  }
}, [enabled, optimisticEnabled]);

// Manual error handling and rollback
const handleToggle = async (checked: boolean) => {
  // ... 30+ lines of state management
};
```

### After (Using Hook)

```tsx
// ~60 lines, much cleaner
const {
  optimisticValue: optimisticEnabled,
  isUpdating: isToggling,
  executeUpdate,
} = useOptimisticUpdate({
  value: enabled,
});

const handleToggle = async (checked: boolean) => {
  await executeUpdate(checked, () => onToggle(user.id, checked));
};
```

## Benefits

1. **Reusability**: Use across all toggle components
2. **Consistency**: Same behavior everywhere
3. **Maintainability**: Centralized logic
4. **Testability**: Easier to test hook separately
5. **Type Safety**: Full TypeScript support

## Usage Guidelines

### When to Use

- Any boolean toggle with async mutations
- Status toggles (enabled/disabled)
- Feature flags
- Any UI that needs immediate feedback

### When NOT to Use

- Synchronous operations (no need for optimistic updates)
- Non-boolean toggles (use different pattern)
- Operations that must wait for server confirmation

## Migration Guide

1. Import the hook: `import { useOptimisticUpdate } from "@/shared/hooks/mutations"`
2. Replace manual state with hook
3. Use `executeUpdate` instead of manual mutation handling
4. Remove refs and complex useEffect logic

## Related Patterns

- [GraphQL Mutation Pattern](../04-patterns/frontend-patterns/graphql-mutation-pattern.md)
- [Management Pattern](../04-patterns/frontend-patterns/management-pattern.md)
- [Management Pattern](../04-patterns/frontend-patterns/management-pattern.md)

