---
title: Hostinger VPC Deployment Runbook
type: infrastructure
category: deployment
status: current
version: 1.0.0
tags: [hostinger, vpc, terraform, k3s, deployment, runbook, operations]
ai_optimized: true
search_keywords: [hostinger, vpc, terraform, k3s, kubernetes, deployment, infrastructure]
related:
  - 11-infrastructure/terraform-guide.md
  - 11-infrastructure/k3s-setup.md
  - 11-infrastructure/k8s-deployment-runbook.md
  - 11-infrastructure/hostinger-setup-guide.md
last_updated: 2026-01-02
---

# Hostinger VPC Deployment Runbook

> **Purpose**: Step-by-step instructions for deploying Neotool infrastructure to Hostinger VPC using Terraform and K3S.

## Overview

This runbook covers deploying a complete K3S Kubernetes cluster on Hostinger VPC using:
- **Hostinger Terraform Provider**: Official provider for provisioning VPS instances
- **K3S**: Lightweight Kubernetes distribution
- **Terraform**: Infrastructure as Code automation
- **Cloudflare R2**: Production storage backend

## Prerequisites

### Required Tools

- Terraform >= 1.0
- kubectl >= 1.28
- kustomize (for Kubernetes manifest deployment)
- SSH client
- Hostinger account with API access

### Required Access

- Hostinger account with API token
- Cloudflare account with R2 bucket configured
- SSH access to Hostinger VPS instances (for troubleshooting)

### Required Information

Before starting, gather:
- Hostinger API token
- Hostinger VPS plan ID
- Hostinger data center ID
- Hostinger template ID (OS image)
- Cloudflare account ID
- Cloudflare R2 credentials

## Pre-Deployment Setup

### 1. Get Hostinger API Token

1. Log in to your Hostinger account
2. Navigate to **API Settings**
3. Generate a new API token
4. Save the token securely (you'll need it for Terraform)

**Reference**: [Hostinger Terraform Provider Documentation](https://www.hostinger.com/support/11080294-getting-started-with-the-hostinger-terraform-provider/)

### 2. Find Hostinger Resource IDs

You need to identify:

- **VPS Plan ID**: Check Hostinger API or control panel
  - Example: `hostingercom-vps-kvm2-usd-1m`
- **Data Center ID**: Geographic location
  - Example: `13` (US data center)
- **Template ID**: OS image
  - Example: `1002` (Ubuntu 22.04)

**Note**: Check Hostinger API documentation for current values.

### 3. Configure Cloudflare R2

1. Create R2 bucket in Cloudflare dashboard
2. Generate R2 API tokens (Access Key ID and Secret Access Key)
3. Note your Cloudflare Account ID

### 4. Prepare SSH Keys (Optional but Recommended)

1. Generate SSH key pair:
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/hostinger_key -C "hostinger-vpc"
   ```

2. Add public key to Hostinger (via API or control panel)
3. Note the SSH key ID for use in Terraform

## Initial Deployment

### Step 1: Configure Terraform Variables

```bash
cd infra/terraform/environments/hostinger
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your values:

```hcl
# Hostinger API Token
hostinger_api_token = "your-hostinger-api-token"

# Cluster Configuration
cluster_name = "neotool-hostinger"
node_count   = 3
k3s_version  = "v1.28.2+k3s1"

# Hostinger VPS Configuration
hostinger_vps_plan       = "hostingercom-vps-kvm2-usd-1m"
hostinger_data_center_id = 13  # Adjust for your region
hostinger_template_id    = 1002  # Ubuntu 22.04

# Optional: SSH Keys
hostinger_ssh_key_ids = ["your-ssh-key-id"]

# Cloudflare R2
cloudflare_account_id        = "your-cloudflare-account-id"
cloudflare_r2_bucket        = "neotool-prod"
cloudflare_r2_access_key_id = "your-r2-access-key-id"
cloudflare_r2_secret_access_key = "your-r2-secret-access-key"
```

### Step 2: Initialize Terraform

```bash
cd infra/terraform/environments/hostinger
terraform init
```

This will:
- Download required providers (Hostinger, Kubernetes, Random)
- Initialize Terraform modules

**Expected Output**:
```
Initializing provider plugins...
- Finding hostinger/hostinger versions matching "~> 0.1"...
- Finding hashicorp/kubernetes versions matching "~> 2.0"...
- Installing hostinger/hostinger v0.1.3...
- Installing hashicorp/kubernetes v2.x.x...
Terraform has been successfully initialized!
```

### Step 3: Validate Configuration

```bash
terraform validate
```

This checks your Terraform configuration for syntax errors.

### Step 4: Plan Deployment

```bash
terraform plan -out=tfplan
```

Review the plan carefully. It should show:
- VPS instances to be created
- K3S installation resources
- Storage class creation
- Kubernetes resources

**Expected Resources**:
- `hostinger_vps.k3s_nodes` (3 instances)
- `hostinger_vps_post_install_script` (K3S installation scripts)
- `random_password` (K3S token, VPS passwords)
- `kubernetes_storage_class` (Cloudflare R2)
- `kubernetes_secret` (R2 credentials)

### Step 5: Apply Deployment

```bash
terraform apply tfplan
```

Or interactively:
```bash
terraform apply
```

**This will**:
1. Create VPS instances on Hostinger
2. Install K3S on each node
3. Configure cluster (server + agents)
4. Set up Cloudflare R2 storage
5. Retrieve and configure kubeconfig

**Expected Duration**: 10-15 minutes

**Important**: Terraform will prompt for confirmation. Type `yes` to proceed.

### Step 6: Verify Deployment

After Terraform completes, verify the cluster:

```bash
# Set kubeconfig
export KUBECONFIG=infra/terraform/environments/hostinger/kubeconfig

# Check nodes
kubectl get nodes

# Expected output:
# NAME                    STATUS   ROLES                  AGE   VERSION
# neotool-hostinger-...   Ready    control-plane,master   5m    v1.28.2+k3s1
# neotool-hostinger-...   Ready    <none>                 4m    v1.28.2+k3s1
# neotool-hostinger-...   Ready    <none>                 4m    v1.28.2+k3s1

# Check system pods
kubectl get pods -n kube-system

# Check storage classes
kubectl get storageclass
# Should show: cloudflare-r2
```

## Deploying Kubernetes Resources

### Step 1: Deploy Base Infrastructure

```bash
cd infra/kubernetes
export KUBECONFIG=../terraform/environments/hostinger/kubeconfig

# Deploy using kustomize (if hostinger overlay exists)
kubectl apply -k overlays/prod

# Or deploy base resources
kubectl apply -k base
```

### Step 2: Verify Kubernetes Resources

```bash
# Check all namespaces
kubectl get namespaces

# Check deployments
kubectl get deployments -A

# Check pods
kubectl get pods -A

# Check persistent volume claims
kubectl get pvc -A
```

### Step 3: Wait for Resources to be Ready

```bash
# Wait for all deployments
kubectl wait --for=condition=available --timeout=300s deployment --all -A

# Check pod status
kubectl get pods -A | grep -v Running
```

## Post-Deployment Verification

### Cluster Health Checks

```bash
# Node status
kubectl get nodes -o wide

# System component health
kubectl get componentstatuses

# Cluster info
kubectl cluster-info
```

### Service Health Checks

```bash
# Check all services
kubectl get services -A

# Check service endpoints
kubectl get endpoints -A

# Test service connectivity
kubectl exec -n neotool-app deployment/app -- curl http://pgbouncer.neotool-data.svc.cluster.local:6432
```

### Storage Verification

```bash
# Check storage classes
kubectl get storageclass

# Check PVCs
kubectl get pvc -A

# Verify R2 secret exists
kubectl get secret cloudflare-r2-credentials -n kube-system
```

### Vault Verification

```bash
# Check Vault pod
kubectl get pods -n neotool-security -l app=vault

# Verify JWT keys exist
kubectl exec -n neotool-security deployment/vault -- vault kv get secret/jwt/keys/default
```

## Troubleshooting

### VPS Creation Fails

**Symptoms**: Terraform fails during VPS creation

**Diagnosis**:
```bash
# Check Terraform logs
terraform apply -auto-approve 2>&1 | tee terraform.log

# Common issues:
# - Invalid API token
# - Invalid plan ID
# - Invalid data center ID
# - Quota exceeded
```

**Solutions**:
1. Verify API token is correct
2. Check Hostinger account quota
3. Verify plan ID matches available plans
4. Check data center ID is valid for your region

### K3S Installation Fails

**Symptoms**: VPS created but K3S not installed

**Diagnosis**:
```bash
# SSH into the VPS
ssh root@<vps-ip>

# Check K3S service status
systemctl status k3s
systemctl status k3s-agent

# Check logs
journalctl -u k3s -f
journalctl -u k3s-agent -f

# Check installation script
cat /tmp/k3s-install.sh
```

**Solutions**:
1. Check network connectivity between nodes
2. Verify firewall rules allow K3S ports (6443, 10250, 8472)
3. Check system resources (CPU, memory, disk)
4. Review post-install script logs

### Cluster Nodes Not Joining

**Symptoms**: Server node ready but agents not joining

**Diagnosis**:
```bash
# Check server node
kubectl get nodes

# Check agent logs (SSH into agent node)
ssh root@<agent-ip>
journalctl -u k3s-agent -f

# Verify token
sudo cat /var/lib/rancher/k3s/server/node-token
```

**Solutions**:
1. Verify K3S token is correct
2. Check network connectivity (server IP reachable from agents)
3. Verify firewall allows port 6443 from agents to server
4. Check server node has enough resources

### Kubeconfig Issues

**Symptoms**: Cannot connect to cluster

**Diagnosis**:
```bash
# Check kubeconfig file
cat infra/terraform/environments/hostinger/kubeconfig

# Verify server IP is correct
kubectl cluster-info

# Test connectivity
curl -k https://<server-ip>:6443
```

**Solutions**:
1. Verify kubeconfig server IP matches VPS IP
2. Check firewall allows port 6443 from your location
3. Verify kubeconfig permissions (should be 600)
4. Re-run kubeconfig retrieval:
   ```bash
   terraform apply -target=module.k3s.null_resource.k3s_kubeconfig_hostinger
   ```

### Storage Class Not Created

**Symptoms**: PVCs pending, storage class missing

**Diagnosis**:
```bash
# Check storage class
kubectl get storageclass

# Check storage module resources
terraform state list | grep storage

# Check Kubernetes provider connection
kubectl get nodes
```

**Solutions**:
1. Verify Kubernetes provider is configured correctly
2. Check Cloudflare R2 credentials are valid
3. Re-apply storage module:
   ```bash
   terraform apply -target=module.storage
   ```

### Pods Not Starting

**Symptoms**: Pods in Pending or CrashLoopBackOff state

**Diagnosis**:
```bash
# Check pod events
kubectl describe pod <pod-name> -n <namespace>

# Check pod logs
kubectl logs <pod-name> -n <namespace>

# Check node resources
kubectl describe node
```

**Common Causes**:
- Insufficient resources
- Image pull errors
- Storage issues
- Network policies

## Maintenance Operations

### Updating K3S Version

1. Update `k3s_version` in `terraform.tfvars`
2. Plan and apply:
   ```bash
   terraform plan
   terraform apply
   ```

**Note**: K3S upgrade requires manual intervention on nodes. Consider using K3S upgrade script.

### Adding Nodes

1. Update `node_count` in `terraform.tfvars`
2. Apply:
   ```bash
   terraform apply
   ```

### Removing Nodes

1. Update `node_count` in `terraform.tfvars`
2. Apply:
   ```bash
   terraform apply
   ```

**Warning**: Ensure you're not removing the server node or leaving insufficient nodes for HA.

### Updating VPS Plans

1. Update `hostinger_vps_plan` in `terraform.tfvars`
2. **Note**: This will recreate VPS instances. Plan accordingly.

### Backup and Recovery

#### Backup Terraform State

```bash
# Backup state file
cp terraform.tfstate terraform.tfstate.backup.$(date +%Y%m%d)

# Or use remote state backend
```

#### Backup K3S Cluster

```bash
# Backup etcd (if using HA)
kubectl exec -n kube-system etcd-<pod> -- etcdctl snapshot save /backup/snapshot.db

# Backup persistent volumes
# Use your backup solution for Cloudflare R2
```

## Rollback Procedures

### Rollback Terraform Changes

```bash
# View state history
terraform state list

# Rollback to previous state
terraform state pull > current.tfstate
# Restore from backup if needed
```

### Rollback Kubernetes Resources

```bash
# Rollback deployment
kubectl rollout undo deployment/<deployment-name> -n <namespace>

# Rollback to specific revision
kubectl rollout undo deployment/<deployment-name> -n <namespace> --to-revision=<revision>
```

## Destroying Infrastructure

### Destroy Everything

**Warning**: This will delete all VPS instances and data!

```bash
cd infra/terraform/environments/hostinger
terraform destroy
```

### Destroy Specific Resources

```bash
# Destroy only storage
terraform destroy -target=module.storage

# Destroy only K3S cluster (keeps VPS)
terraform destroy -target=module.k3s
```

## Monitoring and Alerts

### Cluster Monitoring

```bash
# Node metrics
kubectl top nodes

# Pod metrics
kubectl top pods -A

# Resource usage
kubectl describe node
```

### Set Up Monitoring

1. Deploy Prometheus and Grafana:
   ```bash
   kubectl apply -k infra/kubernetes/base/observability
   ```

2. Access Grafana:
   ```bash
   kubectl port-forward -n neotool-observability svc/grafana 3000:80
   ```

## Security Considerations

### Network Security

- All VPS instances should be in private VPC
- Firewall rules restrict access to K3S ports
- Use SSH keys instead of passwords
- Implement network policies in Kubernetes

### Access Control

- Use RBAC for Kubernetes access
- Rotate API tokens regularly
- Use least-privilege principle
- Audit access logs

### Secrets Management

- Store sensitive data in Vault
- Use Kubernetes secrets for non-sensitive config
- Rotate credentials regularly
- Encrypt data at rest

## Cost Optimization

### Resource Sizing

- Right-size VPS instances based on workload
- Use appropriate storage classes
- Monitor resource usage

### Scaling

- Implement HPA for automatic pod scaling
- Consider node autoscaling (if supported)
- Use Cloudflare R2 for cost-effective storage

## Next Steps

After successful deployment:

1. **Configure DNS**: Set up ingress and DNS records
2. **Set up CI/CD**: Automate application deployments
3. **Implement Monitoring**: Set up alerts and dashboards
4. **Backup Strategy**: Configure automated backups
5. **Security Hardening**: Implement network policies and RBAC
6. **Documentation**: Document environment-specific procedures

## References

- [Hostinger Terraform Provider](https://www.hostinger.com/support/11080294-getting-started-with-the-hostinger-terraform-provider/)
- [K3S Documentation](https://docs.k3s.io/)
- [Terraform Documentation](https://www.terraform.io/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs/)

## Support

For issues:
1. Check troubleshooting section above
2. Review Terraform and Kubernetes logs
3. Consult Hostinger support for VPS issues
4. Check K3S GitHub issues for cluster problems

