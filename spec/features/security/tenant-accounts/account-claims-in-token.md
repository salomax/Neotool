---
id: feature.security.tenant-accounts.account-claims-in-token
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthContextFactory.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/RefreshTokenService.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/AuthContext.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/JwtPrincipalDecoder.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/RequestPrincipal.kt
  graphql: []
  database: []
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthContextFactoryTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/AuthContextIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/JwtTokenIssuerTest.kt
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/principal/JwtPrincipalDecoderTest.kt
  frontend:
    - web/src/shared/utils/auth.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.session-tokens.auth-context-and-jwt-claims
  - feature.security.session-tokens.access-and-refresh-tokens
  - feature.security.tenant-accounts.account-model
  - feature.security.tenant-accounts.membership-invites
schema: spec-metadata-v1
---

# Account Claims In Access Token

Documents how **tenant** membership is embedded in first-party access JWTs. Shared token lifecycle: see `feature.security.session-tokens.*`. OAuth2 AS tokens may carry `current_account` for agent grants — out of scope except noting a separate issuer path.

## Behavior

### Build (`AuthContextFactory.build`)

- Loads ACTIVE memberships only.
- `accounts` → list of `AccountSummary(accountId, role)` (role = `AccountRole.name`).
- `currentAccountId` → membership with `isDefault=true`, else first ACTIVE.
- If no ACTIVE memberships: `currentAccountId=null`, `accounts=null`, `sessionVersion=null`.
- If any ACTIVE: `sessionVersion = 0L` (constant today).

### Emit (`JwtTokenIssuer.generateAccessToken(AuthContext)`)

When present:

- `current_account`: UUID string
- `accounts`: `[{ "id", "role" }, ...]`
- `session_version`: number

When absent: those claims omitted (backward compatible).

### Consume

- `JwtPrincipalDecoder` → `RequestPrincipal.currentAccountId`, `.accounts`, `.sessionVersion`.
- Web `getCurrentAccountIdFromAccessToken` reads `current_account` client-side (UX helper only).

### When claims refresh

| Event | Account claims rebuilt? |
|---|---|
| Password / OAuth IdP sign-in | Yes (`AuthContextFactory` then issue) |
| Sign-up | Yes |
| First-party `refreshAccessToken` | Yes — `RefreshTokenService` rebuilds AuthContext before new access JWT |
| Membership invite/accept/leave/role change alone | **No** — existing access JWT unchanged until login or refresh |
| Access token TTL expiry without refresh | Client must re-auth / refresh to see new memberships |

## Invariants

- Only ACTIVE memberships appear in claims.
- `session_version` does not currently increment on membership mutations (fixed `0L` when set).
- Claims describe tenant accounts, never banking financial accounts.

## Permissions

Backend enforcement:

- Downstream services may use `RequestPrincipal.currentAccountId` / `accountIds` for scoping; membership policy for mutations still uses DB membership, not solely the claim.

UX hints:

- Client-side JWT parse for workspace id is not authorization.

## Data And Contracts

- No GraphQL type for claims; opaque JWT.
- Related session-token specs for issuer/validator/refresh mechanics.

## Impact Surface

- Backend: AuthContextFactory, JwtTokenIssuer, RefreshTokenService
- Common: AuthContext, RequestPrincipal, JwtPrincipalDecoder
- Tests: AuthContext factory/integration, issuer, decoder
- Frontend: auth.ts claim reader
- Mobile / Workers: N/A for first-party user claim shape

## Decisions

- Rehydrate full AuthContext on first-party refresh so membership/permission drift is picked up on refresh, not mid-access-token lifetime.
- Keep account claims optional for legacy tokens.

## Known Gaps

- No automatic token revoke/reissue on membership change; clients can hold stale `accounts` until refresh.
- `session_version` not used as a real revocation counter yet.
- Multi-membership users are constrained by accept-time 1:1 rule today; claim shape already supports a list if that constraint relaxes.

## Legacy Notes

- None used as authority.
