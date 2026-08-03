---
id: feature.security.oauth2-authorization-server.token-grants-refresh-exchange
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2TokenController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenExchangeService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/RefreshTokenService.kt
    - service/kotlin/mcp/src/main/kotlin/io/github/salomax/neotool/mcp/security/McpAuthenticationService.kt
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_36__add_oauth2_refresh_token_grant_metadata.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_40__add_account_id_to_refresh_tokens.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2TokenServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2TokenExchangeServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/OAuth2AuthorizationServerIntegrationTest.kt
    - web/tests/e2e/mcp-oauth.e2e.spec.ts
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.oauth2-authorization-server.authorize-consent-login-flow
  - feature.security.oauth2-authorization-server.scopes-to-permissions-mapping
  - feature.security.oauth2-authorization-server.oauth2-rate-limits
  - feature.security.session-tokens.access-and-refresh-tokens
  - feature.security.service-principals.client-credentials-flow
schema: spec-metadata-v1
---

# OAuth2 AS — Token Grants / Refresh / Exchange

## Boundary

- Endpoint: `POST /oauth2/token` (`OAuth2TokenController`) — **not** `POST /oauth/token` (S2S client credentials).

## Behavior

Supported `grant_type` values in `OAuth2TokenService.issue`:

1. **`authorization_code`**
   - Requires `code`, `client_id`, `redirect_uri`, `code_verifier` (PKCE S256).
   - Code hashed lookup; already-used code revokes associated refresh family then fails.
   - Issues OAuth access JWT via `JwtTokenIssuer.generateOAuthAccessToken` (`type=access`, `client_id`, `scope`, `permissions`=granted scopes, `aud`=resource, `current_account`).
   - Creates OAuth refresh token with client/scopes/resource/account metadata (`createOAuthRefreshToken`).

2. **`refresh_token`**
   - Must be an OAuth-bound refresh (has clientId); rotates via shared `RefreshTokenService.refreshAccessToken` (OAuth branch re-issues OAuth-shaped access token).

3. **`urn:ietf:params:oauth:grant-type:token-exchange`** (`OAuth2TokenExchangeService`)
   - Confidential **service principal** credentials (`client_id` + `client_secret`) — validates via `ServicePrincipalService`, not DCR public clients.
   - Subject must be access token with audience matching configured service resource; requires `jti`, `current_account`, `scope`.
   - Maps scopes → banking permissions; intersects with user’s actual RBAC permissions; issues **service token with user context** (`generateServiceTokenWithUserContext`) for target `audience`.
   - Access-token revocation checker is currently `NoOp` (always false) in security; MCP maintains its own connection revocation store for inbound MCP calls.

Token endpoint applies per-client rate limit before grant handling (see rate-limits spec).

## Invariants

- Authorization code single-use; PKCE verifier must match challenge.
- OAuth access tokens carry scopes in `permissions` claim until exchange translates to banking permission strings.
- Exchange never expands permissions beyond intersection(mapped scopes, user’s RBAC).

## Permissions

Backend enforcement:

- Grant validation + PKCE + client/redirect binding.
- Exchange: service credentials + subject audience + scope→permission intersection.
- MCP (dep): validates bearer access token type, audience, `client_id`, `current_account`, connection revocation — see MCP module as consumer evidence only.

UX hints: none (HTTP API).

## Data And Contracts

- HTTP JSON/form token request/response DTOs.
- Refresh token rows carry OAuth metadata (V0_36/V0_40).

## Impact Surface

- Backend: OAuth2TokenService/ExchangeService, JwtTokenIssuer, RefreshTokenService
- MCP: `McpAuthenticationService` consumes AS access tokens
- Tests: unit + AS integration + mcp-oauth E2E
- Bruno: no `/oauth2/token` collection (has S2S `/oauth/token` separately)

## Decisions

- Token exchange is the bridge from user-granted OAuth scopes to service-delegated banking permissions.
- Shared refresh table for first-party and OAuth grants (distinguished by clientId metadata).

## Known Gaps

- **Deferred — exchange revocation window:** `NoOpOAuth2AccessTokenRevocationChecker` always returns false. After Connected Agents revoke, MCP inbound calls fail and OAuth refresh is revoked, but `token-exchange` may still accept a non-expired OAuth access JWT as `subject_token` until that JWT expires. Treat later: wire exchange to MCP/security revocation (or short-lived access + introspection) so revoke closes this path immediately. Tracked also under `feature.security.connected-agents.revoke-connection`.
- Bruno does not document AS token grants (risk of confusing with S2S Bruno request).

## Legacy Notes

- None used as authority.
