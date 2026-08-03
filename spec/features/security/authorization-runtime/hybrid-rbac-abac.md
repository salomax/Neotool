---
id: feature.security.authorization-runtime.hybrid-rbac-abac
domain: security
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authorization/AuthorizationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authorization/AuthorizationManager.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authorization/AbacEvaluationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authorization/AbacPolicyService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/AuthorizationResolver.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_4__create_group_tables.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_5__create_abac_tables.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthorizationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/HybridAuthorizationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthorizationManagerTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AbacEvaluationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AbacPolicyServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/AuthorizationIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/graphql/AuthorizationResolverTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.authorization-runtime.claim-based-enforcement
  - feature.security.authorization-runtime.authorization-audit
  - feature.security.rbac-administration.permission-catalog
schema: spec-metadata-v1
---

# Authorization Runtime — Hybrid RBAC + ABAC (Internal / Partial)

## Product status

**Internal/partial infrastructure** — not a full product feature. No ABAC admin UI. No ABAC GraphQL CRUD. Runtime GraphQL field enforcement uses claim-based `PermissionChecker`, not this hybrid path.

## Behavior

### RBAC (DB)

- `AuthorizationService` resolves user roles via group memberships + group→role assignments (validity windows), then checks permission names against role_permissions (`existsPermissionForRoles`).
- Also powers AuthContext permission lists at token issue and admin queries (`getUserPermissions` / `getUserRoles`).
- Service principals: `checkServicePermission` via `principal_permissions` (+ optional resource pattern).

### Hybrid with ABAC

- Full `checkPermission(userId, permission, resource…)`:
  1. RBAC deny → final deny (+ audit when enabled)
  2. RBAC allow → `AbacEvaluationService.evaluatePolicies`
  3. ABAC DENY overrides; ALLOW or no match → allow (RBAC already passed)
- `AuthorizationManager` wraps this for `AuthorizationChecker`-shaped calls with optional resource/context attributes; enriches subject with `principalPermissions` from the token for ABAC. **Not** `@Primary`; not injected by GraphQL wirings found in repo.

### Public GraphQL entry (hybrid evaluation)

- Query `checkPermission(userId, permission, resourceId)` (requires caller `security:user:view`) delegates to `AuthorizationService` hybrid path.
- Related: `getUserPermissions`, `getUserRoles`, `isUserActiveMemberOfAccount` (S2S membership boundary).

### ABAC policy CRUD

- `AbacPolicyService` create/update/delete/find — **service/repository only**, no GraphQL / admin UI.

## Invariants

- ABAC never grants access when RBAC denied.
- Explicit ABAC DENY can revoke an RBAC allow on this path only.
- Claim-based GraphQL enforcement does **not** consult ABAC.

## Permissions

Backend enforcement (this path):

- DB RBAC + optional ABAC when `AuthorizationService.checkPermission` / GraphQL `checkPermission` / `AuthorizationManager` is used.

UX hints: none for ABAC.

## Data And Contracts

- GraphQL: `checkPermission` → `AuthorizationResult { allowed, reason }` (+ getUserPermissions/Roles).
- DB: groups/roles/permissions + ABAC tables (V0_5).
- No ABAC mutations in schema.

## Impact Surface

- Backend: AuthorizationService/Manager, Abac* services, AuthorizationResolver
- GraphQL: checkPermission family
- Tests: unit hybrid/ABAC/manager + integration + resolver
- Bruno: Check Permission
- Frontend/UI: none for ABAC

## Decisions

- Keep hybrid stack for admin diagnostics (`checkPermission`) and future resource-aware checks; keep product request path claim-based.
- Document ABAC as internal until a product surface exists.

## Known Gaps

- **Dual checkers:** `@Primary PermissionChecker` vs unused-as-interface `AuthorizationManager` — easy to misread comments that suggest Manager is “for advanced checks” without a wiring story.
- `AuthorizationResolver` validates permission with pattern `^[a-z0-9_-]+:[a-z0-9_-]+$` (two segments). Real catalog uses three segments (`security:user:view`, `banking:transaction:view`). Tests use two-segment names like `transaction:read`. **`checkPermission` rejects real seeded permission names.** Tracked as [#585](https://github.com/invistus/invistus/issues/585).
- No production GraphQL/REST path found that injects `AuthorizationManager` for field enforcement.
- No Bruno/E2E for ABAC policy CRUD (none exposed).

## Legacy Notes

- None used as authority.
