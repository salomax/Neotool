---
id: feature.security.session-tokens.web-client-session
domain: security
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend: []
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database: []
  tests:
    - web/src/shared/providers/__tests__/AuthProvider.test.tsx
    - web/src/shared/utils/__tests__/auth.test.ts
    - web/src/shared/utils/auth-session/__tests__/client.test.ts
    - web/src/shared/config/__tests__/auth-route-policy.test.ts
    - web/tests/e2e/helpers/auth.ts
  frontend:
    - web/src/shared/providers/AuthProvider.tsx
    - web/src/shared/utils/auth.ts
    - web/src/shared/utils/auth-session/shared.ts
    - web/src/shared/utils/auth-session/client.ts
    - web/src/app/api/auth/session/route.ts
    - web/src/shared/config/auth-route-policy.ts
    - web/src/proxy.ts
    - web/src/lib/graphql/client.ts
    - web/src/shared/components/auth/SignInForm.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.authentication.sign-in
  - feature.security.session-tokens.access-and-refresh-tokens
  - feature.security.authentication.email-verification
schema: spec-metadata-v1
---

# Web Client Session

## Behavior

- After successful `signIn` / `signInWithOAuth` / `signUp`, `AuthProvider` stores:
  - `auth_token` (access) and user JSON in `localStorage` if a refresh token was returned (persistent), else `sessionStorage`
  - `auth_refresh_token` alongside when present
- Persistence decision is driven by **presence of refreshToken** in the GraphQL payload (`shouldPersistSession = !!refreshToken`), which backend ties to remember-me / `keepMeConnected` on sign-in/OAuth (signup always returns refresh).
- Sign-in form defaults `rememberMe: true`; if client omits the field, backend applies `keepMeConnected` (default true).
- Optimistic route guard: cookie `auth_hint` set when token present; `web/src/proxy.ts` redirects protected prefixes (`/personal-finance`, `/profile`, `/settings`) to `/signin` when hint missing — **not** authorization.
- Separate cookie `nt_auth_session` via `POST /api/auth/session` encodes Unleash evaluation context (`userId`, optional tenant/role/plan) — not a bearer session for GraphQL.
- Apollo `refreshAccessToken` mutation rotates tokens on auth failure; `auth:token-refreshed` event updates provider state.
- Sign-out clears storage keys, auth hint, and session cookie.

## Invariants

- Backend JWT validation remains the only enforcement boundary for API access.
- Web cookies/hints must never be documented as permission enforcement.
- Protected route list is explicit in `auth-route-policy.ts` (e.g. `/investments` and `/financial-data` are not in the protected list).

## Permissions

Backend enforcement:

- None on these client helpers; GraphQL/API require Bearer access token.

UX hints:

- `PermissionGate` / `AuthorizationProvider` use `currentUser` permissions after session exists (authorization-runtime batch).
- Unverified email redirect is UX-only.

## Data And Contracts

- Client storage keys: `auth_token`, `auth_refresh_token`, user key.
- Cookies: `auth_hint`, `nt_auth_session`.
- Relies on security GraphQL auth mutations for tokens.

## Impact Surface

- Frontend: AuthProvider, auth utils, session route, proxy, Apollo client, auth forms
- GraphQL: consumes signIn/signUp/refresh payloads
- Tests: unit tests for AuthProvider/auth/session/policy; E2E helpers
- Backend: none owned here (token issuance covered by sibling specs)
- Mobile / Workers: none

## Decisions

- Refresh token presence is the client signal for persistent storage (aligned with backend remember-me issuance).
- Split concerns: bearer tokens in web storage vs Unleash context cookie.

## Known Gaps

- Access/refresh tokens in web storage are XSS-sensitive; no httpOnly bearer cookie for GraphQL.
- `nt_auth_session` naming can be confused with auth session; it is feature-flag context only.
- E2E coverage of refresh rotation / storage persistence modes is limited vs unit tests.
- No `remember-me-preference.md` in this batch; `keepMeConnected` behavior is documented here and in `sign-in.md`.

## Legacy Notes

- None used as authority.
