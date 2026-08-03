---
id: feature.security.authorization-runtime.frontend-permission-gates
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend: []
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database: []
  tests:
    - web/src/shared/components/authorization/__tests__/PermissionGate.test.tsx
    - web/src/shared/providers/__tests__/AuthorizationProvider.test.tsx
    - web/tests/e2e/authorization.e2e.spec.ts
  frontend:
    - web/src/shared/providers/AuthorizationProvider.tsx
    - web/src/shared/components/authorization/PermissionGate.tsx
    - web/src/shared/components/authorization/DisableIfNoPermission.tsx
    - web/src/app/(settings)/authorization/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.authorization-runtime.claim-based-enforcement
  - feature.security.rbac-administration.admin-settings-ui
  - feature.security.session-tokens.auth-context-and-jwt-claims
schema: spec-metadata-v1
---

# Authorization Runtime — Frontend Permission Gates (UX Only)

## Boundary

- **UX hints only.** Never treat as enforcement. Backend claim/DB checks remain authoritative.

## Behavior

1. `AuthorizationProvider` (when authenticated) loads `currentUser { roles, permissions, … }` via Apollo and builds a client `Set` of permission **names** + role list. Exposes `has` / `hasAny` / `hasAll` / `refreshAuthorization`.
2. Source of client permissions is **GraphQL currentUser** (DB-derived via security dataloaders), not a direct JWT claim parse — may diverge from access-token claims until refresh/login if RBAC changed mid-session.
3. `PermissionGate`: conditionally render children (`require` all-of, or `anyOf`); optional fallback / loadingFallback.
4. `DisableIfNoPermission`: clones child with `disabled` when permission missing (or while loading).
5. Settings authorization screens wrap actions with real strings (`security:user:view|save`, `security:group:save`, …).

## Invariants

- Hiding a button does not protect the API.
- Loading state fails closed for DisableIfNoPermission (disabled until known).

## Permissions

Backend enforcement: none in these components.

UX hints:

- Gate visibility/enablement from AuthorizationProvider permission set.

## Data And Contracts

- Consumes `currentUser` GraphQL fields (`permissions`, `roles`).
- Own-data: backend allows users to read own roles/permissions/groups without `security:user:view` (`withPermissionOrOwnData` in SecurityWiringFactory).

## Impact Surface

- Frontend: AuthorizationProvider, PermissionGate, DisableIfNoPermission, settings authorization pages
- Tests: component + provider unit; E2E `authorization.e2e.spec.ts`
- Backend: only as data source for currentUser

## Decisions

- Client authz UX driven by live GraphQL user payload for fresher admin UX than JWT-only parsing.

## Known Gaps

- **Stale example strings** in JSDoc of `PermissionGate` / `DisableIfNoPermission` use obsolete `authorization:user:edit|view|delete`. Real strings are `security:user:*`. Tests and settings UI use the real names.
- Client permission set can be newer than JWT claims (or vice versa) depending on cache vs token age — dual sources.
- No mobile gates yet.

## Legacy Notes

- None used as authority.
