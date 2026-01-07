---
title: Kubernetes Deployment Runbook
type: infrastructure
category: deployment
status: current
version: 1.0.0
tags: [kubernetes, k8s, deployment, k3s, runbook, operations]
ai_optimized: true
search_keywords: [kubernetes, k8s, deployment, k3s, kubectl, kustomize, helm, rollback]
related:
  - 11-infrastructure/deployment-guide.md
  - 11-infrastructure/k3s-setup.md
  - 11-infrastructure/terraform-guide.md
  - 11-infrastructure/operations-runbook.md
last_updated: 2026-01-02
---

# Kubernetes Deployment Runbook

> **Purpose**: Step-by-step instructions for deploying Neotool infrastructure to Kubernetes (K3S) clusters.

## Prerequisites

- Terraform >= 1.0
- kubectl installed and configured
- kustomize installed
- Access to target Kubernetes cluster
- Cloud provider credentials (for production)

## Local Development Deployment

### 1. Initialize K3S Cluster

```bash
cd infra/terraform/environments/local
./../../scripts/init-k3s.sh local
```

This will:
- Install K3S on your local machine
- Configure the cluster
- Save kubeconfig to `~/.kube/config`

### 2. Deploy Kubernetes Manifests

```bash
cd infra/kubernetes
./scripts/deploy.sh local
```

This will:
- Apply all base Kubernetes manifests
- Apply local environment patches
- Wait for all deployments to be ready

### 3. Verify Deployment

```bash
kubectl get pods -A
kubectl get services -A
```

## Production VPC Deployment

### 1. Configure Production Environment

```bash
cd infra/terraform/environments/prod
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your production values
```

Required variables:
- `cloudflare_account_id`: Your Cloudflare account ID
- `cloudflare_r2_access_key_id`: R2 access key
- `cloudflare_r2_secret_access_key`: R2 secret key
- `provider_type`: Cloud provider (aws, gcp, azure)
- `region`: Deployment region

### 2. Initialize Infrastructure

```bash
./../../scripts/init-k3s.sh prod
```

This will:
- Create VPC and networking resources
- Deploy K3S cluster in VPC
- Configure Cloudflare R2 storage
- Set up nodes with 8 CPU / 32GB RAM

### 3. Deploy Applications

```bash
cd infra/kubernetes
./scripts/deploy.sh prod
```

### 4. Verify Production Deployment

```bash
kubectl get nodes
kubectl get pods -A
kubectl get pvc -A
```

## Post-Deployment Verification

### Check Service Health

```bash
# Check all deployments
kubectl get deployments -A

# Check pod status
kubectl get pods -A | grep -v Running

# Check service endpoints
kubectl get endpoints -A
```

### Verify Vault JWT Keys

```bash
kubectl exec -n neotool-security deployment/vault -- vault kv get secret/jwt/keys/default
```

### Test Service Communication

```bash
# Test database connection
kubectl exec -n neotool-app deployment/app -- curl http://pgbouncer.neotool-data.svc.cluster.local:6432

# Test Kafka connection
kubectl exec -n neotool-app deployment/app -- nc -zv kafka.neotool-messaging.svc.cluster.local 9092

# Test Vault connection
kubectl exec -n neotool-app deployment/app -- curl http://vault.neotool-security.svc.cluster.local:8200/v1/sys/health
```

## Troubleshooting

### Pods Not Starting

1. Check pod logs:
   ```bash
   kubectl logs -n <namespace> <pod-name>
   ```

2. Check pod events:
   ```bash
   kubectl describe pod -n <namespace> <pod-name>
   ```

### Vault Init Container Failing

1. Check Vault is running:
   ```bash
   kubectl get pods -n neotool-security
   ```

2. Check Vault logs:
   ```bash
   kubectl logs -n neotool-security deployment/vault
   ```

3. Manually run vault-init job:
   ```bash
   kubectl create job --from=job/vault-jwt-init vault-jwt-init-manual -n neotool-security
   ```

### Storage Issues

1. Check PVC status:
   ```bash
   kubectl get pvc -A
   ```

2. Check storage class:
   ```bash
   kubectl get storageclass
   ```

## Rollback Procedures

### Rollback Deployment

```bash
cd infra/kubernetes
./scripts/rollback.sh <environment>
```

### Rollback to Specific Revision

```bash
# List deployment history
kubectl rollout history deployment/<deployment-name> -n <namespace>

# Rollback to specific revision
./scripts/rollback.sh <environment> <revision>
```

## Production VPC Setup

### Node Specifications

- **CPU**: 8 cores per node
- **Memory**: 32GB RAM per node
- **Storage**: Cloudflare R2 (S3-compatible)
- **Network**: Private subnets with NAT gateway

### Security Considerations

- All nodes in private subnets
- Security groups/firewall rules restrict access
- Vault in dev mode (upgrade to HA for production)
- Network policies should be implemented

## Next Steps

After successful deployment:

1. Configure DNS for ingress
2. Set up monitoring alerts
3. Configure backup procedures
4. Implement network policies
5. Set up CI/CD pipelines

