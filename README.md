<p align="center">
  <img src="./design/assets/logos/invistus-logo-blue-transparent.png" alt="Invistus Logo" width="320" />
</p>

# Invistus

**Invistus is a comprehensive personal tool for wealth management.**

The main goal of Invistus is to help users manage wealth with reliable financial data, clear product flows, and production-ready platform capabilities.

Invistus is implemented on top of NeoTool.

## NeoTool Foundation (Boilerplate)

**NeoTool is the pre-wired foundation used by Invistus:** a comprehensive boilerplate that packages architecture decisions, best practices, and operational guardrails so teams can build faster with consistency.

| Item | Role | In this repository |
| --- | --- | --- |
| **NeoTool** | Engineering baseline | Architecture model, standards, patterns, ADRs, workflow templates, and preconfigured platform components |
| **Invistus** | Product implementation | Personal wealth management domain, financial data workloads, and user-facing product capabilities |

<p align="center">
  <img src="./design/misc/neotool/neotool-architecture.svg" alt="Neotool Architecture" />
</p>

### What NeoTool Provides to Invistus

- A comprehensive monorepo baseline with API, web, workers, infra, and docs already wired
- Reusable architecture patterns and implementation standards
- Security, observability, CI/CD, and GitOps operational foundations
- Decision records and workflows to keep product development consistent over time

## What Invistus Delivers

- Spec-driven product development with durable engineering docs in `docs/`
- Polyglot monorepo with explicit separation between API services and async workers
- Federated GraphQL platform with Kotlin services and Next.js frontend
- Cloudflare integration for edge delivery (CDN/DNS/TLS) and object storage (R2)
- GitOps operations with Flux CD and Kubernetes runbooks
- CI pipeline optimized with Nx affected detection across Kotlin, web, and Go worker projects

## Layered Architecture Map

| Layer | Components | What It Solves | Primary Docs |
| --- | --- | --- | --- |
| **Experience** | `web/`, `mobile/` | Product UX and client interactions | [`docs/02-development/frontend-web.md`](docs/02-development/frontend-web.md), [`docs/02-development/mobile-expo.md`](docs/02-development/mobile-expo.md), [`docs/04-decisions/0004-typescript-nextjs-frontend.md`](docs/04-decisions/0004-typescript-nextjs-frontend.md) |
| **API Composition** | GraphQL router + contracts | Unified API across independent services | [`docs/01-architecture/graphql-and-contracts.md`](docs/01-architecture/graphql-and-contracts.md), [`docs/02-development/graphql-codegen.md`](docs/02-development/graphql-codegen.md) |
| **Domain Services** | Kotlin services in `service/kotlin/` | Core business logic with clear service boundaries | [`docs/01-architecture/service-boundaries.md`](docs/01-architecture/service-boundaries.md), [`docs/04-decisions/0003-kotlin-micronaut-backend.md`](docs/04-decisions/0003-kotlin-micronaut-backend.md) |
| **Async Workloads** | Go jobs/consumers in `workers/golang/` | Batch ingestion, enrichment, event-driven processing | [`docs/04-decisions/0018-nx-monorepo-tooling.md`](docs/04-decisions/0018-nx-monorepo-tooling.md), [`docs/02-development/backend-kotlin.md`](docs/02-development/backend-kotlin.md) |
| **Data + Object Storage** | PostgreSQL, R2/MinIO | Transactional data + binary asset storage | [`docs/01-architecture/data-and-persistence.md`](docs/01-architecture/data-and-persistence.md), [`docs/04-decisions/0005-postgresql-database.md`](docs/04-decisions/0005-postgresql-database.md), [`docs/04-decisions/0007-asset-service-cloudflare-r2.md`](docs/04-decisions/0007-asset-service-cloudflare-r2.md) |
| **Infrastructure** | Docker, K3S/Kubernetes, Vault, Flux | Repeatable, auditable operations from dev to prod | [`docs/01-architecture/infrastructure.md`](docs/01-architecture/infrastructure.md), [`docs/05-runbooks/kubernetes-operations.md`](docs/05-runbooks/kubernetes-operations.md) |
| **Observability** | Prometheus, Grafana, Loki, Promtail | Health visibility, troubleshooting, SLO tracking | [`docs/01-architecture/observability.md`](docs/01-architecture/observability.md), [`docs/04-decisions/0013-observability-architecture.md`](docs/04-decisions/0013-observability-architecture.md) |
| **Delivery** | GitHub Actions + Nx + Flux GitOps | Faster CI and controlled production changes | [`.github/workflows/ci.yml`](.github/workflows/ci.yml), [`docs/05-runbooks/deployment-web.md`](docs/05-runbooks/deployment-web.md), [`docs/04-decisions/0020-external-gitops-inventory-boundary.md`](docs/04-decisions/0020-external-gitops-inventory-boundary.md) |

## Topic Breakdown (Summary First, Deep Dives in Docs)

### 1. Services vs Workers

| Topic | Why We Use It | Where to Read |
| --- | --- | --- |
| **Explicit runtime separation** (`service/` vs `workers/`) | Keeps sync APIs and async workloads isolated for clearer ownership and safer deployments | [`docs/01-architecture/system.md`](docs/01-architecture/system.md), [`docs/04-decisions/0018-nx-monorepo-tooling.md`](docs/04-decisions/0018-nx-monorepo-tooling.md) |
| **Monorepo boundaries with Nx tags/constraints** | Preserves architectural guardrails and prevents cross-layer coupling | [`docs/04-decisions/0001-monorepo-architecture.md`](docs/04-decisions/0001-monorepo-architecture.md), [`docs/04-decisions/0018-nx-monorepo-tooling.md`](docs/04-decisions/0018-nx-monorepo-tooling.md) |
| **Batch and consumer standards** | Consistent pattern for ingestion jobs, retries, observability, and operations | [`docs/02-development/backend-kotlin.md`](docs/02-development/backend-kotlin.md), [`docs/05-runbooks/banking-pdf-import-retry-dlq.md`](docs/05-runbooks/banking-pdf-import-retry-dlq.md) |

### 2. Cloudflare (Edge + Storage)

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Cloudflare CDN/DNS/TLS** | Edge routing, HTTPS termination model, domain setup, cache behavior | [`docs/05-runbooks/cloudflare.md`](docs/05-runbooks/cloudflare.md), [`docs/05-runbooks/deployment-web.md`](docs/05-runbooks/deployment-web.md) |
| **Cloudflare R2 for assets** | S3-compatible object storage with clear public/private bucket strategy | [`docs/05-runbooks/cloudflare-r2.md`](docs/05-runbooks/cloudflare-r2.md), [`docs/04-decisions/0007-asset-service-cloudflare-r2.md`](docs/04-decisions/0007-asset-service-cloudflare-r2.md) |
| **Future edge-worker paths** | On-demand image/asset handling and delivery hardening options | [`docs/04-decisions/0007-asset-service-cloudflare-r2.md`](docs/04-decisions/0007-asset-service-cloudflare-r2.md) |

### 3. Security and Platform Reliability

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Authentication + Authorization** | User and service identity, permission enforcement, token flow | [`docs/01-architecture/security.md`](docs/01-architecture/security.md), [`docs/04-decisions/0006-frontend-authorization-layer.md`](docs/04-decisions/0006-frontend-authorization-layer.md) |
| **Service-to-service security** | Secure internal calls across polyglot services | [`docs/01-architecture/security.md`](docs/01-architecture/security.md), [`docs/04-decisions/0008-interservice-security.md`](docs/04-decisions/0008-interservice-security.md) |
| **Secrets management** | Vault-based secret lifecycle and Kubernetes integration | [`docs/05-runbooks/vault.md`](docs/05-runbooks/vault.md), [`docs/05-runbooks/kubernetes-operations.md`](docs/05-runbooks/kubernetes-operations.md) |

### 4. CI/CD and GitOps with Flux

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Nx-optimized CI** | Runs checks only for affected projects to reduce cycle time | [`.github/workflows/ci.yml`](.github/workflows/ci.yml), [`docs/04-decisions/0018-nx-monorepo-tooling.md`](docs/04-decisions/0018-nx-monorepo-tooling.md) |
| **GitOps control plane (Flux CD)** | Declarative cluster reconciliation and auditable operations | [`docs/04-decisions/0020-external-gitops-inventory-boundary.md`](docs/04-decisions/0020-external-gitops-inventory-boundary.md), [`docs/01-architecture/infrastructure.md`](docs/01-architecture/infrastructure.md) |
| **Deployment gates and workflow** | Standardized progression from validation to release | [`docs/05-runbooks/deployment-web.md`](docs/05-runbooks/deployment-web.md), [`docs/03-agent-workflow/review-and-closeout.md`](docs/03-agent-workflow/review-and-closeout.md) |

### 5. Domain Use Cases Implemented in Invistus

Prefer `docs/` for engineering standards and the separate `spec/features` track for feature behavior memory.

| Use Case | Scope | Where to Read |
| --- | --- | --- |
| **Financial data ingestion and normalization** | Institutional and macroeconomic datasets | [`docs/01-architecture/system.md`](docs/01-architecture/system.md) |
| **Institution enrichment and ratings workloads** | Data enrichment workers and rating pipelines | [`docs/01-architecture/system.md`](docs/01-architecture/system.md) |
| **Personal finance product direction** | Accounts/cards flows and user-centric finance features | [`docs/README.md`](docs/README.md) |

### 6. Agents, Skills, and Spec-Driven Development

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Specification-Driven Development (SDD)** | Specs and durable docs guide delivery; agents implement and validate against them | [`docs/03-agent-workflow/context-contract.md`](docs/03-agent-workflow/context-contract.md), [`docs/03-agent-workflow/issue-delivery-flow.md`](docs/03-agent-workflow/issue-delivery-flow.md) |
| **Agent Skills** | Portable, reusable knowledge packages (`SKILL.md`) that Claude Code, Codex, and Cursor all auto-discover from one canonical source | [`docs/03-agent-workflow/context-contract.md`](docs/03-agent-workflow/context-contract.md), [`.agents/skills/`](.agents/skills/) |
| **neotool-dev-agent** | Slack-driven pipeline (issue → design → backlog → impl → review → validate → pr) that dispatches each stage to Claude, Codex, or Cursor | [`.devcontainer/README.md`](.devcontainer/README.md) |

**How skills are discovered.** Invistus follows the [agentskills.io](https://agentskills.io) open standard. Skills live in one canonical place, `.agents/skills/`, and each agent finds them from there:

| Agent | Reads from | How |
| --- | --- | --- |
| Codex | `.agents/skills/` | Native convention, no config needed |
| Cursor | `.agents/skills/` | Native convention, no config needed |
| Claude Code | `.claude/skills/` | Symlink to `.agents/skills/` (Claude Code doesn't support a custom skills path yet) |

Always edit skills in `.agents/skills/` — `.claude/skills` just follows it, nothing to keep in sync.

## Core Service Modules

| Service | Main Responsibility | Current Scope | Primary Docs |
| --- | --- | --- | --- |
| **security** (`service/kotlin/security`) | Identity, authentication, authorization, and cross-service trust | JWT-based auth flows, RBAC/authorization, service-to-service auth patterns, secret integration | [`docs/01-architecture/security.md`](docs/01-architecture/security.md), [`docs/04-decisions/0008-interservice-security.md`](docs/04-decisions/0008-interservice-security.md) |
| **assets** (`service/kotlin/assets`) | Asset upload, storage, and delivery pipeline | GraphQL-driven upload/confirm flow, metadata lifecycle, public/private delivery strategy over R2/MinIO | [`docs/04-decisions/0007-asset-service-cloudflare-r2.md`](docs/04-decisions/0007-asset-service-cloudflare-r2.md), [`docs/05-runbooks/cloudflare-r2.md`](docs/05-runbooks/cloudflare-r2.md) |
| **comms** (`service/kotlin/comms`) | Centralized communication layer (sync + async) | Current implementation prioritizes email orchestration via Kafka + DLQ and GraphQL API; chat/templates/channels are staged in roadmap | [`docs/05-runbooks/comms-email-operations.md`](docs/05-runbooks/comms-email-operations.md), [`docs/01-architecture/system.md`](docs/01-architecture/system.md) |
| **financialdata** (`service/kotlin/financialdata`) | Financial data ingestion and enrichment backbone for wealth features | BACEN IFData ingestion, institutional data search/restriction workflows, ratings/economic-indicator support with worker-assisted processing | [`docs/01-architecture/system.md`](docs/01-architecture/system.md), [`docs/01-architecture/service-boundaries.md`](docs/01-architecture/service-boundaries.md) |

## Patterns and Best Practices by Engineering Layer

| Layer | Key Patterns and Practices | Docs |
| --- | --- | --- |
| **Backend** | Layer rules, service/repository/mapper/resolver patterns, pagination, Kafka patterns | [`docs/02-development/backend-kotlin.md`](docs/02-development/backend-kotlin.md), [`docs/01-architecture/service-boundaries.md`](docs/01-architecture/service-boundaries.md) |
| **Frontend** | Query/mutation, management pages, shared UI, testing and E2E patterns | [`docs/02-development/frontend-web.md`](docs/02-development/frontend-web.md), [`docs/02-development/testing-strategy.md`](docs/02-development/testing-strategy.md) |
| **API Contracts** | GraphQL standards, query design, federation contract discipline | [`docs/01-architecture/graphql-and-contracts.md`](docs/01-architecture/graphql-and-contracts.md), [`docs/02-development/graphql-codegen.md`](docs/02-development/graphql-codegen.md) |
| **Security** | AuthN/AuthZ baseline, interservice trust, secret handling | [`docs/01-architecture/security.md`](docs/01-architecture/security.md), [`docs/05-runbooks/vault.md`](docs/05-runbooks/vault.md) |
| **Observability** | Golden signals, metrics/logging standards, alerting baseline | [`docs/01-architecture/observability.md`](docs/01-architecture/observability.md) |
| **Operations** | K8S runbooks, Cloudflare/R2 setup, staging/prod operational flows | [`docs/05-runbooks/README.md`](docs/05-runbooks/README.md) |


## Local Development

The dev environment runs inside a [DevPod](https://devpod.sh) container (Docker-in-Docker).
All services, infra, and tooling are reproducible from a single command.

### Prerequisites (Mac)

**1. Colima** — Docker runtime for Mac

```bash
brew install colima docker docker-compose

# Starts Colima (14GB RAM + 4GB swap on 18GB Macs) to prevent OOM during
# Next.js/Kotlin compilation and Playwright E2E with multiple services
./scripts/colima-start.sh
```

**2. DevPod CLI**

```bash
brew install --cask devpod
devpod provider add docker
devpod provider use docker
```

**3. Vault JWT keys backup**

The Vault instance inside DevPod starts empty on every `devpod up`. JWT keys are
restored automatically from a local backup file bind-mounted into the container.

Create the backup once (after generating your keys for the first time):

```bash
mkdir -p ~/.vault-keys
# Inside DevPod, after running neotool vault create-secret kid-1:
neotool vault backup --output ~/.vault-keys/backup.json
```

From that point on, every `neotool dev up` restores the keys automatically.

---

### First-time setup

```bash
# 0. Authenticate with GitHub Container Registry (required to pull devcontainer features)
echo $GH_TOKEN | docker login ghcr.io -u <your-github-username> --password-stdin

# 1. Start DevPod workspace (builds image, starts infra, restores Vault keys)
./neotool dev up

# 2. Start the security service first (other services depend on it for JWT validation)
./neotool dev start security
# Auto-attaches to tmux. Wait for "Micronaut... startup completed". Ctrl+b d to detach.

# 3. Register all service principals and write generated secrets to .env.local
./neotool dev seed

# 4. Start all remaining services and workers
./neotool dev start
```

---

### Daily workflow

```bash
./neotool dev up            # resume workspace + activate port forwarding (idempotent)
./neotool dev start           # start all services and workers
./neotool dev status          # check ports and tmux windows
./neotool dev attach          # open tmux session
./neotool dev attach web      # jump directly to a service window
./neotool dev logs banking    # tail logs without attaching
./neotool dev restart web     # restart a single service
./neotool dev stop            # stop all
```

**Services:** `security` (8080) · `assets` (8082) · `assistant` (8083) · `comms` (8084) · `financialdata` (8091) · `banking` (8092) · `web` (3000)

**Workers:** `asset-brand` · `b3-cotahist` · `b3-corporate-events` · `bacen-ifdata` · `cvm-funds` · `indicators` · `institution-consumer` · `ratings` · `pdf-extractor`

---

### Validating changes

```bash
./neotool validate                      # web + mobile + service + security
./neotool validate web                  # a single target
./neotool validate web mobile           # several targets
./neotool validate service:banking      # one backend service
./neotool validate security             # Trivy only
./neotool validate e2e                  # Playwright pre-push bundle (opt-in)
```

Checks run **in parallel across a dependency graph**, not in fixed lanes. Anything
whose prerequisites are met starts immediately — `lint` doesn't wait behind `test`,
and web/mobile/service/security all progress at once. On a TTY you get a live frame
showing each task's status, elapsed time, and current output line:

```
neotool validate · 7/14 done · 2 running · 1 failed · 1m 48s

  ✔ web:typecheck     6s
  ✖ web:test         22s        ▸ 3 failing
  ⠹ web:coverage     18s        ▸ transactions/summary.test.tsx (142/310)
  ⠿ service:coverage  queued (needs service:test)
```

| Option | Effect |
|--------|--------|
| `-j N` | Max concurrent tasks (default: cores/2, clamped 2–8). `-j 1` = fully sequential |
| `--only STEPS` | Restrict to `typecheck,lint,test,coverage` |
| `--skip STEPS` | Drop steps; dependents are pruned transitively |
| `--changed` | Only targets touched vs `origin/main` |
| `--fail-fast` | Cancel in-flight work on first failure |
| `--reporter R` | `auto` (default) · `live` · `plain` · `json` · `github` |
| `--log-dir DIR` | Defaults to `.neotool/validate/<run-id>/` |
| `--list` | Print the resolved task graph and exit without running |

Logs for every task persist (passing ones too). On failure you get a tool-aware
excerpt — `error TS` for tsc, `FAILURE:` / `e:` for gradle, and so on — plus a
copy-pasteable repro:

```
  ✖ web:typecheck failed (rc 2)

    src/features/transactions/summary.ts(88,7): error TS2322: ...

    reproduce:  cd web && pnpm run typecheck
    full log:   .neotool/validate/20260719-104233/web-typecheck.log
```

**Security** mirrors the `security-scan` job in `.github/workflows/ci.yml`
(`fs`, `CRITICAL,HIGH`, exit code 1). It falls back to `docker run aquasec/trivy`
when `trivy` isn't on `PATH`, honours a repo-root `.trivyignore` via `--ignorefile`,
and **fails rather than silently skipping** when neither tool is available — pass
`--allow-missing-tools` to downgrade that to a warning.

```bash
./neotool validate security --scan fs,config,secret
./neotool validate security --severity CRITICAL --format json
```

> **Note:** `e2e` is excluded from the default run — it's slow and needs a running
> stack. The spec list lives in `web/tests/e2e/prepush.txt`; the long-term plan is
> to tag specs `@prepush` and drop the manifest.

**Migrating from the old flags** — these were removed in favour of subcommands and
now exit with the replacement rather than "unknown option":

| Old | New |
|-----|-----|
| `--web` / `--mobile` | `validate web` / `validate mobile` |
| `--service NAME` | `validate service:NAME` |
| `--skip-coverage` | `--skip coverage` |
| `--parallel` | (now the default; tune with `-j N`) |
| `--no-parallel` | `-j 1` |

---

### Port reference

| Service | Port |
|---------|------|
| Web (Next.js) | 3000 |
| Security | 8080 |
| Assets | 8082 |
| Assistant | 8083 |
| Comms | 8084 |
| FinancialData | 8091 |
| Banking | 8092 |
| Apollo Router (GraphQL) | 4000 |
| Postgres (direct) | 5432 |
| Postgres (pgbouncer) | 6432 |
| Kafka | 9094 |
| MinIO console | 9001 |
| Vault UI | 8200 |

---

### Web service — troubleshooting

**Slow compilation or high CPU (`next-server` > 400% CPU)**

The Next.js webpack cache (`.next/`) can grow large over time and cause the `next-server`
process to consume excessive memory, which triggers continuous GC and saturates all CPU cores.

Fix: clear the cache before restarting.

```bash
neotool dev restart web --clean
```

Use `--clean` whenever the web service feels slow, takes too long to compile a route, or
`ps aux | grep next-server` shows CPU consistently above 100%.

Normal restarts (`neotool dev restart web`) preserve the cache for faster startup.

---

### Vault keys — troubleshooting

If the security service returns HTTP 500 on `/.well-known/jwks.json`, the JWT keys
are missing from Vault. Restore manually from inside the DevPod:

```bash
neotool vault restore --secret-path secret/jwt/keys --input ~/.vault-keys/backup.json

# Verify
docker exec -e VAULT_ADDR=http://localhost:8200 -e VAULT_TOKEN=myroot \
  neotool-vault vault kv list secret/jwt/keys

./neotool dev restart security
```

> Full DevPod documentation: [`.devcontainer/README.md`](.devcontainer/README.md)

## Monorepo Layout

| Path | Purpose |
| --- | --- |
| [`web/`](web/) | Next.js web application |
| [`mobile/`](mobile/) | Mobile codebase and migration work |
| [`service/kotlin/`](service/kotlin/) | Kotlin services and shared modules |
| [`workers/golang/`](workers/golang/) | Go async workloads (jobs/consumers) |
| [`contracts/`](contracts/) | GraphQL/OpenAPI contracts |
| [`infra/`](infra/) | Local infrastructure, Terraform, observability configs, scripts |
| [`docs/`](docs/) | Durable engineering docs (architecture, development, ADRs, runbooks) |

## Start Reading Here

1. Engineering docs entry: [`docs/README.md`](docs/README.md)
2. System architecture: [`docs/01-architecture/system.md`](docs/01-architecture/system.md)
3. Why these decisions: [`docs/04-decisions/README.md`](docs/04-decisions/README.md)
4. Runbooks: [`docs/05-runbooks/README.md`](docs/05-runbooks/README.md)
5. Full catalog: [`docs/index.md`](docs/index.md)
