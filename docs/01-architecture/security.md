---
title: Security Architecture
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/security
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/authorization/PermissionChecker.kt
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/service/ServiceTokenClient.kt
  - web/src/shared/providers/AuthorizationProvider.tsx
  - service/kotlin/security/src/main/resources/templates/oauth2-login
load_when:
  - implementing authn/authz
  - adding service principals or OAuth surfaces
  - reviewing permission enforcement
supersedes:
  - docs/09-security/
schema: docs-metadata-v1
---

# Security Architecture

Purpose: summarize authentication, authorization, and interservice security boundaries.

## Context

Security is centered on the `security` Micronaut service (identity, OAuth2, tokens, RBAC administration) with shared JWT/permission utilities in `common`.

## Authority

- `service/kotlin/security` module and oauth templates.
- `PermissionChecker`, `ServiceTokenClient`, web `AuthorizationProvider`.
- ADRs 0006, 0008, 0015–0017.

## Rules

1. Backend enforcement is mandatory for protected operations; UI `PermissionGate` is not authority.
2. User sessions use issued JWTs; validate permissions from claims via `PermissionChecker` unless a security-module check is required.
3. Service callers use client-credentials tokens from security (`ServiceTokenClient` / Go token client).
4. OAuth login and consent HTML are rendered by `security` templates on the auth origin (ADR-0016).
5. Agent/MCP provenance is trusted only on internal delegated service hops (ADR-0017).
6. Secrets and vault procedures belong in runbooks; do not embed credentials in docs.

## Related

- [Service boundaries](service-boundaries.md)
- [ADR-0008](../04-decisions/0008-interservice-security.md)
- [ADR-0016](../04-decisions/0016-oauth-login-consent-ui-rendering.md)
- [Vault runbook](../05-runbooks/README.md)
