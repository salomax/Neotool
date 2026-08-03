---
id: feature.security.oauth2-authorization-server.dynamic-client-registration
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2ClientRegistrationController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2ClientRegistrationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2ClientRegistry.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/job/oauth2/OAuth2ClientGarbageCollectionJob.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/AuthorizationServerMetadataController.kt
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_37__create_oauth2_clients.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2ClientRegistrationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2ClientGarbageCollectionJobTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/OAuth2AuthorizationServerIntegrationTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.oauth2-authorization-server.authorize-consent-login-flow
  - feature.security.oauth2-authorization-server.scopes-to-permissions-mapping
schema: spec-metadata-v1
---

# OAuth2 AS — Dynamic Client Registration

## Boundary

- HTTP `POST /oauth2/register` (public DCR for MCP/agent public clients).
- **Not** `ServiceRegistrationController` / service principal credentials (S2S).

## Behavior

- Open registration (no auth gate on controller); records `createdFromIp`.
- Creates ACTIVE client with generated `client_id` (`dcr_…`); **public clients only** (`token_endpoint_auth_method` must be `none`).
- Validates:
  - `redirect_uris` required; https except localhost/127.0.0.1 http
  - scopes ⊂ configured supported scopes (empty → all supported)
  - resources ⊂ configured supported resources (empty → all supported); absolute URIs
  - grant_types default `authorization_code` + `refresh_token` only
  - response_types default/must be `code`
  - optional `client_name` pattern; optional logo/client URIs stored but not fetched (SSRF note in code)
- Registry used at authorize/token (`OAuth2ClientRegistry.find`); `markUsed` updates last-used.
- Garbage collection job exists for unused DCR clients (`OAuth2ClientGarbageCollectionJob`).
- Advertised as `registration_endpoint` in AS metadata.

## Invariants

- No client secret issued for DCR clients.
- Only code + refresh grants allowed at registration.

## Permissions

Backend enforcement: input validation + configured allowlists for scopes/resources.

UX hints: none (HTTP). Agents/MCP SDKs call register.

## Data And Contracts

- HTTP DTOs `OAuth2ClientRegistrationRequest/Response`.
- DB `security.oauth2_clients` (V0_37).
- No GraphQL.

## Impact Surface

- Backend: registration controller/service, client registry, GC job
- Tests: registration unit + GC unit + AS integration
- Bruno: none for `/oauth2/register`

## Decisions

- Public-client DCR tailored for MCP/local redirect URIs.
- Scope/resource defaults fill from AS metadata config when omitted.

## Known Gaps

- Open registration without auth/rate limit at DCR endpoint itself (token endpoint has rate limits separately).
- No Bruno coverage for DCR.

## Legacy Notes

- None used as authority.
