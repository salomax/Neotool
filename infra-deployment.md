# Neotool Infrastructure Implementation Plan

## Executive Summary

Comprehensive Kubernetes infrastructure for Neotool microservices platform using lightweight K3S for both local development and production deployment in VPC.

**Production Specifications**:
- K3S cluster in VPC (not cloud-managed K8S)
- Node spec: 8 CPU / 32GB RAM per node
- Storage: Cloudflare R2 (not MinIO in production)
- Security: HashiCorp Vault for JWT key management (no rotation initially)
- Observability: Prometheus v2.55.1, Grafana 11.1.4, Loki 2.9.0

**Component Versions** (matching [docker-compose.local.yml](infra/docker/docker-compose.local.yml)):
- PostgreSQL: `postgres:18rc1`
- PgBouncer: `edoburu/pgbouncer:latest`
- Kafka: `apache/kafka:3.7.0` (KRaft mode)
- Vault: `hashicorp/vault:1.21.1`
- Apollo Router: `ghcr.io/apollographql/router:v2.7.0`
- MinIO: `minio/minio:latest` (dev only)

**Out of Scope**:
- Prefect workflows (not included)
- bacen_ifdata workflow (not included)
- Multi-cloud abstractions (K3S only)

**Timeline**: 2 weeks for local K3S + production VPC deployment

---

## Current State

### Existing Architecture
- **Services**: 5 Kotlin/Micronaut microservices (App, Security, Assets, Assistant, Common)
- **Frontend**: Next.js with Apollo Client
- **Gateway**: Apollo Router (GraphQL Federation)
- **Data Layer**: PostgreSQL 18 + PgBouncer, Apache Kafka 3.7.0 (KRaft)
- **Storage**: MinIO (S3-compatible)
- **Security**: Vault integration already coded in Security service
- **Observability**: Prometheus v2.55.1, Grafana 11.1.4, Loki 2.9.0, Promtail 2.9.0
- **Deployment**: Docker Compose only (no K8S/Terraform)
- **CI/CD**: GitHub Actions with staging/production pipelines

### Missing Components
- No Terraform configurations for K3S
- No Kubernetes manifests
- No Helm charts
- No production VPC deployment infrastructure

---

## Implementation Overview

### 1. Directory Structure

```
infra/
├── terraform/                         # Infrastructure as Code
│   ├── modules/
│   │   ├── k3s/                      # K3S cluster module
│   │   ├── networking/               # Multi-cloud network abstractions
│   │   ├── storage/                  # Storage class abstractions
│   │   └── observability/            # Observability stack
│   ├── environments/
│   │   ├── local/                    # K3S local (start here)
│   │   ├── dev/                      # K3S dev cluster
│   │   ├── staging/                  # Cloud K8S (AWS/GCP)
│   │   └── prod/                     # Cloud K8S production
│   └── scripts/
│       ├── init-k3s.sh
│       └── destroy-k3s.sh
├── kubernetes/                        # K8S manifests
│   ├── base/                         # Base Kustomize manifests
│   │   ├── namespaces/
│   │   ├── databases/                # PostgreSQL + PgBouncer
│   │   ├── kafka/                    # Kafka StatefulSet
│   │   ├── vault/                    # Vault + JWT init job
│   │   ├── minio/
│   │   ├── services/                 # Kotlin services
│   │   ├── web/                      # Next.js frontend
│   │   ├── router/                   # Apollo Router
│   │   ├── workflows/                # Prefect server/workers
│   │   ├── cronjobs/                 # bacen_ifdata CronJob
│   │   └── observability/            # Prometheus, Grafana, Loki
│   ├── overlays/                     # Environment overlays
│   │   ├── local/
│   │   ├── dev/
│   │   ├── staging/
│   │   └── prod/
│   └── scripts/
│       ├── deploy.sh
│       └── rollback.sh
├── helm/                             # Helm charts
│   ├── neotool/                      # Umbrella chart
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   ├── values-prod.yaml
│   │   └── charts/                   # Subcharts
│   ├── charts/
│   │   ├── neotool-service/          # Generic service chart
│   │   ├── neotool-workflow/
│   │   └── neotool-cronjob/
│   └── scripts/
│       ├── install.sh
│       └── upgrade.sh
├── docs/                             # Documentation
│   ├── 01-architecture/
│   │   ├── overview.md
│   │   ├── networking.md
│   │   ├── security.md
│   │   └── multi-cloud-strategy.md
│   ├── 02-deployment/
│   │   ├── k3s-setup.md
│   │   ├── terraform-guide.md
│   │   ├── helm-guide.md
│   │   └── migration-path.md
│   ├── 03-operations/
│   │   ├── runbook-deployment.md
│   │   ├── runbook-rollback.md
│   │   ├── runbook-scaling.md
│   │   └── troubleshooting.md
│   ├── 04-security/
│   │   ├── vault-setup.md
│   │   ├── jwt-key-management.md
│   │   └── secrets-management.md
│   └── 07-reference/
│       ├── terminology.md
│       ├── best-practices.md
│       └── adr/                      # Architecture Decision Records
└── docker/                           # Keep existing Docker Compose
```

---

## 2. Core Components Design

### 2.1 Terraform Modules

#### K3S Module (`terraform/modules/k3s/`)
**Purpose**: Lightweight Kubernetes for local/dev environments

**Key Features**:
- Single-node or HA (3+ nodes) configurations
- Traefik ingress included
- Local-path storage provisioner
- Containerd runtime

**Critical Variables**:
```hcl
variable "cluster_name" {}
variable "node_count" { default = 1 }
variable "disable_components" { default = [] }
```

#### Networking Module (`terraform/modules/networking/`)
**Purpose**: Multi-cloud network abstraction

**Supported Providers**:
- `local`: Bridge networking for K3S
- `aws`: VPC + Subnets + NAT + Security Groups
- `gcp`: VPC Network + Cloud NAT + Firewall Rules
- `azure`: VNet + NAT Gateway + NSGs

#### Storage Module (`terraform/modules/storage/`)
**Purpose**: Abstract storage classes

**Storage Classes by Provider**:
- Local: `local-path` (K3S default)
- AWS: `ebs-gp3`, `efs`
- GCP: `pd-ssd`, `filestore`

### 2.2 Kubernetes Namespaces

```yaml
neotool-app           # Application services
neotool-web           # Frontend
neotool-data          # PostgreSQL, PgBouncer
neotool-messaging     # Kafka
neotool-storage       # MinIO
neotool-security      # Vault
neotool-workflows     # Prefect, CronJobs
neotool-observability # Prometheus, Grafana, Loki
```

### 2.3 Service Deployment Pattern

All Kotlin services follow this standard pattern:

**Init Container** (Critical for Security):
```yaml
initContainers:
  - name: vault-init
    image: hashicorp/vault:1.21.1
    command: ["/bin/sh", "-c"]
    args:
      - |
        # Check if JWT keys exist in Vault
        if ! vault kv get secret/jwt/keys/default; then
          echo "Generating 4096-bit RSA key pair..."
          openssl genpkey -algorithm RSA -out /tmp/private.pem -pkeyopt rsa_keygen_bits:4096
          openssl rsa -pubout -in /tmp/private.pem -out /tmp/public.pem

          # Store in Vault KV
          vault kv put secret/jwt/keys/default \
            private=@/tmp/private.pem \
            public=@/tmp/public.pem

          rm -f /tmp/*.pem
        fi
    env:
      - name: VAULT_ADDR
        value: "http://vault.neotool-security.svc.cluster.local:8200"
      - name: VAULT_TOKEN
        valueFrom:
          secretKeyRef:
            name: vault-token
            key: token
```

**Main Container Environment**:
```yaml
env:
  - name: MICRONAUT_ENVIRONMENTS
    value: "kubernetes"
  - name: POSTGRES_HOST
    value: "pgbouncer.neotool-data.svc.cluster.local"
  - name: POSTGRES_PORT
    value: "6432"
  - name: VAULT_ENABLED
    value: "true"
  - name: VAULT_ADDRESS
    value: "http://vault.neotool-security.svc.cluster.local:8200"
  - name: KAFKA_BOOTSTRAP_SERVERS
    value: "kafka.neotool-messaging.svc.cluster.local:9092"
```

**Resource Limits**:
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

**Health Checks**:
```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

### 2.4 Database Resources

#### PostgreSQL StatefulSet
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: neotool-data
spec:
  serviceName: postgres-headless
  replicas: 1  # Single for dev, 3 for prod HA
  volumeClaimTemplates:
    - metadata:
        name: postgres-storage
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: local-path
        resources:
          requests:
            storage: 20Gi
```

#### PgBouncer Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pgbouncer
  namespace: neotool-data
spec:
  replicas: 1
  # Connection pooling config from existing infra/pgbouncer/
```

### 2.5 Kafka StatefulSet (KRaft Mode)

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  namespace: neotool-messaging
spec:
  serviceName: kafka-headless
  replicas: 1  # Single for dev, 3+ for prod
  template:
    spec:
      containers:
        - name: kafka
          image: apache/kafka:3.7.0
          env:
            - name: KAFKA_PROCESS_ROLES
              value: "broker,controller"
            - name: KAFKA_LISTENERS
              value: "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093"
            - name: KAFKA_ADVERTISED_LISTENERS
              value: "PLAINTEXT://kafka.neotool-messaging.svc.cluster.local:9092"
  volumeClaimTemplates:
    - metadata:
        name: kafka-storage
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: local-path
        resources:
          requests:
            storage: 10Gi
```

### 2.6 Vault Setup

#### Vault Deployment (Dev Mode)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vault
  namespace: neotool-security
spec:
  containers:
    - name: vault
      image: hashicorp/vault:1.21.1
      command: ["vault", "server", "-dev"]
      env:
        - name: VAULT_DEV_ROOT_TOKEN_ID
          valueFrom:
            secretKeyRef:
              name: vault-token
              key: token
```

**Note**: For production, switch to HA mode with Raft storage (documented in runbook)

#### Vault JWT Init Job
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: vault-jwt-init
  namespace: neotool-security
spec:
  template:
    spec:
      restartPolicy: OnFailure
      containers:
        - name: vault-init
          image: hashicorp/vault:1.21.1
          command: ["/bin/sh", "-c"]
          args:
            - |
              echo "Waiting for Vault..."
              until vault status; do sleep 2; done

              if vault kv get secret/jwt/keys/default 2>/dev/null; then
                echo "JWT keys already exist"
                exit 0
              fi

              echo "Generating RSA 4096-bit key pair..."
              openssl genpkey -algorithm RSA -out /tmp/private.pem -pkeyopt rsa_keygen_bits:4096
              openssl rsa -pubout -in /tmp/private.pem -out /tmp/public.pem

              vault kv put secret/jwt/keys/default \
                private=@/tmp/private.pem \
                public=@/tmp/public.pem

              rm -f /tmp/*.pem
              echo "JWT keys stored successfully"
```

### 2.7 Observability Stack

**Versions** (matching [docker-compose.local.yml](infra/docker/docker-compose.local.yml)):
- Prometheus: `prom/prometheus:v2.55.1`
- Grafana: `grafana/grafana:11.1.4`
- Loki: `grafana/loki:2.9.0`
- Promtail: `grafana/promtail:2.9.0`
- PostgreSQL Exporter: `prometheuscommunity/postgres-exporter:latest`

#### Prometheus
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: neotool-observability
spec:
  containers:
    - name: prometheus
      image: prom/prometheus:v2.55.1
      volumeMounts:
        - name: config
          mountPath: /etc/prometheus
        - name: storage
          mountPath: /prometheus
```

**Scrape Config**:
- Kubernetes pod discovery (annotation-based)
- PostgreSQL exporter
- Kafka exporter
- All Kotlin services (Micrometer metrics at `/prometheus`)

#### Grafana
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: neotool-observability
spec:
  containers:
    - name: grafana
      image: grafana/grafana:11.1.4
      env:
        - name: GF_SECURITY_ADMIN_PASSWORD
          valueFrom:
            secretKeyRef:
              name: grafana-credentials
              key: password
```

**Pre-configured Dashboards**:
- Neotool service metrics
- PostgreSQL metrics
- Kafka metrics
- Kubernetes cluster overview

#### Loki + Promtail
```yaml
# Loki StatefulSet for log aggregation
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: loki
  namespace: neotool-observability

# Promtail DaemonSet for log collection
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: promtail
  namespace: neotool-observability
```

---

## 3. Helm Charts

### 3.1 Umbrella Chart (`helm/neotool/`)

**Chart.yaml Dependencies**:
```yaml
dependencies:
  - name: postgresql
    version: "12.x.x"
    repository: https://charts.bitnami.com/bitnami
    condition: postgresql.enabled
  - name: kafka
    version: "22.x.x"
    repository: https://charts.bitnami.com/bitnami
    condition: kafka.enabled
  - name: vault
    version: "0.25.x"
    repository: https://helm.releases.hashicorp.com
    condition: vault.enabled
```

**values.yaml Structure**:
```yaml
global:
  environment: dev
  cloudProvider: local  # local, aws, gcp, azure
  imageRegistry: ghcr.io/salomax

services:
  app:
    enabled: true
    replicas: 1
    autoscaling:
      enabled: true
      minReplicas: 1
      maxReplicas: 10
  security: { ... }
  assistant: { ... }
  assets: { ... }

web:
  enabled: true
  replicas: 2
  ingress:
    enabled: true
    hosts:
      - host: neotool.local

workflows:
  prefect:
    enabled: true
  cronjobs:
    bacenIfdata:
      enabled: true
      schedule: "0 7 * * *"

observability:
  prometheus:
    enabled: true
  grafana:
    enabled: true
  loki:
    enabled: true
```

**values-prod.yaml Overrides**:
```yaml
global:
  environment: prod
  cloudProvider: aws  # or gcp

services:
  app:
    replicas: 5
    autoscaling:
      minReplicas: 5
      maxReplicas: 50

postgresql:
  primary:
    persistence:
      storageClass: ebs-gp3
      size: 100Gi

kafka:
  controller:
    replicaCount: 3
  persistence:
    storageClass: ebs-gp3
```

### 3.2 Generic Service Chart (`helm/charts/neotool-service/`)

Reusable chart for all Kotlin microservices with:
- Init container for Vault JWT setup
- Deployment with rolling update strategy
- Service (ClusterIP)
- HPA (Horizontal Pod Autoscaler)
- ServiceMonitor (Prometheus)
- Configurable resources, health checks, env vars

**Usage**:
```bash
helm install neotool-app ./charts/neotool-service \
  --set serviceName=app \
  --set image.repository=ghcr.io/salomax/neotool-app \
  --set image.tag=v1.0.0
```

---

## 4. Multi-Cloud Strategy

### 4.1 Provider Abstractions

**Compute**:
- Local/Dev: K3S on bare metal/VMs
- AWS: EKS (Elastic Kubernetes Service)
- GCP: GKE (Google Kubernetes Engine)
- Azure: AKS (Azure Kubernetes Service)

**Storage Classes**:
```yaml
# Local K3S
storageClassName: local-path

# AWS
storageClassName: ebs-gp3

# GCP
storageClassName: pd-ssd

# Azure
storageClassName: managed-premium
```

**Ingress Controllers**:
- Local: Traefik (K3S default)
- AWS: AWS Load Balancer Controller
- GCP: GKE Ingress
- Azure: Application Gateway

### 4.2 Migration Path

**Phase 1: Local K3S (0-3 months)**
- Develop and test all manifests locally
- Validate Vault JWT integration
- Test workflows and CronJobs
- Establish observability baselines

**Phase 2: Cloud Dev/Staging (3-6 months)**
- Deploy to AWS EKS or GCP GKE
- Use managed databases (RDS/Cloud SQL)
- Enable auto-scaling
- Implement multi-AZ HA

**Phase 3: Production (6+ months)**
- Full production deployment
- Multi-region setup
- DR/backup strategies
- Advanced security (network policies, service mesh)

---

## 5. Security Implementation

### 5.1 JWT Key Management

**Key Generation Flow**:
1. Vault deployed in dev mode initially
2. `vault-jwt-init` Job runs once on cluster initialization
3. Job checks if `secret/jwt/keys/default` exists in Vault
4. If not exists: generates 4096-bit RSA key pair
5. Stores private/public keys in Vault KV v2
6. All services use init containers to verify keys exist

**No Key Rotation**: User requested no rotation initially; manual rotation process documented for future

**Key Storage Path**: `secret/jwt/keys/default` (KV v2 engine)

### 5.2 Secrets Management

**Development**:
- Kubernetes Secrets for basic credentials
- Vault in dev mode (single pod, in-memory)
- Init containers fetch secrets from Vault

**Production** (documented in runbook):
- Vault in HA mode with Raft storage
- External Secrets Operator for K8S↔Vault sync
- Vault CSI driver for secure secret injection
- Sealed Secrets for GitOps

### 5.3 Security Best Practices

- Network Policies to restrict pod-to-pod communication
- RBAC for Kubernetes service accounts
- Pod Security Standards (restricted profile)
- Regular security scanning of container images
- Audit logging enabled

---

## 6. Observability

### 6.1 Metrics

**Prometheus Exporters**:
- Kotlin services: Micrometer metrics at `/prometheus`
- PostgreSQL: postgres-exporter
- Kafka: kafka-exporter
- Apollo Router: built-in metrics at `:9090/metrics`

**ServiceMonitor CRDs**: Auto-discovery of all services with `prometheus.io/scrape: "true"` annotation

**Key Metrics**:
- HTTP request rate/latency/errors
- Database connection pool status
- Kafka consumer lag
- JVM metrics (heap, GC)
- Business metrics (signups, transactions)

### 6.2 Logging

**Stack**:
- Promtail: DaemonSet collecting container logs
- Loki: Centralized log aggregation with S3/MinIO backend
- Grafana: Log visualization and querying

**Log Format**: JSON structured logs with standard fields (timestamp, level, service, trace_id)

### 6.3 Dashboards

**Pre-configured Grafana Dashboards**:
- Neotool Overview (RPS, errors, latency)
- Service Metrics (per-service breakdown)
- PostgreSQL Metrics (from existing `postgresql-metrics.json`)
- Kafka Monitoring (consumer lag, throughput)
- Kubernetes Cluster Overview

---

## 7. Implementation Sequence

### 🚀 FAST TRACK: 2-Week Implementation (Prioritized for ASAP Delivery)

#### Week 1: Core Infrastructure (Days 1-7)

**Day 1-2: Foundation**
- [ ] Create directory structure in `infra/`
- [ ] Write Terraform K3S module (`terraform/modules/k3s/`)
- [ ] Deploy local K3S cluster
- [ ] Create all 8 Kubernetes namespaces
- [ ] Create base Kustomize structure

**Day 3-4: Data Layer**
- [ ] Deploy PostgreSQL StatefulSet + PgBouncer
- [ ] Deploy Kafka StatefulSet (KRaft mode, single node)
- [ ] Deploy MinIO
- [ ] Test database connectivity

**Day 5-6: Security**
- [ ] Deploy Vault in dev mode
- [ ] Create Vault JWT init Job
- [ ] Test JWT key generation and storage
- [ ] Create K8S secrets for credentials

**Day 7: Application Services**
- [ ] Create generic Helm service chart (`helm/charts/neotool-service/`)
- [ ] Deploy Security service with Vault init container
- [ ] Deploy App service
- [ ] Deploy Assistant service
- [ ] Deploy Assets service
- [ ] Verify service-to-service communication

#### Week 2: Frontend, Workflows & Observability (Days 8-14)

**Day 8-9: Frontend & Gateway**
- [ ] Deploy Next.js web frontend
- [ ] Deploy Apollo Router
- [ ] Configure Traefik ingress
- [ ] Test end-to-end GraphQL queries

**Day 10-11: Observability**
- [ ] Deploy Prometheus with scrape configs
- [ ] Deploy Grafana with datasources
- [ ] Import existing dashboards (PostgreSQL, services)
- [ ] Deploy Loki + Promtail
- [ ] Verify metrics/logs collection

**Day 12: Final Integration**
- [ ] End-to-end smoke tests
- [ ] Verify all services can communicate
- [ ] Test GraphQL queries through router
- [ ] Verify JWT token generation and validation
- [ ] Test database connections through PgBouncer

**Day 13: Production VPC Setup**
- [ ] Create Terraform config for production VPC
- [ ] Set up K3S in VPC environment
- [ ] Configure node specs (8 CPU / 32GB RAM)
- [ ] Set up Cloudflare R2 integration for production storage
- [ ] Test connectivity and security

**Day 14: Documentation**
- [ ] Core deployment runbook (`docs/03-operations/runbook-deployment.md`)
- [ ] Vault setup guide (`docs/04-security/vault-setup.md`)
- [ ] Architecture overview (`docs/01-architecture/overview.md`)
- [ ] Quick start guide

### 📋 Post-2-Week Backlog (Lower Priority)

**Phase 3: Production Hardening (Week 3-4)**
- [ ] Create production Helm values
- [ ] Implement HPAs for all services
- [ ] Set up network policies
- [ ] Configure proper backup procedures
- [ ] Security scanning (Trivy, Snyk)

**Phase 4: Advanced Documentation (Week 5-6)**
- [ ] Complete all runbooks (rollback, scaling, troubleshooting)
- [ ] Create ADRs (Architecture Decision Records)
- [ ] Multi-cloud migration playbook
- [ ] Best practices guide

**Phase 5: Cloud Migration Prep (Week 7+)**
- [ ] Create AWS/GCP Terraform modules
- [ ] Deploy staging to cloud K8S
- [ ] Test canary deployments
- [ ] Multi-region setup
- [ ] Final production checklist

---

## 8. Critical Files to Create

### Highest Priority (Week 1)

1. **`infra/terraform/modules/k3s/main.tf`**
   - Core K3S cluster provisioning
   - Starting point for entire infrastructure

2. **`infra/terraform/environments/local/main.tf`**
   - Local environment configuration
   - References K3S module

3. **`infra/kubernetes/base/namespaces/*.yaml`**
   - All 8 namespaces (app, web, data, messaging, storage, security, workflows, observability)

4. **`infra/kubernetes/base/databases/postgres-statefulset.yaml`**
   - PostgreSQL deployment
   - Foundation for all services

5. **`infra/kubernetes/base/databases/pgbouncer-deployment.yaml`**
   - Connection pooling
   - Used by all Kotlin services

6. **`infra/kubernetes/base/kafka/kafka-statefulset.yaml`**
   - Kafka KRaft mode deployment
   - Required for workflows

7. **`infra/kubernetes/base/vault/vault-deployment.yaml`**
   - Vault dev mode deployment
   - Security foundation

8. **`infra/kubernetes/base/vault/vault-init-job.yaml`**
   - JWT key generation job
   - Critical for security initialization

9. **`infra/helm/charts/neotool-service/Chart.yaml` + templates**
   - Generic service chart with init container
   - Reusable for all 5 Kotlin services

10. **`infra/helm/charts/neotool-service/templates/deployment.yaml`**
    - Service deployment template
    - Includes Vault init container pattern

### High Priority (Week 2)

11. **`infra/kubernetes/base/observability/prometheus/deployment.yaml`**
    - Metrics collection
    - ServiceMonitor integration

12. **`infra/kubernetes/base/observability/grafana/deployment.yaml`**
    - Dashboards and visualization

13. **`infra/terraform/modules/storage/cloudflare-r2.tf`**
    - Cloudflare R2 integration for production storage
    - S3-compatible configuration

14. **`infra/terraform/environments/prod/main.tf`**
    - Production VPC configuration
    - Node specifications (8 CPU / 32GB RAM)

15. **`infra/docs/03-operations/runbook-deployment.md`**
    - Primary operational guide
    - Step-by-step deployment instructions

### Medium Priority (Post-2-Weeks)

16. **`infra/helm/neotool/Chart.yaml` + `values.yaml`**
    - Umbrella chart definition (optional, can use Kustomize only)

17. **`infra/kubernetes/base/services/*/deployment.yaml`** (5 services)
    - App, Security, Assistant, Assets deployments

18. **`infra/docs/04-security/vault-setup.md`**
    - Vault configuration guide

19. **`infra/docs/04-security/jwt-key-management.md`**
    - JWT key lifecycle documentation

20. **`infra/docs/01-architecture/overview.md`**
    - Architecture documentation

21. **`infra/docs/02-deployment/cloudflare-r2-setup.md`**
    - Guide for configuring Cloudflare R2 storage backend

---

## 9. Key Decisions & Trade-offs

### 9.1 K3S vs Full Kubernetes
**Decision**: Start with K3S, design for K8S migration

**Rationale**:
- Faster local development
- Lower resource usage
- Simpler setup for dev/staging
- Standard K8S APIs ensure portability

**Trade-off**: Some cloud-native features unavailable in K3S, but Kustomize overlays handle differences

### 9.2 Kustomize + Helm (Not Either/Or)
**Decision**: Use both - Kustomize for base manifests, Helm for distribution

**Rationale**:
- Kustomize: Better for GitOps, clearer structure, easier debugging
- Helm: Better for versioning, templating, dependency management

**Pattern**: Base manifests in `kubernetes/base/`, environment patches in `overlays/`, Helm charts reference Kustomize

### 9.3 Init Containers vs Vault CSI Driver
**Decision**: Init containers initially, document CSI driver for production

**Rationale**:
- Init containers: Simpler, works everywhere, easier to debug
- CSI driver: More secure (no secret exposure), better for production
- Start simple, add complexity when needed

**Migration**: Phase 6 adds CSI driver support

### 9.4 Canary + Rolling Updates
**Decision**: Both deployment strategies supported

**Implementation**:
- Rolling updates: Native K8S (`maxSurge: 1, maxUnavailable: 0`)
- Canary: GitHub Actions already implements (10% → 50% → 100%)
- Helm supports both via values

### 9.5 Multi-Cloud Abstractions
**Decision**: Terraform modules per provider, Kustomize overlays per environment

**Abstraction Points**:
- Storage classes (local-path, ebs-gp3, pd-ssd)
- Ingress controllers (Traefik, ALB, GKE Ingress)
- Managed services (self-hosted vs RDS/Cloud SQL)

---

## 10. Risks & Mitigations

### Risk: Vault Dev Mode in Production
**Mitigation**: Comprehensive production Vault setup documented in `docs/04-security/vault-setup.md` with HA configuration

### Risk: JWT Keys Exposed in Logs
**Mitigation**: Init container cleans up temp files, production uses Vault CSI driver

### Risk: Data Loss During Migration
**Mitigation**: Backup/restore procedures documented, test migrations in dev/staging first

### Risk: K3S → Cloud K8S Incompatibilities
**Mitigation**: Use standard K8S APIs, test cloud staging early, Kustomize overlays handle differences

### Risk: Resource Exhaustion
**Mitigation**: Resource limits on all pods, HPA enabled, cluster autoscaling in cloud

---

## 11. Documentation Deliverables

### Architecture Documentation
- **`infra/docs/01-architecture/overview.md`**: System architecture, component diagram
- **`infra/docs/01-architecture/networking.md`**: Network topology, service mesh
- **`infra/docs/01-architecture/security.md`**: Security architecture, threat model
- **`infra/docs/01-architecture/multi-cloud-strategy.md`**: Cloud migration approach

### Deployment Documentation
- **`infra/docs/02-deployment/k3s-setup.md`**: K3S installation guide
- **`infra/docs/02-deployment/terraform-guide.md`**: Terraform usage
- **`infra/docs/02-deployment/helm-guide.md`**: Helm chart usage
- **`infra/docs/02-deployment/migration-path.md`**: K3S → Cloud K8S migration

### Operational Runbooks
- **`infra/docs/03-operations/runbook-deployment.md`**: Step-by-step deployment
- **`infra/docs/03-operations/runbook-rollback.md`**: Rollback procedures
- **`infra/docs/03-operations/runbook-scaling.md`**: Scaling guide (manual + auto)
- **`infra/docs/03-operations/troubleshooting.md`**: Common issues and fixes

### Security Documentation
- **`infra/docs/04-security/vault-setup.md`**: Vault deployment (dev + prod)
- **`infra/docs/04-security/jwt-key-management.md`**: JWT key generation, rotation
- **`infra/docs/04-security/secrets-management.md`**: K8S secrets, Vault integration

### Reference Documentation
- **`infra/docs/07-reference/terminology.md`**: Glossary of terms
- **`infra/docs/07-reference/best-practices.md`**: Operational best practices
- **`infra/docs/07-reference/adr/`**: Architecture Decision Records
  - `001-k3s-choice.md`
  - `002-vault-integration.md`
  - `003-multi-cloud-abstractions.md`
  - `004-kustomize-helm-strategy.md`

---

## 12. Open Questions for User

### bacen_ifdata Workflow Status
⚠️ **IMPORTANT**: The `workflow/go/financial_data/bacen_ifdata` directory does not exist yet. This workflow needs to be created from scratch.

**Assumptions for Implementation**:
- Go-based ETL workflow similar to SWAPI example (but in Go instead of Python)
- Publishes to Kafka topic `bacen.ifdata.v1`
- Runs daily at 7am UTC via CronJob
- Fetches financial data from Brazilian Central Bank (BACEN) API
- Standard retry/error handling with DLQ pattern

**To be created**:
- [ ] `workflow/go/financial_data/bacen_ifdata/main.go` - Main application
- [ ] `workflow/go/financial_data/bacen_ifdata/Dockerfile` - Container image
- [ ] `workflow/go/financial_data/bacen_ifdata/go.mod` - Go module definition
- [ ] CronJob manifest referencing the new container

### Timeline
🎯 **Target: 2 weeks for local K3S environment**

**Adjusted Implementation Priorities**:
1. **Week 1**: Core infrastructure (K3S, databases, Vault, Kafka, services)
2. **Week 2**: Observability, workflows, CronJob placeholder, documentation

---

## 13. Next Steps After Approval

1. **Create base directory structure**
2. **Write Terraform K3S module** and deploy local cluster
3. **Create Kubernetes base manifests** for all components
4. **Build Helm charts** with proper templating
5. **Deploy and test** end-to-end in local K3S
6. **Write documentation** (runbooks, guides, ADRs)
7. **Prepare cloud migration** (AWS/GCP Terraform modules)

---

## Summary

This plan provides a complete infrastructure transformation from Docker Compose to production-ready K8S on K3S:

✅ **Terraform**: K3S module for local dev + production VPC deployment
✅ **Kubernetes**: Complete manifests for all services + observability
✅ **Security**: Vault integration with JWT key auto-generation (no rotation initially)
✅ **Helm**: Reusable charts with prod/dev values
✅ **Storage**: MinIO for dev, Cloudflare R2 for production
✅ **Deployment**: Rolling update strategies, canary via GitHub Actions
✅ **Documentation**: Comprehensive runbooks, guides, and ADRs
✅ **Production**: K3S in VPC with 8 CPU / 32GB RAM nodes

**Component Versions** (matching docker-compose.local.yml):
- PostgreSQL 18rc1, PgBouncer latest
- Kafka 3.7.0 (KRaft), Vault 1.21.1
- Prometheus v2.55.1, Grafana 11.1.4, Loki/Promtail 2.9.0
- Apollo Router v2.7.0

**Out of Scope**:
- Prefect workflows
- bacen_ifdata Go workflow
- Multi-cloud abstractions (K3S only)

**Ready to implement once approved!**
