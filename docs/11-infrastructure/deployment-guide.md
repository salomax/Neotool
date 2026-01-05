---
title: Deployment Guide
type: infrastructure
category: deployment
status: current
version: 1.0.0
tags: [deployment, kubernetes, docker, cicd, github-actions]
ai_optimized: true
search_keywords: [deployment, deploy, kubernetes, docker, staging, production, rollback]
related:
  - 11-infrastructure/README.md
  - 02-architecture/infrastructure-architecture.md
  - 08-workflows/deployment-workflow.md
last_updated: 2026-01-02
---

# Deployment Guide

> **Purpose**: Step-by-step procedures for deploying NeoTool to staging and production environments.

## Overview

This guide covers deploying NeoTool services to Kubernetes clusters using GitHub Actions CI/CD pipeline and manual kubectl commands when needed.

### Deployment Strategy

- **Automated**: Staging deploys automatically on PR merge to `main`
- **Manual Approval**: Production requires manual approval in GitHub Actions
- **Rolling Updates**: Zero-downtime deployments with health checks
- **Rollback**: Instant rollback capability to previous version

---

## Quick Reference

| Task | Command/Action |
|------|----------------|
| **Deploy to staging** | Merge PR → automatic deployment |
| **Deploy to production** | Approve deployment in GitHub Actions |
| **Rollback** | `kubectl rollout undo deployment/<name> -n neotool-prod` |
| **Check deployment status** | `kubectl rollout status deployment/<name> -n neotool-prod` |
| **View deployment history** | `kubectl rollout history deployment/<name> -n neotool-prod` |

---

## Prerequisites

### Required Access

- [x] GitHub repository access
- [x] Kubernetes cluster access (kubeconfig)
- [x] Container registry access (ghcr.io)
- [x] AWS/GCP/Azure credentials (for Terraform)

### Required Tools

```bash
# Verify tool installation
kubectl version --client
docker --version
terraform --version
helm version

# Configure kubectl context
kubectl config use-context neotool-prod
kubectl config current-context
```

### Environment Variables

```bash
# GitHub Secrets (configured in repository settings)
KUBE_CONFIG                 # Base64-encoded kubeconfig
GHCR_TOKEN                  # GitHub Container Registry token
DATABASE_URL                # Production database connection string
JWT_SECRET                  # JWT signing secret
AWS_ACCESS_KEY_ID           # AWS credentials (if using AWS)
AWS_SECRET_ACCESS_KEY
```

---

## Local Development Deployment

### Docker Compose (Infrastructure Only)

```bash
# Start infrastructure services
cd infra/docker
docker-compose -f docker-compose.local.yml up -d

# Verify services are running
docker-compose ps

# View logs
docker-compose logs -f postgres

# Stop services
docker-compose down
```

**Services Started**:
- PostgreSQL 18+ (port 5432)
- Prometheus (port 9090)
- Grafana (port 3001)
- Loki (port 3100)

### Native Application Processes

```bash
# Terminal 1: Backend
cd service/kotlin
./gradlew run

# Terminal 2: Frontend
cd web
npm run dev

# Terminal 3: Apollo Router
cd service/gateway
./router --config router.yaml
```

**URLs**:
- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- GraphQL Playground: http://localhost:4000/graphql
- Grafana: http://localhost:3001 (admin/admin)

**See**: [Getting Started](../01-overview/getting-started.md) for complete setup.

---

## Staging Deployment

### Automatic Deployment (CI/CD)

**Trigger**: Merge PR to `main` branch

**GitHub Actions Workflow**:

```yaml
# .github/workflows/deploy-staging.yml
name: Deploy to Staging

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Build and push images
        # ... (builds Docker images)

      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/backend \
            backend=ghcr.io/${{ github.repository }}/backend:${{ github.sha }} \
            -n neotool-staging

          kubectl rollout status deployment/backend -n neotool-staging
```

**Process**:
1. PR merged to `main`
2. GitHub Actions triggered
3. Build Docker images
4. Push to ghcr.io
5. Update Kubernetes deployments
6. Verify health checks
7. Notify team (Slack/Discord)

**Verification**:
```bash
# Check deployment status
kubectl get deployments -n neotool-staging

# View pods
kubectl get pods -n neotool-staging

# Check logs
kubectl logs -f deployment/backend -n neotool-staging
```

### Manual Staging Deployment

**When to use**: Testing specific image or configuration changes

```bash
# 1. Set context to staging
kubectl config use-context neotool-staging

# 2. Apply manifests
kubectl apply -f infra/k8s/overlays/staging/

# 3. Update image (if deploying specific version)
kubectl set image deployment/backend \
  backend=ghcr.io/org/neotool/backend:v1.2.3 \
  -n neotool-staging

# 4. Monitor rollout
kubectl rollout status deployment/backend -n neotool-staging

# 5. Verify deployment
kubectl get pods -n neotool-staging
kubectl logs -f deployment/backend -n neotool-staging --tail=100
```

**Rollback if needed**:
```bash
kubectl rollout undo deployment/backend -n neotool-staging
```

---

## Production Deployment

### Automated Deployment (Manual Approval)

**Trigger**: Manual approval in GitHub Actions

**Process**:

1. **Staging passes** → Create production deployment run
2. **Navigate to GitHub Actions** → Find "Deploy to Production" workflow
3. **Review changes**:
   - Image version
   - Database migrations
   - Configuration changes
4. **Approve deployment** → Click "Approve" button
5. **Monitor deployment** → Watch logs in real-time
6. **Verify health** → Check metrics and logs

**GitHub Actions Workflow**:

```yaml
# .github/workflows/deploy-production.yml
name: Deploy to Production

on:
  workflow_dispatch:  # Manual trigger only

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production  # Requires manual approval
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/backend \
            backend=ghcr.io/${{ github.repository }}/backend:${{ github.sha }} \
            -n neotool-prod

          kubectl rollout status deployment/backend -n neotool-prod --timeout=5m
```

**Post-Deployment Checklist**:
- [ ] All pods are running
- [ ] Health checks passing
- [ ] No error spikes in logs
- [ ] Metrics within normal range
- [ ] Database migrations completed
- [ ] Monitor for 30 minutes

### Manual Production Deployment

**Use case**: Emergency patches, rollbacks, or infrastructure changes

**Pre-Deployment**:

```bash
# 1. Verify current state
kubectl config use-context neotool-prod
kubectl get deployments -n neotool-prod

# 2. Backup database
kubectl exec -it postgresql-0 -n neotool-data -- \
  pg_dump -U postgres neotool > backup-$(date +%Y%m%d-%H%M%S).sql

# 3. Notify team
# Post in #deployments Slack channel
```

**Deployment Steps**:

```bash
# 1. Apply infrastructure changes (if any)
cd infra/terraform/environments/prod
terraform plan
terraform apply

# 2. Apply Kubernetes manifests
kubectl apply -f infra/k8s/overlays/prod/

# 3. Update service images
kubectl set image deployment/backend \
  backend=ghcr.io/org/neotool/backend:v1.2.3 \
  -n neotool-prod

kubectl set image deployment/frontend \
  frontend=ghcr.io/org/neotool/frontend:v1.2.3 \
  -n neotool-prod

# 4. Monitor rollout (in separate terminals)
kubectl rollout status deployment/backend -n neotool-prod
kubectl rollout status deployment/frontend -n neotool-prod

# 5. Tail logs
stern backend -n neotool-prod
```

**Verification**:

```bash
# Check all pods are ready
kubectl get pods -n neotool-prod

# Verify no errors
kubectl logs -f deployment/backend -n neotool-prod --tail=50 | grep -i error

# Check metrics in Grafana
# Open: https://grafana.neotool.io

# Test critical endpoints
curl -I https://api.neotool.io/health
curl -X POST https://api.neotool.io/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ __typename }"}'
```

---

## Database Migrations

### Pre-Deployment Migration Check

```bash
# View pending migrations
kubectl exec -it deployment/backend -n neotool-prod -- \
  ./gradlew flywayInfo

# Dry-run migration (staging)
kubectl exec -it deployment/backend -n neotool-staging -- \
  ./gradlew flywayValidate
```

### Migration Execution

**Automatic** (during deployment):
- Flyway runs on application startup
- Migrations execute before service accepts traffic
- Service starts only if migrations succeed

**Manual** (if needed):
```bash
# Run migrations manually
kubectl exec -it deployment/backend -n neotool-prod -- \
  ./gradlew flywayMigrate

# Rollback migration (DANGEROUS - test in staging first!)
kubectl exec -it deployment/backend -n neotool-prod -- \
  ./gradlew flywayUndo
```

### Migration Rollback

**If migration fails**:

1. **Stop deployment**:
   ```bash
   kubectl scale deployment/backend --replicas=0 -n neotool-prod
   ```

2. **Restore database from backup**:
   ```bash
   kubectl exec -it postgresql-0 -n neotool-data -- \
     psql -U postgres neotool < backup-latest.sql
   ```

3. **Rollback to previous image**:
   ```bash
   kubectl rollout undo deployment/backend -n neotool-prod
   ```

4. **Restart deployment**:
   ```bash
   kubectl scale deployment/backend --replicas=3 -n neotool-prod
   ```

---

## Rollback Procedures

### Application Rollback

**Quick rollback** (previous version):
```bash
# Rollback to previous revision
kubectl rollout undo deployment/backend -n neotool-prod

# Verify rollback
kubectl rollout status deployment/backend -n neotool-prod
kubectl get pods -n neotool-prod
```

**Rollback to specific revision**:
```bash
# View deployment history
kubectl rollout history deployment/backend -n neotool-prod

# Rollback to revision #3
kubectl rollout undo deployment/backend --to-revision=3 -n neotool-prod
```

### Infrastructure Rollback

**Terraform**:
```bash
cd infra/terraform/environments/prod

# View state
terraform show

# Rollback via Git
git revert <commit-sha>
terraform apply
```

**Kubernetes manifests**:
```bash
# Revert to previous commit
git revert <commit-sha>
kubectl apply -f infra/k8s/overlays/prod/
```

---

## Blue-Green Deployment

**Use case**: Major version releases, significant changes

### Setup

```bash
# 1. Deploy "green" environment alongside "blue"
kubectl apply -f infra/k8s/overlays/prod/blue-green/green/

# 2. Wait for green pods to be ready
kubectl wait --for=condition=ready pod -l app=backend,version=green \
  -n neotool-prod --timeout=5m

# 3. Test green environment internally
kubectl port-forward svc/backend-green 8080:8080 -n neotool-prod
curl http://localhost:8080/health
```

### Traffic Switch

```bash
# 1. Update service selector to point to green
kubectl patch service backend -n neotool-prod \
  -p '{"spec":{"selector":{"version":"green"}}}'

# 2. Monitor for issues
kubectl logs -f deployment/backend-green -n neotool-prod

# 3. If successful, remove blue deployment after 1 hour
kubectl delete deployment backend-blue -n neotool-prod
```

### Rollback

```bash
# Switch traffic back to blue
kubectl patch service backend -n neotool-prod \
  -p '{"spec":{"selector":{"version":"blue"}}}'
```

---

## Canary Deployment

**Use case**: Gradual rollout, risk mitigation

### Process

```bash
# 1. Deploy canary with 10% traffic
kubectl apply -f infra/k8s/overlays/prod/canary/

# Canary deployment has 1 replica vs 9 stable replicas
# Service routes 10% traffic to canary pod

# 2. Monitor canary metrics for 30 minutes
# Check error rates, latency, resource usage in Grafana

# 3. If successful, increase to 50%
kubectl scale deployment/backend-canary --replicas=5 -n neotool-prod
kubectl scale deployment/backend-stable --replicas=5 -n neotool-prod

# 4. If still successful, complete rollout
kubectl scale deployment/backend-canary --replicas=10 -n neotool-prod
kubectl scale deployment/backend-stable --replicas=0 -n neotool-prod
kubectl delete deployment/backend-stable -n neotool-prod
```

---

## Troubleshooting

### Deployment Stuck

**Symptoms**: Pods not reaching Ready state

**Diagnosis**:
```bash
kubectl describe pod <pod-name> -n neotool-prod
kubectl logs <pod-name> -n neotool-prod
kubectl get events -n neotool-prod --sort-by='.lastTimestamp'
```

**Common Issues**:
1. **Image pull error**: Check registry credentials
2. **Health check failure**: Check `/health` endpoint
3. **Database connection**: Verify DATABASE_URL secret
4. **Resource limits**: Check CPU/memory constraints

**Resolution**:
```bash
# Fix issue, then restart rollout
kubectl rollout restart deployment/backend -n neotool-prod
```

### Migration Failure

**Symptoms**: Service won't start, migration errors in logs

**Resolution**:
1. Scale down deployment
2. Run migration manually
3. Check migration SQL for errors
4. Fix migration or rollback database
5. Restart deployment

### Rollback Failure

**Symptoms**: Rollback command succeeds but issues persist

**Resolution**:
```bash
# Hard reset to known-good version
kubectl set image deployment/backend \
  backend=ghcr.io/org/neotool/backend:v1.0.0-stable \
  -n neotool-prod
```

---

## Related Documentation

- [Infrastructure Overview](./README.md)
- [Operations Runbook](./operations-runbook.md)
- [Troubleshooting Guide](./troubleshooting-guide.md)
- [Infrastructure Architecture](../02-architecture/infrastructure-architecture.md)
- [Deployment Workflow](../08-workflows/deployment-workflow.md)

---

**Version**: 1.0.0 (2026-01-02)
**Last Verified**: Production deployment 2026-01-02
**Review Frequency**: After each major deployment

*Deploy with confidence. Monitor closely. Rollback quickly.*
