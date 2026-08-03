---
title: Local Development Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/docker/docker-compose.local.yml
  - infra/docker/docker-compose.staging.yml
  - infra/docker/.env.local
  - infra/router/router.local.yaml
  - contracts/graphql/supergraph/supergraph.local.graphql
load_when:
  - bringing up local docker dependencies
  - debugging router/schema mismatch locally
supersedes:
schema: docs-metadata-v1
---

# Local Development Runbook

Purpose: operate local Docker Compose stacks and recover common router/schema failures.

## Symptoms

- Postgres/router/dependencies not reachable for local apps.
- GraphQL field missing at the gateway while subgraph code has it.
- Stale containers after schema or env changes.

## Authority

Compose files and env samples under `infra/docker/`. Local router config `infra/router/router.local.yaml`. Local supergraph `contracts/graphql/supergraph/supergraph.local.graphql`.

## Commands

```bash
# Local stack (profiles vary; inspect compose file for service profiles)
docker compose -f infra/docker/docker-compose.local.yml --env-file infra/docker/.env.local up -d
docker compose -f infra/docker/docker-compose.local.yml ps
docker compose -f infra/docker/docker-compose.local.yml logs -f <service>

# Staging-like compose in this repo
docker compose -f infra/docker/docker-compose.staging.yml --env-file infra/docker/.env.staging up -d --build
docker compose -f infra/docker/docker-compose.staging.yml up -d --force-recreate router
```

If the router rejects new subgraph fields, regenerate/refresh the supergraph artifact used by the router before recreating the router container.

## Rollback

```bash
docker compose -f infra/docker/docker-compose.local.yml down --remove-orphans
# destructive reset (data loss):
docker compose -f infra/docker/docker-compose.local.yml down -v --remove-orphans
```

## Verification

- `docker compose ... ps` shows expected services healthy.
- Router answers `/graphql` with the expected schema fields.
- App clients can authenticate against local security when that profile is up.

## Related

- [Infrastructure architecture](../01-architecture/infrastructure.md)
- [Repo commands](../02-development/repo-commands.md)
- [GraphQL contracts](../01-architecture/graphql-and-contracts.md)
