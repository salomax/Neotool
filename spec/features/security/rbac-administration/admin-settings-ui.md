---
id: feature.security.rbac-administration.admin-settings-ui
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
    - web/tests/e2e/authorization.e2e.spec.ts
    - web/tests/e2e/user-management.e2e.spec.ts
    - web/tests/e2e/group-management.e2e.spec.ts
    - web/tests/e2e/role-management.e2e.spec.ts
    - web/src/app/(settings)/authorization/users/__tests__/
    - web/src/app/(settings)/authorization/groups/__tests__/
    - web/src/app/(settings)/authorization/roles/__tests__/
    - web/src/app/(settings)/authorization/permissions/__tests__/
  frontend:
    - web/src/app/(settings)/authorization/users/
    - web/src/app/(settings)/authorization/groups/
    - web/src/app/(settings)/authorization/roles/
    - web/src/app/(settings)/authorization/permissions/
    - web/src/shared/components/authorization/
    - web/src/shared/providers/AuthorizationProvider.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.rbac-administration.group-role-permission-management
  - feature.security.rbac-administration.permission-catalog
  - feature.security.identity-users.user-lifecycle
  - feature.security.authorization-runtime.frontend-permission-gates
schema: spec-metadata-v1
---

# RBAC Administration — Admin Settings UI

## Behavior

- Web settings area under `web/src/app/(settings)/authorization/` with four sections:
  - **users/** — list/search, status toggle, drawer, group assignment UI
  - **groups/** — list/search, drawer/form, user + role assignment
  - **roles/** — list/search, drawer/form, permission + group assignment
  - **permissions/** — read-only catalog list/search (no create permission UI)
- Screens wrap mutations/views with `PermissionGate` using real `security:*` strings.
- Relies on `AuthorizationProvider` for client-side capability hints; GraphQL mutations enforce permissions on the server.

## Invariants

- UI cannot invent permission catalog entries.
- Failure of PermissionGate must not be confused with API authorization success.

## Permissions

Backend enforcement: GraphQL wirings (see sibling specs).

UX hints: PermissionGate / DisableIfNoPermission on buttons and sections.

## Data And Contracts

- Consumes security subgraph user/group/role/permission operations.
- No ABAC policy screens.

## Impact Surface

- Frontend settings authorization tree
- E2E: authorization, user/group/role management
- Component tests under each section

## Decisions

- Permissions tab is browse-only; assignments happen on roles.
- Users/groups/roles are the operator surface for Invistus RBAC (distinct from tenant membership UI).

## Known Gaps

- No ABAC UI (by design — internal).
- Mobile has no admin authorization settings.
- Client gates can hide actions the API would still reject (or briefly show actions if provider stale).

## Legacy Notes

- None used as authority.
