---
title: Kubernetes Operations Runbook
type: infrastructure
category: operations
status: current
version: 1.0.0
tags: [kubernetes, k8s, operations, runbook, gitops, flux, kubectl, troubleshooting]
ai_optimized: true
search_keywords: [kubernetes, k8s, operations, kubectl, flux, gitops, troubleshooting, debugging, logs, database, pgbouncer]
related:
  - 11-infrastructure/architecture.md
  - 11-infrastructure/hostinger-runbook.md
  - 10-observability/observability-overview.md
last_updated: 2026-01-15
---

# Kubernetes Operations Runbook

> **Complete guide for day-to-day Kubernetes operations**: GitOps workflows, pod management, database access, monitoring, and troubleshooting.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [GitOps with Flux CD](#gitops-with-flux-cd)
3. [Pod & Deployment Management](#pod--deployment-management)
4. [Database Operations](#database-operations)
5. [Secrets Management](#secrets-management)
6. [Monitoring & Metrics](#monitoring--metrics)
7. [Logs & Debugging](#logs--debugging)
8. [Port Forwarding](#port-forwarding)
9. [Troubleshooting](#troubleshooting)

---

## Quick Reference

### Most Used Commands

```bash
# View logs
kubectl logs -f deployment/backend -n production --tail=100

# Port forward
kubectl port-forward svc/backend 8080:8080 -n production

# Scale deployment
kubectl scale deployment/backend --replicas=5 -n production

# Restart deployment
kubectl rollout restart deployment/backend -n production

# Exec into pod
kubectl exec -it deployment/backend -n production -- /bin/sh

# View pod status
kubectl get pods -n production -o wide

# Recent events
kubectl get events -n production --sort-by='.lastTimestamp'

# Flux reconciliation
flux reconcile kustomization infrastructure --with-source

# Check Flux status
flux get all
```

### Environment Setup

```bash
# Set kubeconfig
export KUBECONFIG=~/.kube/config-hostinger

# Verify connection
kubectl cluster-info
kubectl get nodes

# Check Flux health
flux check
```

---

## GitOps with Flux CD

### How GitOps Works

**The GitOps Loop**:

1. Developer commits changes to Git repository
2. Flux GitRepository controller polls Git (every 1 minute)
3. Flux detects changes in watched paths
4. Kustomization controller reconciles changes
5. HelmRelease controller reconciles Helm charts
6. Cluster state matches Git state ✅

### Flux Architecture

```
GitHub Repository
    ↓ (polling every 1 minute)
GitRepository Controller (flux-system)
    ↓
Kustomization Controllers
    ├─→ infrastructure (10 min interval)
    └─→ apps (5 min interval)
    ↓
K3S Cluster Resources
```

### Managing Flux

#### Check Flux Status

```bash
# Flux health check
flux check

# View all Flux resources
flux get all

# View Kustomizations
flux get kustomizations

# View HelmReleases
flux get helmreleases -n flux-system

# View Git sources
flux get sources git
```

#### Force Reconciliation

```bash
# Force Git sync
flux reconcile source git flux-system --with-source

# Force infrastructure sync
flux reconcile kustomization infrastructure --with-source

# Force apps sync
flux reconcile kustomization apps --with-source

# Force Helm release sync
flux reconcile helmrelease vault -n flux-system
```

#### Watch Reconciliation

```bash
# Watch all Kustomizations
flux get kustomizations --watch

# Watch specific Kustomization
flux get kustomization infrastructure --watch

# View reconciliation events
flux events --for Kustomization/infrastructure
```

#### Suspend/Resume Reconciliation

```bash
# Pause reconciliation (for maintenance)
flux suspend kustomization apps

# Resume reconciliation
flux resume kustomization apps

# Suspend HelmRelease
flux suspend helmrelease vault -n flux-system

# Resume HelmRelease
flux resume helmrelease vault -n flux-system
```

### Deploying Applications via GitOps

#### Complete Workflow Example

**Step 1: Create Application Manifest**

```bash
# Create deployment manifest
cat > infra/kubernetes/flux/apps/services/my-service.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-service
  namespace: production
spec:
  replicas: 2
  selector:
    matchLabels:
      app: my-service
  template:
    metadata:
      labels:
        app: my-service
    spec:
      containers:
      - name: my-service
        image: my-service:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: postgres-credentials
              key: url
EOF
```

**Step 2: Add to Kustomization**

```bash
# Add to kustomization.yaml
echo "  - my-service.yaml" >> infra/kubernetes/flux/apps/services/kustomization.yaml
```

**Step 3: Commit and Push**

```bash
git add infra/kubernetes/flux/apps/services/
git commit -m "Add my-service deployment"
git push origin main  # or your branch
```

**Step 4: Flux Detects and Deploys (automatic)**

```bash
# Watch Flux apply changes (within 5 minutes)
flux get kustomizations apps --watch

# Output:
# NAME   READY   MESSAGE                         REVISION
# apps   True    Applied revision: main/abc123   main/abc123
```

**Step 5: Verify Deployment**

```bash
# Check deployment
kubectl get deployment my-service -n production

# Check pods
kubectl get pods -n production -l app=my-service

# View logs
kubectl logs -f deployment/my-service -n production
```

#### Update Application Image

```bash
# Edit deployment manifest
vim infra/kubernetes/flux/apps/services/my-service.yaml
# Change: image: my-service:1.0.0 → image: my-service:1.1.0

# Commit and push
git add .
git commit -m "Update my-service to v1.1.0"
git push

# Flux auto-updates! (within 5 minutes)
```

#### Scale Application

```bash
# Edit deployment
vim infra/kubernetes/flux/apps/services/my-service.yaml
# Change: replicas: 2 → replicas: 5

# Commit and push
git add .
git commit -m "Scale my-service to 5 replicas"
git push

# Flux auto-scales!
```

#### Rollback Deployment

```bash
# Option 1: Git revert
git revert HEAD
git push

# Option 2: Checkout previous version
git checkout HEAD~1 -- infra/kubernetes/flux/apps/services/my-service.yaml
git commit -m "Rollback my-service"
git push

# Flux automatically rolls back!
```

---

## Pod & Deployment Management

### Viewing Pods

```bash
# List all pods
kubectl get pods -n production

# Detailed pod info
kubectl get pods -n production -o wide

# Pod resource usage
kubectl top pods -n production

# Describe pod (events, status, containers)
kubectl describe pod backend-7d8f9c5b6-xyz12 -n production

# Watch pod status
kubectl get pods -n production -w
```

### Pod Lifecycle

#### Restart Pods

```bash
# Restart deployment (rolling restart)
kubectl rollout restart deployment/backend -n production

# Delete specific pod (will be recreated)
kubectl delete pod backend-7d8f9c5b6-xyz12 -n production

# Force delete stuck pod
kubectl delete pod backend-7d8f9c5b6-xyz12 -n production --grace-period=0 --force
```

#### Execute Commands in Pod

```bash
# Interactive shell
kubectl exec -it deployment/backend -n production -- /bin/sh

# Run single command
kubectl exec deployment/backend -n production -- env

# Run in specific container (multi-container pod)
kubectl exec -it backend-7d8f9c5b6-xyz12 -c sidecar -n production -- /bin/sh
```

#### Copy Files To/From Pod

```bash
# Copy from pod
kubectl cp production/backend-7d8f9c5b6-xyz12:/app/config.yaml ./config.yaml

# Copy to pod
kubectl cp ./local-file.txt production/backend-7d8f9c5b6-xyz12:/tmp/
```

### Deployment Management

#### Deployment Status

```bash
# List deployments
kubectl get deployments -n production

# Deployment details
kubectl describe deployment backend -n production

# Rollout status
kubectl rollout status deployment/backend -n production

# Rollout history
kubectl rollout history deployment/backend -n production

# Revision details
kubectl rollout history deployment/backend --revision=3 -n production
```

#### Scaling

**Manual Scaling**:
```bash
# Scale to 5 replicas
kubectl scale deployment/backend --replicas=5 -n production

# Verify scaling
kubectl get deployment backend -n production
kubectl get pods -n production -l app=backend
```

**Horizontal Pod Autoscaler (HPA)**:
```bash
# Create HPA
kubectl autoscale deployment backend \
  --cpu-percent=70 \
  --min=3 \
  --max=10 \
  -n production

# View HPA status
kubectl get hpa -n production

# HPA details
kubectl describe hpa backend -n production

# Delete HPA
kubectl delete hpa backend -n production
```

#### Updating Deployments

**Update Image**:
```bash
# Set new image
kubectl set image deployment/backend \
  backend=ghcr.io/org/neotool/backend:v1.2.4 \
  -n production

# Monitor rollout
kubectl rollout status deployment/backend -n production
```

**Update Environment Variables**:
```bash
# Update env var
kubectl set env deployment/backend LOG_LEVEL=DEBUG -n production

# Remove env var
kubectl set env deployment/backend LOG_LEVEL- -n production
```

**Pause/Resume Rollout**:
```bash
# Pause (for making multiple changes)
kubectl rollout pause deployment/backend -n production

# Make changes...
kubectl set image deployment/backend backend=new-image
kubectl set env deployment/backend NEW_VAR=value

# Resume
kubectl rollout resume deployment/backend -n production
```

---

## Database Operations

### PostgreSQL Access

#### Interactive psql Session

```bash
# Connect to database
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool

# In psql:
\l                  # List databases
\c neotool          # Connect to database
\dt                 # List tables
\d users            # Describe table
\q                  # Quit
```

#### Run SQL Queries

```bash
# Single query
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool -c "SELECT COUNT(*) FROM users;"

# From file
kubectl exec -i postgresql-0 -n production -- \
  psql -U postgres -d neotool < query.sql

# Output to CSV
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool -c "COPY (SELECT * FROM users) TO STDOUT WITH CSV HEADER;" > users.csv
```

#### Database Info

```bash
# Database size
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool -c "\l+"

# Table sizes
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool -c "
    SELECT
      schemaname,
      tablename,
      pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
    FROM pg_tables
    WHERE schemaname = 'public'
    ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;"

# Active connections
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool -c "SELECT * FROM pg_stat_activity;"
```

### PgBouncer Operations

#### About PgBouncer

PgBouncer is a connection pooler that reduces the overhead of creating new database connections.

**Architecture**:
```
Applications (port 6432, auth: plain)
    ↓
PgBouncer (connection pooling)
    ↓
PostgreSQL (port 5432, auth: SCRAM-SHA-256)
```

**Configuration**:
- **Auth Type**: `plain` (accepts plain text from clients)
- **Pool Mode**: `transaction` (connection returns to pool after each transaction)
- **Max Client Connections**: 1000
- **Default Pool Size**: 25

#### Verify PgBouncer

```bash
# Check if PgBouncer is running
kubectl get pods -n production -l app=pgbouncer

# View logs
kubectl logs -n production -l app=pgbouncer

# Test connection
kubectl exec -n production deployment/pgbouncer -- \
  sh -c 'PGPASSWORD=$POSTGRES_PASSWORD psql -h localhost -p 6432 -U neotool -d neotool_db -c "SELECT 1;"'
```

#### PgBouncer Configuration

```bash
# View pgbouncer.ini
kubectl exec -n production deployment/pgbouncer -- cat /etc/pgbouncer/pgbouncer.ini

# View userlist.txt
kubectl exec -n production deployment/pgbouncer -- cat /etc/pgbouncer/userlist.txt
```

#### PgBouncer Statistics

```bash
# View pool statistics
kubectl exec -n production deployment/pgbouncer -- \
  psql -h localhost -p 6432 -U pgbouncer pgbouncer -c "SHOW POOLS;"

# View active clients
kubectl exec -n production deployment/pgbouncer -- \
  psql -h localhost -p 6432 -U pgbouncer pgbouncer -c "SHOW CLIENTS;"

# View database statistics
kubectl exec -n production deployment/pgbouncer -- \
  psql -h localhost -p 6432 -U pgbouncer pgbouncer -c "SHOW STATS;"
```

#### Connect to Database via PgBouncer

**From Application**:
```yaml
# Connection string
postgresql://neotool:password@pgbouncer.production.svc.cluster.local:6432/neotool_db
```

**From Local Machine (DBeaver)**:
```bash
# Terminal 1: Port forward
kubectl port-forward -n production svc/pgbouncer 6432:6432

# In DBeaver:
# - Host: localhost
# - Port: 6432
# - Database: neotool_db
# - Username: neotool
# - Password: (from secret postgres-credentials)
# - SSL: DISABLE
```

**Get Password**:
```bash
kubectl get secret postgres-credentials -n production -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d
```

#### PgBouncer Troubleshooting

**Issue: Pod not starting**

```bash
# Check pod status
kubectl describe pod -n production -l app=pgbouncer
kubectl logs -n production -l app=pgbouncer
```

**Common causes**:
1. Secret `pgbouncer-credentials` not synced → Check External Secrets
2. Secret `pgbouncer-userlist` not exists → Run setup script
3. ResourceQuota → Ensure initContainer has resources defined

**Issue: "wrong password type" error**

Means password in pgbouncer.ini is incorrect or in wrong format.

```bash
# Verify connection string
kubectl exec -n production deployment/pgbouncer -- \
  cat /etc/pgbouncer/pgbouncer.ini | grep neotool_db

# Should show:
# neotool_db = host=postgres-headless port=5432 dbname=neotool_db user=neotool password=<password>

# If wrong, recreate pod
kubectl delete pod -n production -l app=pgbouncer
```

**Issue: Connection timeout**

```bash
# Check PgBouncer is ready
kubectl get pods -n production -l app=pgbouncer

# Check if listening on port
kubectl exec -n production deployment/pgbouncer -- netstat -tlnp | grep 6432

# View logs in real-time
kubectl logs -n production -l app=pgbouncer -f
```

### Database Backups

#### Manual Backup

```bash
# Full database dump
kubectl exec -it postgresql-0 -n production -- \
  pg_dump -U postgres neotool > backup-$(date +%Y%m%d-%H%M%S).sql

# Compressed backup
kubectl exec -it postgresql-0 -n production -- \
  pg_dump -U postgres neotool | gzip > backup-$(date +%Y%m%d-%H%M%S).sql.gz

# Schema only
kubectl exec -it postgresql-0 -n production -- \
  pg_dump -U postgres -s neotool > schema-$(date +%Y%m%d).sql

# Specific table
kubectl exec -it postgresql-0 -n production -- \
  pg_dump -U postgres -t users neotool > users-backup.sql
```

#### Restore from Backup

```bash
# Restore full database
kubectl exec -i postgresql-0 -n production -- \
  psql -U postgres neotool < backup.sql

# Restore compressed backup
gunzip -c backup.sql.gz | kubectl exec -i postgresql-0 -n production -- \
  psql -U postgres neotool
```

---

## Secrets Management

### Vault Operations

#### Check Vault Status

```bash
# Check pod status
kubectl get pods vault-0 -n production

# Check Vault seal status
kubectl exec -n production vault-0 -- vault status

# Output interpretation:
# Sealed: false  ← Unsealed (operational)
# Sealed: true   ← Sealed (need to unseal)
```

#### Unseal Vault

Vault seals automatically after pod restart for security.

```bash
# Load credentials
source ~/.neotool/vault-credentials.txt

# Unseal (requires 3 of 5 keys)
kubectl exec -n production vault-0 -- vault operator unseal "$UNSEAL_KEY_1"
kubectl exec -n production vault-0 -- vault operator unseal "$UNSEAL_KEY_2"
kubectl exec -n production vault-0 -- vault operator unseal "$UNSEAL_KEY_3"

# Or use script
cd ~/src/Neotool/infra/kubernetes/scripts
./vault-unseal.sh
```

#### View Secrets in Vault

```bash
# List secrets
kubectl exec -n production vault-0 -- vault kv list secret/

# Get secret
kubectl exec -n production vault-0 -- vault kv get secret/postgres

# If permission denied, authenticate with root token:
source ~/.neotool/vault-credentials.txt
kubectl exec -n production vault-0 -- sh -c "VAULT_TOKEN='$ROOT_TOKEN' vault kv get secret/postgres"
```

#### Store Secrets in Vault

```bash
# Store new secret
kubectl exec -n production vault-0 -- sh -c "VAULT_TOKEN='$ROOT_TOKEN' vault kv put secret/my-app \
  api_key=abc123 \
  db_password=secret"
```

### External Secrets Operator

#### Check ExternalSecret Status

```bash
# List ExternalSecrets
kubectl get externalsecret -n production

# Detailed status
kubectl describe externalsecret postgres-credentials -n production

# Should show:
# Status: SecretSynced
# Ready: True
```

#### Check SecretStore

```bash
# View SecretStore
kubectl get secretstore -n production

# Detailed status
kubectl describe secretstore vault-backend -n production

# Should show:
# Ready: True
```

#### Force Secret Sync

```bash
# Force reconciliation by annotation
kubectl annotate externalsecret postgres-credentials -n production force-sync="$(date +%s)" --overwrite

# Or delete and recreate
kubectl delete externalsecret postgres-credentials -n production
kubectl apply -f ~/src/Neotool/infra/kubernetes/flux/infrastructure/external-secrets-config/postgres-external-secret.yaml
```

### Kubernetes Secrets

#### View Secrets

```bash
# List secrets
kubectl get secrets -n production

# Secret details (values are base64 encoded)
kubectl describe secret postgres-credentials -n production

# Decode secret value
kubectl get secret postgres-credentials -n production -o jsonpath='{.data.password}' | base64 -d
```

#### Update Secret

```bash
# Create/update from literal
kubectl create secret generic database-credentials \
  --from-literal=username=postgres \
  --from-literal=password=newpassword \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart pods to pick up new secret
kubectl rollout restart deployment/backend -n production
```

---

## Monitoring & Metrics

### Resource Usage

#### Node Metrics

```bash
# Node resource usage
kubectl top nodes

# Detailed node info
kubectl describe node <node-name>
```

#### Pod Metrics

```bash
# Pod CPU/Memory usage
kubectl top pods -n production

# Sort by CPU
kubectl top pods -n production --sort-by=cpu

# Sort by memory
kubectl top pods -n production --sort-by=memory

# Container-level metrics
kubectl top pods -n production --containers
```

### Prometheus

#### Access Prometheus

```bash
# Port forward Prometheus
kubectl port-forward -n production svc/prometheus 9090:9090

# Open: http://localhost:9090
```

#### Example PromQL Queries

```promql
# Request rate (per second)
rate(http_requests_total[5m])

# Error rate
rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m])

# 95th percentile latency
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# Memory usage by pod
container_memory_usage_bytes{namespace="production", pod=~"backend.*"}

# CPU usage by pod
rate(container_cpu_usage_seconds_total{namespace="production", pod=~"backend.*"}[5m])
```

### Grafana

#### Access Grafana

```bash
# Port forward Grafana
kubectl port-forward -n production svc/grafana 3000:80

# Open: http://localhost:3000
# Default: admin/admin (change on first login)
```

#### Key Dashboards

- **Kubernetes Cluster**: Node health, pod status, resource usage
- **PostgreSQL**: Connections, queries, cache hit ratio
- **JVM Metrics**: Heap, GC, threads (backend)
- **GraphQL**: Query performance, resolver metrics

### Loki (Logs)

#### Access Loki via Grafana

1. Open Grafana: `http://localhost:3000`
2. Navigate to Explore → Loki
3. Use LogQL queries

#### Example LogQL Queries

```logql
# All backend logs
{namespace="production", app="backend"}

# Error logs only
{namespace="production", app="backend"} |= "ERROR"

# Logs containing specific user ID
{namespace="production", app="backend"} |= "userId=123"

# Rate of errors per minute
rate({namespace="production"} |= "ERROR" [1m])
```

---

## Logs & Debugging

### Application Logs

#### Single Pod Logs

```bash
# Tail logs from deployment
kubectl logs -f deployment/backend -n production --tail=100

# Logs from specific pod
kubectl logs backend-7d8f9c5b6-xyz12 -n production

# Previous container (if pod crashed)
kubectl logs backend-7d8f9c5b6-xyz12 -n production --previous

# Logs since timestamp
kubectl logs deployment/backend -n production --since=1h

# Logs with timestamps
kubectl logs deployment/backend -n production --timestamps
```

#### Multi-Pod Logs (with stern)

```bash
# Install stern
brew install stern

# Tail all backend pods
stern backend -n production

# Filter by log level
stern backend -n production | grep ERROR

# Multiple deployments
stern 'backend|frontend' -n production

# Exclude noise
stern backend -n production --exclude='health check'
```

#### All Pods in Namespace

```bash
# All pods
kubectl logs --all-containers=true --prefix=true -n production

# Save logs to file
kubectl logs deployment/backend -n production > backend-logs.txt
```

### Infrastructure Logs

#### Kubernetes Events

```bash
# Recent events
kubectl get events -n production --sort-by='.lastTimestamp'

# Events for specific pod
kubectl describe pod backend-7d8f9c5b6-xyz12 -n production

# Warning events only
kubectl get events -n production --field-selector type=Warning
```

#### Flux Logs

```bash
# All Flux controllers
flux logs --all-namespaces --follow

# Specific controller
flux logs --kind=Kustomization --name=infrastructure

# View events
flux events --for Kustomization/infrastructure
```

---

## Port Forwarding

### Service Port Forwarding

```bash
# Backend service
kubectl port-forward svc/backend 8080:8080 -n production
# Access: http://localhost:8080

# Frontend service
kubectl port-forward svc/frontend 3000:3000 -n production
# Access: http://localhost:3000

# PostgreSQL database
kubectl port-forward svc/postgresql 5432:5432 -n production
# Connect: psql -h localhost -U postgres -d neotool

# PgBouncer
kubectl port-forward svc/pgbouncer 6432:6432 -n production
# Connect: psql -h localhost -p 6432 -U neotool -d neotool_db

# GraphQL Playground
kubectl port-forward svc/apollo-router 4000:4000 -n production
# Access: http://localhost:4000/graphql

# Grafana
kubectl port-forward -n production svc/grafana 3000:80
# Access: http://localhost:3000

# Prometheus
kubectl port-forward -n production svc/prometheus 9090:9090
# Access: http://localhost:9090
```

### Background Port Forwarding

```bash
# Run in background
kubectl port-forward svc/backend 8080:8080 -n production &

# List background jobs
jobs

# Kill background port-forward
kill %1  # Job number from jobs command

# Or kill by port
lsof -ti:8080 | xargs kill
```

---

## Troubleshooting

### Pod Not Starting

#### Diagnosis

```bash
# Check pod status
kubectl get pod backend-7d8f9c5b6-xyz12 -n production

# Describe pod for events
kubectl describe pod backend-7d8f9c5b6-xyz12 -n production

# Check logs
kubectl logs backend-7d8f9c5b6-xyz12 -n production

# Previous container logs (if crashed)
kubectl logs backend-7d8f9c5b6-xyz12 -n production --previous
```

#### Common Issues

**1. Image Pull Error**

```bash
# Check imagePullSecrets
kubectl get pod backend-7d8f9c5b6-xyz12 -n production -o yaml | grep imagePullSecrets

# Verify secret exists
kubectl get secret ghcr-secret -n production
```

**2. CrashLoopBackOff**

```bash
# View crash logs
kubectl logs backend-7d8f9c5b6-xyz12 -n production --previous

# Check liveness/readiness probes
kubectl describe pod backend-7d8f9c5b6-xyz12 -n production | grep -A 10 Liveness
```

**3. Resource Limits**

```bash
# Check resource requests/limits
kubectl describe pod backend-7d8f9c5b6-xyz12 -n production | grep -A 5 Limits

# View node capacity
kubectl describe node <node-name>
```

### Flux Not Syncing

#### Diagnosis

```bash
# Check GitRepository
flux get sources git flux-system

# Check for errors
flux events --for GitRepository/flux-system

# Check Kustomization
flux get kustomizations
```

#### Solutions

```bash
# Force reconcile
flux reconcile source git flux-system --with-source

# Check branch name
kubectl get gitrepository flux-system -n flux-system -o yaml | grep branch

# Verify deploy key in GitHub
# Settings → Deploy keys (should see Flux key)
```

### Database Connection Issues

#### Check Connectivity

```bash
# Test from backend pod
kubectl exec -it deployment/backend -n production -- \
  nc -zv pgbouncer.production.svc.cluster.local 6432

# Check DATABASE_URL secret
kubectl get secret postgres-credentials -n production -o jsonpath='{.data.url}' | base64 -d

# Verify PostgreSQL is running
kubectl get pods -n production -l app=postgres
kubectl logs postgresql-0 -n production
```

#### Connection Pool Issues

```bash
# Check active connections
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -d neotool -c "SELECT count(*) FROM pg_stat_activity;"

# Max connections
kubectl exec -it postgresql-0 -n production -- \
  psql -U postgres -c "SHOW max_connections;"
```

### High CPU/Memory Usage

#### Identify Resource-Heavy Pods

```bash
# Top CPU consumers
kubectl top pods -n production --sort-by=cpu

# Top memory consumers
kubectl top pods -n production --sort-by=memory

# Resource limits
kubectl describe pod backend-7d8f9c5b6-xyz12 -n production | grep -A 10 Limits
```

#### Take Heap Dump (JVM)

```bash
# Exec into pod
kubectl exec -it deployment/backend -n production -- /bin/sh

# Inside pod: Generate heap dump
jmap -dump:live,format=b,file=/tmp/heap.hprof $(pgrep java)

# Copy heap dump locally
kubectl cp production/backend-7d8f9c5b6-xyz12:/tmp/heap.hprof ./heap.hprof

# Analyze with VisualVM, Eclipse MAT, or JProfiler
```

### Vault Issues

#### Vault Sealed

```bash
# Check status
kubectl exec -n production vault-0 -- vault status

# If sealed, unseal
source ~/.neotool/vault-credentials.txt
./infra/kubernetes/scripts/vault-unseal.sh
```

#### External Secrets Not Syncing

```bash
# Check ExternalSecret
kubectl get externalsecret -n production
kubectl describe externalsecret postgres-credentials -n production

# Check SecretStore
kubectl get secretstore -n production
kubectl describe secretstore vault-backend -n production

# Check Vault connection
kubectl exec -n production vault-0 -- vault status
```

#### Service Account Not Authorized

**Symptom**: `Code: 403. Errors: * service account name not authorized`

**Solution**: Verify and reconfigure Vault role

```bash
source ~/.neotool/vault-credentials.txt

# Check current role
kubectl exec -n production vault-0 -- sh -c "VAULT_TOKEN='$ROOT_TOKEN' vault read auth/kubernetes/role/app"

# Should show: bound_service_account_names = [external-secrets-vault-auth]

# If wrong, reconfigure:
kubectl exec -n production vault-0 -- sh -c "VAULT_TOKEN='$ROOT_TOKEN' vault write auth/kubernetes/role/app \
    bound_service_account_names=external-secrets-vault-auth \
    bound_service_account_namespaces=production \
    policies=app-policy \
    ttl=24h"

# Wait ~30 seconds, then verify:
kubectl get secretstore vault-backend -n production
# Should show: READY: True
```

### HelmRelease Stuck

```bash
# Check Helm release status
helm list -n production
helm status vault -n production

# View Flux events
flux events --for HelmRelease/vault

# Suspend and resume
flux suspend helmrelease vault -n flux-system
flux resume helmrelease vault -n flux-system
```

### Certificate Issues

#### Check Certificate Expiry

```bash
# TLS secret
kubectl get secret tls-cert -n production -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -dates

# Cert-manager certificates
kubectl get certificates -n production
kubectl describe certificate neotool-tls -n production
```

---

## Maintenance Tasks

### Daily Operations

```bash
# Flux health
flux check

# Cluster health
kubectl get nodes
kubectl top nodes
kubectl get pods -A | grep -v Running

# Flux reconciliation
flux get all
```

### Weekly Maintenance

#### Update Helm Charts

```bash
# Check for chart updates
flux get helmrelease -n flux-system

# Update chart version in Git
vim infra/kubernetes/flux/infrastructure/vault/helmrelease.yaml
# Change: version: '0.27.0' → version: '0.28.0'

git add .
git commit -m "Update Vault chart to 0.28.0"
git push

# Flux upgrades automatically
```

#### Review Resource Usage

```bash
# Check resource quotas
kubectl describe resourcequota production-quota -n production

# Check actual usage
kubectl top pods -n production --sort-by=memory
kubectl top pods -n production --sort-by=cpu
```

### Cleanup

#### Delete Old ReplicaSets

```bash
# List old ReplicaSets
kubectl get replicasets -n production

# Delete ReplicaSets with 0 desired/current replicas
kubectl delete replicaset $(kubectl get replicaset -n production -o jsonpath='{.items[?(@.spec.replicas==0)].metadata.name}') -n production
```

#### Clean Up Completed Jobs

```bash
# Delete completed jobs
kubectl delete jobs --field-selector status.successful=1 -n production

# Delete failed jobs
kubectl delete jobs --field-selector status.failed=1 -n production
```

---

## Related Documentation

- [Infrastructure Architecture](./architecture.md) - High-level architecture overview
- [Hostinger Infrastructure Runbook](./hostinger-runbook.md) - VPS provisioning, K3S cluster setup
- [Observability Overview](../10-observability/observability-overview.md) - Monitoring stack details

---

**Version**: 1.0.0
**Last Updated**: 2026-01-15
**Review Frequency**: Monthly

*Run operations smoothly. Debug efficiently. Maintain proactively.* 🚀
