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

- Spec-driven product development with architecture and standards living in `docs/`
- Polyglot monorepo with explicit separation between API services and async workers
- Federated GraphQL platform with Kotlin services and Next.js frontend
- Cloudflare integration for edge delivery (CDN/DNS/TLS) and object storage (R2)
- GitOps operations with Flux CD and Kubernetes runbooks
- CI pipeline optimized with Nx affected detection across Kotlin, web, and Go worker projects

## Layered Architecture Map

| Layer | Components | What It Solves | Primary Docs |
| --- | --- | --- | --- |
| **Experience** | `web/`, `mobile/` | Product UX and client interactions | [`docs/02-architecture/frontend-architecture.md`](docs/02-architecture/frontend-architecture.md), [`docs/92-adr/0004-typescript-nextjs-frontend.md`](docs/92-adr/0004-typescript-nextjs-frontend.md) |
| **API Composition** | GraphQL router + contracts | Unified API across independent services | [`docs/02-architecture/api-architecture.md`](docs/02-architecture/api-architecture.md), [`docs/06-contracts/graphql-standards.md`](docs/06-contracts/graphql-standards.md) |
| **Domain Services** | Kotlin services in `service/kotlin/` | Core business logic with clear service boundaries | [`docs/02-architecture/service-architecture.md`](docs/02-architecture/service-architecture.md), [`docs/92-adr/0003-kotlin-micronaut-backend.md`](docs/92-adr/0003-kotlin-micronaut-backend.md) |
| **Async Workloads** | Go jobs/consumers in `workers/golang/` | Batch ingestion, enrichment, event-driven processing | [`docs/92-adr/0003-nx-monorepo-tooling.md`](docs/92-adr/0003-nx-monorepo-tooling.md), [`docs/05-backend/patterns/batch-processing-pattern.md`](docs/05-backend/patterns/batch-processing-pattern.md) |
| **Data + Object Storage** | PostgreSQL, R2/MinIO | Transactional data + binary asset storage | [`docs/02-architecture/data-architecture.md`](docs/02-architecture/data-architecture.md), [`docs/92-adr/0005-postgresql-database.md`](docs/92-adr/0005-postgresql-database.md), [`docs/92-adr/0007-asset-service-cloudflare-r2.md`](docs/92-adr/0007-asset-service-cloudflare-r2.md) |
| **Infrastructure** | Docker, K3S/Kubernetes, Vault, Flux | Repeatable, auditable operations from dev to prod | [`docs/11-infrastructure/architecture.md`](docs/11-infrastructure/architecture.md), [`docs/11-infrastructure/k8s-runbook.md`](docs/11-infrastructure/k8s-runbook.md) |
| **Observability** | Prometheus, Grafana, Loki, Promtail | Health visibility, troubleshooting, SLO tracking | [`docs/10-observability/observability-overview.md`](docs/10-observability/observability-overview.md), [`docs/03-features/infrastructure/observability/observability-vps-k3s.md`](docs/03-features/infrastructure/observability/observability-vps-k3s.md) |
| **Delivery** | GitHub Actions + Nx + Flux GitOps | Faster CI and controlled production changes | [`.github/workflows/ci.yml`](.github/workflows/ci.yml), [`docs/11-infrastructure/k8s-runbook.md`](docs/11-infrastructure/k8s-runbook.md), [`docs/08-workflows/deployment-workflow.md`](docs/08-workflows/deployment-workflow.md) |

## Topic Breakdown (Summary First, Deep Dives in Docs)

### 1. Services vs Workers

| Topic | Why We Use It | Where to Read |
| --- | --- | --- |
| **Explicit runtime separation** (`service/` vs `workers/`) | Keeps sync APIs and async workloads isolated for clearer ownership and safer deployments | [`docs/92-adr/0003-nx-monorepo-tooling.md`](docs/92-adr/0003-nx-monorepo-tooling.md) |
| **Monorepo boundaries with Nx tags/constraints** | Preserves architectural guardrails and prevents cross-layer coupling | [`docs/11-infrastructure/nx-configuration.md`](docs/11-infrastructure/nx-configuration.md), [`docs/92-adr/0003-nx-monorepo-tooling.md`](docs/92-adr/0003-nx-monorepo-tooling.md) |
| **Batch and consumer standards** | Consistent pattern for ingestion jobs, retries, observability, and operations | [`docs/05-backend/patterns/batch-processing-pattern.md`](docs/05-backend/patterns/batch-processing-pattern.md), [`docs/05-backend/patterns/kafka-consumer-pattern.md`](docs/05-backend/patterns/kafka-consumer-pattern.md) |

### 2. Cloudflare (Edge + Storage)

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Cloudflare CDN/DNS/TLS** | Edge routing, HTTPS termination model, domain setup, cache behavior | [`docs/11-infrastructure/cloudflare-runbook.md`](docs/11-infrastructure/cloudflare-runbook.md), [`docs/11-infrastructure/web-deployment-runbook.md`](docs/11-infrastructure/web-deployment-runbook.md) |
| **Cloudflare R2 for assets** | S3-compatible object storage with clear public/private bucket strategy | [`docs/11-infrastructure/cloudflare-r2-runbook.md`](docs/11-infrastructure/cloudflare-r2-runbook.md), [`docs/92-adr/0007-asset-service-cloudflare-r2.md`](docs/92-adr/0007-asset-service-cloudflare-r2.md), [`docs/03-features/assets/asset-service/README.md`](docs/03-features/assets/asset-service/README.md) |
| **Future edge-worker paths** | On-demand image/asset handling and delivery hardening options | [`docs/03-features/assets/asset-service/DECISIONS.md`](docs/03-features/assets/asset-service/DECISIONS.md) |

### 3. Security and Platform Reliability

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Authentication + Authorization** | User and service identity, permission enforcement, token flow | [`docs/09-security/authentication.md`](docs/09-security/authentication.md), [`docs/09-security/authorization.md`](docs/09-security/authorization.md) |
| **Service-to-service security** | Secure internal calls across polyglot services | [`docs/09-security/service-to-service-auth.md`](docs/09-security/service-to-service-auth.md), [`docs/92-adr/0008-interservice-security.md`](docs/92-adr/0008-interservice-security.md) |
| **Secrets management** | Vault-based secret lifecycle and Kubernetes integration | [`docs/09-security/vault-setup.md`](docs/09-security/vault-setup.md), [`docs/11-infrastructure/k8s-runbook.md`](docs/11-infrastructure/k8s-runbook.md) |

### 4. CI/CD and GitOps with Flux

| Topic | What It Tackles | Where to Read |
| --- | --- | --- |
| **Nx-optimized CI** | Runs checks only for affected projects to reduce cycle time | [`.github/workflows/ci.yml`](.github/workflows/ci.yml), [`docs/92-adr/0003-nx-monorepo-tooling.md`](docs/92-adr/0003-nx-monorepo-tooling.md) |
| **GitOps control plane (Flux CD)** | Declarative cluster reconciliation and auditable operations | [`docs/11-infrastructure/k8s-runbook.md`](docs/11-infrastructure/k8s-runbook.md), [`docs/11-infrastructure/architecture.md`](docs/11-infrastructure/architecture.md) |
| **Deployment gates and workflow** | Standardized progression from validation to release | [`docs/08-workflows/deployment-workflow.md`](docs/08-workflows/deployment-workflow.md), [`docs/11-infrastructure/web-deployment-runbook.md`](docs/11-infrastructure/web-deployment-runbook.md) |

### 5. Domain Use Cases Implemented in Invistus

| Use Case | Scope | Where to Read |
| --- | --- | --- |
| **Financial data ingestion and normalization** | Institutional and macroeconomic datasets | [`docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md`](docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md), [`docs/03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md`](docs/03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md) |
| **Institution enrichment and ratings workloads** | Data enrichment workers and rating pipelines | [`docs/03-features/financial_data/institution-enhancement/institution-enhancement.md`](docs/03-features/financial_data/institution-enhancement/institution-enhancement.md), [`docs/03-features/financial_data/ratings/financial-institution-ratings.md`](docs/03-features/financial_data/ratings/financial-institution-ratings.md) |
| **Personal finance product direction** | Accounts/cards flows and user-centric finance features | [`docs/03-features/personal-finance/personal-finance-overview.md`](docs/03-features/personal-finance/personal-finance-overview.md), [`docs/03-features/personal-finance/phase-1-accounts-cards.md`](docs/03-features/personal-finance/phase-1-accounts-cards.md) |

## Core Service Modules

| Service | Main Responsibility | Current Scope | Primary Docs |
| --- | --- | --- | --- |
| **security** (`service/kotlin/security`) | Identity, authentication, authorization, and cross-service trust | JWT-based auth flows, RBAC/authorization, service-to-service auth patterns, secret integration | [`docs/03-features/security/README.md`](docs/03-features/security/README.md), [`docs/09-security/authentication.md`](docs/09-security/authentication.md), [`docs/09-security/service-to-service-auth.md`](docs/09-security/service-to-service-auth.md) |
| **assets** (`service/kotlin/assets`) | Asset upload, storage, and delivery pipeline | GraphQL-driven upload/confirm flow, metadata lifecycle, public/private delivery strategy over R2/MinIO | [`docs/03-features/assets/asset-service/README.md`](docs/03-features/assets/asset-service/README.md), [`docs/03-features/assets/asset-service/DECISIONS.md`](docs/03-features/assets/asset-service/DECISIONS.md), [`docs/92-adr/0007-asset-service-cloudflare-r2.md`](docs/92-adr/0007-asset-service-cloudflare-r2.md) |
| **comms** (`service/kotlin/comms`) | Centralized communication layer (sync + async) | Current implementation prioritizes email orchestration via Kafka + DLQ and GraphQL API; chat/templates/channels are staged in roadmap | [`docs/03-features/comms/comms-module-requirements.md`](docs/03-features/comms/comms-module-requirements.md), [`docs/03-features/comms/comms-runbook.md`](docs/03-features/comms/comms-runbook.md), [`docs/03-features/comms/template-engine-requirements.md`](docs/03-features/comms/template-engine-requirements.md) |
| **financialdata** (`service/kotlin/financialdata`) | Financial data ingestion and enrichment backbone for wealth features | BACEN IFData ingestion, institutional data search/restriction workflows, ratings/economic-indicator support with worker-assisted processing | [`docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md`](docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md), [`docs/03-features/financial_data/bacen-institution-data-report/institution-search-service.md`](docs/03-features/financial_data/bacen-institution-data-report/institution-search-service.md), [`docs/03-features/financial_data/ratings/financial-institution-ratings.md`](docs/03-features/financial_data/ratings/financial-institution-ratings.md), [`docs/03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md`](docs/03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md) |

## Patterns and Best Practices by Engineering Layer

| Layer | Key Patterns and Practices | Docs |
| --- | --- | --- |
| **Backend** | Layer rules, service/repository/mapper/resolver patterns, pagination, Kafka patterns | [`docs/05-backend/standards/layer-rules.md`](docs/05-backend/standards/layer-rules.md), [`docs/05-backend/patterns/`](docs/05-backend/patterns/) |
| **Frontend** | Query/mutation, management pages, shared UI, testing and E2E patterns | [`docs/07-frontend/patterns/`](docs/07-frontend/patterns/) |
| **API Contracts** | GraphQL standards, query design, federation contract discipline | [`docs/06-contracts/`](docs/06-contracts/) |
| **Security** | AuthN/AuthZ baseline, interservice trust, secret handling | [`docs/09-security/`](docs/09-security/) |
| **Observability** | Golden signals, metrics/logging standards, alerting baseline | [`docs/10-observability/observability-overview.md`](docs/10-observability/observability-overview.md) |
| **Operations** | K8S runbooks, Cloudflare/R2 setup, staging/prod operational flows | [`docs/11-infrastructure/`](docs/11-infrastructure/) |


## Local Development

The dev environment runs inside a [DevPod](https://devpod.sh) container (Docker-in-Docker).
All services, infra, and tooling are reproducible from a single command.

### Prerequisites (Mac)

**1. Colima** — Docker runtime for Mac

```bash
brew install colima docker docker-compose
colima start --cpu 6 --memory 12 --disk 80 --vm-type vz --vz-rosetta
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

From that point on, every `neotool dev init` restores the keys automatically.

---

### First-time setup

```bash
# 0. Authenticate with GitHub Container Registry (required to pull devcontainer features)
echo $GH_TOKEN | docker login ghcr.io -u <your-github-username> --password-stdin

# 1. Start DevPod workspace (builds image, starts infra, restores Vault keys)
./neotool dev init

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
./neotool dev init            # resume workspace + activate port forwarding (idempotent)
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
| [`docs/`](docs/) | Architecture, ADRs, features, runbooks, standards |

## Start Reading Here

1. Architecture overview: [`docs/02-architecture/system-architecture.md`](docs/02-architecture/system-architecture.md)
2. Why these decisions: [`docs/92-adr/README.md`](docs/92-adr/README.md)
3. Infrastructure and GitOps: [`docs/11-infrastructure/README.md`](docs/11-infrastructure/README.md)
4. Full docs index: [`docs/MANIFEST.md`](docs/MANIFEST.md)
