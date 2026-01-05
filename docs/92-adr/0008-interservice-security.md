---
title: ADR-0008 Interservice Security (Polyglot Microservices)
type: adr
category: security
status: proposed
version: 2.1.0
tags: [security, jwt, mtls, service-to-service, kotlin, python, permissions]
related:
  - adr/0003-kotlin-micronaut-backend.md
  - adr/0006-frontend-authorization-layer.md
---

# ADR-0008: Interservice Security (Polyglot Microservices)

## Status
Proposed

## Context

Neotool is evolving to polyglot microservices (Kotlin/Micronaut, Python, others) and needs consistent, robust interservice security for two modes:
1. **Service-to-service (no user context)**: only the calling service identity matters.
2. **User context propagation**: a user-authenticated call reaches Service A, which calls Service B and must convey both its own identity and the user identity.

Key constraints:
- Strong caller authentication; least privilege per service.
- Authorize by permissions/claims and propagate user identity when present (aligned with the existing security module).
- Polyglot-friendly; no hard dependency on a service mesh (mesh-compatible later).
- Minimal coupling and operational overhead; new services onboard easily.
- IdP-agnostic (Google, Okta, etc.) with TLS for transport.

## Decision (to be finalized)

- **Baseline**: JWT over TLS for service identity and authorization, with user-context propagation when present.
- **Optional hardening**: Mesh-managed mTLS (SPIFFE/SPIRE) for service identity once/if a mesh is adopted; not required for initial rollout.

## Options (trade-offs)

| Option | What it uses | Pros (AAA/common) | Cons / Risks |
| --- | --- | --- | --- |
| JWT over TLS (baseline) | IdP-issued JWT (client credentials) + TLS | Polyglot-friendly; rich claims; works with user propagation/OBO; standard in large estates | Needs IdP/JWKS; correct `aud`/claims required; bearer relies on TLS |
| JWT over TLS + mesh mTLS | Baseline plus mutual TLS via mesh/SPIFFE | Defense-in-depth; constrains which workloads reach listeners; common in high-assurance | Dual lifecycle (certs + tokens); mesh ops overhead |
| Mesh mTLS only | Mesh-issued cert identity (no bearer) | Strong transport auth; auto-rotation in mesh | No user propagation channel; limited metadata; mesh dependency |
| API Gateway-only JWT | Gateway enforces; internals mostly trusted | Simple ingress model | Weak east-west trust; ad-hoc propagation; not zero-trust |
| HMAC per request | Shared-secret signatures | Lightweight deps | Key distribution/rotation pain; poor user propagation; limited metadata |

## Authorization and data model

- **Principals**: Keep service principals (and optionally users) in a `principals` table tracking identity, status, and credential binding (client credential or cert reference).
- **Assignments**: Reuse the existing permission vocabulary. Prefer a unified assignment table such as `principal_permissions(principal_id, principal_type ENUM('user','service'), permission_id, resource_pattern)` rather than a separate service-only permissions table. If a dedicated `service_permissions` table is retained, permission keys must remain identical to user permission keys to avoid drift.
- **Issuer truth**: The token issuer computes user permissions/claims from the IdP/session; it should NOT accept `user_permissions` from caller input (avoid confused-deputy).

## Token profile (provider-agnostic)

- **Service token (no user)**: `iss`, `sub` = service id, `aud` = target service, `type` = `service`, `permissions` (service permissions), `iat`/`exp`.
- **With user context**: Add `user_id` (or subject) and a `user_permissions`/`roles` claim computed by the issuer from the user’s session/IdP. Prefer OBO where the IdP mints a token for the downstream audience; fallback is forwarding the inbound user token with audience/scope checks in the callee.
- **Transport**: Always TLS. mTLS can later add possession proof for workloads via mesh.
- **IdP**: Must support client credentials and JWKS; choice can vary (Google, Okta, etc.).

## Implementation outline (summarized; code/schema live in a separate spec)

1. Define the JWT profile (claims, `type=service`, audience rules, permission claim format) compatible with the current security module.
2. Add service principals to the identity store; adopt unified `principal_permissions` (or, if required, keep `service_permissions` but reuse the same permission keys).
3. Implement issuance for service accounts via IdP client-credential flows; avoid long-lived secrets where possible.
4. Implement validation libraries in Kotlin/Python: verify `iss/aud/exp/nbf`, signature (JWKS), `type=service`, permission claims; for user propagation, validate both service and user tokens.
5. Observability: log caller service id and user subject; metrics for token validation failures and authorization denials.
6. Optional: pilot mesh mTLS in non-prod; if adopted, map mesh identities (e.g., SPIFFE ID) to service principals.

## Next steps

- Confirm baseline (JWT over TLS) and defer mesh mTLS to a follow-up ADR if adopted.
- Decide on `principal_permissions` vs. dedicated `service_permissions`; keep permission vocabulary shared.
- Publish the JWT claim profile and audience rules; align with existing security permissions/claims.
- Choose supported IdP flows (Google, Okta, etc.) for client credentials and OBO/user token issuance.
- Produce a separate implementation spec with schema migrations and code snippets and link it here.
- TODO: When K8s/service mesh is introduced, pilot mesh mTLS in non-prod and decide on prod rollout; ignore mTLS elsewhere until then.

## Open questions

1. Which IdP flows (client credentials/OBO) will be supported first, given multiple providers (Google, Okta, etc.)?
2. Should we adopt `principal_permissions` (type=user/service) or keep a dedicated `service_permissions` table while reusing the same permission keys?
3. Environments requiring mTLS: do we pilot mesh mTLS in staging/prod, or rely on TLS + JWT initially?

## References

- OAuth2 client credentials and on-behalf-of patterns (IdP-specific docs)
- Micronaut Security JWT validation
- Python JWT validation (PyJWT, authlib) with JWKS
