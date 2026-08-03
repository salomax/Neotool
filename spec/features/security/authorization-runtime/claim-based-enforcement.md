---
id: feature.security.authorization-runtime.claim-based-enforcement
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/authorization/AuthorizationChecker.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/authorization/PermissionChecker.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/graphql/AuthenticatedGraphQLWiringFactory.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/graphql/GraphQLPermissionHelper.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/RequiresAuthorization.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/AuthorizationInterceptor.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityWiringFactory.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql: []
  database: []
  tests:
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/authorization/PermissionCheckerTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/AuthorizationInterceptorIntegrationTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.session-tokens.auth-context-and-jwt-claims
  - feature.security.authorization-runtime.hybrid-rbac-abac
  - feature.security.authorization-runtime.frontend-permission-gates
schema: spec-metadata-v1
---

# Authorization Runtime — Claim-Based Enforcement

## Boundary

- **This path:** request enforcement via JWT permission claims (`PermissionChecker` / `AuthorizationChecker`).
- **Not** DB hybrid RBAC+ABAC (`AuthorizationService` / `AuthorizationManager`) — sibling spec.
- **Not** frontend `PermissionGate` (UX only).

## Behavior

1. `PermissionChecker` (`@Singleton` `@Primary`) implements `AuthorizationChecker.require(principal, permission)` by checking strings in the JWT:
   - USER (or SERVICE without user context): `principal.permissionsFromToken`
   - SERVICE with user context (token exchange): `principal.userPermissions`
2. GraphQL modules inject `AuthorizationChecker` into `AuthenticatedGraphQLWiringFactory` / wiring factories (`SecurityWiringFactory`, `BankingWiringFactory`, …). `env.withPermission(action)` → claim check → deny as `AuthorizationDeniedException`.
3. Optional HTTP AOP: `@RequiresAuthorization("…")` + `AuthorizationInterceptor` also injects `AuthorizationChecker` (same primary bean). Used in integration test controller; not widely applied on production HTTP controllers yet.
4. Permissions embedded at login/refresh via `AuthContextFactory` → `JwtTokenIssuer` (see session-tokens claims spec). RBAC changes do **not** update live access JWTs until re-issue.

## Invariants

- Across product GraphQL, the injected `AuthorizationChecker` is **claim-based** (`@Primary PermissionChecker`), not `AuthorizationManager`.
- Missing permission → deny; no DB round-trip on this path.
- Service-with-user-context tokens authorize with `userPermissions`, not the service principal’s own permission list.

## Permissions

Backend enforcement:

- Claim membership for the required permission string (exact match).

UX hints:

- Frontend gates / disabled buttons — never substitute for this check.

## Data And Contracts

- No GraphQL type for the checker itself; each field/mutation declares required permission constants (e.g. `SecurityPermissions`, `BankingPermissions`).

## Impact Surface

- Common: PermissionChecker, AuthorizationChecker, GraphQL helpers
- Security / banking / other Kotlin GraphQL wirings
- HTTP interceptor infrastructure
- Tests: PermissionChecker unit + AuthorizationInterceptor IT

## Decisions

- Stateless claim checks are the production default for cross-service GraphQL for performance and decoupling from security DB.
- `@Primary` on `PermissionChecker` resolves the dual `AuthorizationChecker` implementations in the security classpath.

## Known Gaps

- `AuthorizationManager` also implements `AuthorizationChecker` but is not the injected GraphQL path (only concrete-type / unused in production wirings found).
- Live permission revocation requires refresh/login (stale JWT window).
- Production REST surface rarely uses `@RequiresAuthorization` (infra exists; coverage is test-oriented).

## Legacy Notes

- None used as authority.
