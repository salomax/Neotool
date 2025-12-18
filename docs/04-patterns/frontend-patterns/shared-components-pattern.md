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
  - 04-patterns/frontend-patterns/styling-pattern.md
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
- **`navigation/`** - Navigation components (Breadcrumb, Tabs, Link, etc.)
- **`pagination/`** - Pagination components
- **`primitives/`** - Basic UI primitives (Button, Avatar, etc.)
- **`patterns/`** - Reusable UI patterns

### Avatar Component

Enhanced Avatar component with automatic error handling and fallback to initials.

**Location**: `web/src/shared/components/ui/primitives/Avatar.tsx`

**Usage**:
```typescript
import { Avatar } from "@/shared/components/ui/primitives/Avatar";

// With image URL (automatically falls back to initials on error)
<Avatar
  name="John Doe"
  src="https://example.com/avatar.jpg"
  size="small"
/>

// Without image (shows initials)
<Avatar name="John Doe" size="medium" />

// Custom children
<Avatar name="John Doe">
  <CustomIcon />
</Avatar>
```

**Props**:
- `name?: string` - User name for generating initials fallback
- `src?: string` - Image URL (optional)
- `size?: 'small' | 'medium' | 'large'` - Avatar size (default: 'medium')
- `children?: React.ReactNode` - Custom content (overrides initials)
- `'data-testid'?: string` - Custom test ID
- All Material-UI Avatar props

**Features**:
- Automatic error handling: Falls back to initials when image fails to load
- Handles `NS_BINDING_ABORTED` and other network errors gracefully
- Generates initials from name (first + last initial)
- Size variants: small (24px), medium (40px), large (56px)
- Automatic test ID generation from name prop
- Resets error state when `src` changes to allow retry

**Image Error Handling**:
The Avatar component automatically handles image loading errors:
- When an image fails to load (network errors, CORS issues, 404s, etc.), it automatically falls back to showing initials
- The error state resets when the `src` prop changes, allowing retry attempts
- No broken image icons are shown to users - always falls back to initials

**Note on `NS_BINDING_ABORTED` Errors**:
You may see `NS_BINDING_ABORTED` errors in the browser console, especially in development mode with React Strict Mode. These are harmless and occur when:
- React Strict Mode causes double renders (development only)
- Component unmounts before image finishes loading
- Navigation happens during image load

These errors don't affect functionality - the component handles them gracefully by falling back to initials. The Avatar component includes cleanup logic to minimize these errors, but they may still appear in console logs. This is expected browser behavior and doesn't indicate a problem with the component.

**Best Practices**:
- Always provide a `name` prop for fallback initials
- Use appropriate size for context (small for lists, medium for cards, large for profiles)
- The component handles all error cases internally - no need for external error handling

### Management Components (`web/src/shared/components/management/`)

Components specific to management interfaces:

- **`ManagementLayout`** - Layout wrapper for management pages

### Feature-Specific Components (`web/src/shared/components/{feature}/`)

Components specific to a particular feature (e.g., `authorization/`, `auth/`).

## Feedback Components

### ErrorAlert

Generic error alert component with optional retry functionality and flexible visibility control.

**Location**: `web/src/shared/components/ui/feedback/alerts/ErrorAlert.tsx`

**Usage**:
```typescript
import { ErrorAlert } from "@/shared/components/ui/feedback";

function MyComponent() {
  const { error, refetch } = useMyQuery();
  const [showError, setShowError] = useState(true);

  return (
    <>
      {/* Auto-hide when no error (default behavior) */}
      <ErrorAlert error={error} onRetry={refetch} />

      {/* Explicit visibility control */}
      <ErrorAlert error="test" visible={showError} />

      {/* Force hide (even if error exists) */}
      <ErrorAlert error={error} visible={false} />

      {/* With string error (no retry) */}
      <ErrorAlert error="Something went wrong" />

      {/* With Error object and retry */}
      <ErrorAlert 
        error={error} 
        onRetry={refetch}
        fallbackMessage="Failed to load data"
      />
    </>
  );
}
```

**Props**:
- `error: Error | string | null | undefined` - Error object or error message string to display
- `onRetry?: () => void` - Optional callback invoked when user clicks close/retry button. If provided, shows a close button.
- `fallbackMessage?: string` - Fallback message if error.message is not available (default: "An error occurred")
- `visible?: boolean` - Controls visibility of the alert:
  - `undefined` (default): Auto-determine from error presence
  - `true`: Show if error exists, hide if no error
  - `false`: Always hide (even if error exists)

**Features**:
- Supports both Error objects and string messages
- Optional retry functionality via `onRetry` callback (close button only appears when provided)
- Flexible visibility control with `visible` prop
- Auto-hides when no error (backward compatible default behavior)
- Consistent error UI across the application
- No need for conditional rendering syntax - component handles it internally

**Visibility Patterns**:
- **Default (auto-hide)**: `<ErrorAlert error={error} />` - Hides automatically when error is null/undefined/empty
- **Explicit control**: `<ErrorAlert error={error} visible={showError} />` - Control visibility with state
- **Force hide**: `<ErrorAlert error={error} visible={false} />` - Always hidden regardless of error
- **Force show**: `<ErrorAlert error={error} visible={true} />` - Shows if error exists

### WarningAlert

Generic warning alert component with optional close functionality and flexible visibility control.

**Location**: `web/src/shared/components/ui/feedback/alerts/WarningAlert.tsx`

**Usage**:
```typescript
import { WarningAlert } from "@/shared/components/ui/feedback";

function MyComponent() {
  const [warningMessage, setWarningMessage] = useState<string | null>(null);
  const [showWarning, setShowWarning] = useState(true);

  return (
    <>
      {/* Auto-hide when no message (default behavior) */}
      <WarningAlert message={warningMessage} />

      {/* Explicit visibility control */}
      <WarningAlert message="test" visible={showWarning} />

      {/* Force hide (even if message exists) */}
      <WarningAlert message={warningMessage} visible={false} />

      {/* With close handler */}
      <WarningAlert 
        message={warningMessage} 
        onClose={() => setWarningMessage(null)} 
      />
    </>
  );
}
```

**Props**:
- `message: string | null | undefined` - Warning message to display
- `onClose?: () => void` - Optional callback invoked when user clicks close button. If provided, shows a close button.
- `fallbackMessage?: string` - Fallback message if message is not available (default: "Warning")
- `visible?: boolean` - Controls visibility of the alert:
  - `undefined` (default): Auto-determine from message presence
  - `true`: Show if message exists, hide if no message
  - `false`: Always hide (even if message exists)

**Features**:
- Optional close functionality via `onClose` callback (close button only appears when provided)
- Flexible visibility control with `visible` prop
- Auto-hides when no message (backward compatible default behavior)
- Consistent warning UI across the application
- No need for conditional rendering syntax - component handles it internally

**Visibility Patterns**:
- **Default (auto-hide)**: `<WarningAlert message={message} />` - Hides automatically when message is null/undefined/empty
- **Explicit control**: `<WarningAlert message={message} visible={showWarning} />` - Control visibility with state
- **Force hide**: `<WarningAlert message={message} visible={false} />` - Always hidden regardless of message
- **Force show**: `<WarningAlert message={message} visible={true} />` - Shows if message exists

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
- `error: Error | string | null | undefined` - Error object or error message string to display in error alert
- `onErrorRetry?: () => void` - Optional callback invoked when user clicks retry on error alert. If provided, shows a close button.
- `errorFallbackMessage?: string` - Fallback message for error alert (default: "An error occurred")
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

## Navigation Components

### Breadcrumb

Breadcrumb navigation component for displaying page hierarchy and enabling quick navigation to parent pages.

**Location**: `web/src/shared/components/ui/navigation/Breadcrumb.tsx`

**Usage**:
```typescript
import { Breadcrumb, BreadcrumbItem } from "@/shared/components/ui/navigation";

// Auto-generate from pathname
function ProductPage() {
  return (
    <div>
      <Breadcrumb />
      {/* Page content */}
    </div>
  );
}

// Manual items for custom flows
function PageB() {
  const items: BreadcrumbItem[] = [
    { label: "Page A", href: "/a" },
    { label: "Page B" },
  ];

  return (
    <div>
      <Breadcrumb items={items} />
      {/* Page content */}
    </div>
  );
}

// With route configuration for dynamic routes
function UserProfilePage() {
  const routeConfig = [
    { path: "/users", label: "Users" },
    { path: "/users/[id]", label: (params) => `User ${params.id}` },
  ];

  return (
    <div>
      <Breadcrumb routeConfig={routeConfig} />
      {/* Page content */}
    </div>
  );
}
```

**Props**:
- `items?: BreadcrumbItem[]` - Manual breadcrumb items (takes precedence over auto-generation)
- `autoGenerate?: boolean` - Auto-generate from pathname (default: true)
- `routeConfig?: RouteConfig[]` - Route configuration for custom labels
- `homeLabel?: string` - Home label (default: "Home")
- `homeHref?: string` - Home href (default: "/")
- `maxItems?: number` - Maximum visible items (default: 5)
- `showHome?: boolean` - Show home item (default: true)
- `currentPageClickable?: boolean` - Make last item clickable (default: false)
- `responsive?: boolean` - Collapse on mobile (default: true)
- `separator?: React.ReactNode` - Custom separator
- `variant?: "default" | "compact" | "minimal"` - Visual variant
- `size?: "small" | "medium" | "large"` - Size variant
- `renderItem?: (item, index, isLast) => React.ReactNode` - Custom item renderer
- `renderSeparator?: () => React.ReactNode` - Custom separator renderer

**Features**:
- Auto-generation from Next.js pathname
- Manual items for custom navigation flows
- Route configuration for dynamic routes
- Automatic truncation with maxItems
- Responsive collapse on mobile
- Accessibility support (ARIA labels, keyboard navigation)
- Customizable variants and sizes
- Icon support per breadcrumb item

**See also**: [Breadcrumb Pattern](./breadcrumb-pattern.md) - Detailed breadcrumb usage patterns

## UI Primitives

### Avatar

Enhanced Avatar component with automatic error handling and fallback to initials.

**Location**: `web/src/shared/components/ui/primitives/Avatar.tsx`

**Usage**:
```typescript
import { Avatar } from "@/shared/components/ui/primitives/Avatar";

// With image URL (automatically falls back to initials on error)
<Avatar
  name="John Doe"
  src="https://example.com/avatar.jpg"
  size="small"
/>

// Without image (shows initials)
<Avatar name="John Doe" size="medium" />

// Custom children
<Avatar name="John Doe">
  <CustomIcon />
</Avatar>
```

**Props**:
- `name?: string` - User name for generating initials fallback
- `src?: string` - Image URL (optional)
- `size?: 'small' | 'medium' | 'large'` - Avatar size (default: 'medium')
- `children?: React.ReactNode` - Custom content (overrides initials)
- `'data-testid'?: string` - Custom test ID
- All Material-UI Avatar props

**Features**:
- Automatic error handling: Falls back to initials when image fails to load
- Handles `NS_BINDING_ABORTED` and other network errors gracefully
- Generates initials from name (first + last initial)
- Size variants: small (24px), medium (40px), large (56px)
- Automatic test ID generation from name prop
- Resets error state when `src` changes to allow retry

**Image Error Handling**:
The Avatar component automatically handles image loading errors:
- When an image fails to load (network errors, CORS issues, 404s, etc.), it automatically falls back to showing initials
- The error state resets when the `src` prop changes, allowing retry attempts
- No broken image icons are shown to users - always falls back to initials

**Note on `NS_BINDING_ABORTED` Errors**:
You may see `NS_BINDING_ABORTED` errors in the browser console, especially in development mode with React Strict Mode. These are harmless and occur when:
- React Strict Mode causes double renders (development only)
- Component unmounts before image finishes loading
- Navigation happens during image load

These errors don't affect functionality - the component handles them gracefully by falling back to initials. The Avatar component includes cleanup logic to minimize these errors, but they may still appear in console logs. This is expected browser behavior and doesn't indicate a problem with the component.

**Best Practices**:
- Always provide a `name` prop for fallback initials
- Use appropriate size for context (small for lists, medium for cards, large for profiles)
- The component handles all error cases internally - no need for external error handling

### Link

Enhanced Link component that integrates Next.js Link with Material-UI Link.

**Location**: `web/src/shared/components/ui/navigation/Link.tsx`

**Usage**:
```typescript
import { Link } from "@/shared/components/ui/navigation";

// Internal link
<Link href="/products">Products</Link>

// External link
<Link href="https://example.com" external>External Site</Link>

// With icon
<Link href="/products" showIcon>Products</Link>
```

**Props**:
- `href: string` - Link destination
- `external?: boolean` - External link (opens in new tab)
- `showIcon?: boolean` - Show external link icon
- `name?: string` - Name for test ID generation
- All Material-UI Link props

**Features**:
- Integrates Next.js Link with MUI Link
- Automatic external link handling
- Test ID generation support
- Consistent styling with design system

### Tabs

Advanced tabs component with drag-and-drop, closable tabs, and badges.

**Location**: `web/src/shared/components/ui/navigation/Tabs.tsx`

**Usage**:
```typescript
import { Tabs, TabItem } from "@/shared/components/ui/navigation";

const tabs: TabItem[] = [
  { id: "tab1", label: "Tab 1", content: <div>Content 1</div> },
  { id: "tab2", label: "Tab 2", content: <div>Content 2</div> },
];

<Tabs 
  tabs={tabs}
  showCloseButtons
  draggable
  onTabsChange={(newTabs) => console.log(newTabs)}
/>
```

**Features**:
- Closable tabs
- Drag-and-drop reordering
- Badge support
- Icon support
- Responsive scrolling
- Customizable variants

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

### 6. Image Loading Error Handling

Components that display images from external sources must handle loading errors gracefully:

**Common Issues**:
- `NS_BINDING_ABORTED`: Network requests aborted during component lifecycle (unmount, re-render, navigation). **These are often unavoidable with native img tags, especially in React Strict Mode (development). They are harmless and don't affect functionality.**
- CORS errors: Cross-origin requests blocked by browser security policies
- 404 errors: Image URLs that no longer exist
- Network timeouts: Slow or failed network connections

**Best Practices**:
1. **Always provide fallback content**: Use initials, placeholder icons, or default images
2. **Handle `onError` events**: Implement error handlers that gracefully degrade the UI
3. **Reset error state on prop changes**: Allow retry when image source changes
4. **Use memoized error handlers**: Prevent unnecessary re-renders with `useCallback`
5. **Don't show broken image icons**: Remove `src` attribute when error occurs to trigger fallback

**Example Pattern** (as implemented in Avatar component):
```typescript
const [imgError, setImgError] = React.useState(false);
const isMountedRef = React.useRef(true);

// Reset error state when src changes
React.useEffect(() => {
  setImgError(false);
}, [src]);

// Cleanup on unmount
React.useEffect(() => {
  isMountedRef.current = true;
  return () => {
    isMountedRef.current = false;
  };
}, []);

// Handle image load errors gracefully
const handleImageError = React.useCallback(() => {
  if (isMountedRef.current) {
    setImgError(true);
  }
}, []);

// Only use src if it's provided and hasn't errored
const imageSrc = src && !imgError ? src : undefined;
const imageSrc = src && !imgError ? src : undefined;

return (
  <img 
    src={imageSrc}
    onError={handleImageError}
    // Fallback content shown when src is undefined
  >
    {fallbackContent}
  </img>
);
```

**Preventing Future Issues**:
- When creating new components that display images, always implement error handling
- Use the Avatar component as a reference implementation
- Test with invalid URLs, network failures, and CORS-blocked images
- Ensure fallback content is always visible and accessible

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

- [Breadcrumb Pattern](./breadcrumb-pattern.md) - Detailed breadcrumb navigation patterns
- [Management Pattern](./management-pattern.md) - How to use shared components in management pages
- [Toast Notification Pattern](./toast-notification-pattern.md) - User feedback patterns
- [Styling Pattern](./styling-pattern.md) - Styling rules and theme token usage
