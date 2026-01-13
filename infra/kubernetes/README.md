# Neotool Kubernetes GitOps Infrastructure

> **Complete guide to GitOps deployment using Flux CD on Kubernetes**

This repository contains the complete GitOps infrastructure for deploying Neotool to Kubernetes using Flux CD. All infrastructure is managed declaratively through Git, with automatic synchronization from repository to cluster.

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [How GitOps Works](#how-gitops-works)
4. [Prerequisites](#prerequisites)
5. [Quick Start](#quick-start)
6. [Step-by-Step GitOps Workflow](#step-by-step-gitops-workflow)
7. [Maintenance Guide](#maintenance-guide)
8. [Testing Guide](#testing-guide)
9. [Local Development](#local-development)
10. [Troubleshooting](#troubleshooting)
11. [Reference](#reference)

---

## Overview

### What is GitOps?

GitOps is a methodology where **Git is the single source of truth** for infrastructure and application configurations. Changes are made by committing to Git, and Flux CD automatically applies those changes to your Kubernetes cluster.

### Key Benefits

- ✅ **Declarative**: Infrastructure defined in YAML files
- ✅ **Version Controlled**: All changes tracked in Git
- ✅ **Automated**: Push to Git → Automatic deployment
- ✅ **Auditable**: Full history of all changes
- ✅ **Reversible**: Easy rollback via Git revert
- ✅ **Consistent**: Same process for dev, staging, and production

---

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    GitHub Repository                        │
│  (Source of Truth - All K8s manifests live here)            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ Git Push / Polling
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Flux Controllers (flux-system)             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ GitRepository│  │ Kustomization│  │ HelmRelease  │   │
│  │  Controller  │  │  Controller  │  │  Controller  │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
└─────────┼─────────────────┼─────────────────┼───────────┘
          │                 │                 │
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────┐
│              K3S Cluster (Production)               │
│  ┌──────────────────────────────────────────────┐   │
│  │  Production Namespace                        │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐    │   │
│  │  │  Vault   │  │External  │  │PostgreSQL│    │   │
│  │  │          │  │ Secrets  │  │          │    │   │
│  │  └──────────┘  └──────────┘  └──────────┘    │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐    │   │
│  │  │  Apps    │  │  Web     │  │ Services │    │   │
│  │  └──────────┘  └──────────┘  └──────────┘    │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### Directory Structure

```
infra/kubernetes/
├── flux/                              # Flux GitOps configuration
│   ├── clusters/
│   │   └── production/                # Production cluster config
│   │       ├── flux-system/          # Auto-generated Flux manifests
│   │       │   ├── gotk-components.yaml
│   │       │   ├── gotk-sync.yaml
│   │       │   └── kustomization.yaml
│   │       ├── infrastructure.yaml    # Infrastructure Kustomization
│   │       ├── database.yaml          # Database Kustomization
│   │       ├── streaming.yaml         # Streaming Kustomization
│   │       ├── observability.yaml     # Observability Kustomization
│   │       ├── apps.yaml              # Applications Kustomization (services, web)
│   │       └── external-secrets-config.yaml
│   │
│   ├── infrastructure/                # Platform services (infrastructure layer)
│   │   ├── namespace.yaml            # Production namespace
│   │   ├── resource-quota.yaml      # Resource limits
│   │   ├── kustomization.yaml       # Infrastructure kustomization
│   │   ├── sources/
│   │   │   └── helmrepositories.yaml # Helm chart repositories
│   │   ├── vault/
│   │   │   └── helmrelease.yaml      # Vault Helm release
│   │   └── external-secrets/
│   │       ├── helmrelease.yaml      # External Secrets Operator
│   │       ├── service-account.yaml  # SA for Vault auth
│   │       ├── secret-store.yaml     # Vault connection config
│   │       └── postgres-external-secret.yaml
│   │
│   ├── apps/                         # Application manifests (organized by component type)
│   │   ├── database/                 # PostgreSQL, PgBouncer
│   │   │   ├── kustomization.yaml
│   │   │   ├── postgres/
│   │   │   └── pgbouncer/
│   │   ├── streaming/                # Kafka
│   │   │   ├── kustomization.yaml
│   │   │   └── kafka/
│   │   ├── observability/            # Prometheus, Grafana, Loki, Promtail
│   │   │   ├── kustomization.yaml
│   │   │   ├── prometheus/
│   │   │   ├── grafana/
│   │   │   ├── loki/
│   │   │   └── promtail/
│   │   ├── services/                 # Kotlin services (to be added)
│   │   │   └── kustomization.yaml
│   │   └── web/                      # Next.js, Apollo Router (to be added)
│   │       └── kustomization.yaml
│   │
│   ├── bootstrap.sh                  # Bootstrap Flux (production)
│   ├── bootstrap-dev.sh              # Bootstrap Flux (dev branch)
│   ├── apply-local.sh                # Apply without GitOps (testing)
│   ├── vault-init.sh                 # Initialize Vault
│   ├── vault-unseal.sh               # Unseal Vault
│   ├── vault-configure.sh            # Configure Vault K8s auth
│   └── vault-store-postgres.sh       # Store PostgreSQL credentials
│
└── scripts/                          # Helper scripts
    └── rollback.sh                   # Rollback helper (if needed)
```

### Component Relationships

```
┌─────────────────────────────────────────────────────────────┐
│                    Reconciliation Flow                       │
└─────────────────────────────────────────────────────────────┘

1. GitRepository (flux-system)
   └─> Watches: github.com/salomax/Neotool (branch: 20260108_3)
       Interval: 1 minute
       └─> Provides source for all Kustomizations

2. Kustomization (flux-system)
   └─> Path: ./infra/kubernetes/flux/clusters/production
       └─> Applies: infrastructure.yaml, apps.yaml

3. Kustomization (infrastructure)
   └─> Path: ./infra/kubernetes/flux/infrastructure
       DependsOn: (none - first to deploy)
       └─> Applies:
           ├─> Namespace (production)
           ├─> ResourceQuota
           ├─> HelmRepository (hashicorp, external-secrets)
           ├─> HelmRelease (vault)
           └─> HelmRelease (external-secrets)

4. Kustomization (apps)
   └─> Path: ./infra/kubernetes/flux/apps
       DependsOn: infrastructure
       └─> Applies: (to be added)
           ├─> PostgreSQL
           ├─> Kotlin Services
           └─> Next.js Web App

5. HelmRelease (vault)
   └─> Chart: hashicorp/vault
       Target: production namespace
       └─> Deploys: Vault server

6. HelmRelease (external-secrets)
   └─> Chart: external-secrets/external-secrets
       DependsOn: vault
       └─> Deploys: External Secrets Operator

7. ExternalSecret (postgres-credentials)
   └─> SecretStore: vault-backend
       RefreshInterval: 5 minutes
       └─> Syncs: Vault → Kubernetes Secret
```

---

## How GitOps Works

### The GitOps Loop

1. **Developer commits changes** to Git repository
2. **Flux GitRepository controller** polls Git (every 1 minute) or receives webhook
3. **Flux detects changes** in watched paths
4. **Kustomization controller** reconciles changes:
   - Fetches manifests from Git
   - Applies to cluster using `kubectl apply`
   - Waits for resources to be ready (if `wait: true`)
5. **HelmRelease controller** reconciles Helm charts:
   - Checks chart version
   - Upgrades/installs if needed
6. **Cluster state matches Git state** ✅

### Reconciliation Intervals

| Component | Interval | Purpose |
|-----------|----------|---------|
| GitRepository | 1 minute | Check for Git changes |
| Kustomization (infrastructure) | 10 minutes | Apply infrastructure changes |
| Kustomization (apps) | 5 minutes | Apply application changes |
| HelmRelease | 30 minutes | Check for chart updates |
| ExternalSecret | 5 minutes | Sync secrets from Vault |

### Dependency Management

Flux respects dependencies defined in Kustomizations:

```yaml
# apps.yaml
spec:
  dependsOn:
    - name: infrastructure  # Apps wait for infrastructure
```

**Deployment Order**:
1. `infrastructure` Kustomization (no dependencies)
2. `apps` Kustomization (waits for infrastructure)

**Within Infrastructure**:
1. Namespace & ResourceQuota
2. HelmRepositories (chart sources)
3. Vault HelmRelease
4. External Secrets HelmRelease (depends on Vault)

---

## Prerequisites

### Required Tools

| Tool | Purpose | Installation |
|------|---------|-------------|
| **kubectl** | Kubernetes CLI | [Install kubectl](https://kubernetes.io/docs/tasks/tools/) |
| **flux** | Flux CD CLI | `brew install fluxcd/tap/flux` (macOS)<br>`curl -s https://fluxcd.io/install.sh \| sudo bash` (Linux) |
| **helm** | Helm package manager | `brew install helm` (macOS)<br>`curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \| bash` (Linux) |
| **jq** | JSON processor | `brew install jq` (macOS)<br>`sudo apt-get install jq` (Linux) |

### Cluster Access

```bash
# Set kubeconfig
export KUBECONFIG=~/.kube/config-hostinger

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### GitHub Access

For GitOps to work, you need:

1. **GitHub Personal Access Token** (for bootstrap)
   - Create at: https://github.com/settings/tokens
   - Scopes: `repo` (full control)
   - Save securely (you'll use it once during bootstrap)

2. **Repository Access**
   - Repository: `github.com/salomax/Neotool`
   - Branch: `20260108_3` (dev) or `main` (prod)
   - Flux will create a deploy key automatically

### Pre-flight Checks

```bash
# Check Flux prerequisites
flux check --pre

# Expected output:
# ✔ Kubernetes 1.34.3+k3s1 >=1.32.0-0
# ✔ prerequisites checks passed
```

---

## Quick Start

### 1. Bootstrap Flux (One-Time Setup)

```bash
cd infra/kubernetes/flux

# Interactive bootstrap
./bootstrap.sh

# Or for development branch
./bootstrap-dev.sh
```

**What happens**:
- ✅ Installs Flux controllers in `flux-system` namespace
- ✅ Creates GitHub deploy key
- ✅ Commits Flux manifests to your repo
- ✅ Sets up Git → Cluster sync

### 2. Verify Flux Installation

```bash
# Check Flux health
flux check

# View all Flux resources
flux get all

# Watch reconciliation
flux get kustomizations --watch
```

### 3. Initialize Vault (One-Time)

```bash
cd infra/kubernetes/flux

# Initialize Vault
./vault-init.sh

# Unseal Vault (requires 3 of 5 keys)
./vault-unseal.sh

# Configure Kubernetes authentication
./vault-configure.sh

# Store PostgreSQL credentials
./vault-store-postgres.sh
```

**Important**: Vault credentials are saved to `~/.neotool/vault-credentials.txt` - **BACKUP THIS FILE!**

### 4. Verify Everything Works

```bash
# Check infrastructure
flux get helmrelease -n flux-system
kubectl get pods -n production

# Check External Secrets sync
kubectl get externalsecret -n production
kubectl get secret postgres-credentials -n production
```

---

## Step-by-Step GitOps Workflow

### Understanding the Flow

Let's trace a complete example: **Deploying a new application**

#### Step 1: Create Application Manifest

```bash
# Create new app directory
mkdir -p infra/kubernetes/flux/apps/myapp

# Create deployment manifest
cat > infra/kubernetes/flux/apps/myapp/deployment.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  namespace: production
spec:
  replicas: 2
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      containers:
      - name: myapp
        image: myapp:1.0.0
        ports:
        - containerPort: 8080
EOF
```

#### Step 2: Add to Kustomization

```bash
# Update apps kustomization
cat > infra/kubernetes/flux/apps/kustomization.yaml <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - myapp/deployment.yaml
EOF
```

#### Step 3: Commit and Push

```bash
git add infra/kubernetes/flux/apps/
git commit -m "Add myapp deployment"
git push origin 20260108_3
```

#### Step 4: Flux Detects Changes

```bash
# Watch Flux detect the change (within 1 minute)
flux get sources git flux-system

# Output:
# NAME        READY   MESSAGE                         REVISION
# flux-system True    Fetched revision: abc123...      abc123...
```

#### Step 5: Kustomization Reconciles

```bash
# Watch Kustomization apply changes
flux get kustomizations apps --watch

# Output:
# NAME   READY   MESSAGE                         REVISION
# apps   True    Applied revision: abc123...      abc123...
```

#### Step 6: Verify Deployment

```bash
# Check deployment
kubectl get deployment myapp -n production

# Check pods
kubectl get pods -n production -l app=myapp

# View logs
kubectl logs -f deployment/myapp -n production
```

### Complete Workflow Diagram

```
Developer                    Git                    Flux                    Cluster
    │                        │                       │                       │
    │ 1. Edit manifest       │                       │                       │
    ├───────────────────────>│                       │                       │
    │                        │                       │                       │
    │ 2. git commit          │                       │                       │
    ├───────────────────────>│                       │                       │
    │                        │                       │                       │
    │ 3. git push            │                       │                       │
    ├───────────────────────>│                       │                       │
    │                        │                       │                       │
    │                        │ 4. Poll Git (1 min)   │                       │
    │                        │<──────────────────────┤                       │
    │                        │                       │                       │
    │                        │ 5. Detect changes     │                       │
    │                        ├──────────────────────>│                       │
    │                        │                       │                       │
    │                        │                       │ 6. Fetch manifests   │
    │                        │<──────────────────────┤                       │
    │                        │                       │                       │
    │                        │                       │ 7. Apply to cluster │
    │                        │                       ├──────────────────────>│
    │                        │                       │                       │
    │                        │                       │ 8. Wait for ready    │
    │                        │                       ├──────────────────────>│
    │                        │                       │                       │
    │                        │                       │ 9. Report status     │
    │                        │                       │<──────────────────────┤
    │                        │                       │                       │
    │ 10. Check status       │                       │                       │
    ├────────────────────────────────────────────────────────────────────────>│
    │                        │                       │                       │
```

### Common Operations

#### Update Application Image

```bash
# Edit deployment
vim infra/kubernetes/flux/apps/myapp/deployment.yaml
# Change: image: myapp:1.0.0 → image: myapp:1.1.0

# Commit and push
git add .
git commit -m "Update myapp to v1.1.0"
git push

# Flux auto-updates! (within 5 minutes)
```

#### Scale Application

```bash
# Edit deployment
vim infra/kubernetes/flux/apps/myapp/deployment.yaml
# Change: replicas: 2 → replicas: 5

# Commit and push
git add .
git commit -m "Scale myapp to 5 replicas"
git push

# Flux auto-scales!
```

#### Rollback Deployment

```bash
# Revert Git commit
git revert HEAD
git push

# Or checkout previous version
git checkout HEAD~1 -- infra/kubernetes/flux/apps/myapp/
git commit -m "Rollback myapp"
git push

# Flux automatically rolls back!
```

---

## Maintenance Guide

### Daily Operations

#### Check System Health

```bash
# Flux health
flux check

# Cluster resources
kubectl get pods -A
kubectl top nodes
kubectl top pods -A

# Flux reconciliation status
flux get all
```

#### Monitor Reconciliation

```bash
# Watch all Kustomizations
flux get kustomizations --watch

# Watch specific Kustomization
flux get kustomization infrastructure --watch

# View reconciliation events
flux events --for Kustomization/infrastructure
```

#### View Logs

```bash
# All Flux controllers
flux logs --all-namespaces --follow

# Specific controller
flux logs --kind=Kustomization --name=infrastructure

# Application logs
kubectl logs -f deployment/myapp -n production
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

#### Backup Vault

```bash
# Vault credentials are in:
cat ~/.neotool/vault-credentials.txt

# Export Vault data (if needed)
kubectl exec -n production vault-0 -- vault kv list secret/
```

### Monthly Maintenance

#### Review and Clean Up

```bash
# Check for unused resources
kubectl get all -n production

# Review Git history
git log --oneline --graph --all -- infra/kubernetes/flux/

# Check for drift (manual changes)
flux reconcile kustomization infrastructure --with-source
```

#### Security Updates

```bash
# Update base images
# Edit deployment manifests
vim infra/kubernetes/flux/apps/*/deployment.yaml

# Update image tags
git add .
git commit -m "Security: Update base images"
git push
```

### Troubleshooting Common Issues

#### Kustomization Not Reconciling

```bash
# Check source
flux get sources git flux-system

# Force reconcile
flux reconcile source git flux-system
flux reconcile kustomization infrastructure --with-source
```

#### HelmRelease Stuck

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

#### ExternalSecret Not Syncing

```bash
# Check ExternalSecret status
kubectl get externalsecret -n production
kubectl describe externalsecret postgres-credentials -n production

# Check SecretStore
kubectl get secretstore -n production
kubectl describe secretstore vault-backend -n production

# Check Vault connection
kubectl exec -n production vault-0 -- vault status
```

#### Vault Sealed (After Pod Restart)

```bash
# Vault requires manual unsealing after pod restart
source ~/.neotool/vault-credentials.txt

kubectl exec -n production vault-0 -- vault operator unseal "$UNSEAL_KEY_1"
kubectl exec -n production vault-0 -- vault operator unseal "$UNSEAL_KEY_2"
kubectl exec -n production vault-0 -- vault operator unseal "$UNSEAL_KEY_3"
```

---

## Testing Guide

### Testing GitOps Workflow

#### 1. Test Local Changes (Dry Run)

```bash
# Preview what would be applied
kubectl kustomize infra/kubernetes/flux/infrastructure

# Validate YAML
kubectl apply --dry-run=client -k infra/kubernetes/flux/infrastructure
```

#### 2. Test in Development Branch

```bash
# Bootstrap Flux on dev branch
cd infra/kubernetes/flux
./bootstrap-dev.sh

# Make test changes
vim infra/kubernetes/flux/infrastructure/namespace.yaml
# Add a label

# Commit and push
git add .
git commit -m "Test: Add namespace label"
git push origin 20260108_3

# Watch Flux apply
flux get kustomizations --watch

# Verify
kubectl get namespace production -o yaml | grep label
```

#### 3. Test Rollback

```bash
# Make a breaking change
vim infra/kubernetes/flux/apps/myapp/deployment.yaml
# Set invalid image: image: invalid:latest

# Commit and push
git add .
git commit -m "Test: Breaking change"
git push

# Watch it fail
flux get kustomizations apps --watch

# Rollback
git revert HEAD
git push

# Watch it recover
flux get kustomizations apps --watch
```

### Testing Infrastructure Changes

#### Test Vault Update

```bash
# Update Vault version
vim infra/kubernetes/flux/infrastructure/vault/helmrelease.yaml
# Change: version: '0.27.0' → version: '0.27.1'

# Commit and push
git add .
git commit -m "Test: Update Vault to 0.27.1"
git push

# Monitor upgrade
flux get helmrelease vault -n flux-system --watch
kubectl get pods -n production -l app.kubernetes.io/name=vault --watch
```

#### Test Resource Quota

```bash
# Try to exceed quota
vim infra/kubernetes/flux/apps/myapp/deployment.yaml
# Set: resources.requests.memory: "100Gi"  # Exceeds quota

# Commit and push
git add .
git commit -m "Test: Exceed resource quota"
git push

# Watch it fail
kubectl get events -n production --sort-by='.lastTimestamp'
# Should see: "exceeded quota" error
```

### Integration Testing

#### Test Secret Sync

```bash
# Update secret in Vault
kubectl exec -n production vault-0 -- vault kv put secret/postgres password=newpassword123

# Wait for ExternalSecret to sync (5 minutes)
sleep 300

# Verify Kubernetes secret updated
kubectl get secret postgres-credentials -n production -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d
# Should show: newpassword123
```

#### Test Dependency Order

```bash
# Suspend infrastructure
flux suspend kustomization infrastructure

# Try to deploy app (should wait)
vim infra/kubernetes/flux/apps/myapp/deployment.yaml
# Add deployment

git add .
git commit -m "Test: Deploy app"
git push

# Check apps Kustomization (should show: waiting for infrastructure)
flux get kustomization apps

# Resume infrastructure
flux resume kustomization infrastructure

# Apps should now deploy
flux get kustomization apps --watch
```

---

## Local Development

### Option 1: Local Apply (No GitOps)

For rapid iteration without Git commits:

```bash
cd infra/kubernetes/flux

# Apply manifests directly
./apply-local.sh

# Or apply specific component
kubectl apply -k infrastructure/vault/
```

**Use when**:
- Testing configuration changes
- Rapid iteration
- Debugging issues
- Don't want to commit incomplete work

**Limitations**:
- Changes not tracked in Git
- Need to manually apply
- Not testing real GitOps workflow

### Option 2: GitOps on Development Branch

Full GitOps workflow on a development branch:

```bash
# Bootstrap Flux on dev branch
cd infra/kubernetes/flux
./bootstrap-dev.sh

# Make changes
vim infrastructure/vault/helmrelease.yaml

# Commit and push
git add .
git commit -m "Update Vault config"
git push origin 20260108_3

# Flux auto-deploys! (within 1-10 minutes)
```

**Use when**:
- Testing full GitOps workflow
- Want automatic deployments
- Preparing for production

### Option 3: Local Kubernetes (Kind/Minikube)

Test on local cluster:

```bash
# Install Kind
brew install kind

# Create cluster
kind create cluster --name neotool

# Set kubeconfig
export KUBECONFIG=~/.kube/config

# Bootstrap Flux
cd infra/kubernetes/flux
./bootstrap-dev.sh

# Now test locally!
```

### Development Workflow

#### Recommended: Hybrid Approach

1. **Infrastructure**: Apply locally for testing
   ```bash
   ./apply-local.sh
   ```

2. **Applications**: Use GitOps
   ```bash
   # Bootstrap for apps only
   flux bootstrap github \
     --owner=salomax \
     --repository=Neotool \
     --branch=20260108_3 \
     --path=infra/kubernetes/flux/apps \
     --personal
   ```

3. **When ready**: Move to full GitOps
   ```bash
   # Re-bootstrap for everything
   ./bootstrap.sh
   ```

### Local Testing Checklist

- [ ] Can connect to cluster (`kubectl get nodes`)
- [ ] Flux CLI installed (`flux version`)
- [ ] Pre-flight checks pass (`flux check --pre`)
- [ ] Can apply manifests locally (`kubectl apply -k infrastructure/`)
- [ ] Vault initializes successfully
- [ ] External Secrets syncs from Vault
- [ ] GitOps sync works (if bootstrapped)

---

## Troubleshooting

### Flux Not Syncing

**Symptoms**: Changes in Git not appearing in cluster

**Diagnosis**:
```bash
# Check GitRepository
flux get sources git flux-system

# Check for errors
flux events --for GitRepository/flux-system

# Check Kustomization
flux get kustomizations
```

**Solutions**:
```bash
# Force reconcile
flux reconcile source git flux-system --with-source

# Check branch name
kubectl get gitrepository flux-system -n flux-system -o yaml | grep branch

# Verify deploy key in GitHub
# Settings → Deploy keys (should see Flux key)
```

### Pods Not Starting

**Symptoms**: Pods stuck in Pending or CrashLoopBackOff

**Diagnosis**:
```bash
# Check pod status
kubectl get pods -n production
kubectl describe pod <pod-name> -n production

# Check events
kubectl get events -n production --sort-by='.lastTimestamp'
```

**Common Causes**:
- Resource quota exceeded
- Image pull errors
- Missing secrets
- Storage issues

**Solutions**:
```bash
# Check resource quota
kubectl describe resourcequota production-quota -n production

# Check image
kubectl describe pod <pod-name> -n production | grep Image

# Check secrets
kubectl get secrets -n production
```

### Vault Issues

**Vault Sealed**:
```bash
# Unseal Vault
source ~/.neotool/vault-credentials.txt
./vault-unseal.sh
```

**Vault Not Initialized**:
```bash
# Initialize Vault
./vault-init.sh
```

**External Secrets Not Syncing**:
```bash
# Check Vault connection
kubectl exec -n production vault-0 -- vault status

# Check SecretStore
kubectl get secretstore vault-backend -n production -o yaml

# Check ExternalSecret
kubectl get externalsecret -n production
kubectl describe externalsecret postgres-credentials -n production
```

### HelmRelease Issues

**HelmRelease Stuck**:
```bash
# Check Helm release
helm list -n production
helm status vault -n production

# View Flux events
flux events --for HelmRelease/vault

# Suspend and resume
flux suspend helmrelease vault -n flux-system
# Fix issue
flux resume helmrelease vault -n flux-system
```

**Chart Update Failing**:
```bash
# Check chart version
kubectl get helmrelease vault -n flux-system -o yaml | grep version

# Test chart locally
helm template vault hashicorp/vault --version 0.27.0

# Update version in Git
vim infrastructure/vault/helmrelease.yaml
git add .
git commit -m "Fix: Update Vault chart version"
git push
```

### Network Issues

**Service Not Accessible**:
```bash
# Check service
kubectl get svc -n production
kubectl get endpoints <service-name> -n production

# Port forward for testing
kubectl port-forward -n production svc/<service-name> 8080:80
```

**Linkerd Issues**:
```bash
# Check Linkerd
linkerd check

# Check if namespace has injection
kubectl get namespace production -o yaml | grep linkerd.io/inject

# Enable injection
kubectl annotate namespace production linkerd.io/inject=enabled
```

---

## Reference

### Key Files

| File | Purpose |
|------|---------|
| `flux/clusters/production/infrastructure.yaml` | Infrastructure Kustomization |
| `flux/clusters/production/apps.yaml` | Applications Kustomization |
| `flux/infrastructure/kustomization.yaml` | Infrastructure resources |
| `flux/infrastructure/vault/helmrelease.yaml` | Vault Helm release |
| `flux/infrastructure/external-secrets/helmrelease.yaml` | External Secrets Operator |
| `flux/infrastructure/external-secrets/secret-store.yaml` | Vault connection config |
| `flux/infrastructure/external-secrets/postgres-external-secret.yaml` | PostgreSQL secret sync |

### Important Commands

```bash
# Flux
flux check                                    # Check Flux health
flux get all                                  # List all Flux resources
flux get kustomizations                       # List Kustomizations
flux get helmrelease                          # List HelmReleases
flux reconcile kustomization <name> --with-source  # Force reconcile
flux suspend kustomization <name>             # Pause reconciliation
flux resume kustomization <name>              # Resume reconciliation
flux logs --all-namespaces --follow           # View Flux logs

# Kubernetes
kubectl get pods -n production                # List pods
kubectl get helmrelease -n flux-system       # List Helm releases
kubectl get externalsecret -n production     # List ExternalSecrets
kubectl get secretstore -n production        # List SecretStores
kubectl describe <resource> -n production     # Detailed info
kubectl logs -f <pod> -n production           # View logs
kubectl port-forward -n production svc/<name> <local>:<remote>  # Port forward

# Vault
./vault-init.sh                               # Initialize Vault
./vault-unseal.sh                             # Unseal Vault
./vault-configure.sh                           # Configure Vault
./vault-store-postgres.sh                     # Store PostgreSQL credentials
```

### Reconciliation Intervals

| Resource | Interval | Notes |
|----------|----------|-------|
| GitRepository | 1 minute | Polls Git for changes |
| Kustomization (infrastructure) | 10 minutes | Applies infrastructure |
| Kustomization (apps) | 5 minutes | Applies applications |
| HelmRelease | 30 minutes | Checks for chart updates |
| ExternalSecret | 5 minutes | Syncs secrets from Vault |
| HelmRepository | 24 hours | Updates chart index |

### Resource Limits

**Production Namespace Quota**:
- CPU Requests: 8 cores
- CPU Limits: 12 cores
- Memory Requests: 16Gi
- Memory Limits: 24Gi
- Pods: 50 max
- Storage: 50Gi max

### Security Best Practices

1. ✅ **Never commit secrets** - Use Vault + External Secrets
2. ✅ **Use dedicated service accounts** - Don't use `default`
3. ✅ **Enable Linkerd injection** - Automatic mTLS
4. ✅ **Set resource limits** - Prevent resource exhaustion
5. ✅ **Use non-root containers** - Security contexts
6. ✅ **Regular backups** - Vault credentials, database
7. ✅ **Monitor reconciliation** - Watch for failures
8. ✅ **Review Git history** - Audit all changes

### Useful Links

- [Flux Documentation](https://fluxcd.io/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [HashiCorp Vault](https://www.vaultproject.io/docs)
- [External Secrets Operator](https://external-secrets.io/)
- [K3S Documentation](https://docs.k3s.io/)

---

## Applications Structure

All applications are organized by component type in `flux/apps/`:

### Database (`flux/apps/database/`)
- **PostgreSQL**: StatefulSet with PVC (100Gi storage)
- **PgBouncer**: Connection pooler for PostgreSQL

### Streaming (`flux/apps/streaming/`)
- **Kafka**: StatefulSet with KRaft mode (no Zookeeper)

### Observability (`flux/apps/observability/`)
- **Prometheus**: Metrics collection
- **Grafana**: Dashboards and visualization
- **Loki**: Log aggregation
- **Promtail**: Log collection (DaemonSet)

### Services (`flux/apps/services/`)
- Your Kotlin microservices (to be added)
- Template kustomization ready

### Web (`flux/apps/web/`)
- Next.js frontend (to be added)
- Apollo Router (GraphQL gateway) (to be added)
- Template kustomization ready

### Adding New Components

1. Create directory: `flux/apps/novo-componente/`
2. Add Kubernetes manifests
3. Create `kustomization.yaml` in the component directory
4. Create Kustomization in `clusters/production/novo-componente.yaml`
5. Commit and push - Flux will deploy automatically!

**Note**: All resources use the `production` namespace (not `neotool-*` namespaces).

---

## Next Steps

1. **Deploy Applications**: Add your application manifests to `flux/apps/services/` and `flux/apps/web/`
2. **Set Up Monitoring**: Prometheus and Grafana are already configured in `flux/apps/observability/`
3. **Configure CI/CD**: Integrate with GitHub Actions
4. **Add More Environments**: Create staging/development overlays
5. **Implement Log Retention**: Vault log cleanup CronJob is already configured

---

**Version**: 1.0.0  
**Last Updated**: 2026-01-13  
**Maintained By**: DevOps Team

*GitOps: Infrastructure as Code, Deployed Automatically* 🚀
