---
id: feature.security.rbac-administration.permission-catalog
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/PermissionManagementService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/rbac/SecurityPermissions.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/PermissionManagementResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/repo/PermissionRepository.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_1__init.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_12__add_assets_permissions.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_16__add_permissions.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_20__add_comms_permissions.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_25__add_banking_permissions_and_user_groups.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_26__add_transaction_permissions.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_27__add_account_membership_query_permission.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_28__add_investment_permissions.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_30__add_financialdata_market_data_export_permission.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_32__add_account_membership_manage_permissions.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/PermissionManagementServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/graphql/PermissionManagementResolverTest.kt
  frontend:
    - web/src/app/(settings)/authorization/permissions/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.rbac-administration.group-role-permission-management
  - feature.security.authorization-runtime.claim-based-enforcement
  - feature.security.oauth2-authorization-server.scopes-to-permissions-mapping
schema: spec-metadata-v1
---

# RBAC Administration — Permission Catalog

## Ownership

- **Semantic meaning** of each permission string: owning product domain (banking, assets, investment, comms, financialdata, security, …).
- **Technical catalog**, Flyway seeds, DB table, read APIs, admin list UI: **security**.

## Behavior

- Permissions stored in `security.permissions` (name unique). Seeded by Flyway across domains (V0_1 security:*, assets, banking, investment, comms, financialdata, account-membership, …).
- Typed constants for security’s own strings: `SecurityPermissions` (`security:user|group|role:*`, `security:account-membership:view`).
- `PermissionManagementService` is **read-only**: list/search paginated permissions; roles-for-permission lookup. **No create/update/delete permission GraphQL.**
- GraphQL `permissions(first, after, query)` requires `security:role:view`; used by admin UI and role assignment pickers.
- Cross-domain permission strings also appear in domain Kotlin constants (e.g. banking) and OAuth scope→banking mapper — security DB must stay aligned via migrations.

## Invariants

- New product permissions ship as Flyway seeds in security migrations (or coordinated migration ownership), not ad-hoc GraphQL creates.
- Catalog names are exact-match keys for JWT claims and RBAC checks.

## Permissions

Backend enforcement:

- Listing/searching catalog gated by security admin view permissions (see wiring).

UX hints:

- Permissions settings page lists catalog for operators.

## Data And Contracts

- GraphQL: `permissions` connection; `Permission` type; nested on roles.
- DB: `security.permissions`, `role_permissions`.
- No mutation to mutate permission rows via API.

## Impact Surface

- Backend: PermissionManagementService/Resolver, repositories
- Database: seed migrations listed in evidence
- Frontend: `(settings)/authorization/permissions`
- Bruno: Get Permissions
- Tests: management service + resolver

## Decisions

- Catalog is migration-driven to keep multi-service permission strings reviewable in PRs.

## Known Gaps

- No GraphQL/API to add permissions without a migration (intentional, but ops friction).
- Possible drift between domain constant classes and Flyway if a service adds a string without a security seed.
- `checkPermission` input validator still assumes two-segment names (see hybrid-rbac-abac) while catalog is mostly three-segment.

## Legacy Notes

- None used as authority.
