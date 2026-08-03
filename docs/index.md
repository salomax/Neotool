---
title: Engineering Docs Index
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - docs/README.md
  - docs/
load_when:
  - locating a specific durable engineering doc
  - auditing docs coverage
supersedes:
  - docs/MANIFEST.md
  - docs/README.md
schema: docs-metadata-v1
---

# Engineering Docs Index

Purpose: flat catalog of durable engineering documents under `docs/`.

## Authority

- Navigation destinations are files under `docs/` only.
- Content authority remains code/config/tests cited by each leaf doc.

## Rules

1. Use relative links only to files that already exist.
2. Prefer the leaf doc that matches the task; avoid loading whole sections.
3. Treat this catalog as the durable engineering index.

## Catalog

### Root

| Doc | Type |
|---|---|
| [README.md](README.md) | entry |
| [index.md](index.md) | index |

### 01-architecture

| Doc | Status |
|---|---|
| [README.md](01-architecture/README.md) | active |
| [system.md](01-architecture/system.md) | active |
| [service-boundaries.md](01-architecture/service-boundaries.md) | active |
| [data-and-persistence.md](01-architecture/data-and-persistence.md) | active |
| [graphql-and-contracts.md](01-architecture/graphql-and-contracts.md) | active |
| [security.md](01-architecture/security.md) | active |
| [observability.md](01-architecture/observability.md) | active |
| [infrastructure.md](01-architecture/infrastructure.md) | active |

### 02-development

| Doc | Status |
|---|---|
| [README.md](02-development/README.md) | active |
| [repo-commands.md](02-development/repo-commands.md) | active |
| [backend-kotlin.md](02-development/backend-kotlin.md) | active |
| [frontend-web.md](02-development/frontend-web.md) | active |
| [mobile-expo.md](02-development/mobile-expo.md) | active |
| [graphql-codegen.md](02-development/graphql-codegen.md) | active |
| [testing-strategy.md](02-development/testing-strategy.md) | active |

### 03-agent-workflow

| Doc | Status |
|---|---|
| [README.md](03-agent-workflow/README.md) | active |
| [context-contract.md](03-agent-workflow/context-contract.md) | active |
| [issue-delivery-flow.md](03-agent-workflow/issue-delivery-flow.md) | active |
| [code-search-and-token-discipline.md](03-agent-workflow/code-search-and-token-discipline.md) | active |
| [review-and-closeout.md](03-agent-workflow/review-and-closeout.md) | active |

### 04-decisions

| Doc | Status |
|---|---|
| [README.md](04-decisions/README.md) | active |
| [adr-template.md](04-decisions/adr-template.md) | active |
| [0001–0020 ADRs](04-decisions/README.md) | migrated and normalized |

### 05-runbooks

| Doc | Status |
|---|---|
| [README.md](05-runbooks/README.md) | active |
| [local-development.md](05-runbooks/local-development.md) | active |
| [deployment-web.md](05-runbooks/deployment-web.md) | active |
| [kubernetes-operations.md](05-runbooks/kubernetes-operations.md) | active |
| [cloudflare.md](05-runbooks/cloudflare.md) | active |
| [cloudflare-r2.md](05-runbooks/cloudflare-r2.md) | active |
| [vault.md](05-runbooks/vault.md) | active |
| [feature-flags-unleash.md](05-runbooks/feature-flags-unleash.md) | active |
| [banking-pdf-import-retry-dlq.md](05-runbooks/banking-pdf-import-retry-dlq.md) | active |
| [credit-card-bill-import-remediation.md](05-runbooks/credit-card-bill-import-remediation.md) | active |
| [comms-email-operations.md](05-runbooks/comms-email-operations.md) | active |

## Related

- [README.md](README.md)
