---
id: features.security
domain: security
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-02
pilot: complete
schema: spec-metadata-v1
---

# Security Features

Domain index: [`spec/domains/security.md`](../../domains/security.md).

**Pilot status:** reverse-spec batches 1–5 are complete. Load the domain file first, then only the feature files needed for the task.

Optional not created: `session-tokens/remember-me-preference.md` — `keepMeConnected` is documented in `authentication/sign-in.md` and `session-tokens/web-client-session.md`.

Section order matches the official tree in `spec/domains/security.md`.

## Authentication

- `authentication/sign-in.md`
- `authentication/sign-up.md`
- `authentication/oauth-identity-providers.md`
- `authentication/password-reset.md`
- `authentication/email-verification.md`

## Session tokens

- `session-tokens/access-and-refresh-tokens.md`
- `session-tokens/auth-context-and-jwt-claims.md`
- `session-tokens/jwks-and-signing-keys.md`
- `session-tokens/web-client-session.md`

## Identity users

- `identity-users/user-lifecycle.md`
- `identity-users/user-profile-avatar.md`

## RBAC administration

- `rbac-administration/permission-catalog.md`
- `rbac-administration/group-role-permission-management.md`
- `rbac-administration/admin-settings-ui.md`

## Authorization runtime

- `authorization-runtime/claim-based-enforcement.md`
- `authorization-runtime/hybrid-rbac-abac.md` (internal/partial)
- `authorization-runtime/authorization-audit.md`
- `authorization-runtime/frontend-permission-gates.md`

## Tenant accounts

`security.accounts` (tenant workspaces — not banking accounts):

- `tenant-accounts/account-model.md`
- `tenant-accounts/membership-invites.md`
- `tenant-accounts/membership-policy-matrix.md`
- `tenant-accounts/account-claims-in-token.md`

## OAuth2 Authorization Server

Agents/MCP — **not** app Google IdP, **not** S2S `/oauth/token`:

- `oauth2-authorization-server/authorize-consent-login-flow.md`
- `oauth2-authorization-server/token-grants-refresh-exchange.md`
- `oauth2-authorization-server/dynamic-client-registration.md`
- `oauth2-authorization-server/scopes-to-permissions-mapping.md`
- `oauth2-authorization-server/oauth2-rate-limits.md`

## Service principals

Machine auth — **S2S `/oauth/token`**, not AS `/oauth2/token`, not human IdP:

- `service-principals/client-credentials-flow.md`
- `service-principals/service-registration-and-credentials.md`

## Connected Agents

Sibling of OAuth2 AS (consents list/revoke + MCP activity dep):

- `connected-agents/list-connected-agents.md`
- `connected-agents/revoke-connection.md`

## Reverse-spec history

Completed in order: (1) authentication + session-tokens → (2) tenant-accounts → (3) oauth2-authorization-server + connected-agents → (4) authorization-runtime + rbac-administration + identity-users → (5) service-principals.
