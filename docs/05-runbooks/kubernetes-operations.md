---
title: Kubernetes Operations Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/kubernetes/scripts/vault-setup.sh
  - infra/kubernetes/scripts/postgres-setup.sh
  - infra/kubernetes/scripts/unleash-setup.sh
  - infra/terraform/cloud
load_when:
  - running in-repo k8s setup scripts
  - locating cluster IaC when manifests are external
supersedes:
schema: docs-metadata-v1
---

# Kubernetes Operations Runbook

Purpose: operate Kubernetes using scripts and IaC that exist in this repository, and avoid stale in-tree manifest paths.

## Symptoms

- Vault/Postgres/Unleash secrets not initialized in a cluster namespace.
- Uncertainty whether manifests live in this repo or an external GitOps repo.

## Authority

This repo currently contains setup scripts under `infra/kubernetes/scripts/` and Terraform under `infra/terraform/`. There is **no** `infra/kubernetes/base/` tree in-repo. Historical instructions that `kubectl apply -f infra/kubernetes/base/...` are stale. Day-2 Flux/kubectl recipes live under an external GitOps boundary; use [ADR-0020](../04-decisions/0020-external-gitops-inventory-boundary.md) instead of inventing local manifest paths.

## Commands

```bash
# In-repo bootstrap helpers (require kubectl context + existing namespace)
infra/kubernetes/scripts/vault-setup.sh --namespace production
infra/kubernetes/scripts/postgres-setup.sh
infra/kubernetes/scripts/unleash-setup.sh

# Discover cloud modules
ls infra/terraform/cloud/modules
ls infra/terraform/cloud/environments
```

## Rollback

- Re-run setup scripts only when idempotent and after backing up Vault credentials files referenced by the scripts.
- For workload rollbacks, use the environment GitOps process — not undocumented local manifests.

## Verification

- Scripts exit 0 and report success.
- `kubectl get ns` / target pods exist for the namespace you passed.
- Do not claim `infra/kubernetes/base` exists.

## Related

- [Infrastructure](../01-architecture/infrastructure.md)
- [ADR-0020](../04-decisions/0020-external-gitops-inventory-boundary.md)
- [Vault](vault.md)
- [Feature flags](feature-flags-unleash.md)
