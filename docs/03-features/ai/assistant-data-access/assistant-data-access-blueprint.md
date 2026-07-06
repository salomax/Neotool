---
title: Assistant Secure Data-Access Foundations Blueprint
type: feature-spec
category: ai
status: draft
version: 1.0.0
tags: [ai, llm, assistant, tool-calling, graphql, security, channels]
ai_optimized: true
search_keywords: [assistant, ai, user-data, graphql, tool-calling, mcp, account-scope, prompt-injection, in-app-chat, whatsapp, lgpd]
related:
  - ./llm-orchestrator-blueprint.md
  - ../../02-architecture/service-architecture.md
  - ../../02-architecture/api-architecture.md
  - ../../03-features/security/README.md
  - ../../09-security/README.md
  - ../../92-adr/0008-interservice-security.md
last_updated: 2026-07-03
---

# Assistant Secure Data-Access Foundations Blueprint

## Module Name

**Module name:** `assistant` (evolution — no new deployable in this phase)

- Builds on the existing `assistant` service (Kotlin + Micronaut, GraphQL Federation subgraph)
- Reuses the current LLM orchestration: provider routing/failover (Gemini/OpenAI), tool-calling
  loop, account-scoped `ToolRegistry`/`ToolExecutor`, RBAC (`AssistantPermissions`), usage
  budgets, escalation, streaming
- Adds: read-only GraphQL-backed domain tools, a channel abstraction, and the security hardening
  required to expose user data safely

## Overview

Establish the foundational layer that lets the AI assistant agent securely read the
**authenticated user's own financial data** through the federated GraphQL API, and deliver that
experience through a **pluggable channel abstraction** (in-app chat first).

This is a foundations + architecture-decision effort, not a feature-complete assistant. Today the
only builtin tool is `GetCurrentTimeExecutor`, so the agent cannot answer questions like
"show my last transactions" or "what's my portfolio summary". This blueprint closes that gap
under strict, non-bypassable security controls.

## Use Cases (v1)

1. **Search a movement / transaction** — "find my last transfer to X", "transactions over R$500
   in June" — answered from live data via GraphQL tools, scoped to the user.
2. **Summarized data** — "what's my portfolio summary", "how much did I spend on food last month".
3. **In-app chat** — reference channel wired to the assistant GraphQL API (`chat` +
   `messageStream`).

Future (out of scope, separate issues): WhatsApp/other channels; investment suggestions/advisory.

## Goals

- Read-only, account-scoped access to user data (transactions, balances, portfolio, summaries)
  via GraphQL-backed tools on the existing `ToolRegistry`.
- **Non-bypassable account scoping** derived from the authenticated principal (JWT/session).
- A **channel abstraction** with in-app chat (mobile) as the reference implementation.
- A **single enforcement point** for authz, budgets, rate limiting, and audit.

## Non-goals (separate / future)

- WhatsApp or any external messaging channel.
- Investment suggestions / advisory features.
- Any write operation, trade, or money movement.
- A large tool catalog — only a minimal starter set to validate the foundation.

## Architecture Decision (tradeoff + recommendation)

Three ways to let the agent read user data:

**A) Internal `ToolRegistry` executors calling the federated GraphQL API (reuse what exists)**
- + Reuses `ToolExecutor`/`ToolRegistry`, RBAC, budgets, and audit already in the service.
- + Account scope enforced in-process from the JWT — never leaves our trust boundary.
- + Fastest path; no new deployable; strong control over what the LLM can call.
- − Tools coupled to our service; not directly reusable by external/3rd-party agents.

**B) Dedicated MCP server exposing user data as MCP tools/resources**
- + Standardized, interoperable — any MCP-compatible agent can consume it.
- + Clean separation of "data access" from "orchestration".
- − New deployable + new attack surface; per-user auth-context propagation and scoping must be
  re-solved at the MCP boundary; duplicates budget/audit/rate-limit machinery.
- − Overkill for a single first-party in-app channel.

**C) Hybrid — internal executors now, MCP as a later gateway wrapping the same executors**
- + Ships the secure in-app experience fast on existing infra (A), and can expose an MCP facade
  later (B) reusing the exact same authorized, audited executors — no duplicated security.
- + Keeps a single enforcement point for account-scope, authz, budgets, and audit.

**Recommendation: adopt (C).** Implement (A) now as the foundation; treat MCP as a future,
optional gateway over the same executors when an external/multi-agent need is concrete. This
keeps one security boundary, reuses all existing controls, and stays interoperable later. An ADR
should ratify this and record the LLM-provider PII/retention posture.

## Security Requirements (crux)

- **Account scope from principal only.** The `accountId` passed to every `ToolExecutor` comes
  from the authenticated JWT/session context — never from user text or LLM output.
- **Auth-context propagation.** Outbound GraphQL calls carry the user's authorization context;
  no broad service account is used to fetch per-user data.
- **Read-only phase.** Write/mutation capability is blocked at the tool layer.
- **Prompt-injection defense.** System-prompt hardening, tool-output isolation, strict JSON
  schema validation of tool selection/arguments; model output is never trusted to widen scope.
- **Per-user rate limiting + usage budgets** applied to data-access tools.
- **Audit logging** for every invocation: principal, account, tool, args-hash, status, latency,
  cost.
- **PII minimization + LGPD.** Document and minimize what user data reaches the LLM provider;
  record provider data-retention posture in the ADR; align with existing security standards.

## Design Sketch

### Tools (starter set, read-only)

- `getRecentTransactions(limit, since?)` → recent movements for the scoped account.
- `getPortfolioSummary()` → aggregated portfolio/balance snapshot.

Each implements `ToolExecutor.execute(accountId, input): Result<JsonObject>` and is registered in
`ToolRegistry` with input/output JSON schema (validated before/after model calls).

### GraphQL access

A thin authorized GraphQL client used by executors, propagating the user's auth context to the
federated supergraph. No new persistence; reuses existing subgraphs (banking / investments).

### Channel abstraction

An interface decoupling the agent/tool core from delivery. In-app chat (mobile) is the first
implementation, wired to the assistant GraphQL API (`chat` + `messageStream`). Future channels
(WhatsApp) implement the same interface without touching the core.

## Acceptance Criteria

- [ ] ADR documents the chosen data-access approach (A vs B vs C) with security trade-offs and is
      approved before implementation.
- [ ] At least 2 read-only GraphQL-backed tools registered (`getRecentTransactions`,
      `getPortfolioSummary`), each with input/output JSON schema.
- [ ] Account scope for every tool call derives exclusively from the authenticated principal; a
      test proves a prompt asking for another account's data cannot access it.
- [ ] Outbound GraphQL calls propagate the user's authorization context (no broad service
      account for per-user data).
- [ ] Tools are read-only; any write/mutation capability blocked at the tool layer.
- [ ] Prompt-injection mitigations in place (system prompt hardening, tool-output isolation,
      schema validation of tool args).
- [ ] Per-user rate limiting and usage budgets enforced on data-access tools.
- [ ] Every tool invocation produces an audit log entry.
- [ ] PII handling reviewed and minimized; provider retention posture recorded in ADR (LGPD).
- [ ] Channel abstraction exists; in-app chat is one implementation, future channels pluggable.
- [ ] In-app chat wired end-to-end in mobile against the assistant GraphQL API.
- [ ] Security review completed and signed off before merge.

## Open Questions

- Which subgraphs/fields form the minimal starter data set?
- Auth-context propagation mechanism to the supergraph (token passthrough vs. exchange).
- LLM-provider selection and its data-retention/PII posture for production.
- Streaming UX in mobile chat (partial tokens, tool-call indicators).

## Related

- [LLM Orchestrator Blueprint](./llm-orchestrator-blueprint.md)
- [Security Features](../../03-features/security/README.md)
- Pending ADR: `92-adr/00XX-assistant-secure-data-access.md`
- Pending issue: `GH-<number>`
