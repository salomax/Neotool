---
title: Infrastructure Architecture
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/docker
  - infra/kubernetes
  - infra/terraform
  - infra/vault
  - infra/unleash
  - infra/postgres
  - infra/pgbouncer
  - infra/router
load_when:
  - understanding runtime topology
  - choosing where to change deploy/local infra
supersedes:
schema: docs-metadata-v1
---

# Infrastructure Architecture

Purpose: map infrastructure components present in the repository.

## Context

Runtime and platform assets live under `infra/`: local Docker Compose, Kubernetes manifests, Terraform, secrets, feature flags, data stores, and the GraphQL router.

## Authority

- Present top-level dirs: docker, kubernetes, observability, pgbouncer, postgres, router, terraform, unleash, vault.
- Compose files include `docker-compose.local.yml` and `docker-compose.staging.yml`.

## Rules

1. Local stacks start from `infra/docker` compose files.
2. In-repo Kubernetes helpers are scripts under `infra/kubernetes/scripts/`; full cluster manifests may live in external GitOps — do not assume `infra/kubernetes/base/` exists.
3. Cloud resources and modules live under `infra/terraform`.
4. Feature flags: Unleash under `infra/unleash` (operational detail in runbooks).
5. Secrets: Vault under `infra/vault` (operational detail in runbooks).
6. Do not document env-specific hostnames unless verified in current compose/manifests.
7. Keep deployment procedures in `05-runbooks`, not duplicated here.

## Related

- [System](system.md)
- [Observability](observability.md)
- [Runbooks](../05-runbooks/README.md)
- [ADR-0002](../04-decisions/0002-containerized-architecture.md)
- [ADR-0020](../04-decisions/0020-external-gitops-inventory-boundary.md)
