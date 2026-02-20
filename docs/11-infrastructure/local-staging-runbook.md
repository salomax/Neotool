---
title: Local Staging Runbook
type: runbook
category: infrastructure
status: current
version: 1.0.0
tags: [staging, local, docker-compose, runbook, infrastructure]
ai_optimized: true
search_keywords:
  [staging, local, docker, compose, supergraph, router, setup, validation]
related:
  - 11-infrastructure/README.md
  - 08-workflows/deployment-workflow.md
  - 06-contracts/graphql-standards.md
  - 11-infrastructure/feature-flags-unleash.md
  - 93-reference/commands.md
last_updated: 2026-01-19
---

# Local Staging Runbook

> **Purpose**: Step-by-step tutorial to run the **staging** environment locally (Docker Compose) and validate the stack.

---

## Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Bash/Zsh shell
- Enough free disk space for images and volumes

---

## 0) Stop Any Running Containers (Avoid Port Conflicts)

Staging binds common ports (3000, 4000, 5432, 9092, 8200, 9000, etc.).  
**Stop all running containers first** to avoid port conflicts:

```bash
docker stop $(docker ps -q)
```

If you prefer to stop only NeoTool containers:

```bash
docker compose -f infra/docker/docker-compose.staging.yml down --remove-orphans
```

---

## 1) Generate the Staging Supergraph

The router uses the generated `supergraph.staging.graphql`. Generate it with Rover (use `--all` so staging is included):

```bash
./neotool graphql all --all
```

Or with Docker for Rover:

```bash
./neotool graphql generate --all --docker
```

This creates/updates:
`contracts/graphql/supergraph/supergraph.staging.graphql`

---

## 2) Start the Staging Stack

```bash
docker-compose -f infra/docker/docker-compose.staging.yml up --build
```

Keep this running in the foreground while testing, or add `-d` to run detached.

---

## 3) Confirm Service Health

```bash
docker-compose -f infra/docker/docker-compose.staging.yml ps
```

Expect **healthy** for:
`vault`, `postgres`, `pgbouncer`, `kafka`, `security`, `assets`, `financialdata`, `router`, `web`, `minio`.

---

## 4) Validate Core Endpoints

**Vault**

```bash
curl http://localhost:8200/v1/sys/health
```

**Router Health**

```bash
curl http://localhost:8088/health
```

**GraphQL Gateway (Router)**

```bash
curl -X POST http://localhost:4000/graphql \
  -H 'content-type: application/json' \
  -d '{"query":"{ __typename }"}'
```

**Web App**

```bash
curl -I http://localhost:3000
```

---

## 5) Validate Subgraphs via Router

**Schema Introspection**

```bash
curl -X POST http://localhost:4000/graphql \
  -H 'content-type: application/json' \
  -d '{"query":"{ __schema { queryType { name } } }"}'
```

**Assets Type Check**

```bash
curl -X POST http://localhost:4000/graphql \
  -H 'content-type: application/json' \
  -d '{"query":"{ __type(name:\"Asset\") { name } }"}'
```

**Financial Data Type Check**

```bash
curl -X POST http://localhost:4000/graphql \
  -H 'content-type: application/json' \
  -d '{"query":"{ __type(name:\"BacenFinancialInstitution\") { name } }"}'
```

---

## 6) Optional: Teardown

```bash
docker compose -f infra/docker/docker-compose.staging.yml down --remove-orphans
```

If you need a full reset (volumes included):

```bash
docker compose -f infra/docker/docker-compose.staging.yml down -v --remove-orphans
```

---

## Troubleshooting Quick Notes

- If a service is **unhealthy**, check its logs:
  ```bash
  docker compose -f infra/docker/docker-compose.staging.yml logs -f <service>
  ```
- If the router fails, re-generate the supergraph and restart the router.
- If ports are still busy, confirm no external containers remain:
  ```bash
  docker ps
  ```

### "Cannot query field bacenInstitutionSlugs on type Query" (400)

The gateway (Apollo Router at port 4000) validates queries against its **composed supergraph schema**. This error means the schema the router is using does not include `bacenInstitutionSlugs` (or other fields you added to the financialdata subgraph).

**Cause**: The router is running with an **out-of-date supergraph file**. The supergraph is composed from subgraph schemas in `contracts/graphql/subgraphs/`. If you added or changed fields in the financialdata subgraph but did not regenerate the supergraph, the router still has the old schema.

**Fix**:

1. **Regenerate the supergraph** so it includes the latest financialdata schema:

   ```bash
   ./neotool graphql generate --all
   ```

   (Use `--all` so that `supergraph.staging.graphql` and `supergraph.local.graphql` are generated, not only production. Use `--docker` if Rover is not installed locally.)

2. **Restart the router** so it loads the new file:
   - Docker Compose staging: `docker compose -f infra/docker/docker-compose.staging.yml up -d --force-recreate router`
   - Or bring the whole stack down and up again.

3. **If the router is not from this repo** (e.g. you run it from neotool-flux or another repo), that environment must compose the supergraph using the **latest** financialdata subgraph schema (e.g. after deploying the financialdata service, trigger supergraph composition there and ensure it pulls the updated schema).

### 504 Gateway Timeout (e.g. SitemapInstitutions / bacenInstitutionSlugs)

The router returns 504 when it does not get a response from a subgraph within its timeout (default 30s; local config uses 60s). The subgraph may be up on the host (`http://localhost:8091/graphql`) but either:

1. **Router cannot reach the subgraph** — The router runs in Docker and uses `host.docker.internal:8091` to reach services on the host. On Linux, `host.docker.internal` may not exist unless you add `extra_hosts` for the router service. From the router container, verify:

   ```bash
   docker exec neotool-graphql-router wget -q -O- --timeout=5 "http://host.docker.internal:8091/graphql" -d '{"query":"{ __typename }"}' 2>&1 | head -5
   ```

   Or use `curl` if the image has it. If this fails, fix Docker→host connectivity (e.g. use host network or add `extra_hosts: host.docker.internal:host-gateway` for the router service).

2. **Subgraph is slow** — The query takes longer than the router timeout. Hit the subgraph directly from the host to measure:
   ```bash
   time curl -s -X POST http://localhost:8091/graphql -H "Content-Type: application/json" -d '{"query":"query { bacenInstitutionSlugs(first: 100) { edges { node { id name } } pageInfo { hasNextPage endCursor } } }"}' | head -c 500
   ```
   If this takes >30s (or >60s with the local timeout), optimize the backend (e.g. indexes, query) or increase `traffic_shaping.all.timeout` in `infra/router/router.local.yaml`.
