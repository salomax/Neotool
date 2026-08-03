---
id: feature.security.session-tokens.access-and-refresh-tokens
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/RefreshTokenService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityAuthResolver.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/jwt/JwtTokenValidator.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/config/JwtConfig.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_13__create_refresh_tokens_table.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_36__add_oauth2_refresh_token_grant_metadata.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_40__add_account_id_to_refresh_tokens.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/JwtTokenIssuerTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/RefreshTokenServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/RefreshTokenServiceIntegrationTest.kt
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/jwt/JwtTokenValidatorTest.kt
    - contracts/api-collections/bruno/collections/supergraph/Security/Refresh Access Token.bru
  frontend:
    - web/src/lib/graphql/client.ts
    - web/src/shared/providers/AuthProvider.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.session-tokens.auth-context-and-jwt-claims
  - feature.security.session-tokens.jwks-and-signing-keys
  - feature.security.session-tokens.web-client-session
  - feature.security.authentication.sign-in
schema: spec-metadata-v1
---

# Access And Refresh Tokens (first-party app)

## Behavior

- **Access JWT (first-party):** short-lived RS256 token (`JwtConfig.accessTokenExpirationSeconds`, default 15m). Claims include `sub` (userId), `email`, `type=access`, `permissions`, optional account claims, `iss`, `iat`/`exp`, and `jti` via `JwtTokenIssuer.signBuilder`.
- **Refresh JWT:** long-lived (`refreshTokenExpirationSeconds`, default 7d), `type=refresh`; plaintext returned to client; **SHA-256 hash** stored in `refresh_tokens` with family id.
- **Rotation (`RefreshTokenService.refreshAccessToken`):** one-time use; reuse of replaced token revokes entire family; revoked/expired/disabled user fails; first-party path rehydrates `AuthContext` and issues new access + refresh; OAuth AS refresh path (clientId present) re-issues OAuth-shaped access token — **out of scope for app login docs beyond noting shared storage**.
- GraphQL `refreshAccessToken(refreshToken)` returns `SignInPayload` with rotated tokens.
- Validation across services: `JwtTokenValidator` (common) requires issuer, RS256, and non-blank `jti` for access tokens.

## Invariants

- Access tokens are stateless; refresh tokens are revocable via DB.
- Refresh reuse must invalidate the family.
- Disabled principal cannot refresh.

## Permissions

Backend enforcement:

- Permissions for API calls come from access-token claims after validation (see authorization-runtime later).
- `refreshAccessToken` is public but requires a valid refresh secret.

UX hints:

- Apollo client refreshes on auth errors and dispatches `auth:token-refreshed`.

## Data And Contracts

- GraphQL: `refreshAccessToken`, `RefreshAccessTokenInput`, `SignInPayload`.
- DB: `security.refresh_tokens` (+ OAuth metadata columns used by AS path).

## Impact Surface

- Backend: `JwtTokenIssuer`, `RefreshTokenService`, `SecurityAuthResolver.refreshAccessToken`
- Common: `JwtTokenValidator`, `JwtConfig`
- GraphQL / DB / tests / Bruno as listed
- Frontend: Apollo refresh + storage keys
- Workers: not for user refresh (S2S is separate)

## Decisions

- OAuth 2.0 BCP-style refresh rotation with reuse detection.
- Shared refresh table serves both first-party and OAuth AS grants (distinguished by `clientId` / scopes / resource).

## Known Gaps

- Do not confuse with S2S service tokens (`generateServiceToken`) or OAuth AS access tokens (`generateOAuthAccessToken`) — different claim shapes.
- Web stores refresh token in local/session storage (XSS surface) — documented under web-client-session.

## Legacy Notes

- None used as authority.
