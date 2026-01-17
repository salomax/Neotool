---
title: Infrastructure & Operations
type: infrastructure
category: deployment
status: current
version: 2.0.0
tags: [infrastructure, deployment, operations, kubernetes, docker, terraform]
ai_optimized: true
search_keywords: [infrastructure, deployment, operations, kubernetes, docker, devops, sre]
related:
  - 11-infrastructure/architecture.md
  - 11-infrastructure/k8s-runbook.md
  - 11-infrastructure/hostinger-runbook.md
  - 02-architecture/infrastructure-architecture.md
  - 08-workflows/deployment-workflow.md
  - 10-observability/observability-overview.md
  - 92-adr/0002-containerized-architecture.md
last_updated: 2026-01-15
---

# Infrastructure & Operations

> **Purpose**: Operational guides, deployment procedures, runbooks, and infrastructure management for NeoTool in production.

## 🚀 Start Here

**New to Neotool infrastructure?** Start with these three focused guides:

### **📐 [Infrastructure Architecture](./architecture.md)** - High-Level Overview
Understanding the system design, technology choices, and component relationships.

### **☸️ [Kubernetes Operations Runbook](./k8s-runbook.md)** - Day-to-Day Kubernetes Operations
Complete guide for GitOps workflows, pod management, database access, monitoring, and troubleshooting.

### **🖥️ [Hostinger Infrastructure Runbook](./hostinger-runbook.md)** - VPS Provisioning & K3S Setup
Terraform automation, K3S cluster management, and VPS-level operations.

---

## Overview

This section provides **operational documentation** for deploying, managing, and troubleshooting NeoTool infrastructure. While [Infrastructure Architecture](../02-architecture/infrastructure-architecture.md) describes the **design**, this section covers the **operations**.

### Documentation Structure

```
docs/11-infrastructure/
├── README.md                  # This file (navigation)
├── architecture.md            # High-level infrastructure overview
├── feature-flags-unleash.md   # Feature flags with Unleash
├── k8s-runbook.md            # Kubernetes operations & troubleshooting
└── hostinger-runbook.md      # VPS provisioning & K3S management
```

---

## Quick Links

### For DevOps/SRE

**📐 Architecture Understanding**:
1. **[Infrastructure Architecture](./architecture.md)** - System design and components
   - Architecture patterns (GitOps, layered)
   - Technology stack and rationale
   - Component architecture
   - Network, storage, and security architecture

**🖥️ Infrastructure Provisioning**:
2. **[Hostinger Infrastructure Runbook](./hostinger-runbook.md)** - VPS and K3S setup
   - VPS creation and SSH setup
   - Terraform provisioning automation
   - K3S cluster management
   - VPS operations and troubleshooting

**☸️ Kubernetes Operations**:
3. **[Kubernetes Operations Runbook](./k8s-runbook.md)** - Daily operations
   - GitOps with Flux CD
   - Pod and deployment management
   - Database operations (PostgreSQL, PgBouncer)
   - Secrets management (Vault)
   - Monitoring and logs
   - Comprehensive troubleshooting

### For Developers

**Understanding Infrastructure**:
- [Infrastructure Architecture](./architecture.md) - High-level overview
- [Kubernetes Operations Runbook](./k8s-runbook.md) - Day-to-day operations
- [Feature Flags with Unleash](./feature-flags-unleash.md) - Feature flag system setup

**Common Tasks**:
- [Deploy via GitOps](./k8s-runbook.md#deploying-applications-via-gitops) - Push to Git → Auto-deploy
- [View Logs](./k8s-runbook.md#logs--debugging) - kubectl logs, stern, Loki queries
- [Database Access](./k8s-runbook.md#database-operations) - psql, pg_dump, queries
- [Port Forwarding](./k8s-runbook.md#port-forwarding) - Access services locally
- [Local Development](../01-overview/getting-started.md) - Docker Compose local env

---

## Common Tasks

### Deployment

| Task | Documentation | Frequency |
|------|--------------|-----------|
| **Deploy via GitOps** | [K8S Runbook - GitOps](./k8s-runbook.md#gitops-with-flux-cd) | **On every Git push** |
| Initial infrastructure setup | [Hostinger Runbook](./hostinger-runbook.md) | One-time |
| Update application | Edit manifests in Git → Push → Flux auto-deploys | Per feature |
| Rollback deployment | `git revert` → Push → Flux auto-reverts | As needed |

### Operations

| Task | Documentation | Frequency |
|------|--------------|-----------|
| View application logs | [K8S Runbook - Logs](./k8s-runbook.md#logs--debugging) | Daily |
| Scale pods | [K8S Runbook - Scaling](./k8s-runbook.md#pod--deployment-management) | As needed |
| Database backup | [K8S Runbook - Database](./k8s-runbook.md#database-backups) | Daily |
| Database access | [K8S Runbook - Database](./k8s-runbook.md#database-operations) | As needed |
| Port forwarding | [K8S Runbook - Port Forwarding](./k8s-runbook.md#port-forwarding) | Daily |

### Troubleshooting

| Issue | Documentation | Priority |
|-------|--------------|----------|
| Flux not syncing | [K8S Runbook - Troubleshooting](./k8s-runbook.md#flux-not-syncing) | High |
| Pods not starting | [K8S Runbook - Troubleshooting](./k8s-runbook.md#pod-not-starting) | High |
| Vault sealed | [K8S Runbook - Vault](./k8s-runbook.md#vault-issues) | High |
| Database connectivity | [K8S Runbook - Database Issues](./k8s-runbook.md#database-connection-issues) | High |
| VPS/K3S issues | [Hostinger Runbook - Troubleshooting](./hostinger-runbook.md#troubleshooting) | High |

---

## Environment

### Current Setup

| Environment | Infrastructure | Deployment Method | Status |
|-------------|----------------|-------------------|--------|
| **Production** | K3S on Hostinger VPS (single node) | **GitOps (Flux CD)** | ✅ Active |

**See**: [Hostinger Infrastructure Runbook](./hostinger-runbook.md) for complete setup guide.

### Environment Details

- **Infrastructure**: Single Hostinger VPS
- **Kubernetes**: K3S (lightweight Kubernetes)
- **Deployment**: GitOps with Flux CD
- **Storage**: K3S local-path provisioner
- **Secrets**: HashiCorp Vault
- **Monitoring**: Prometheus, Grafana, Loki

---

## Architecture Overview

### Simple Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     GitHub Repository                        │
│            (Single Source of Truth - GitOps)                 │
└──────────────────────┬──────────────────────────────────────┘
                       │ Flux CD Auto-Sync (1 min)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              K3S Cluster (Hostinger VPS)                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ production namespace                                 │   │
│  │  • Vault (Secrets)                                   │   │
│  │  • PostgreSQL + PgBouncer (Database)                 │   │
│  │  • Kafka (Streaming)                                 │   │
│  │  • Prometheus, Grafana, Loki (Monitoring)            │   │
│  │  • Kotlin Services (Apps)                            │   │
│  │  • Next.js (Web)                                     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**See**: [Infrastructure Architecture](../02-architecture/infrastructure-architecture.md) for detailed design.

---

## Component Specifications

### Component Versions

All infrastructure components use pinned versions for reproducibility:

| Component | Version | Notes |
|-----------|---------|-------|
| **PostgreSQL** | `postgres:18rc1` | Database server |
| **PgBouncer** | `edoburu/pgbouncer:latest` | Connection pooler |
| **Kafka** | `apache/kafka:3.7.0` | KRaft mode (no Zookeeper) |
| **Vault** | `hashicorp/vault:1.21.1` | Secrets management |
| **Apollo Router** | `ghcr.io/apollographql/router:v2.7.0` | GraphQL gateway |
| **Prometheus** | `prom/prometheus:v2.55.1` | Metrics collection |
| **Grafana** | `grafana/grafana:11.1.4` | Visualization |
| **Loki** | `grafana/loki:2.9.0` | Log aggregation |
| **Promtail** | `grafana/promtail:2.9.0` | Log collection |
| **MinIO** | `minio/minio:latest` | S3-compatible storage (dev only) |

**Production Storage**: Cloudflare R2 (S3-compatible) instead of MinIO.

### Kubernetes Namespace

All components are deployed in a **single namespace**: `production`

| Namespace | Purpose | Components |
|-----------|---------|------------|
| `production` | All application components | Vault, PostgreSQL, PgBouncer, Kafka, Prometheus, Grafana, Loki, Kotlin services, Next.js |

**Note**: Single namespace simplifies management for single-node cluster.

### Current Specifications

**K3S Cluster**:
- **Nodes**: 1 (single-node cluster)
- **Storage**: K3S local-path provisioner
- **Infrastructure**: Hostinger VPS (manually provisioned)
- **Deployment**: GitOps with Flux CD
- **Secrets**: HashiCorp Vault

---

## Tools & Access

### Required Tools

| Tool | Purpose | Installation |
|------|---------|-------------|
| **kubectl** | Kubernetes CLI | [Install kubectl](https://kubernetes.io/docs/tasks/tools/) |
| **terraform** | Infrastructure as Code | [Install Terraform](https://www.terraform.io/downloads) |
| **flux** | GitOps CLI | `brew install fluxcd/tap/flux` |
| **helm** | Kubernetes package manager | [Install Helm](https://helm.sh/docs/intro/install/) |
| **stern** | Multi-pod log tailing | `brew install stern` |

**See**: [Kubernetes Operations Runbook](./k8s-runbook.md) for tool usage.

---

## Monitoring & Observability

### Available Dashboards

- **Grafana**: Metrics visualization (Prometheus data)
- **Loki**: Log aggregation and search
- **Prometheus**: Direct metrics queries

**Access**:
```bash
# Grafana
kubectl port-forward -n production svc/grafana 3000:80
# Open: http://localhost:3000

# Prometheus
kubectl port-forward -n production svc/prometheus 9090:9090
# Open: http://localhost:9090
```

**See**: [Kubernetes Operations Runbook](./k8s-runbook.md#monitoring--metrics) for monitoring tasks.

---

## Best Practices

### GitOps Deployment

✅ **Do**:
- All changes via Git (infrastructure as code)
- Use feature branches for testing
- Monitor Flux reconciliation after pushing
- Keep Vault credentials backed up
- Document environment-specific configs

❌ **Don't**:
- Apply manifests directly with `kubectl apply` (use GitOps)
- Make manual changes in production
- Skip testing in development first
- Commit secrets to Git (use Vault)
- Force push to main branch

---

## Related Documentation

### Core Guides
- **[Infrastructure Architecture](./architecture.md)** - High-level system design
- **[Kubernetes Operations Runbook](./k8s-runbook.md)** - Daily K8S operations
- **[Hostinger Infrastructure Runbook](./hostinger-runbook.md)** - VPS provisioning & K3S setup

### Architecture
- [Infrastructure Architecture](../02-architecture/infrastructure-architecture.md) - System design
- [System Architecture](../02-architecture/system-architecture.md) - Overall architecture

### Workflows
- [Deployment Workflow](../08-workflows/deployment-workflow.md) - Deployment process
- [Feature Development](../08-workflows/feature-development.md) - Development workflow

### Observability
- [Observability Overview](../10-observability/observability-overview.md) - Monitoring stack

---

## Quick Reference

### Most Used Commands

```bash
# Set kubeconfig
export KUBECONFIG=~/.kube/config-hostinger

# View pod logs
kubectl logs -f deployment/my-service -n production

# Port forward to service
kubectl port-forward svc/my-service 8080:8080 -n production

# Scale deployment
kubectl scale deployment/my-service --replicas=3 -n production

# Restart deployment
kubectl rollout restart deployment/my-service -n production

# Check Flux status
flux get all

# Force Flux reconciliation
flux reconcile source git flux-system
flux reconcile kustomization infrastructure
```

**See**: [Kubernetes Operations Runbook](./k8s-runbook.md#quick-reference) for complete command reference.

---

**Version**: 3.0.0 (2026-01-15)
**Maintained By**: DevOps Team
**Last Updated**: Simplified to 3 focused documents: Architecture, K8S Runbook, Hostinger Runbook

*Simple infrastructure. GitOps deployments. Production-ready.*
