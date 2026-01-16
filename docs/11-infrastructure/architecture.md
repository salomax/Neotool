---
title: Infrastructure Architecture
type: infrastructure
category: architecture
status: current
version: 1.0.0
tags: [infrastructure, architecture, kubernetes, k3s, gitops, cloud-native]
ai_optimized: true
search_keywords: [architecture, infrastructure, kubernetes, k3s, gitops, flux, vault, components, stack]
related:
  - 11-infrastructure/k8s-runbook.md
  - 11-infrastructure/hostinger-runbook.md
  - 02-architecture/infrastructure-architecture.md
last_updated: 2026-01-15
---

# Neotool Infrastructure Architecture

> **High-level overview of Neotool's cloud-native infrastructure**: Architecture patterns, technology choices, and component relationships.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Patterns](#architecture-patterns)
3. [Technology Stack](#technology-stack)
4. [Component Architecture](#component-architecture)
5. [Network Architecture](#network-architecture)
6. [Storage Architecture](#storage-architecture)
7. [Security Architecture](#security-architecture)
8. [Operational Model](#operational-model)

---

## Overview

### What is Neotool Infrastructure?

Neotool uses a **cloud-native, Infrastructure as Code (IaC)** approach with:

- **GitOps**: Git as the single source of truth for all infrastructure
- **Kubernetes (K3S)**: Lightweight container orchestration
- **Declarative Configuration**: All infrastructure defined in YAML/HCL
- **Automated Deployment**: Push to Git → Automatic application to cluster

### Key Principles

| Principle | Description |
|-----------|-------------|
| **Declarative** | Desired state defined in Git, system converges to match |
| **Immutable** | Replace rather than modify (containers, pods) |
| **Observable** | Metrics, logs, and traces for all components |
| **Secure by Default** | Secrets encrypted, network policies enforced, RBAC enabled |
| **Self-Healing** | Automatic recovery from failures |

---

## Architecture Patterns

### GitOps Pattern

**Single Source of Truth**: Git repository contains all infrastructure and application configurations.

```
┌─────────────────────────────────────────────────────────┐
│                   GitHub Repository                      │
│        (All Kubernetes manifests stored here)            │
└──────────────────┬──────────────────────────────────────┘
                   │
                   │ Git Polling (1 minute)
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│              Flux CD Controllers                         │
│  • GitRepository Controller (watches Git)                │
│  • Kustomization Controller (applies manifests)          │
│  • HelmRelease Controller (manages Helm charts)          │
└──────────────────┬──────────────────────────────────────┘
                   │
                   │ Apply changes
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│                K3S Kubernetes Cluster                    │
│  • Infrastructure: Vault, External Secrets, Networking   │
│  • Applications: Database, Streaming, Services, Web      │
└─────────────────────────────────────────────────────────┘
```

**Benefits**:
- All changes version controlled and auditable
- Easy rollback (Git revert)
- Consistent across environments
- Automated deployments
- No manual `kubectl apply`

### Layered Architecture

Infrastructure is organized in **dependency layers**:

```
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                      │
│  • Kotlin Services (microservices)                       │
│  • Next.js Web App                                       │
│  • Apollo Router (GraphQL gateway)                       │
└───────────────────┬─────────────────────────────────────┘
                    │ Depends on
                    ▼
┌─────────────────────────────────────────────────────────┐
│                   Data Layer                             │
│  • PostgreSQL (database)                                 │
│  • PgBouncer (connection pooler)                         │
│  • Kafka (event streaming)                               │
└───────────────────┬─────────────────────────────────────┘
                    │ Depends on
                    ▼
┌─────────────────────────────────────────────────────────┐
│                   Observability Layer                    │
│  • Prometheus (metrics)                                  │
│  • Grafana (dashboards)                                  │
│  • Loki (logs)                                           │
│  • Promtail (log collection)                             │
└───────────────────┬─────────────────────────────────────┘
                    │ Depends on
                    ▼
┌─────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                   │
│  • Vault (secrets management)                            │
│  • External Secrets Operator                             │
│  • Network Policies                                      │
│  • Storage Classes                                       │
│  • Namespace & Resource Quotas                           │
└─────────────────────────────────────────────────────────┘
```

**Deployment Order**: Infrastructure → Observability → Data → Applications

---

## Technology Stack

### Core Infrastructure

| Technology | Purpose | Why This Choice |
|-----------|---------|-----------------|
| **K3S** | Kubernetes distribution | Lightweight (~70MB), production-ready, perfect for VPS environments, full K8s API compatibility |
| **Flux CD** | GitOps operator | Industry standard, CNCF graduated project, declarative, pull-based (secure) |
| **Terraform** | Infrastructure provisioning | Industry standard IaC, cloud-agnostic, strong ecosystem |
| **Ubuntu** | Operating system | Excellent K3S compatibility, LTS support (5 years), wide community, proven in production |

### Secrets Management

| Technology | Purpose | Why This Choice |
|-----------|---------|-----------------|
| **HashiCorp Vault** | Secret storage | Industry standard, encryption at rest, audit logs, dynamic secrets, K8s integration |
| **External Secrets Operator** | Secret synchronization | CNCF project, syncs Vault → K8s Secrets automatically, refresh on schedule |

### Data Layer

| Technology | Purpose | Why This Choice |
|-----------|---------|-----------------|
| **PostgreSQL 18.1** | Relational database | Most advanced open-source RDBMS, JSON support, strong ACID guarantees, SCRAM-SHA-256 auth |
| **PgBouncer** | Connection pooler | Lightweight, reduces connection overhead, transaction pooling, plain text auth support for SCRAM |
| **Kafka (KRaft)** | Event streaming | Industry standard for event-driven architecture, no Zookeeper dependency (KRaft mode), high throughput |

### Observability

| Technology | Purpose | Why This Choice |
|-----------|---------|-----------------|
| **Prometheus** | Metrics collection | Industry standard, pull-based (scalable), PromQL query language, K8s native |
| **Grafana** | Visualization | Best-in-class dashboards, supports multiple data sources, alerting |
| **Loki** | Log aggregation | Like Prometheus for logs, cost-effective (indexes labels not content), Grafana integration |
| **Promtail** | Log collection | Lightweight agent, native Loki integration, runs as DaemonSet |

### Storage

| Technology | Purpose | Why This Choice |
|-----------|---------|-----------------|
| **K3S Local-Path** | Volume provisioner | Built-in, simple, sufficient for single-node/small clusters, hostPath volumes |
| **Cloudflare R2** | Object storage | S3-compatible API, no egress fees, integrated with Cloudflare ecosystem, cost-effective |

---

## Component Architecture

### Infrastructure Layer

**Purpose**: Foundation services that other components depend on.

**Components**:

1. **Namespace & Resource Quotas**
   - Isolates production workloads
   - Enforces resource limits (CPU: 8/12 cores, Memory: 16/24Gi)
   - Prevents resource exhaustion

2. **Vault**
   - Centralized secret storage
   - Encrypts secrets at rest
   - Kubernetes authentication
   - Manual unsealing required (security)

3. **External Secrets Operator**
   - Watches Vault for secret changes
   - Syncs to Kubernetes Secrets automatically
   - Refresh interval: 5 minutes
   - Service account authentication

4. **Network Policies**
   - Restricts pod-to-pod communication
   - Enforces least privilege access
   - Examples:
     - Only PgBouncer can access PostgreSQL
     - Only applications can access PgBouncer

5. **Helm Repositories**
   - HashiCorp (Vault charts)
   - External Secrets (operator charts)

**Reconciliation**: Every 10 minutes via Flux

### Data Layer

**Purpose**: Persistent data storage and streaming.

**Components**:

1. **PostgreSQL**
   - StatefulSet with persistent volume (100Gi)
   - SCRAM-SHA-256 authentication
   - Automatic backups (to be configured)
   - High availability (to be added)

2. **PgBouncer**
   - Connection pooling (reduces overhead)
   - Transaction pool mode
   - Plain text auth to PostgreSQL (internal)
   - Accepts plain text from clients
   - Configuration:
     - Max clients: 1000
     - Default pool size: 25
     - Port: 6432

3. **Kafka**
   - StatefulSet (KRaft mode, no Zookeeper)
   - Event streaming platform
   - Persistent storage
   - To be configured for replication

**Reconciliation**: Every 5 minutes via Flux

### Observability Layer

**Purpose**: Monitoring, alerting, and troubleshooting.

**Components**:

1. **Prometheus**
   - Scrapes metrics from all pods
   - Retention: 15 days (configurable)
   - Alert rules (to be configured)
   - Service discovery via K8s API

2. **Grafana**
   - Pre-configured dashboards:
     - Kubernetes Cluster
     - PostgreSQL
     - JVM Metrics
     - GraphQL
   - Default credentials: admin/admin
   - Port: 3000

3. **Loki**
   - Log aggregation
   - Retention: 7 days (configurable)
   - Query via LogQL
   - Grafana data source

4. **Promtail**
   - DaemonSet (runs on all nodes)
   - Collects logs from pods
   - Labels logs with metadata
   - Pushes to Loki

**Reconciliation**: Every 5 minutes via Flux

### Application Layer

**Purpose**: Business logic and user-facing services.

**Components** (to be added):

1. **Kotlin Microservices**
   - Business logic services
   - REST/GraphQL APIs
   - Connects via PgBouncer
   - Publishes events to Kafka

2. **Next.js Web App**
   - Frontend application
   - Server-side rendering
   - Connects to Apollo Router

3. **Apollo Router**
   - GraphQL gateway
   - Federated schema
   - Aggregates microservices

**Reconciliation**: Every 5 minutes via Flux

---

## Network Architecture

### Service Discovery

**DNS-based**: All services accessible via Kubernetes DNS.

**Format**: `<service-name>.<namespace>.svc.cluster.local`

**Examples**:
- PostgreSQL: `postgres-headless.production.svc.cluster.local:5432`
- PgBouncer: `pgbouncer.production.svc.cluster.local:6432`
- Kafka: `kafka.production.svc.cluster.local:9092`
- Vault: `vault.production.svc.cluster.local:8200`

### Network Policies

**Default Deny**: All ingress traffic blocked unless explicitly allowed.

**Key Policies**:
1. **postgres-allow-pgbouncer**: Only PgBouncer can access PostgreSQL
2. **pgbouncer-allow-apps**: Only apps with label `access-pgbouncer=true` can access PgBouncer
3. More to be added as needed

### External Access

**Method**: Port forwarding for development, Ingress for production (to be configured).

**Port Forward Examples**:
```bash
# Database
kubectl port-forward -n production svc/pgbouncer 6432:6432

# Grafana
kubectl port-forward -n production svc/grafana 3000:80

# Prometheus
kubectl port-forward -n production svc/prometheus 9090:9090
```

### Pod Network

- **CIDR**: `10.42.0.0/16` (Flannel CNI)
- **Service CIDR**: `10.43.0.0/16`
- **Cluster DNS**: `10.43.0.10` (CoreDNS)

### Required Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 6443 | TCP | Kubernetes API Server (cluster management) |
| 10250 | TCP | Kubelet API (node agent) |
| 8472 | UDP | Flannel VXLAN (pod networking overlay) |
| 22 | TCP | SSH (server management) |
| 80/443 | TCP | HTTP/HTTPS (ingress traffic) |

---

## Storage Architecture

### Volume Types

| Type | Use Case | Backend |
|------|----------|---------|
| **emptyDir** | Temporary data, configuration files | RAM or node disk |
| **PersistentVolume** | Database data, logs | K3S local-path provisioner |
| **ConfigMap** | Configuration files | Kubernetes API |
| **Secret** | Sensitive data | Kubernetes API (synced from Vault) |

### Storage Classes

**Default**: `local-path` (K3S built-in)

**Characteristics**:
- **Access Mode**: ReadWriteOnce (single node)
- **Reclaim Policy**: Delete (volume deleted when claim deleted)
- **Binding**: Immediate

**Limitations**:
- Single-node only (data not replicated)
- Data lost if node fails
- Not suitable for multi-node clusters

**Future**: Consider Longhorn or Rook/Ceph for distributed storage.

### Persistent Volumes

| Component | Size | Purpose |
|-----------|------|---------|
| PostgreSQL | 100Gi | Database data |
| Vault | 10Gi | Encrypted secrets |
| Prometheus | 50Gi | Metrics storage |
| Loki | 50Gi | Log storage |
| Kafka | 100Gi | Event log |

### Object Storage

**Cloudflare R2**:
- S3-compatible API
- No egress fees
- Bucket: `neotool-prod`
- Use cases:
  - Database backups
  - Application assets (images, files)
  - Log archives (long-term retention)

---

## Security Architecture

### Secrets Management Flow

```
┌─────────────────────────────────────────────────────────┐
│  1. Store secrets in Vault (encrypted at rest)          │
│     • PostgreSQL credentials                             │
│     • API keys                                           │
│     • JWT secrets                                        │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  2. External Secrets Operator watches Vault              │
│     • Authenticates via Kubernetes service account       │
│     • Configured via SecretStore resource                │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  3. ExternalSecret defines what to sync                  │
│     • Maps Vault path → K8s Secret keys                  │
│     • Refresh interval: 5 minutes                        │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  4. Kubernetes Secret created automatically              │
│     • Base64 encoded (standard K8s)                      │
│     • Mounted as env vars or files in pods               │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  5. Application pods consume secrets                     │
│     • No hardcoded secrets in code                       │
│     • No secrets in Git                                  │
└─────────────────────────────────────────────────────────┘
```

### Authentication & Authorization

**Vault Authentication**:
- Kubernetes auth method
- Service account JWT tokens
- Role-based policies
- TTL: 24 hours

**Kubernetes RBAC**:
- Namespace isolation (production namespace)
- Service account per component
- Least privilege principle

**Database Authentication**:
- PostgreSQL: SCRAM-SHA-256 (modern, secure)
- PgBouncer: Plain text auth (internal only, never exposed)
- Credentials synced from Vault

### Network Security

**Network Policies**: Enforce pod-to-pod communication rules.

**Encryption**:
- At rest: Vault encrypts all secrets
- In transit: Internal cluster communication (TLS to be added)

**Firewall**:
- Only required ports open (6443, 10250, 8472, 22)
- SSH key-based authentication only

---

## Operational Model

### Reconciliation Intervals

| Component | Interval | Purpose |
|-----------|----------|---------|
| GitRepository | 1 minute | Check Git for changes |
| Kustomization (infrastructure) | 10 minutes | Apply infrastructure changes |
| Kustomization (apps) | 5 minutes | Apply application changes |
| HelmRelease | 30 minutes | Check for chart updates |
| ExternalSecret | 5 minutes | Sync secrets from Vault |

### Resource Quotas

**Production Namespace**:
- CPU Requests: 8 cores
- CPU Limits: 12 cores
- Memory Requests: 16Gi
- Memory Limits: 24Gi
- Max Pods: 50
- Max Storage: 50Gi

### High Availability

**Current State**: Single-node cluster (simplicity, cost-effective)

**Future Considerations**:
- Multi-node cluster (3+ nodes)
- Database replication (PostgreSQL streaming replication)
- Kafka replication (3+ brokers)
- Load balancing (multiple app replicas)

### Disaster Recovery

**Current**:
- Vault credentials backed up to `~/.neotool/vault-credentials.txt`
- Manual database backups via `pg_dump`
- Infrastructure in Git (easy to recreate)

**Future**:
- Automated database backups to R2
- Vault auto-unseal (AWS KMS or similar)
- Backup retention policies
- Disaster recovery runbook

---

## Deployment Flow

### Initial Deployment

1. **Provision Infrastructure** (Terraform)
   - Create VPS
   - Install K3S
   - Configure cluster

2. **Bootstrap GitOps** (Flux)
   - Install Flux controllers
   - Connect to Git repository
   - Create deploy key

3. **Deploy Infrastructure Layer** (Flux auto-deploys)
   - Namespace & quotas
   - Vault
   - External Secrets

4. **Initialize Secrets** (Manual, one-time)
   - Initialize Vault
   - Configure Kubernetes auth
   - Store application secrets

5. **Deploy Application Layers** (Flux auto-deploys)
   - Observability
   - Data layer
   - Applications

### Ongoing Deployments

1. **Developer commits changes** to Git
2. **Flux detects changes** (within 1 minute)
3. **Flux applies changes** to cluster
4. **Kubernetes reconciles** desired state
5. **Applications updated** automatically

**No manual `kubectl apply` needed!**

### Rollback

1. **Git revert** commit
2. **Push to Git**
3. **Flux auto-rolls back** (within 1 minute)

---

## Monitoring & Observability

### Metrics (Prometheus)

**Collection**: Scrapes metrics from all pods (via service discovery)

**Key Metrics**:
- Request rate, error rate, latency (RED)
- CPU, memory, disk usage
- Database connections, query performance
- Kafka throughput, consumer lag

**Retention**: 15 days

### Logs (Loki)

**Collection**: Promtail DaemonSet scrapes pod logs

**Indexing**: Labels only (not content) - cost-effective

**Retention**: 7 days

**Query**: LogQL (similar to PromQL)

### Dashboards (Grafana)

**Pre-configured**:
- Kubernetes Cluster Overview
- PostgreSQL Performance
- JVM Metrics
- GraphQL Performance
- Custom application dashboards

### Alerting (To Be Configured)

**Alert Manager**: Prometheus AlertManager

**Channels**:
- Email
- Slack
- PagerDuty (for critical alerts)

---

## Related Documentation

- [Kubernetes Operations Runbook](./k8s-runbook.md) - Day-to-day Kubernetes operations, troubleshooting
- [Hostinger Infrastructure Runbook](./hostinger-runbook.md) - VPS provisioning, K3S cluster management
- [Architecture Decisions](../02-architecture/infrastructure-architecture.md) - Detailed ADRs

---

**Version**: 1.0.0
**Last Updated**: 2026-01-15
**Maintained By**: DevOps Team

*Cloud-native, GitOps-driven, production-ready architecture.* 🚀
