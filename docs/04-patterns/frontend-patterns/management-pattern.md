---
title: Management Pattern
type: pattern
category: frontend
status: current
version: 1.4.0
tags: [management, hooks, components, reusable, pattern, frontend]
ai_optimized: true
search_keywords: [management, useManagementBase, useDebouncedSearch, useSorting, ManagementTable, ManagementList, AssignmentComponent, ErrorAlert, DeleteConfirmationDialog, useToggleStatus, ManagementLayout, reusable, hooks, components, duplicate hooks, child components, drawer, modal, table, columns, pagination, Header, Content]
related:
  - 04-patterns/frontend-patterns/graphql-mutation-pattern.md
  - 04-patterns/frontend-patterns/toast-notification-pattern.md
  - 04-patterns/frontend-patterns/mutation-pattern.md
  - 04-patterns/frontend-patterns/shared-components-pattern.md
---

# Management Pattern

> **Purpose**: Standard pattern for creating reusable management modules (UserManagement, GroupManagement, RoleManagement, etc.) with consistent hooks and components.

## Overview

The Management Pattern provides reusable hooks and components for building consistent management interfaces across the application. This pattern reduces code duplication and ensures consistent behavior for search, pagination, sorting, and mutations.

## Core Requirements

### Required Hooks

Every management module MUST use:

1. **`useDebouncedSearch`** - For managing search input with debounce
2. **`useSorting`** - For managing sort state and GraphQL conversion
3. **`useMutationWithRefetch`** - For mutations with race condition prevention

### Required Components

Management modules SHOULD use:

1. **`ErrorAlert`** - Generic error alert with retry functionality (from `@/shared/components/ui/feedback`)
2. **`ManagementLayout`** - Layout wrapper for consistent management page structure (from `@/shared/components/management`)
3. **`DeleteConfirmationDialog`** - Generic delete confirmation dialog (from `@/shared/components/ui/feedback`)
4. **`ManagementTable`** - Generic table component with columns, loading states, pagination, sorting (from `@/shared/components/management`)
5. **`AssignmentComponent`** - Generic component for assigning/removing relationships (future)

### Recommended Hooks

Management modules SHOULD use:

1. **`useToggleStatus`** - For enable/disable toggle operations with toast notifications (from `@/shared/hooks/mutations`)

## Pattern Structure

### Hook: useDebouncedSearch

Manages search input with debounce, separating immediate input state from debounced search state.

```typescript
import { useDebouncedSearch } from "@/shared/hooks/search";

function UserManagement() {
  const { inputValue, searchQuery, handleInputChange, handleSearch } = useDebouncedSearch({
    initialValue: "",
    debounceMs: 300,
    onSearchChange: () => goToFirstPage(),
  });

  return (
    <input
      value={inputValue}
      onChange={(e) => {
        handleInputChange(e.target.value);
        handleSearch(e.target.value);
      }}
    />
  );
}
```

**Features:**
- Immediate input updates (no lag while typing)
- Debounced search queries (reduces API calls)
- Automatic pagination reset on search change

### Hook: useSorting

Manages sort state and converts to GraphQL format.

```typescript
import { useSorting } from "@/shared/hooks/sorting";

type UserOrderField = 'DISPLAY_NAME' | 'EMAIL' | 'ENABLED';

function UserList() {
  const { orderBy, graphQLOrderBy, handleSort } = useSorting<UserOrderField>({
    initialSort: null,
    onSortChange: () => goToFirstPage(),
  });

  return (
    <TableSortLabel
      active={orderBy?.field === 'DISPLAY_NAME'}
      direction={orderBy?.direction || 'asc'}
      onClick={() => handleSort('DISPLAY_NAME')}
    >
      Name
    </TableSortLabel>
  );
}
```

**Features:**
- Sort state management (null -> asc -> desc -> null)
- Automatic GraphQL format conversion
- Automatic pagination reset on sort change

### Hook: useMutationWithRefetch

Executes mutations with race condition prevention and automatic refetch.

```typescript
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { GetUsersDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";

function useUserManagement() {
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
      userId // mutation key for race condition prevention
    );
  }, [executeMutation, enableUserMutation]);
}
```

**Features:**
- Race condition prevention (prevents duplicate mutations)
- Automatic refetch after successful mutation
- Error handling with user-friendly messages

### Hook: useToggleStatus

Handles enable/disable toggle operations with consistent error handling and toast notifications.

```typescript
import { useToggleStatus } from "@/shared/hooks/mutations";

function UserManagement() {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { enableUser, disableUser } = useUserManagement();

  const handleToggleStatus = useToggleStatus({
    enableFn: enableUser,
    disableFn: disableUser,
    enableSuccessMessage: "userManagement.toast.userEnabled",
    disableSuccessMessage: "userManagement.toast.userDisabled",
    enableErrorMessage: "userManagement.toast.userEnableError",
    disableErrorMessage: "userManagement.toast.userDisableError",
    t,
  });

  return (
    <UserList
      onToggleStatus={handleToggleStatus}
      // ... other props
    />
  );
}
```

**Features:**
- Consistent error handling pattern
- Automatic toast notifications (success/error)
- Reusable across different entity types
- Reduces boilerplate in management components

## Component Patterns

### ErrorAlert Component

Generic error alert component with optional retry functionality and flexible visibility control.

```typescript
import { ErrorAlert } from "@/shared/components/ui/feedback";

function UserManagement() {
  const { error, refetch } = useUserManagement();

  return (
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      {/* ErrorAlert is automatically rendered by ManagementLayout */}
      {/* ... other subcomponents */}
    </ManagementLayout>
  );
}
```

**Key Features**:
- Supports both `Error` objects and `string` messages
- Optional `onRetry` callback (close button only appears when provided)
- Optional `visible` prop for explicit visibility control
- Auto-hides when no error (backward compatible default behavior)
- No need for conditional rendering syntax - component handles it internally

See [Shared Components Pattern](./shared-components-pattern.md#erroralert) for complete documentation.

**Features:**
- Conditional rendering (returns null if no error)
- Retry functionality via onClose callback
- Fallback message support
- Consistent error UI across all management pages

### DeleteConfirmationDialog Component

Generic delete confirmation dialog with i18n support.

```typescript
import { DeleteConfirmationDialog } from "@/shared/components/ui/feedback";

function RoleManagement() {
  const { t } = useTranslation(authorizationManagementTranslations);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState<Role | null>(null);
  const { deleteRole, deleteLoading } = useRoleManagement();

  const handleDeleteConfirm = useCallback(async () => {
    if (roleToDelete) {
      try {
        await deleteRole(roleToDelete.id);
        toast.success(t("roleManagement.toast.roleDeleted", { name: roleToDelete.name }));
        setDeleteConfirmOpen(false);
        setRoleToDelete(null);
      } catch (err) {
        toast.error(extractErrorMessage(err, t("roleManagement.toast.roleDeleteError")));
      }
    }
  }, [roleToDelete, deleteRole, toast, t]);

  return (
    <DeleteConfirmationDialog
      open={deleteConfirmOpen}
      item={roleToDelete}
      loading={deleteLoading}
      onConfirm={handleDeleteConfirm}
      onCancel={() => {
        setDeleteConfirmOpen(false);
        setRoleToDelete(null);
      }}
      titleKey="roleManagement.deleteDialog.title"
      messageKey="roleManagement.deleteDialog.message"
      cancelKey="roleManagement.deleteDialog.cancel"
      deleteKey="roleManagement.deleteDialog.delete"
      deletingKey="roleManagement.deleteDialog.deleting"
      t={t}
    />
  );
}
```

**Features:**
- Generic type parameter for any item with `id` and `name`
- i18n support through translation keys
- Replaces `{name}` placeholder in messages
- Loading state disables buttons
- Consistent delete confirmation UX

### ManagementLayout Component

Generic layout wrapper for consistent management page structure using static subcomponents.

```typescript
import { ManagementLayout } from "@/shared/components/management/ManagementLayout";
import { DeleteConfirmationDialog } from "@/shared/components/ui/feedback";

function UserManagement() {
  const { error, refetch, users, loading, ... } = useUserManagement();

  return (
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <Box sx={{ display: "flex", gap: 2, alignItems: "flex-end" }}>
          <Box sx={{ flexGrow: 1 }} maxWidth="sm">
            <UserSearch
              value={inputValue}
              onChange={handleInputChange}
              onSearch={handleSearch}
              placeholder={t("userManagement.searchPlaceholder")}
            />
          </Box>
          <Box sx={{ mb: 2 }}>
            <Button onClick={handleCreate}>Create</Button>
          </Box>
        </Box>
      </ManagementLayout.Header>
      <ManagementLayout.Content>
        <UserList
          users={users}
          loading={loading}
          // ... other props
        />
        {/* Delete dialogs should be included within Content */}
        {deleteConfirmOpen && (
          <DeleteConfirmationDialog {...deleteDialogProps} />
        )}
      </ManagementLayout.Content>
      <ManagementLayout.Drawer>
        <UserDrawer
          open={drawerOpen}
          onClose={handleCloseDrawer}
          userId={editingUser?.id || null}
        />
      </ManagementLayout.Drawer>
    </ManagementLayout>
  );
}
```

**Features:**
- Consistent layout structure across all management pages
- Error alert at the top (automatically rendered)
- Static subcomponents for type-safe slot-based composition:
  - `ManagementLayout.Header` - Wraps header content (search, actions, filters, etc.)
  - `ManagementLayout.Content` - Wraps main content with flex layout (`flex: 1`, `minHeight: 0`)
  - `ManagementLayout.Drawer` - Wraps drawer components
- Declarative API with better type safety
- All subcomponents are optional (only include what you need)
- Generic slot names allow for flexible content (not just search/lists)
- Delete dialogs belong within the Content component, not as a separate slot

**Header Alignment Pattern:**
When displaying both a search field and action button in the Header, use `alignItems: "flex-end"` to align their bottom edges, and wrap the button in a `Box` with `mb: 2` to match the search component's margin. This ensures proper visual alignment:
- Search components (like `UserSearch`, `GroupSearch`, `RoleSearch`) have `mb: 2` margin
- Action buttons should be wrapped in `<Box sx={{ mb: 2 }}>` to match
- Use `alignItems: "flex-end"` instead of `alignItems: "center"` for bottom-edge alignment

### Drawer Cancel Button Pattern

**Default Behavior**: The Cancel button in Drawer components (UserDrawer, GroupDrawer, RoleDrawer, etc.) MUST follow this pattern:

1. **Always Enabled**: The Cancel button should remain enabled regardless of form changes. It should only be disabled during loading/saving operations to prevent interruption.

2. **Closes Drawer**: When clicked, the Cancel button MUST close the drawer by calling the `onClose` callback. Any form reset logic should be executed before closing.

3. **Implementation Pattern**:
```typescript
<Button
  variant="outlined"
  onClick={() => {
    // Reset form state if needed
    resetChanges();
    // Always close the drawer
    onClose();
  }}
  disabled={saving || createLoading || updateLoading} // Only disable during operations
>
  {t("common.cancel")}
</Button>
```

**Key Points**:
- Cancel button is NOT disabled based on `hasChanges` or form state
- Cancel button MUST call `onClose()` to close the drawer
- Cancel button MAY reset form state before closing (optional, but recommended)
- Cancel button SHOULD be disabled only during active save/load operations

This ensures users can always cancel and close the drawer, providing a consistent and predictable user experience across all management drawers.

## Avoiding Duplicate Hook Instances

### Critical Rule: Never Call Complex Management Hooks in Child Components

**Problem**: Complex management hooks (like `useUserManagement`, `useRoleManagement`, etc.) contain many sub-hooks:
- Multiple GraphQL query hooks
- Multiple GraphQL mutation hooks
- Pagination hooks (`useRelayPagination`)
- Search hooks (`useDebouncedSearch`)
- Sorting hooks (`useSorting`)
- Mutation refetch hooks (`useMutationWithRefetch`)

When both a parent component and a child component (like a drawer or modal) call the same complex hook, React sees different numbers of hooks between renders, causing the error: **"Rendered more hooks than during the previous render."**

### ❌ Incorrect Pattern: Calling Management Hook in Child Component

```typescript
// ❌ INCORRECT - Parent component
function UserManagement() {
  const { users, enableUser, disableUser, ... } = useUserManagement();
  // ... uses hook for list management
}

// ❌ INCORRECT - Child component (Drawer)
function UserDrawer({ userId, open, onClose }) {
  // This creates a SECOND instance of useUserManagement!
  // This will cause "Rendered more hooks than during the previous render" error
  const {
    assignGroupToUser,
    removeGroupFromUser,
    assignRoleToUser,
    removeRoleFromUser,
  } = useUserManagement(); // ❌ DON'T DO THIS
  
  // ... drawer implementation
}
```

**Why this fails:**
- `useUserManagement` calls 10+ hooks internally
- When drawer opens/closes, React sees different hook counts
- React's Rules of Hooks are violated

### ✅ Correct Pattern: Use Direct Mutation Hooks in Child Components

```typescript
// ✅ CORRECT - Parent component
function UserManagement() {
  const { users, enableUser, disableUser, ... } = useUserManagement();
  // ... uses hook for list management
}

// ✅ CORRECT - Child component (Drawer)
import {
  useAssignGroupToUserMutation,
  useRemoveGroupFromUserMutation,
  useAssignRoleToUserMutation,
  useRemoveRoleFromUserMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { GetUserWithRelationshipsDocument } from '@/lib/graphql/operations/authorization-management/queries.generated';

function UserDrawer({ userId, open, onClose }) {
  // Use direct mutation hooks instead of the full management hook
  const [assignGroupToUserMutation, { loading: assignGroupLoading }] = useAssignGroupToUserMutation();
  const [removeGroupFromUserMutation, { loading: removeGroupLoading }] = useRemoveGroupFromUserMutation();
  const [assignRoleToUserMutation, { loading: assignRoleLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleLoading }] = useRemoveRoleFromUserMutation();
  
  const { data, refetch } = useGetUserWithRelationshipsQuery({
    skip: !open || !userId,
    variables: { id: userId! },
  });

  const handleAssignGroup = useCallback(async (groupId: string) => {
    if (!userId) return;
    try {
      await assignGroupToUserMutation({
        variables: { userId, groupId },
        refetchQueries: [GetUserWithRelationshipsDocument],
      });
      await refetch();
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Failed to assign group'));
      throw err;
    }
  }, [userId, assignGroupToUserMutation, refetch, toast]);

  // ... rest of drawer implementation
}
```

**Why this works:**
- Only calls the specific hooks needed (4 mutation hooks + 1 query hook)
- No duplicate hook instances
- Consistent hook count across renders
- More efficient (no unnecessary pagination/search/sorting logic)

### When to Use Each Pattern

**Use the full management hook (`useUserManagement`) when:**
- Managing a list/table view
- Need pagination, search, sorting
- Need to display and manage multiple items

**Use direct mutation hooks when:**
- In child components (drawers, modals, dialogs)
- Only need specific mutations
- Don't need list management features
- Component is conditionally rendered (open/closed)

### Quick Reference

| Scenario | Pattern | Example |
|----------|---------|---------|
| List/Table component | Full management hook | `UserManagement` component |
| Drawer/Modal for editing | Direct mutation hooks | `UserDrawer` component |
| Inline edit form | Direct mutation hooks | Edit form in table row |
| Standalone form | Direct mutation hooks | Create/Edit page |

## Complete Example: UserManagement Component

Full example showing all recommended patterns:

```typescript
import { useState, useCallback } from "react";
import { useUserManagement, type User } from "@/shared/hooks/authorization/useUserManagement";
import { useToggleStatus } from "@/shared/hooks/mutations";
import { ManagementLayout } from "@/shared/components/management/ManagementLayout";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { UserSearch } from "./UserSearch";
import { UserList } from "./UserList";
import { UserDrawer } from "./UserDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";

export const UserManagement: React.FC<UserManagementProps> = ({
  initialSearchQuery = "",
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const {
    users,
    searchQuery,
    inputValue,
    handleInputChange,
    handleSearch,
    pageInfo,
    paginationRange,
    canLoadPreviousPage,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    enableUser,
    disableUser,
    loading,
    enableLoading,
    disableLoading,
    error,
    refetch,
    setFirst,
    orderBy,
    handleSort,
  } = useUserManagement({
    initialSearchQuery,
    initialFirst: 10,
  });

  const handleTableResize = useCallback(
    (pageSize: number) => {
      if (pageSize > 0) {
        setFirst(pageSize);
      }
    },
    [setFirst]
  );

  const handleEdit = useCallback((user: User) => {
    setEditingUser(user);
    setDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setEditingUser(null);
  }, []);

  // Use reusable toggle status hook
  const handleToggleStatus = useToggleStatus({
    enableFn: enableUser,
    disableFn: disableUser,
    enableSuccessMessage: "userManagement.toast.userEnabled",
    disableSuccessMessage: "userManagement.toast.userDisabled",
    enableErrorMessage: "userManagement.toast.userEnableError",
    disableErrorMessage: "userManagement.toast.userDisableError",
    t,
  });

  return (
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <UserSearch
          value={inputValue}
          onChange={handleInputChange}
          onSearch={handleSearch}
          placeholder={t("userManagement.searchPlaceholder")}
          maxWidth="sm"
        />
      </ManagementLayout.Header>
      <ManagementLayout.Content>
        <UserList
          users={users}
          loading={loading}
          onEdit={handleEdit}
          onToggleStatus={handleToggleStatus}
          toggleLoading={enableLoading || disableLoading}
          emptyMessage={
            searchQuery
              ? t("userManagement.emptySearchResults")
              : t("userManagement.emptyList")
          }
          pageInfo={pageInfo}
          paginationRange={paginationRange}
          onLoadNext={loadNextPage}
          onLoadPrevious={loadPreviousPage}
          onGoToFirst={goToFirstPage}
          canLoadPreviousPage={canLoadPreviousPage}
          onTableResize={handleTableResize}
          recalculationKey={`${users.length}-${loading ? "loading" : "ready"}`}
          orderBy={orderBy}
          onSortChange={handleSort}
        />
      </ManagementLayout.Content>
      <ManagementLayout.Drawer>
        <UserDrawer
          open={drawerOpen}
          onClose={handleCloseDrawer}
          userId={editingUser?.id || null}
        />
      </ManagementLayout.Drawer>
    </ManagementLayout>
  );
};
```

## Complete Example: UserManagement Hook

```typescript
import { useState } from "react";
import { useGetUsersQuery, GetUsersDocument } from '@/lib/graphql/operations/authorization-management/queries.generated';
import { useEnableUserMutation, useDisableUserMutation } from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import type { UserOrderField } from '@/shared/utils/sorting';

export function useUserManagement(options: UseUserManagementOptions = {}) {
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // Search with debounce
  const { searchQuery, setSearchQuery, handleInputChange, handleSearch } = useDebouncedSearch({
    initialValue: options.initialSearchQuery || "",
    debounceMs: 300,
    onSearchChange: () => goToFirstPage(),
  });

  // Sorting
  const { orderBy, graphQLOrderBy, handleSort } = useSorting<UserOrderField>({
    initialSort: null,
    onSortChange: () => setAfter(null),
  });

  // GraphQL query
  const { data, loading, error, refetch } = useGetUsersQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: graphQLOrderBy || undefined,
    },
  });

  // Mutations with refetch
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery: GetUsersDocument,
    refetchVariables: { first, after, query: searchQuery, orderBy: graphQLOrderBy },
    onRefetch: refetch,
  });

  const [enableUserMutation] = useEnableUserMutation();
  const [disableUserMutation] = useDisableUserMutation();

  const enableUser = useCallback(async (userId: string) => {
    await executeMutation(enableUserMutation, { userId }, userId);
  }, [executeMutation, enableUserMutation]);

  const disableUser = useCallback(async (userId: string) => {
    await executeMutation(disableUserMutation, { userId }, userId);
  }, [executeMutation, disableUserMutation]);

  // Pagination
  const {
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
    canLoadPreviousPage,
  } = useRelayPagination(
    data?.users?.nodes || [],
    data?.users?.pageInfo || null,
    data?.users?.totalCount ?? null,
    searchQuery,
    after,
    setAfter,
    { initialAfter: null, initialSearchQuery: options.initialSearchQuery }
  );

  return {
    users: data?.users?.nodes || [],
    searchQuery,
    setSearchQuery,
    inputValue: handleInputChange,
    handleSearch,
    orderBy,
    handleSort,
    enableUser,
    disableUser,
    loading,
    error,
    pageInfo: data?.users?.pageInfo,
    paginationRange,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    canLoadPreviousPage,
  };
}
```

## Component Pattern: ManagementTable

Generic table component with declarative column definitions, loading states, pagination, sorting, and row actions.

### Basic Usage

```typescript
import { ManagementTable, type Column } from "@/shared/components/management";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";

function UserList({ users, loading, onEdit, onToggleStatus, orderBy, onSortChange, ...props }) {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Define columns declaratively
  const columns: Column<User, UserOrderField>[] = useMemo(
    () => [
      {
        id: "name",
        label: t("userManagement.table.name"),
        accessor: (user) => (
          <Typography variant="body2" fontWeight="medium">
            {user.displayName || user.email}
          </Typography>
        ),
        sortable: !!onSortChange,
        sortField: "DISPLAY_NAME",
      },
      {
        id: "email",
        label: t("userManagement.table.email"),
        accessor: (user) => (
          <Typography variant="body2" color="text.secondary">
            {user.email}
          </Typography>
        ),
        sortable: !!onSortChange,
        sortField: "EMAIL",
      },
      {
        id: "status",
        label: t("userManagement.table.status"),
        align: "center",
        render: (user) => (
          <UserStatusToggle
            user={user}
            enabled={user.enabled}
            onToggle={onToggleStatus}
          />
        ),
        sortable: !!onSortChange,
        sortField: "ENABLED",
      },
    ],
    [t, onSortChange, onToggleStatus]
  );

  // Render actions (can return single or multiple actions)
  const renderActions = useMemo(
    () => (user: User) => (
      <Tooltip title={t("userManagement.editUser")}>
        <IconButton onClick={() => onEdit(user)}>
          <EditIcon />
        </IconButton>
      </Tooltip>
    ),
    [t, onEdit]
  );

  return (
    <ManagementTable<User, UserOrderField>
      columns={columns}
      data={users}
      loading={loading}
      emptyMessage={t("userManagement.emptyList")}
      sortState={orderBy}
      onSortChange={onSortChange}
      renderActions={renderActions}
      actionsLabel={t("userManagement.table.actions")}
      pagination={{
        pageInfo: props.pageInfo,
        paginationRange: props.paginationRange,
        onLoadNext: props.onLoadNext,
        onLoadPrevious: props.onLoadPrevious,
        onGoToFirst: props.onGoToFirst,
        canLoadPreviousPage: props.canLoadPreviousPage,
      }}
      onTableResize={props.onTableResize}
      recalculationKey={props.recalculationKey}
      tableId="user-list-table"
      getRowId={(user) => user.id}
    />
  );
}
```

### Multiple Actions Example

```typescript
// Multiple actions in a single row
const renderActions = useMemo(
  () => (role: Role) => (
    <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
      <Tooltip title={t("roleManagement.editRole")}>
        <IconButton onClick={() => onEdit(role)}>
          <EditIcon />
        </IconButton>
      </Tooltip>
      {onDelete && (
        <Tooltip title={t("roleManagement.deleteRole")}>
          <IconButton onClick={() => onDelete(role)}>
            <DeleteIcon />
          </IconButton>
        </Tooltip>
      )}
    </Box>
  ),
  [t, onEdit, onDelete]
);
```

### Features

- **Declarative Column Definition**: Define columns with `id`, `label`, `accessor`/`render`, `align`, `width`, `sortable`, `sortField`
- **Loading States**: Automatic skeleton rows, loading bar, and spinner
- **Empty State**: Customizable empty message or custom renderer
- **Sorting**: Integrated with `useSorting` hook via `sortState` and `onSortChange`
- **Pagination**: Relay pagination support via `pagination` prop
- **Row Actions**: Single or multiple actions per row via `renderActions`
- **Responsive**: Uses `DynamicTableBox` for automatic page size calculation
- **i18n Support**: All labels should use translation keys
- **Type Safety**: Fully typed with generics `<T, F extends string>`

### Column Definition

```typescript
interface Column<T, F extends string> {
  id: string;                    // Unique column identifier
  label: string;                 // Column header label (should use i18n)
  accessor?: (row: T) => React.ReactNode;  // Extract cell value
  render?: (row: T) => React.ReactNode;    // Custom cell renderer
  align?: 'left' | 'center' | 'right';    // Text alignment
  width?: string | number;       // Column width
  sortable?: boolean;            // Whether column is sortable
  sortField?: F;                 // Sort field identifier (when sortable)
}
```

**Note**: Use either `accessor` or `render`, not both. `accessor` is simpler for basic values, `render` gives full control for complex cells.

## Component Pattern: AssignmentComponent

Generic component for assigning/removing relationships.

```typescript
import { AssignmentComponent } from "@/shared/components/management/AssignmentComponent";

function UserGroupAssignment({ userId, assignedGroups, onAssignGroup, onRemoveGroup }) {
  return (
    <AssignmentComponent
      assignedItems={assignedGroups}
      onAssign={onAssignGroup}
      onRemove={onRemoveGroup}
      searchQuery={async (query) => {
        const { data } = await searchGroups({ query });
        return data?.groups?.nodes || [];
      }}
      getItemLabel={(group) => group.name}
      getItemId={(group) => group.id}
      placeholder="Search groups..."
      label="Groups"
    />
  );
}
```

## Quick Reference Checklist

When implementing a new management module, verify:

### Required Hooks
- [ ] `useDebouncedSearch` imported and used for search
- [ ] `useSorting` imported and used for sort state
- [ ] `useMutationWithRefetch` imported and used for all mutations
- [ ] `useToggleStatus` used for enable/disable operations (if applicable)

### Required Components
- [ ] `ErrorAlert` component used for error display (automatically rendered by `ManagementLayout`)
- [ ] `ManagementLayout` wrapper used for consistent structure with static subcomponents (`ManagementLayout.Header`, `ManagementLayout.Content`, `ManagementLayout.Drawer`)
- [ ] `DeleteConfirmationDialog` used for delete operations (if applicable, included within `ManagementLayout.Content`)

### Functionality
- [ ] Search resets pagination to first page
- [ ] Sort changes reset pagination cursor
- [ ] Mutations include refetchQueries with current variables
- [ ] Race condition prevention implemented for mutations
- [ ] Loading states handled correctly
- [ ] Error handling with user-friendly messages
- [ ] Toast notifications shown for mutations (see [Toast Notification Pattern](./toast-notification-pattern.md))

### Component Patterns
- [ ] List components use `ManagementTable` with declarative column definitions
- [ ] All table labels use i18n translation keys
- [ ] Row actions support single or multiple actions via `renderActions`
- [ ] Assignment components use generic `AssignmentComponent` when possible (future)

### Drawer Patterns
- [ ] Cancel button is always enabled (only disabled during save/load operations)
- [ ] Cancel button closes the drawer by calling `onClose()`
- [ ] Cancel button may reset form state before closing (optional but recommended)
- [ ] Save button is disabled when there are no changes or during save operations

### Critical Rules
- [ ] **Child components (drawers/modals) use direct mutation hooks, NOT the full management hook**
- [ ] **No duplicate instances of complex management hooks in parent and child components**

## Related Documentation

- [Shared Components Pattern](./shared-components-pattern.md) - Reusable shared components (ErrorAlert, DeleteConfirmationDialog, ManagementLayout, useToggleStatus)
- [GraphQL Mutation Pattern](./graphql-mutation-pattern.md) - Mutation patterns with refetch
- [Toast Notification Pattern](./toast-notification-pattern.md) - User feedback for mutations
- [Mutation Pattern](./mutation-pattern.md) - Mutation hook patterns

