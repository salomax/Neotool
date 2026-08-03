---
title: ADR-0017 MCP Direct Banking Dispatch for Provenance
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/mcp/src/test/kotlin/io/github/salomax/neotool/mcp/dispatch/McpBankingGraphQLClientTest.kt
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/agent/AgentCallContext.kt
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/service/ServiceTokenClient.kt
load_when:
  - changing MCP dispatch path
  - debugging agent provenance headers
supersedes:
schema: docs-metadata-v1
---

# ADR-0017: MCP Direct Banking Dispatch for Provenance

Purpose: record the durable decision and consequences for mcp direct banking dispatch for provenance.

## Status

accepted

## Context

MCP provenance headers must not be forgeable via the client-facing Apollo Router propagate list.

## Decision

Dispatch MCP tool calls directly to banking’s internal GraphQL endpoint with service tokens. Carry `X-Mcp-*` provenance on that internal hop. Banking must honor provenance only for trusted delegated service principals.

## Consequences

- Prevents client forgery of agent origin.
- Forgoes router federation features for MCP→banking calls (acceptable because dispatch targets banking with hand-authored operations).

## Evidence

- `McpBankingGraphQLClientTest` exists under mcp dispatch tests.
- Service token + agent context utilities exist in common.

## Related

- [README](README.md)
- [ADR-0008](0008-interservice-security.md)
- [ADR-0015](0015-mcp-tool-surface-and-agent-threat-model.md)
