---
title: Local Staging Runbook
type: runbook
category: infrastructure
status: current
version: 1.0.0
tags: [staging, local, docker-compose, runbook, infrastructure]
ai_optimized: true
search_keywords: [staging, local, docker, compose, supergraph, router, setup, validation]
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

The router uses the generated `supergraph.staging.graphql`. Generate it with Rover in Docker:

```bash
./neotool graphql all
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
