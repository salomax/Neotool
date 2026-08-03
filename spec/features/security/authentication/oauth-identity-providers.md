---
id: feature.security.authentication.oauth-identity-providers
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth/GoogleOAuthProvider.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth/OAuthProviderRegistry.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth/OAuthService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityAuthResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/config/OAuthConfig.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
  database: []
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/GoogleOAuthProviderTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/OAuthProviderRegistryTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthenticationServiceTest.kt
    - contracts/api-collections/bruno/collections/supergraph/Security/Sign In with OAuth.bru
  frontend:
    - web/src/shared/hooks/auth/useOAuth.ts
    - web/src/shared/components/auth/SignInForm.tsx
    - web/src/shared/providers/AuthProvider.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.authentication.sign-in
  - feature.security.authentication.sign-up
  - feature.security.session-tokens.access-and-refresh-tokens
schema: spec-metadata-v1
---

# OAuth Identity Providers (app login)

## Behavior

- App login IdP flow is GraphQL `signInWithOAuth(provider, idToken, rememberMe?)` — **not** the OAuth2 Authorization Server (`/oauth2/*`) and **not** S2S `/oauth/token`.
- Supported provider implementation today: **Google** (`GoogleOAuthProvider.getProviderName() == "google"`).
- Registry resolves provider by name; unknown provider → validation error on `provider`.
- Google validation (`GoogleOAuthProvider.validateAndExtractClaims`): requires configured client id; checks audience contains client id, issuer matches config, not expired; extracts email (required), optional name/picture, `email_verified` claim into `OAuthUserClaims`.
- `authenticateWithOAuth`:
  - Existing user by email: may update displayName/avatar from claims; disabled principal → null (sign-in fails).
  - New user: created with `passwordHash = null`, principal enabled, default group, personal account or pending-invite bind (same as signup).
  - **Does not persist** `OAuthUserClaims.emailVerified` onto `UserEntity.emailVerified`.
- Resolver then builds AuthContext, access token, and refresh token using the same remember-me / `keepMeConnected` rules as password sign-in.

## Invariants

- IdP tokens must validate against configured audience/issuer before user resolution.
- OAuth-only users have no password hash (password sign-in blocked).
- Provider registry is the extension point; comments mention microsoft/github but only Google is implemented.

## Permissions

Backend enforcement:

- Public mutation.
- New users get default group permissions for token claims.

UX hints:

- Web Google button via `useOAuth` → `signInWithOAuth("google", idToken, rememberMe)`.
- Unverified email UX may still redirect to `/verify-email` if `user.emailVerified` is false after login.

## Data And Contracts

- GraphQL: `SignInWithOAuthInput { provider, idToken, rememberMe }`, returns `SignInPayload`.
- Config: `OAuthConfig.google` client id / issuer (env-resolved).

## Impact Surface

- Backend: `oauth/*` IdP package, `authenticateWithOAuth`, `SecurityAuthResolver.signInWithOAuth`
- GraphQL: `signInWithOAuth`
- Tests: Google provider unit tests, registry tests, auth service OAuth cases, Bruno
- Frontend: `useOAuth`, SignInForm Google path
- Mobile / Workers: none

## Decisions

- Find-or-create by email; no separate OAuth identity table in this path.
- Keep app IdP (`service/oauth`) separate from AS (`service/oauth2`).

## Known Gaps

- `email_verified` from Google is extracted but not written to `User.emailVerified` — backend/web may disagree on verification state for OAuth users.
- Microsoft/other providers referenced in comments/schema examples are not implemented.
- Limited web E2E for Google IdP (MCP E2E covers AS, not this flow).

## Legacy Notes

- None used as authority.
