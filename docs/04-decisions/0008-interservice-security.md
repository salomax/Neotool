---
title: ADR-0008 Interservice Security
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/service/ServiceTokenClient.kt
  - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuthTokenController.kt
  - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
  - workers/golang/shared/auth/token_client.go
load_when:
  - calling another service with service identity
  - propagating delegated service tokens
supersedes:
schema: docs-metadata-v1
---

# ADR-0008: Interservice Security

Purpose: record the durable decision and consequences for interservice security.

## Status

accepted

## Context

Services must authenticate to each other with least privilege, including pure service calls and delegated user/agent context, across Kotlin and Go workers.

## Decision

Use JWT service tokens over TLS via the security service client-credentials token endpoint.

- Service principals and permissions are issued by security (`JwtTokenIssuer.generateServiceToken`).
- Callers obtain tokens through `ServiceTokenClient` (Kotlin) or `workers/golang/shared/auth` (Go).
- Tokens carry audience and permissions; delegated flows may include `delegatedBy`.
- Mesh mTLS is optional hardening, not the baseline.

## Consequences

- Implemented baseline replaces the legacy ADR status `proposed`.
- The companion migration-plan file is archived (not an ADR).
- ADR-0017 builds on this for MCP→banking provenance.

## Evidence

- `ServiceTokenClient.getServiceToken` caches audience-scoped tokens.
- `OAuthTokenController` serves `/oauth` client-credentials.
- Go `TokenClient` mirrors client-credentials for workers.

## Related

- [README](README.md)
- [ADR-0017](0017-mcp-direct-banking-dispatch-for-provenance.md)
- [Security architecture](../01-architecture/README.md)
