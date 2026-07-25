---
title: ADR-0017 MCP Dispatches Directly to Banking to Carry Trusted Provenance
type: adr
category: security
status: accepted
version: 1.0.0
tags: [security, mcp, provenance, audit, apollo-router, service-to-service]
related:
  - docs/92-adr/0008-interservice-security.md
  - docs/92-adr/0009-resource-ownership-strategy.md
  - docs/92-adr/0015-mcp-tool-surface-and-agent-threat-model.md
---

# ADR-0017: MCP Dispatches Directly to Banking to Carry Trusted Provenance

## Status

Accepted — records a decision made while implementing #419 Milestone 1 (agent-action audit trail).

## Context

#419 requires distinguishing domain changes made by an external agent (via MCP) from those made through the web/mobile UI. The chosen mechanism is a per-tool-call provenance triple — `X-Mcp-Tool-Call-Id`, `X-Mcp-Client-Id`, `X-Mcp-Token-Id` — propagated from the MCP module to banking, where the changed rows are stamped with their origin.

That provenance must be **trustworthy**: a user must not be able to forge agent-origin on their own changes, and the audit trail is the foundation for M3's bulk reversal, so a forgeable marker is worse than none.

The Apollo Router — the client-facing federation gateway — propagates only an allowlist of headers (`authorization`, `x-request-id`, `traceparent`, `tracestate`, `baggage`). It does **not** forward `X-Mcp-*`. Two naive options both fail:

- **Broaden the router's `propagate` list to include `X-Mcp-*`.** `propagate` forwards *incoming client* headers, so this immediately lets any authenticated user send a forged `X-Mcp-Client-Id` and have it reach banking. The router cannot distinguish a header set by a trusted internal caller from one set by the client. Trust-sensitive headers therefore cannot ride the router safely.
- **Leave dispatch on the router without the headers.** The provenance is stripped and never arrives; every MCP change is silently stamped `UI`. (This was the state that failed round-1 review.)

## Decision

**The MCP module dispatches tool calls directly to the banking service's GraphQL endpoint (an internal, cluster-only address), not through the Apollo Router.** The `X-Mcp-*` provenance headers ride that internal service-to-service hop. Banking honours them only when the request principal is a `SERVICE` token whose `delegated_by` claim equals the trusted MCP delegator (`mcp-service`); on any other principal — including a user request carrying forged `X-Mcp-*` — they are ignored and origin stays `UI`.

### Rationale

- **The router is the client-edge gateway; MCP→banking is internal service-to-service.** Internal calls in this estate already talk service-to-service directly with a service token (ADR-0008), not through the client-facing router. MCP dispatch belongs to that category, not to the "client through the gateway" category. Provenance is trustworthy *because* it rides the internal hop, not the client edge.
- **The router structurally cannot carry a trust-sensitive header.** Its propagate mechanism cannot separate client-supplied from internally-injected headers, so routing `X-Mcp-*` through it would be strictly less secure.
- **MCP dispatch only ever targets banking**, with hand-authored queries, so it does not use federation/cross-subgraph composition — the main capability the router provides and this bypass forgoes.
- **Auth is unaffected.** Banking validates the delegated service token regardless of path (`requireNotNull(currentAccountId)`, permission checks). The token is the authorization boundary, not the network path.

## Consequences

- **Two ingresses to banking exist:** the router (client-facing) and the direct internal endpoint (MCP). **Invariant to hold:** banking's GraphQL endpoint must remain reachable only inside the cluster and must always enforce token authentication — the direct path must never become an unauthenticated or externally reachable door.
- **Mid-epic path change:** #417 originally dispatched MCP reads through the router; #419 M1 moved all MCP→banking dispatch to the direct endpoint. This is now consistent (a single client), and is recorded here so it does not read as accidental.
- **Router-level concerns that no longer apply on this path** (query-cost limits, gateway rate limiting) are covered elsewhere for MCP: token/edge rate limiting (#420, #435) sits upstream of dispatch, and MCP queries are fixed and hand-authored, not arbitrary client queries. Distributed tracing continues via `traceparent`.

## Alternatives considered

- **Broaden router `propagate` for `X-Mcp-*`** — rejected: opens client spoofing of provenance (see Context).
- **Sign the provenance into a tamper-evident claim** so it travels through the router and banking verifies it regardless of path. This is the "most correct" option *if* MCP later needs the router (e.g. federation across subgraphs). It is heavier (per-call signing/verification), and the per-call `tool_call_id` cannot live in the delegated token itself (the token is reused across calls). Deferred; revisit if MCP ever needs federated dispatch or a single ingress becomes a hard requirement.

## Revisit if

- MCP dispatch needs to query more than one subgraph (federation), or
- a single-ingress-to-banking policy becomes mandatory — in which case move to the signed-provenance-claim alternative above rather than back to plain headers on the router.
