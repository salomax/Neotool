# ADR 0006: Frontend Authorization Layer

## Status
Accepted

## Context
NeoTool's web application requires a robust authorization layer that makes UI visibility and enablement depend on actual permissions fetched from the security service. The application needs to:

1. Extend the current authentication system to include authorization (roles and permissions)
2. Provide declarative primitives for permission checks
3. Apply authorization consistently across User, Role, and Group management modules
4. Keep the backend as the source of truth for permissions

The existing `AuthProvider` handles authentication (user identity and tokens) but does not include authorization data (roles and permissions). We need to build on this foundation to add authorization capabilities.

## Decision
We will implement a comprehensive frontend authorization layer that:

1. **Extends GraphQL queries** to include roles and permissions in `currentUser` and authentication mutations
2. **Creates an AuthorizationProvider** that wraps `AuthProvider` and manages authorization state
3. **Provides declarative components** (`PermissionGate`, `DisableIfNoPermission`) for permission-based UI rendering
4. **Exposes hooks** (`useAuthorization`, `useCheckPermission`) for programmatic permission checks
5. **Applies authorization** to all management pages (User, Role, Group) and navigation

### Architecture

```
AuthProvider (authentication)
  └── AuthorizationProvider (authorization)
       └── Application Components
            └── PermissionGate / DisableIfNoPermission
```

### Key Components

#### AuthorizationProvider
- Manages `permissions: Set<string>` and `roles: Role[]` state
- Provides methods: `has()`, `hasAny()`, `hasAll()`, `refreshAuthorization()`
- Automatically fetches authorization data from `currentUser` query
- Resets state on sign-out, refreshes on sign-in

#### PermissionGate Component
- Declarative component for conditional rendering based on permissions
- Props: `require`, `anyOf`, `fallback`, `loadingFallback`
- Renders children only if permission check passes

#### DisableIfNoPermission Component
- Wrapper that disables child components (e.g., buttons) when permission is missing
- Props: `permission`, `anyOf`
- Sets `disabled` prop on child element

#### useAuthorization Hook
- Exposes all `AuthorizationProvider` context values
- Throws error if used outside provider


### Permission Format
We use the backend permission format (e.g., `security:user:view`, `security:role:save`, `security:group:delete`).

### GraphQL Integration
- `CURRENT_USER` query includes `roles { id name }` and `permissions { id name }`
- Authentication mutations (`SIGN_IN`, `SIGN_UP`, `SIGN_IN_WITH_OAUTH`) return expanded user with roles/permissions
- All permission checks are done client-side against the permissions list from `currentUser` (no additional GraphQL queries needed)

## Consequences

### Positive
- **Declarative API**: Easy to use `PermissionGate` and `DisableIfNoPermission` components
- **Type Safety**: TypeScript types generated from GraphQL schema
- **Performance**: Permission checks use Set lookups (O(1)), GraphQL queries are cached
- **Consistency**: Single source of truth (backend) for all permission checks
- **Maintainability**: Centralized authorization logic in provider and hooks
- **Testability**: Components and hooks can be easily unit tested

### Negative
- **Additional Provider**: Adds another context provider layer (minimal performance impact)
- **GraphQL Query Overhead**: `currentUser` query now fetches more data (roles and permissions)
- **Learning Curve**: Developers need to learn new components and hooks

### Security Considerations
- **No Permission Enumeration**: We intentionally do NOT expose a `checkPermission` GraphQL query to prevent attackers from enumerating which resources they can access
- **Client-Side Only for UX**: Permission checks in the frontend are purely for UX (showing/hiding UI elements)
- **Backend Validation Required**: All mutations and queries must validate permissions server-side - never trust client-side checks for security

### Neutral
- **Backend Dependency**: Frontend relies on backend permission structure
- **Caching Strategy**: Apollo Client handles caching automatically

## Implementation Details

### File Structure
```
web/src/
├── lib/graphql/operations/
│   ├── auth/
│   │   ├── queries.ts (updated)
│   │   └── mutations.ts (updated)
├── shared/
│   ├── providers/
│   │   ├── AuthorizationProvider.tsx (new)
│   │   └── index.ts (updated)
│   ├── hooks/authorization/
│   │   ├── useAuthorization.ts (exported from provider)
│   │   └── index.ts (updated)
│   └── components/authorization/
│       ├── PermissionGate.tsx (new)
│       ├── DisableIfNoPermission.tsx (new)
│       └── index.ts (new)
└── app/
    ├── providers.tsx (updated)
    └── (settings)/authorization/
        ├── users/ (updated)
        ├── roles/ (updated)
        └── groups/ (updated)
```

### Usage Examples

#### PermissionGate
```tsx
// Single permission
<PermissionGate require="security:user:edit" fallback={<div>No access</div>}>
  <EditButton />
</PermissionGate>

// Multiple permissions (all required)
<PermissionGate require={["security:user:edit", "security:user:view"]}>
  <UserActions />
</PermissionGate>

// Any of multiple permissions
<PermissionGate anyOf={["security:user:edit", "security:user:view"]}>
  <UserActions />
</PermissionGate>
```

#### DisableIfNoPermission
```tsx
<DisableIfNoPermission permission="security:user:edit">
  <Button>Edit User</Button>
</DisableIfNoPermission>
```

#### useAuthorization Hook
```tsx
const { has, hasAny, hasAll, permissions, roles } = useAuthorization();

if (has('security:user:edit')) {
  // User can edit
}

if (hasAny(['security:user:edit', 'security:user:view'])) {
  // User has at least one permission
}
```


### Navigation Filtering
The `SidebarRail` component filters navigation items based on permissions:
- `/settings` link only appears if user has any authorization management permission

### Testing
- Unit tests for `AuthorizationProvider`, `useAuthorization`, and `PermissionGate`
- E2E tests verify UI visibility and action enablement based on permissions
- Test fixtures for users with/without permissions

## References
- [GraphQL Standards](../../05-standards/api-standards/graphql-standards.md)
- [Shared Components Pattern](../../04-patterns/frontend-patterns/shared-components-pattern.md)
- [Authorization Management Feature](../../03-features/security/authorization/authorization-management.feature)
