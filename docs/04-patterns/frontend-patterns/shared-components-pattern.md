---
title: Shared Components Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [components, shared, reusable, ui, feedback, layout, frontend]
ai_optimized: true
search_keywords: [shared components, reusable components, ui components, feedback components, ErrorAlert, DeleteConfirmationDialog, ManagementLayout, useToggleStatus, Header, Content]
related:
  - 04-patterns/frontend-patterns/management-pattern.md
  - 04-patterns/frontend-patterns/toast-notification-pattern.md
  - 04-patterns/frontend-patterns/component-pattern.md
---

# Shared Components Pattern

> **Purpose**: Standard pattern for creating and organizing reusable shared components across the frontend application.

## Overview

The Shared Components Pattern provides a structured approach to building reusable UI components that can be used across different features and modules. These components are organized by purpose and placed in the `web/src/shared/components/` directory.

## Component Organization

Shared components are organized into the following categories:

### UI Components (`web/src/shared/components/ui/`)

Generic UI components organized by purpose:

- **`feedback/`** - User feedback components organized by type:
  - `alerts/` - Alert components (ErrorAlert, WarningAlert)
  - `dialogs/` - Dialog components (ConfirmationDialog, DeleteConfirmationDialog, etc.)
  - `states/` - Empty, error, and loading state components
  - `toast/` - Toast notification provider
  - `tooltip/` - Tooltip component
  - `Chat/` - Chat interface components
- **`forms/`** - Form components and form fields
- **`layout/`** - Layout components (Box, Stack, Grid, etc.)
- **`data-display/`** - Data display components (Table, Chart, etc.)
- **`navigation/`** - Navigation components (Tabs, Link, etc.)
- **`pagination/`** - Pagination components
- **`primitives/`** - Basic UI primitives (Button, Avatar, etc.)
- **`patterns/`** - Reusable UI patterns

### Management Components (`web/src/shared/components/management/`)

Components specific to management interfaces:

- **`ManagementLayout`** - Layout wrapper for management pages

### Feature-Specific Components (`web/src/shared/components/{feature}/`)

Components specific to a particular feature (e.g., `authorization/`, `auth/`).

## Feedback Components

### ErrorAlert

Generic error alert component with retry functionality.

**Location**: `web/src/shared/components/ui/feedback/alerts/ErrorAlert.tsx`

**Usage**:
```typescript
import { ErrorAlert } from "@/shared/components/ui/feedback";

function MyComponent() {
  const { error, refetch } = useMyQuery();

  return (
    <ErrorAlert
      error={error}
      onRetry={refetch}
      fallbackMessage="Failed to load data"
    />
  );
}
```

**Props**:
- `error: Error | null | undefined` - Error object to display
- `onRetry: () => void` - Callback invoked when user clicks close/retry
- `fallbackMessage?: string` - Fallback message if error.message is not available

**Features**:
- Conditional rendering (returns null if no error)
- Retry functionality via onClose callback
- Consistent error UI across the application

### LoadingState

Generic loading state component with centered spinner.

**Location**: `web/src/shared/components/ui/feedback/states/EmptyErrorState.tsx`

**Usage**:
```typescript
import { LoadingState } from "@/shared/components/ui/feedback";

function UserDrawer() {
  const { loading } = useUserDrawer(userId, open);

  return (
    <Drawer.Body>
      <LoadingState isLoading={loading} />
      <LoadingState isLoading={loading} minHeight="300px" size={32} />
      {/* ... rest of content */}
    </Drawer.Body>
  );
}
```

**Props**:
- `isLoading: boolean` - Whether to show the loading state (component returns null when false)
- `minHeight?: string | number` - Minimum height for the loading container (default: "400px")
- `size?: number` - Size of the CircularProgress spinner (default: MUI default)

**Features**:
- Centered spinner (both horizontally and vertically)
- Configurable minimum height for proper vertical centering
- Configurable spinner size
- Handles conditional rendering internally based on `isLoading` prop
- Consistent loading UI across the application
- Reusable across different contexts (drawers, pages, etc.)
- Cleaner JSX without conditional rendering syntax

### DeleteConfirmationDialog

Generic delete confirmation dialog with i18n support.

**Location**: `web/src/shared/components/ui/feedback/dialogs/DeleteConfirmationDialog.tsx`

**Usage**:
```typescript
import { DeleteConfirmationDialog } from "@/shared/components/ui/feedback";

function RoleManagement() {
  const { t } = useTranslation(authorizationManagementTranslations);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [itemToDelete, setItemToDelete] = useState<Role | null>(null);
  const { deleteRole, deleteLoading } = useRoleManagement();

  const handleDeleteConfirm = useCallback(async () => {
    if (itemToDelete) {
      try {
        await deleteRole(itemToDelete.id);
        toast.success(t("roleManagement.toast.roleDeleted", { name: itemToDelete.name }));
        setDeleteConfirmOpen(false);
        setItemToDelete(null);
      } catch (err) {
        toast.error(extractErrorMessage(err, t("roleManagement.toast.roleDeleteError")));
      }
    }
  }, [itemToDelete, deleteRole, toast, t]);

  return (
    <DeleteConfirmationDialog
      open={deleteConfirmOpen}
      item={itemToDelete}
      loading={deleteLoading}
      onConfirm={handleDeleteConfirm}
      onCancel={() => {
        setDeleteConfirmOpen(false);
        setItemToDelete(null);
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

**Props**:
- `open: boolean` - Whether the dialog is open
- `item: T | null` - Item to be deleted (must have `id` and `name` properties)
- `loading: boolean` - Whether the delete operation is in progress
- `onConfirm: () => void` - Callback invoked when user confirms deletion
- `onCancel: () => void` - Callback invoked when user cancels deletion
- `titleKey: string` - Translation key for dialog title
- `messageKey: string` - Translation key for dialog message (supports `{name}` placeholder)
- `cancelKey: string` - Translation key for cancel button text
- `deleteKey: string` - Translation key for delete button text
- `deletingKey: string` - Translation key for delete button text when loading
- `t: (key: string, params?: Record<string, string>) => string` - Translation function

**Features**:
- Generic type parameter for any item with `id` and `name`
- i18n support through translation keys
- Replaces `{name}` placeholder in messages
- Loading state disables buttons
- Consistent delete confirmation UX

## Management Components

### ManagementLayout

Generic layout wrapper for consistent management page structure using static subcomponents.

**Location**: `web/src/shared/components/management/ManagementLayout.tsx`

**Usage**:
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
        <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
          <UserSearch
            value={inputValue}
            onChange={handleInputChange}
            onSearch={handleSearch}
            placeholder={t("userManagement.searchPlaceholder")}
          />
          <Button onClick={handleCreate}>Create</Button>
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
- Static subcomponents for type-safe slot-based composition
- `ManagementLayout.Header` - Wraps header content (search, actions, filters, etc.)
- `ManagementLayout.Content` - Wraps main content with flex layout
- `ManagementLayout.Drawer` - Wraps drawer components
- All subcomponents are optional
- Delete dialogs belong within the Content component, not as a separate slot

**Props**:
- `error: Error | null | undefined` - Error object to display in error alert
- `onErrorRetry: () => void` - Callback invoked when user clicks retry on error alert
- `errorFallbackMessage?: string` - Fallback message for error alert
- `children: React.ReactNode` - Child components using ManagementLayout subcomponents

**Subcomponents** (static properties):
- `ManagementLayout.Header` - Wraps header content (search, actions, etc.) - slot component
- `ManagementLayout.Content` - Wraps main content with flex layout (`flex: 1`, `minHeight: 0`)
- `ManagementLayout.Drawer` - Wraps drawer components (slot component)

**Features**:
- Consistent layout structure across all management pages
- Error alert automatically rendered at the top
- Type-safe slot-based composition using static subcomponents
- Declarative API with better developer experience
- All subcomponents are optional (only include what you need)
- Generic slot names allow for flexible content (not just search/lists)

## Mutation Hooks

### useToggleStatus

Reusable hook for enable/disable toggle operations with toast notifications.

**Location**: `web/src/shared/hooks/mutations/useToggleStatus.ts`

**Usage**:
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

**Options**:
- `enableFn: (id: string) => Promise<void>` - Function to enable the item
- `disableFn: (id: string) => Promise<void>` - Function to disable the item
- `enableSuccessMessage: string` - Translation key for success message when enabling
- `disableSuccessMessage: string` - Translation key for success message when disabling
- `enableErrorMessage: string` - Translation key for error message when enabling fails
- `disableErrorMessage: string` - Translation key for error message when disabling fails
- `t: (key: string, params?: Record<string, string>) => string` - Translation function

**Returns**:
- `(id: string, enabled: boolean) => Promise<void>` - Callback function that handles the toggle

**Features**:
- Consistent error handling pattern
- Automatic toast notifications (success/error)
- Reusable across different entity types
- Reduces boilerplate in management components

## Component Design Principles

### 1. Genericity

Components should be generic and reusable across different contexts:

- Use TypeScript generics when appropriate (e.g., `DeleteConfirmationDialog<T>`)
- Accept props that allow customization
- Avoid hardcoding feature-specific logic

### 2. Composition

Components should be composable:

- Pass child components as React nodes
- Use render props or children when appropriate
- Allow customization through props

### 3. Consistency

Components should follow consistent patterns:

- Use the same error handling approach
- Follow the same i18n pattern
- Use consistent styling (Material-UI theme)
- Follow the same prop naming conventions

### 4. Accessibility

Components should be accessible:

- Include proper ARIA labels
- Support keyboard navigation
- Ensure proper focus management
- Test with screen readers

### 5. Documentation

Components should be well-documented:

- Include JSDoc comments
- Document all props with TypeScript types
- Provide usage examples
- Document any special behavior

## Export Pattern

All shared components should be exported through index files:

```typescript
// web/src/shared/components/ui/feedback/index.ts
export * from './alerts';
export * from './dialogs';
export * from './states';
export * from './toast';
export * from './tooltip';
export * from './Chat';
```

This allows clean imports:

```typescript
import { ErrorAlert, DeleteConfirmationDialog } from "@/shared/components/ui/feedback";
```

## When to Create a Shared Component

Create a shared component when:

1. **Duplication**: The same component pattern appears in 2+ places
2. **Reusability**: The component can be used across different features
3. **Consistency**: The component enforces a consistent UI/UX pattern
4. **Maintainability**: Centralizing the component makes maintenance easier

## When NOT to Create a Shared Component

Don't create a shared component when:

1. **Single Use**: The component is only used in one place
2. **Feature-Specific**: The component contains feature-specific business logic
3. **Too Simple**: The component is just a simple wrapper with no added value
4. **Premature Abstraction**: You're guessing future needs rather than solving current problems

## Related Documentation

- [Management Pattern](./management-pattern.md) - How to use shared components in management pages
- [Toast Notification Pattern](./toast-notification-pattern.md) - User feedback patterns
- [Component Pattern](./component-pattern.md) - General component patterns
