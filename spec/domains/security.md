---
id: domain.security
status: canonical
kind: domain
source_of_truth: code
last_verified: 2026-08-02
pilot: complete
schema: spec-metadata-v1
---

# Security

**Pilot domain:** reverse-spec feature tree under `spec/features/security/` is complete (batches 1–5). Index: `spec/features/security/README.md`.

## Boundary

Owns human and machine identity, authentication, session/token lifecycle, JWT claims (permissions, account context), RBAC catalog administration, runtime access checks, OAuth2 authorization server for agents/MCP, service principals, tenant account membership, and connected-agent consent management.

Primary code owners:

- `service/kotlin/security/`
- `service/kotlin/common/.../security/` (shared JWT principal, claim checks — cite under feature specs; no `platform/` folder)
- `service/kotlin/common/.../service/ServiceTokenClient.kt` (canonical S2S token client)
- `contracts/graphql/subgraphs/security/`
- `web/` auth, authorization settings, account-membership, connected-agents
- `workers/golang/shared/auth/` (client credentials consumer)

## Not In Scope

- Banking/financial `account` entities and product eligibility rules
- Full MCP product behavior (`service/kotlin/mcp/` specs live outside security; MCP auth/revoke/activity are **evidence/deps** only)
- Generic user preferences (`theme`, `blurBalances`, locale UI) — not a security subdomain; `keepMeConnected` is covered under sign-in / web-client-session (no separate `remember-me-preference.md` unless needed later)
- Mobile (no relevant implementation yet)
- Legacy `removed historical docs` (historical only)

## Subdomains

| Subdomain | Responsibility |
|---|---|
| `authentication` | Sign-in/up, IdP OAuth (Google), password reset, email verification |
| `session-tokens` | Access/refresh JWT, AuthContext/claims, JWKS/keys, web client session |
| `identity-users` | User lifecycle/profile (enable/disable/update/avatar) — separate from RBAC admin |
| `rbac-administration` | Groups/roles/permissions catalog CRUD and admin UI; technical owner of seed/catalog |
| `authorization-runtime` | Request enforcement (claim-based primary; hybrid RBAC+ABAC internal/partial), audit, UX permission gates |
| `tenant-accounts` | Workspace `security.accounts` + membership invites/roles/policy (not banking accounts) |
| `oauth2-authorization-server` | OAuth2 AS for agents: authorize/consent/token/DCR/exchange/scopes→permissions |
| `service-principals` | Client-credentials S2S (`/oauth/token`), service registration |
| `connected-agents` | Sibling of AS: list/revoke consents + activity via MCP |

Permission ownership: each product domain owns the **semantic meaning** of its permission strings; security owns **technical catalog, Flyway seeds, and administration**.

ABAC: **internal/partial infrastructure**, not a fully shipped product feature (no GraphQL/admin surface).

## Official Feature Tree

```text
spec/features/security/
  authentication/
    sign-in.md
    sign-up.md
    oauth-identity-providers.md
    password-reset.md
    email-verification.md
  session-tokens/
    access-and-refresh-tokens.md
    auth-context-and-jwt-claims.md
    jwks-and-signing-keys.md
    web-client-session.md
    # remember-me-preference.md — optional; keepMeConnected folded into sign-in + web-client-session
  identity-users/
    user-lifecycle.md
    user-profile-avatar.md
  rbac-administration/
    permission-catalog.md
    group-role-permission-management.md
    admin-settings-ui.md
  authorization-runtime/
    claim-based-enforcement.md
    hybrid-rbac-abac.md                # internal/partial
    authorization-audit.md
    frontend-permission-gates.md
  tenant-accounts/
    account-model.md
    membership-invites.md
    membership-policy-matrix.md
    account-claims-in-token.md
  oauth2-authorization-server/
    authorize-consent-login-flow.md
    token-grants-refresh-exchange.md
    dynamic-client-registration.md
    scopes-to-permissions-mapping.md
    oauth2-rate-limits.md
  service-principals/
    client-credentials-flow.md
    service-registration-and-credentials.md
  connected-agents/
    list-connected-agents.md
    revoke-connection.md
```

## Source Entrypoints

- Backend: `service/kotlin/security/src/main/`; shared `service/kotlin/common/src/main/kotlin/.../security/` + `.../service/ServiceTokenClient.kt`
- GraphQL: `contracts/graphql/subgraphs/security/schema.graphqls` (+ service copy under `security/src/main/resources/graphql/`)
- HTTP AS / S2S: `security/.../http/OAuth2*`, `OAuthTokenController`, `JwksController`, `ServiceRegistrationController`
- Database: `security/src/main/resources/db/migration/`
- Tests: `security/src/test/`; web E2E `authorization`, `mcp-oauth`, `account-membership`, `connected-agents`; Bruno `contracts/api-collections/.../Security/`
- Frontend: `web/src/app/(authentication)/`, `(settings)/authorization/`, `features/account-membership/`, `features/connected-agents/`, `shared/providers/Auth*`, `shared/components/authorization/`
- Workers: `workers/golang/shared/auth/token_client.go`
- MCP evidence (not security specs): `service/kotlin/mcp/.../security/`

## Cross-Domain Dependencies

- **banking/transactions** — permission strings + OAuth scopes `finance:*` → `banking:*`
- **mcp** — token audience, revoke, recentActivity (deps for AS / connected-agents)
- **comms** — invitation / password-reset / verification email delivery
- **assets / comms / assistant / financialdata** — own permission semantics; security seeds/admin
- **all Kotlin services + workers** — consume JWT / service tokens via `common` (`PermissionChecker`, `ServiceTokenClient`, Go `token_client`)
- **feature flags** — `nt_auth_session` cookie is Unleash context, not auth enforcement

## Known Gaps

- Bruno lagging GraphQL (membership, connected-agents, preferences)
- Dual OAuth surfaces: IdP `oauth` vs AS `oauth2` vs S2S `/oauth/token` (intentional separation)
- Dual checkers: `PermissionChecker` (@Primary, claims) vs `AuthorizationManager` (DB hybrid; not GraphQL wiring path)
- ABAC tables/services without product API/UI
- “Account” naming collision (tenant vs banking)
- Web route guard optimistic only (`auth_hint`); tokens in web storage
- Flyway comments still point at obsolete `removed historical docs`
- **Deferred (agents):** AS `token-exchange` uses `NoOpOAuth2AccessTokenRevocationChecker` — Connected Agents revoke does not immediately block exchange of a still-valid OAuth access JWT (see token-grants + revoke-connection)
- **Deferred (agents):** OAuth scope catalog split — Kotlin `OAuth2Scopes` vs YAML `supported-scopes`; drift `finance:bills:write` missing from YAML (see scopes-to-permissions-mapping)
- **Access control:** `checkPermission` two-segment regex vs three-segment catalog — [#585](https://github.com/invistus/invistus/issues/585)
- **Access control:** frontend JSDoc examples still cite obsolete `authorization:user:*` strings
- **Access control:** authorization audit default-off; no GraphQL/UI; claim denials not audited (**by design** — hybrid/admin path only)
- **Access control:** `security:user:delete` seeded without `deleteUser` mutation — keep; implement later
- **Machine auth:** rotate / update permissions / enable-disable exist in service layer but **no HTTP** — **accepted** (no ops need today)
- **Machine auth:** canonical S2S client is `common.service.ServiceTokenClient` (legacy `common.security.service` duplicate removed)

## Open Questions

- Whether a dedicated `remember-me-preference.md` is ever needed (currently folded into sign-in + web-client-session)
- Whether `common/security` evidence paths should be duplicated per feature or summarized once in domain intros (practice: cite under each feature as needed)

## Reverse-spec batches (completed)

1. Foundation — `authentication/*` + `session-tokens/*`
2. Tenant — `tenant-accounts/*`
3. Agents — `oauth2-authorization-server/*` + `connected-agents/*`
4. Access control — `authorization-runtime/*` + `rbac-administration/*` + `identity-users/*`
5. Machine auth — `service-principals/*`

Pilot complete: no further reverse-spec batches planned for this domain unless code gains a new subdomain.

## Reverse Spec Notes

- Backend + contracts are authoritative; frontend PermissionGate / claims are UX hints only.
- Do not load `removed historical docs` as truth.
- Keep AS (`oauth2`) separate from app IdP login (`oauth`) and S2S (`/oauth/token`) in every spec.
- Load this domain file first, then only the batch/feature specs needed for the task.
- GraphQL request path injects `@Primary PermissionChecker` (JWT claims); `AuthorizationManager` is not the injected enforcement path.
