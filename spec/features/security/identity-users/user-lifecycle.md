---
id: feature.security.identity-users.user-lifecycle
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/UserManagementService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/UserManagementResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityWiringFactory.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/rbac/SecurityPermissions.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_1__init.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/UserManagementServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/graphql/UserManagementResolverTest.kt
    - web/tests/e2e/user-management.e2e.spec.ts
  frontend:
    - web/src/app/(settings)/authorization/users/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.identity-users.user-profile-avatar
  - feature.security.authentication.sign-up
  - feature.security.rbac-administration.group-role-permission-management
  - feature.security.rbac-administration.admin-settings-ui
schema: spec-metadata-v1
---

# Identity Users — User Lifecycle

## Boundary

- Admin lifecycle for existing users (list, update profile fields, enable/disable, group membership).
- **Not** sign-up / sign-in / OAuth IdP (authentication subdomain).
- **Not** RBAC role catalog CRUD (rbac-administration) except user↔group assignment.
- **Not** tenant account invites (tenant-accounts).

## Behavior

- `UserManagementService`:
  - `searchUsers` / get by id (cursor pagination, orderBy)
  - `updateUser` (displayName; email immutable)
  - `enableUser` / `disableUser` (principal enabled flag coordination)
  - `assignGroupToUser` / `removeGroupFromUser`
- GraphQL: `user`, `users`, `updateUser`, `enableUser`, `disableUser`, group assign/remove mutations.
- Gates: `security:user:view` for reads; `security:user:save` for enable/disable/update/group changes. Catalog/constant `security:user:delete` exists but **no `deleteUser` GraphQL mutation** in the security schema.
- No admin `createUser` — users enter via sign-up / OAuth / invites flows.

## Invariants

- Email is not changed via updateUser.
- Disabled users should fail authentication paths (see authentication specs); admin disable is the control plane.

## Permissions

Backend enforcement: `security:user:view|save` (+ delete constant if/when wired).

UX hints: UserList/UserDrawer PermissionGates.

## Data And Contracts

- GraphQL User type + admin mutations listed above.
- Bruno: Get Users/User, Update/Enable/Disable User, Assign/Remove Group.

## Impact Surface

- Backend UserManagement*
- Web users settings + E2E user-management
- Tests: service + resolver unit

## Decisions

- Soft lifecycle via enable/disable rather than destructive delete as primary admin action.
- Keep catalog/constant `security:user:delete` for a future `deleteUser` (or equivalent) mutation — do not remove the seed; implement later.

## Known Gaps

- **Deferred:** `security:user:delete` is seeded and defined in `SecurityPermissions`, but there is no public `deleteUser` mutation yet — admin lifecycle today is enable/disable only.
- Permission changes via groups require token refresh to affect claim enforcement.

## Legacy Notes

- None used as authority.
