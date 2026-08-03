---
title: ADR-0015 MCP Tool Surface and Agent Threat Model
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/mcp
  - service/kotlin/settings.gradle.kts
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/agent/AgentCallContext.kt
load_when:
  - extending MCP tools
  - reviewing agent write threat model
supersedes:
schema: docs-metadata-v1
---

# ADR-0015: MCP Tool Surface and Agent Threat Model

Purpose: record the durable decision and consequences for mcp tool surface and agent threat model.

## Status

accepted

## Context

External agents need a stable MCP tool catalog and a write threat model before expanding financial mutations.

## Decision

Expose Invistus through the dedicated `mcp` module with an explicit tool surface and agent-call context. Treat tenant isolation via existing account-scoped services as mandatory. Do not assume banking enforces `AccountRole` ceilings that are not implemented there.

## Consequences

- Legacy status was `proposed`; module and agent context code now exist, so this migration records `accepted` for the architectural boundary.
- Detailed tool catalog and confirmation policy remain sensitive; prefer code + tests over copying long catalog tables into docs.

## Evidence

- `:mcp` included in Gradle settings; module sources and dispatch tests exist.
- `AgentCallContext` lives under common security agent package.

## Related

- [README](README.md)
- [ADR-0016](0016-oauth-login-consent-ui-rendering.md)
- [ADR-0017](0017-mcp-direct-banking-dispatch-for-provenance.md)
