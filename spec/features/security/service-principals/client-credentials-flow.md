---
id: feature.security.service-principals.client-credentials-flow
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuthTokenController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/dto/OAuthTokenDTOs.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/ServicePrincipalService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/service/ServiceTokenClient.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/authorization/PermissionChecker.kt
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_11__create_principals_tables.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_15__add_service_credentials.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/OAuthTokenControllerIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/ServicePrincipalIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/ServicePrincipalServiceTest.kt
    - workers/golang/shared/auth/token_client_test.go
  frontend: []
  mobile: []
  workers:
    - workers/golang/shared/auth/token_client.go
legacy_refs: []
related_specs:
  - feature.security.service-principals.service-registration-and-credentials
  - feature.security.oauth2-authorization-server.token-grants-refresh-exchange
  - feature.security.authorization-runtime.claim-based-enforcement
  - feature.security.session-tokens.auth-context-and-jwt-claims
schema: spec-metadata-v1
---

# Service Principals — Client Credentials Flow (S2S)

## Boundary (do not confuse)

| Surface | Path | Purpose |
|---|---|---|
| **This flow** | `POST /oauth/token` | Machine S2S client credentials |
| OAuth2 AS (agents) | `POST /oauth2/token` | Auth code / refresh / **token exchange** |
| App IdP | GraphQL `signIn` / `signInWithOAuth` | Human login |

## Behavior

1. `OAuthTokenController` accepts JSON `TokenRequest`: `grant_type` must be `client_credentials`; requires `client_id` + `client_secret`; optional `audience`, `delegated_by`.
2. Validates via `ServicePrincipalService.validateServiceCredentials` (SERVICE principal by `external_id` = serviceId, enabled, secret hash verify). Invalid → 401; disabled → 403 (controller also re-checks enabled).
3. Loads permission **names** from `principal_permissions` → `permissions` table (`getServicePermissions`).
4. Issues JWT via `JwtTokenIssuer.generateServiceToken`:
   - `sub` = principal UUID (not the string serviceId)
   - `type` = `service`
   - `permissions` = service permission list
   - `aud` = request `audience` or defaults to `client_id`
   - optional `delegated_by`
   - standard iss/iat/exp (access token TTL)
5. Response: `{ access_token, token_type: Bearer, expires_in }` — **no refresh token**.

### Consumers (not authority)

- Kotlin `common.service.ServiceTokenClient.getServiceToken` → `POST {security.service.url}/oauth/token` with configured `security.service.id` / `secret`, caches by audience (+ delegated_by), 60s expiry buffer.
- Go workers `workers/golang/shared/auth/token_client.go` → same endpoint/grant; fixed audience at construction; cache with 60s buffer.
- Downstream GraphQL/HTTP uses claim-based `PermissionChecker` on `permissions` (or `user_permissions` when token has user context — see below).

### Not this endpoint — user-context / exchange

- `ServiceTokenClient.getServiceTokenWithUserContext` calls **`POST /oauth2/token`** with grant `urn:ietf:params:oauth:grant-type:token-exchange` (AS path). Issues `generateServiceTokenWithUserContext` (`user_id`, `user_permissions`, optional `current_account`). Documented fully under OAuth2 AS token grants; cited here only so consumers are not mistaken for `/oauth/token`.

## Invariants

- Only `client_credentials` on `/oauth/token`.
- Permissions embedded at issue time from DB; later `updateServicePermissions` does not rewrite live JWTs until re-fetch.
- Workers/Kotlin clients are helpers — security service remains source of truth for validation and issuance.

## Permissions

Backend enforcement:

- Credential validity + enabled principal before issue.
- Downstream: claim check of embedded `permissions` (service) or `userPermissions` (exchanged token).

UX hints: none (machine API).

## Data And Contracts

- HTTP DTOs only (no GraphQL for this flow).
- Bruno: `Get OAuth token (JWT) by Client secret.bru` (hardcoded example secret — may drift from register flow vars).

## Impact Surface

- Backend: OAuthTokenController, ServicePrincipalService, JwtTokenIssuer
- Common: ServiceTokenClient (active package `common.service`)
- Workers: Go TokenClient
- Tests: OAuthTokenController IT, ServicePrincipal unit/IT, Go token_client_test
- Bruno: partial (token; register separate)

## Decisions

- Audience is caller-chosen (or client_id default); callers must request the audience the target service expects.
- Pure service tokens use principal UUID as `sub`; human identity is never on this grant.
- Canonical Kotlin consumer is `common.service.ServiceTokenClient` (legacy duplicate under `common.security.service` removed).

## Known Gaps

- Bruno token request uses a fixed secret string instead of always chaining vars from register request.
- Go client does not implement token-exchange / user-context path (Kotlin client does via `/oauth2/token`).
- No GraphQL surface for S2S token (by design).
- Rotate / update permissions / enable-disable of service principals: service methods exist but **no HTTP** — accepted gap for now (ops not needed today); see registration spec.

## Legacy Notes

- None used as authority.
