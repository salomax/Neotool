---
id: feature.security.connected-agents.revoke-connection
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/connectedagents/ConnectedAgentsService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/ConnectedAgentsResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/RefreshTokenService.kt
    - service/kotlin/mcp/src/main/kotlin/io/github/salomax/neotool/mcp/security/McpAuthenticationService.kt
    - service/kotlin/mcp/src/main/kotlin/io/github/salomax/neotool/mcp/security/ConnectionRevocationStore.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_38__create_oauth2_consents.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/ConnectedAgentsServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/ConnectedAgentsGraphQLIntegrationTest.kt
    - web/tests/e2e/connected-agents.e2e.spec.ts
    - web/src/features/connected-agents/components/__tests__/ConnectedAgentsSection.test.tsx
  frontend:
    - web/src/features/connected-agents/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.connected-agents.list-connected-agents
  - feature.security.oauth2-authorization-server.token-grants-refresh-exchange
schema: spec-metadata-v1
---

# Connected Agents — Revoke Connection

## Boundary

- Security owns consent revoke + OAuth refresh family revoke for the connection.
- MCP owns connection-level access-token revocation store used when MCP authenticates inbound agent calls (dep evidence only).

## Behavior

1. GraphQL mutation `revokeConnectedAgent(connectionId: ID!)` (security).
2. `ConnectedAgentsService.revokeConnectedAgent` (OWNER of consent’s account):
   1. Fan-out MCP mutation `revokeConnection(userId, clientId, accountId)` first — MCP GraphQL errors abort the whole revoke (consent/refresh untouched).
   2. `RefreshTokenService.revokeOAuthTokensForConnection` for that user/client/account using MCP `revokedAt`.
   3. Persist consent `revokedAt` / `updatedAt` so the connection leaves the list.
3. Effects: refresh grants for that connection stop; MCP rejects inbound agent calls for revoked connection; AS access JWTs remain cryptographically valid until expiry — MCP store enforces at MCP edge; AS token-exchange uses `NoOpOAuth2AccessTokenRevocationChecker` (see token grants).

## Invariants

- Revoke is consent-id addressed but account-OWNER gated.
- Ordering is MCP-first then security persistence (no security-only revoke if MCP fails).

## Permissions

Backend enforcement: OWNER-only revoke.

UX hints: web confirm dialog + toast — not a substitute for backend.

## Data And Contracts

- GraphQL: `revokeConnectedAgent` → `RevokeConnectedAgentPayload`.
- DB: consent `revokedAt`.
- MCP: `revokeConnection` mutation (dependency).

## Impact Surface

- Backend security: ConnectedAgentsService / resolver / RefreshTokenService
- Backend MCP: ConnectionRevocationStore / McpAuthenticationService (consumer)
- Web: connected-agents revoke UX
- Tests: unit + GraphQL + E2E + component
- Bruno: none

## Decisions

- Dual revoke (MCP + refresh + consent) because MCP validates access tokens against its revocation store, while refresh lives in security.

## Known Gaps

- **Deferred — exchange revocation window:** revoke closes MCP access + OAuth refresh + consent, but AS `token-exchange` still uses `NoOpOAuth2AccessTokenRevocationChecker` (does not read MCP revoke store). A non-expired OAuth access JWT can still be exchanged until expiry. Same deferred item as `feature.security.oauth2-authorization-server.token-grants-refresh-exchange`.
- No Bruno coverage.
- If MCP succeeds and security persistence fails mid-transaction, operational inconsistency risk (validate against `@Transactional` + tests).

## Legacy Notes

- None used as authority.
