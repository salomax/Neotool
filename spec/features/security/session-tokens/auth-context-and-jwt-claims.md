---
id: feature.security.session-tokens.auth-context-and-jwt-claims
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthContextFactory.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/AuthContext.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/JwtPrincipalDecoder.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/RequestPrincipal.kt
  graphql: []
  database: []
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthContextFactoryTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/AuthContextIntegrationTest.kt
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/principal/AuthContextTest.kt
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/principal/JwtPrincipalDecoderTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/JwtTokenIssuerTest.kt
  frontend:
    - web/src/shared/utils/auth.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.session-tokens.access-and-refresh-tokens
  - feature.security.authentication.sign-in
schema: spec-metadata-v1
---

# Auth Context And JWT Claims

## Behavior

- `AuthContextFactory.build(user)` normalizes post-auth context for **any** login method:
  1. Role names via `AuthorizationService.getUserRoles` (empty list on failure)
  2. Permission names via `getUserPermissions` (empty on failure)
  3. ACTIVE account memberships → `AccountSummary(accountId, role)`; `currentAccountId` = default membership or first
  4. `sessionVersion = 0L` when accounts present, else null; `accounts` null when empty
- `JwtTokenIssuer.generateAccessToken(AuthContext)` writes:
  - Always: `sub`, `email`, `type=access`, `permissions`, issuer/exp/iat, `jti`
  - If present: `current_account`, `accounts` as `[{id, role}]`, `session_version`
- Consumers decode via `JwtPrincipalDecoder` → `RequestPrincipal` with `permissionsFromToken`, `currentAccountId`, `accounts`, `sessionVersion`, `clientId` (when present on OAuth-shaped tokens).
- Web helper `getCurrentAccountIdFromAccessToken` reads `current_account` claim client-side (UX/routing helper, not enforcement).

## Invariants

- Roles/permissions lists in AuthContext are never null (empty allowed).
- Account claims omitted when user has no ACTIVE memberships (backward compatible).
- AuthContext construction is provider-agnostic (password, OAuth IdP, refresh rehydrate).

## Permissions

Backend enforcement:

- Permission strings in the access token are the inputs to claim-based `PermissionChecker` in other services.
- Freshness of permissions depends on re-issue (login/refresh); changing RBAC does not rewrite existing access JWTs until refresh/login.

UX hints:

- Frontend may read `current_account` from JWT for workspace context; must not treat it as sole authz.

## Data And Contracts

- Shared types: `AuthContext`, `AccountSummary`, `RequestPrincipal` in `common/security`.
- No dedicated GraphQL type for AuthContext; claims are opaque JWT.

## Impact Surface

- Backend: AuthContextFactory, JwtTokenIssuer, AuthorizationService (read path)
- Common: AuthContext, JwtPrincipalDecoder, RequestPrincipal
- Tests: AuthContext factory/integration, issuer, principal decoder
- Frontend: `auth.ts` claim reader
- Mobile / Workers: none for this claim shape (workers use service tokens)

## Decisions

- Fail-open to empty roles/permissions if authorization lookup throws during context build (logs warning).
- `session_version` currently seeded as `0L` when accounts exist (revocation semantics limited until evolved).

## Known Gaps

- `session_version` not clearly rotated on security events in this batch’s evidence (value fixed at 0 when set).
- Roles are loaded into AuthContext but first-party access JWT from AuthContext path primarily embeds **permissions** (and account claims), not a `roles` claim — roles mainly via `currentUser` GraphQL for UX.

## Legacy Notes

- None used as authority.
