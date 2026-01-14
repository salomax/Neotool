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
  - 11-infrastructure/INFRASTRUCTURE-GUIDE.md
  - 02-architecture/infrastructure-architecture.md
  - 08-workflows/deployment-workflow.md
  - 10-observability/observability-overview.md
  - 92-adr/0002-containerized-architecture.md
last_updated: 2026-01-13
---

# Infrastructure & Operations

> **Purpose**: Operational guides, deployment procedures, runbooks, and infrastructure management for NeoTool in production.

## 🚀 Start Here

**New to Neotool infrastructure?** Start with the complete guide:

### **📖 [Infrastructure & Hostinger Runbook](./infra-hostinger-runbook.md)** - Complete End-to-End Guide

This is your **single source of truth** for Neotool infrastructure. It covers:
- Infrastructure summary and architecture
- Terraform platform provisioning (SSH-based K3S installation)
- K3S cluster setup on Hostinger VPS
- Kubernetes & GitOps with Flux CD
- Complete deployment workflow (7 phases)
- Operations & maintenance
- Cleanup procedures

**This guide consolidates all infrastructure knowledge in one place.**

---

## Overview

This section provides **operational documentation** for deploying, managing, and troubleshooting NeoTool infrastructure. While [Infrastructure Architecture](../02-architecture/infrastructure-architecture.md) describes the **design**, this section covers the **operations**.

### Documentation Structure

```
docs/11-infrastructure/
├── README.md                        # This file (navigation)
├── infra-hostinger-runbook.md       # Complete infrastructure guide
└── operations-runbook.md            # Daily kubectl commands reference
```

---

## Quick Links

### For DevOps/SRE

**🎯 Complete Deployment Guide**:
1. **[Infrastructure & Hostinger Runbook](./infra-hostinger-runbook.md)** - Complete end-to-end deployment
   - How to create VPS on Hostinger
   - Terraform setup and K3S installation
   - Flux GitOps bootstrap
   - Vault configuration
   - Application deployment

**🛠️ Daily Operations Reference**:
2. **[Operations Runbook](./operations-runbook.md)** - Day-to-day kubectl commands
   - View logs (kubectl, stern, Loki)
   - Manage pods and deployments
   - Database access and queries
   - Port forwarding
   - Scaling and resource management
   - ConfigMaps and Secrets
   - Troubleshooting common issues

**🔄 GitOps Deep Dive**:
3. **[Kubernetes GitOps README](../../infra/kubernetes/README.md)** - Flux CD technical details
   - Flux architecture and components
   - GitOps workflow
   - Kustomizations and HelmReleases
   - Testing and maintenance

### For Developers

**Deployment**:
- **[Infrastructure & Hostinger Runbook](./infra-hostinger-runbook.md)** - Complete deployment guide
- **[GitOps Guide](../../infra/kubernetes/README.md)** - GitOps workflow with Flux CD
- [Local Development Setup](../01-overview/getting-started.md) - Docker Compose local env

**Daily Operations**:
- [View Logs](./operations-runbook.md#viewing-logs) - kubectl logs, stern, Loki queries
- [Database Access](./operations-runbook.md#database-access) - psql, pg_dump, queries
- [Port Forwarding](./operations-runbook.md#port-forwarding) - Access services locally

---

## Common Tasks

### Deployment

| Task | Documentation | Frequency |
|------|--------------|-----------|
| **Deploy via GitOps** | **[GitOps Guide](../../infra/kubernetes/README.md)** | **On every Git push** |
| Initial setup | [Infrastructure Runbook](./infra-hostinger-runbook.md) | One-time |
| Update application | Edit manifests in Git → Push → Flux auto-deploys | Per feature |
| Rollback deployment | `git revert` → Push → Flux auto-reverts | As needed |

### Operations

| Task | Documentation | Frequency |
|------|--------------|-----------|
| View application logs | [Operations Runbook](./operations-runbook.md#viewing-logs) | Daily |
| Scale pods | [Operations Runbook](./operations-runbook.md#scaling) | As needed |
| Database backup | [Operations Runbook](./operations-runbook.md#database-backups) | Daily |
| Database access | [Operations Runbook](./operations-runbook.md#database-access) | As needed |
| Port forwarding | [Operations Runbook](./operations-runbook.md#port-forwarding) | Daily |

### Troubleshooting

| Issue | Documentation | Priority |
|-------|--------------|----------|
| Flux not syncing | [Infrastructure Runbook](./infra-hostinger-runbook.md#troubleshooting) | High |
| Pods not starting | [Operations Runbook](./operations-runbook.md#pod-not-starting) | High |
| Vault sealed | [Infrastructure Runbook](./infra-hostinger-runbook.md#vault-sealed-after-restart) | High |
| Database connectivity | [Operations Runbook](./operations-runbook.md#database-connection-issues) | High |

---

## Environment

### Current Setup

| Environment | Infrastructure | Deployment Method | Status |
|-------------|----------------|-------------------|--------|
| **Production** | K3S on Hostinger VPS (single node) | **GitOps (Flux CD)** | ✅ Active |

**See**: [Infrastructure & Hostinger Runbook](./infra-hostinger-runbook.md) for complete setup guide.

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

**See**: [Operations Runbook](./operations-runbook.md) for tool usage.

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

**See**: [Operations Runbook](./operations-runbook.md#monitoring) for monitoring tasks.

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
- **[Infrastructure & Hostinger Runbook](./infra-hostinger-runbook.md)** - Complete deployment guide
- **[Operations Runbook](./operations-runbook.md)** - Daily operations reference
- **[Kubernetes GitOps README](../../infra/kubernetes/README.md)** - Flux CD deep dive

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

**See**: [Operations Runbook](./operations-runbook.md) for complete command reference.

---

**Version**: 2.0.0 (2026-01-13)
**Maintained By**: DevOps Team
**Last Updated**: Infrastructure documentation consolidated and simplified

*Simple infrastructure. GitOps deployments. Production-ready.*
