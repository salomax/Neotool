---
id: feature.security.authentication.sign-in
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityAuthResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/preferences/UserPreferencesService.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
  database: []
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/GraphQLAuthenticationIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthenticationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/UserProfileFeaturesIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/graphql/SecurityAuthResolverTest.kt
    - contracts/api-collections/bruno/collections/supergraph/Security/Login as Admin.bru
  frontend:
    - web/src/shared/providers/AuthProvider.tsx
    - web/src/shared/components/auth/SignInForm.tsx
    - web/tests/e2e/helpers/auth.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.session-tokens.access-and-refresh-tokens
  - feature.security.session-tokens.auth-context-and-jwt-claims
  - feature.security.session-tokens.web-client-session
  - feature.security.authentication.oauth-identity-providers
schema: spec-metadata-v1
---

# Sign In (email/password)

## Behavior

- `signIn(input: SignInInput!)` authenticates with email + password via `AuthenticationService.authenticate`.
- Failed auth (missing user, blank password, no password hash, bad password, disabled principal) returns `INVALID_CREDENTIALS` without distinguishing cause (`SecurityAuthResolver.signIn`, `AuthenticationService.authenticate`).
- On success: builds `AuthContext`, issues access JWT, optionally creates refresh token.
- **Remember-me / `keepMeConnected`:**
  - If GraphQL input includes `rememberMe`, that boolean wins.
  - If omitted, backend uses `userPreferencesService.get(userId).keepMeConnected` (default `true` when no prefs row).
  - Refresh token is created only when the resolved value is true (`SecurityAuthResolver.signIn`).
- Password users without a hash (typical OAuth-only accounts) cannot sign in with password (`AuthenticationService.authenticate`).
- Email verification is **not** required by backend for password sign-in.

## Invariants

- Disabled user principal must not authenticate.
- Invalid credentials must not leak whether the email exists.
- Access token is always returned on successful sign-in; refresh token only when remember-me resolves true.
- Argon2id verifies stored password hashes (`AuthenticationService` hashing helpers).

## Permissions

Backend enforcement:

- No permission claim required to call `signIn` (public mutation).
- Post-login authorization uses permissions embedded in the issued access token (see session-tokens specs).

UX hints:

- Web `SignInForm` defaults `rememberMe: true` and passes it to `AuthProvider.signIn`.
- If stored/returned user has `emailVerified === false`, `AuthProvider` redirects to `/verify-email` (client-only; backend still issued tokens).

## Data And Contracts

- GraphQL: `mutation signIn`, `SignInInput { email, password, rememberMe }`, `SignInPayload { token, refreshToken, user }`.
- Prefs field: `UserPreferences.keepMeConnected` (default true).

## Impact Surface

- Backend: `AuthenticationService`, `SecurityAuthResolver`, `AuthContextFactory`, `RefreshTokenService`, `UserPreferencesService`
- GraphQL: security subgraph `signIn`
- Database: users + principals (enabled check); refresh_tokens when remember-me
- Tests: unit/integration GraphQL auth; `UserProfileFeaturesIntegrationTest` covers omitted `rememberMe` → prefs
- Frontend: sign-in page/form, `AuthProvider`
- Mobile: none in this round
- Workers: none

## Decisions

- Prefer explicit `rememberMe` over stored preference when the client sends the field.
- Treat disabled principal as failed credentials (same client error path).

## Known Gaps

- No dedicated web E2E covering remember-me / `keepMeConnected` omission vs preference.
- Password sign-in does not check `emailVerified`; UX may still force verify-email after login.

## Legacy Notes

- None used as authority.
