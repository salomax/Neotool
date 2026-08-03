---
id: feature.security.oauth2-authorization-server.authorize-consent-login-flow
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2AuthorizationController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2LoginController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2ConsentController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/AuthorizationServerMetadataController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2AuthorizationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2ConsentService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2SessionService.kt
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_34__create_oauth2_authorization_requests.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_35__create_oauth2_authorization_codes.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_38__create_oauth2_consents.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/OAuth2AuthorizationServerIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2AuthorizationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2ConsentServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2SessionServiceTest.kt
    - web/tests/e2e/mcp-oauth.e2e.spec.ts
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.oauth2-authorization-server.token-grants-refresh-exchange
  - feature.security.oauth2-authorization-server.dynamic-client-registration
  - feature.security.oauth2-authorization-server.scopes-to-permissions-mapping
  - feature.security.connected-agents.list-connected-agents
  - feature.security.authentication.sign-in
schema: spec-metadata-v1
---

# OAuth2 AS — Authorize / Login / Consent

## Boundary (do not confuse)

- **This flow:** HTTP Authorization Server under `/oauth2/*` for agents/MCP (authorization code + PKCE).
- **Not** app Google IdP (`signInWithOAuth` / `service/oauth`).
- **Not** S2S client credentials (`POST /oauth/token`).

## Behavior

1. **Metadata:** `GET /.well-known/oauth-authorization-server` advertises issuer, authorize/token/jwks/register endpoints, supported scopes/grants/PKCE S256, `resource_indicators_supported=true`.
2. **Authorize** `GET /oauth2/authorize`:
   - Requires `response_type=code`, registered `client_id` + matching `redirect_uri`, PKCE `code_challenge` with method `S256`, non-empty `scope` subset of catalog ∩ client scopes, required `resource` registered for client.
   - Persists PENDING authorization request; redirects to `/oauth2/login` or `/oauth2/consent` based on AS session cookie.
3. **Login HTML** `GET|POST /oauth2/login`:
   - Password authenticate via `AuthenticationService.authenticate` (email/password only — not Google GraphQL IdP).
   - Sets signed httpOnly AS session cookie (`OAuth2SessionService`, SameSite=Lax); redirects to consent.
4. **Consent** `GET|POST /oauth2/consent`:
   - Requires AS session; binds user + CSRF; resolves **tenant** account automatically (default ACTIVE membership, else first) — no account picker on the consent UI.
   - If prior consent already covers requested scopes → silent `approveExistingConsent` (no prompt).
   - Else HTML consent (shows account label + scopes); approve issues hashed authorization code (TTL capped ≤60s), persists consent grant per `(user, client, account)`, redirects with `code` (+ `state`).
   - Deny redirects with OAuth error.

## Invariants

- PKCE S256 mandatory; public AS session is separate from web app JWT storage.
- Consent/account binding uses `security.accounts` membership (tenant), not banking accounts.
- Authorization codes are single-use (reuse path revokes refresh family — see token grants).

## Permissions

Backend enforcement:

- Client/redirect/resource/scope validation at authorize.
- CSRF on consent approve/deny.
- User must own the pending request.

UX hints:

- Server-rendered login/consent templates (not Next.js).
- Scope labels via `OAuth2ScopeDescriptionService` (i18n properties).

## Data And Contracts

- HTTP only (no GraphQL for this flow).
- DB: authorization_requests, authorization_codes, oauth2_consents (+ account_id).

## Impact Surface

- Backend: OAuth2* controllers/services, session, templates
- Database: V0_34/35/38
- Tests: AS integration + unit; web `mcp-oauth.e2e.spec.ts`
- MCP: consumes resulting tokens (dep), not owner of this UI
- Bruno: no dedicated authorize/consent collection (JWKS only nearby)

## Decisions

- Resource indicator required at authorize time.
- Incremental consent: only newly requested scopes need prompt.
- No account chooser on consent: v1 membership is 1:1 (a user is tied to one ACTIVE account at a time), so auto-resolve is enough until multi-account-per-user ships. See `feature.security.tenant-accounts.membership-invites` / account-claims.

## Known Gaps

- AS login is password-only; OAuth-only users without password cannot complete this HTML login path.
- Bruno does not cover authorize→consent→code.
- If/when a user may hold multiple ACTIVE memberships, consent must gain an explicit account selection (or equivalent binding rule).

## Legacy Notes

- None used as authority.
