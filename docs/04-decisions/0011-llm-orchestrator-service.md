---
title: ADR-0011 LLM Orchestrator Service
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/assistant
  - service/kotlin/settings.gradle.kts
load_when:
  - adding assistant/LLM orchestration behavior
  - choosing where tool registries live
supersedes:
schema: docs-metadata-v1
---

# ADR-0011: LLM Orchestrator Service

Purpose: record the durable decision and consequences for llm orchestrator service.

## Status

accepted

## Context

Product assistant capabilities need a dedicated orchestration boundary rather than embedding provider SDKs in every service.

## Decision

Own LLM orchestration in the `assistant` Kotlin/Micronaut module (tool registry, provider integration, assistant APIs), separate from domain services like banking.

## Consequences

- Centralizes tool metadata and provider concerns.
- MCP remote-agent surface is a different boundary (`mcp` module; ADR-0015/0017).

## Evidence

- `:assistant` included in `settings.gradle.kts`.
- `service/kotlin/assistant` module tree exists.

## Related

- [README](README.md)
- [ADR-0015](0015-mcp-tool-surface-and-agent-threat-model.md)
