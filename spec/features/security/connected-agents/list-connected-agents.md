---
id: feature.security.connected-agents.list-connected-agents
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/connectedagents/ConnectedAgentsService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/ConnectedAgentsResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/repo/OAuth2ConsentRepository.kt
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
  - feature.security.connected-agents.revoke-connection
  - feature.security.oauth2-authorization-server.authorize-consent-login-flow
  - feature.security.oauth2-authorization-server.scopes-to-permissions-mapping
schema: spec-metadata-v1
---

# Connected Agents — List

## Boundary

- Sibling subdomain of OAuth2 AS: management UI/API over **existing consents**, not authorize/token endpoints.
- MCP is a **dependency** for `recentActivity` only — do not treat MCP internals as security authority.

## Behavior

1. GraphQL query `connectedAgents(accountId: ID!)` lists non-revoked OAuth2 consents for that **tenant account**.
2. `ConnectedAgentsService.connectedAgents`:
   - **OWNER + ACTIVE membership** on `accountId` (`requireOwner`) — ADMIN/MEMBER denied.
   - Loads all account consents with `revokedAt IS NULL` ordered by `lastGrantedAt` desc (not filtered to actor’s own userId).
   - Enriches with client display name (`OAuth2ClientRegistry`) and scope descriptions.
3. Field `recentActivity(first)`: security queries MCP GraphQL `recentActivity` with connection’s `userId` + `clientId`. MCP GraphQL errors → hard failure (`IllegalStateException`); missing data → empty list.

## Invariants

- `connectionId` = consent row id; one row per `(user, client, account)` grant surface used by AS.
- Tenant `accountId` matches consent binding from AS consent flow.

## Permissions

Backend enforcement:

- OWNER-only for list and nested activity.
- Standard authenticated GraphQL principal required.

UX hints:

- Web section under profile/settings; confirm dialogs and toasts are UX only — OWNER gate is backend.

## Data And Contracts

- GraphQL: `connectedAgents`, `ConnectedAgent`, `recentActivity` field (security schema).
- DB: `oauth2_consents`.
- MCP: activity query only (evidence dep).

## Impact Surface

- Backend: ConnectedAgentsService, ConnectedAgentsResolver, consent repo
- GraphQL contract: security schema
- Web: `web/src/features/connected-agents/`
- Tests: unit + GraphQL integration + E2E + component tests
- Bruno: none for connectedAgents

## Decisions

- List is consent-centric (security); activity is MCP overlay.
- OWNER sees every connection on the account (any granting user).

## Known Gaps

- No Bruno coverage.
- MCP activity failures surface as server errors (not soft-empty).

## Legacy Notes

- None used as authority.
