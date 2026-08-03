---
title: Runbooks
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/
load_when:
  - responding to an operational incident
  - performing deployment or local environment recovery
supersedes:
  - historical infrastructure runbooks
schema: docs-metadata-v1
---

# Runbooks

Purpose: index for operational procedures that remain actionable against current infra.

## Authority

- Infra manifests and scripts under `infra/`, plus verified operational docs.
- Duplicate Unleash/Vault/Cloudflare material collapses to one canonical runbook each.

## Rules

1. Every runbook needs symptoms, commands, rollback, and verification.
2. Exclude purely historical incidents unless the procedure is still reusable.
3. Prefer current command paths over stale prose.
4. Do not document paths that are absent from the repo (for example missing `infra/kubernetes/base/`).

## Documents

| Doc | Purpose |
|---|---|
| [local-development.md](local-development.md) | Local/dev environment |
| [deployment-web.md](deployment-web.md) | Web deployment |
| [kubernetes-operations.md](kubernetes-operations.md) | Kubernetes ops (in-repo scripts) |
| [cloudflare.md](cloudflare.md) | Cloudflare DNS/CDN |
| [cloudflare-r2.md](cloudflare-r2.md) | Cloudflare R2 |
| [vault.md](vault.md) | Vault operations |
| [feature-flags-unleash.md](feature-flags-unleash.md) | Unleash feature flags |
| [banking-pdf-import-retry-dlq.md](banking-pdf-import-retry-dlq.md) | Banking PDF import DLQ |
| [credit-card-bill-import-remediation.md](credit-card-bill-import-remediation.md) | Credit card bill import remediation |
| [comms-email-operations.md](comms-email-operations.md) | Comms email provider and DLQ ops |

## Related

- [../README.md](../README.md)
- [../01-architecture/infrastructure.md](../01-architecture/infrastructure.md)
- [../02-development/repo-commands.md](../02-development/repo-commands.md)
