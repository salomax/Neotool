---
title: ADR-0020 External GitOps Inventory Boundary
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - scripts/cli/commands/graphql/generate-schema.sh
  - scripts/cli/commands/graphql.sh
  - infra/kubernetes/scripts/vault-setup.sh
  - infra/kubernetes/scripts/postgres-setup.sh
  - infra/kubernetes/scripts/unleash-setup.sh
  - infra/terraform/cloud
load_when:
  - documenting kubernetes or deployment ownership boundaries
  - deciding whether a cluster artifact should live in this repo
supersedes:
schema: docs-metadata-v1
---

# ADR-0020: External GitOps Inventory Boundary

Purpose: record the accepted boundary between in-repo infrastructure assets and
the external GitOps inventory.

## Status

accepted

## Context

This repository contains infrastructure scripts, Terraform, router assets, and
setup helpers, but it does not contain a full in-tree Flux or Kubernetes base
inventory. Some scripts explicitly sync outputs into an external sibling repo
such as `../invistus-flux`.

## Decision

Treat this repository as the owner of:

- infrastructure helper scripts
- Terraform modules and environments present under `infra/terraform`
- router and supergraph artifacts generated in-repo

Treat the external GitOps repo as the owner of:

- environment-specific Flux application inventory
- cluster deployment reconciliation state
- day-2 manifest changes not checked into this repository

Do not invent in-repo manifest paths that are absent from the filesystem.

## Consequences

- Runbooks in this repo describe only assets that exist here.
- Cluster rollout or rollback procedures that depend on Flux inventory must
  point to the external GitOps process instead of stale local paths.
- Scripts that sync artifacts to Flux are allowed to reference the sibling repo
  explicitly without pretending it is part of this repository.

## Evidence

- `generate-schema.sh` syncs router outputs to
  `${INVISTUS_FLUX_DIR:-$PROJECT_ROOT/../invistus-flux}`.
- `graphql.sh` exposes `--sync-flux` / `--no-sync-flux`.
- Kubernetes setup in this repo is script-based under `infra/kubernetes/scripts/`.
- Terraform cloud modules/environments exist in `infra/terraform/cloud`.

## Related

- [README.md](README.md)
- [../01-architecture/infrastructure.md](../01-architecture/infrastructure.md)
- [../05-runbooks/kubernetes-operations.md](../05-runbooks/kubernetes-operations.md)
