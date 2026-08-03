---
id: feature.security.oauth2-authorization-server.scopes-to-permissions-mapping
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2Scopes.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2ScopePermissionMapper.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2ScopeDescriptionService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenExchangeService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
  graphql: []
  database: []
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2ScopePermissionMapperTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2ScopeDescriptionServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2TokenExchangeServiceTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.oauth2-authorization-server.token-grants-refresh-exchange
  - feature.security.oauth2-authorization-server.authorize-consent-login-flow
schema: spec-metadata-v1
---

# OAuth2 AS — Scopes To Permissions Mapping

## Behavior

- Catalog of OAuth scopes is `OAuth2Scopes` (finance-oriented strings such as `finance:transactions:read|write`, cashflow, accounts, bills, categories, rules).
- `DefaultOAuth2ScopePermissionMapper` maps each scope → one or more **banking** permission strings, e.g.:

```16:38:service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2ScopePermissionMapper.kt
    private val scopePermissions =
        mapOf(
            OAuth2Scopes.FINANCE_CASHFLOW_READ to setOf("banking:transaction:view"),
            OAuth2Scopes.FINANCE_TRANSACTIONS_READ to setOf("banking:transaction:view"),
            OAuth2Scopes.FINANCE_TRANSACTIONS_WRITE to
                setOf(
                    "banking:transaction:create",
                    "banking:transaction:update",
                    "banking:transaction:delete",
                ),
            OAuth2Scopes.FINANCE_ACCOUNTS_READ to setOf("banking:account:view"),
            // ... bills/categories/rules → banking:transaction:* or banking:category:*
        )
```

- Startup validates mapper covers exactly `OAuth2Scopes.all` (no missing/unknown/empty).
- **Authorize / user access token:** stores granted OAuth scopes; `generateOAuthAccessToken` puts those scopes into both `scope` and `permissions` claims (not banking strings yet).
- **Token exchange:** maps scopes → banking permissions, intersects with user’s actual RBAC permissions, embeds intersection as `userPermissions` on delegated service token; scopes without any intersecting permission are dropped from effective scope list.
- Consent UI descriptions: `OAuth2ScopeDescriptionService` (locale properties), not the banking permission names.

## Invariants

- Semantic ownership of `banking:*` permissions remains the banking/transactions domain; security owns the OAuth scope catalog + mapping table used by the AS.
- Mapping is fail-fast at boot if incomplete.

## Permissions

Backend enforcement:

- Exchange-time intersection prevents granting banking permissions the user does not have via RBAC.
- Downstream banking APIs still enforce claim/permission checks on the exchanged token.

UX hints:

- Human-readable scope descriptions on consent / connected-agents UI.

## Data And Contracts

- Code catalog + mapper (no GraphQL).
- AS metadata `scopes_supported` comes from config (should align with catalog).

## Impact Surface

- Backend: OAuth2Scopes, mapper, descriptions, token exchange
- Tests: mapper/description/exchange unit tests
- Cross-domain: banking permission strings

## Decisions

- Delay permission translation until token exchange so user access tokens remain scope-shaped for MCP resource audience.

## Known Gaps

- Only finance/banking scopes exist today; no investment/comms scopes in catalog.
- **Deferred — dual scope lists drift:** authorize/mapper use Kotlin `OAuth2Scopes`; metadata + DCR use YAML `oauth.authorization-server.metadata.supported-scopes`. Today `finance:bills:write` is in `OAuth2Scopes` / mapper but **missing** from `application.yml` `supported-scopes`, so discovery/DCR under-advertise it. Treat later: single source of truth (codegen, boot assert YAML ⊆/== `OAuth2Scopes.all`, or drive metadata from the Kotlin catalog).

## Legacy Notes

- None used as authority.
