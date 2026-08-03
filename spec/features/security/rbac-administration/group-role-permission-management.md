---
id: feature.security.rbac-administration.group-role-permission-management
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/GroupManagementService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/RoleManagementService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/UserManagementService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityWiringFactory.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/rbac/SecurityPermissions.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_1__init.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_4__create_group_tables.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_10__setup_administrators_group.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/GroupManagementServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/RoleManagementServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/graphql/GroupManagementResolverTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/graphql/RoleManagementResolverTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/GroupManagementIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/RoleManagementGraphQLIntegrationTest.kt
    - web/tests/e2e/group-management.e2e.spec.ts
    - web/tests/e2e/role-management.e2e.spec.ts
  frontend:
    - web/src/app/(settings)/authorization/groups/
    - web/src/app/(settings)/authorization/roles/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.rbac-administration.permission-catalog
  - feature.security.rbac-administration.admin-settings-ui
  - feature.security.identity-users.user-lifecycle
  - feature.security.authorization-runtime.claim-based-enforcement
schema: spec-metadata-v1
---

# RBAC Administration — Group / Role / Permission Assignment

## Boundary

- Administers **global** security groups/roles/permissions (authorization settings), not tenant account OWNER/ADMIN/MEMBER matrix (that is `tenant-accounts`).

## Behavior

### Groups

- CRUD-ish: create/update/delete group; list/search; assign/remove users; assign/remove roles to groups (`GroupManagementService`).
- GraphQL gated by `security:group:view|save|delete`.

### Roles

- create/update/delete role; list/search; assign/remove permissions (`RoleManagementService`).
- GraphQL gated by `security:role:view|save|delete`.
- Assigning permissions links existing catalog rows — does not create permission names.

### Users ↔ groups

- `assignGroupToUser` / `removeGroupFromUser` on user management (requires `security:user:save`).
- Effective permissions at token issue = union of permissions from roles inherited via group memberships (and validity windows).

### Bootstrapping

- Flyway seeds Administrators group and baseline role/permission links (V0_1, V0_10, domain permission migrations).

## Invariants

- Permission strings must already exist in catalog before role assignment.
- Changing assignments affects **new** AuthContext/JWT only after login/refresh (claim enforcement lag).

## Permissions

Backend enforcement:

| Area | Typical gates |
|---|---|
| Groups | `security:group:view/save/delete` |
| Roles | `security:role:view/save/delete` |
| User↔group | `security:user:save` |
| Permission list | role/user view paths |

UX hints: PermissionGate on settings drawers/lists.

## Data And Contracts

- GraphQL mutations/queries for groups, roles, assignPermissionToRole, assignRoleToGroup, assignGroupToUser, etc. (security schema).
- DB: groups, group_memberships, roles, role_permissions, group_role_assignments.

## Impact Surface

- Backend management services + SecurityWiringFactory
- GraphQL contract + Bruno (Create/Update/Delete Group/Role, Assign*, Get*)
- Web settings groups/roles
- E2E group-management + role-management

## Decisions

- Groups are the primary vehicle for granting roles to users (no direct user→role table in the main admin path — UI may show derived roles).

## Known Gaps

- After admin changes, users keep old JWT permissions until refresh — no forced session bump documented here.
- Direct principal_permissions for services is separate (service-principals batch).

## Legacy Notes

- None used as authority.
