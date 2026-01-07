---
title: Hostinger VPC Setup Guide
type: infrastructure
category: deployment
status: current
version: 1.0.0
tags: [hostinger, vpc, terraform, setup, configuration]
ai_optimized: true
search_keywords: [hostinger, vpc, terraform, setup, configuration, api, vps]
related:
  - 11-infrastructure/hostinger-vpc-runbook.md
  - 11-infrastructure/terraform-guide.md
  - 11-infrastructure/k3s-setup.md
last_updated: 2026-01-02
---

# Hostinger VPC Setup Guide

> **Purpose**: Comprehensive guide for setting up and configuring Hostinger VPC infrastructure for Neotool deployment.

## Overview

This guide covers the complete setup process for deploying Neotool on Hostinger VPC using:
- Hostinger Terraform Provider (official)
- K3S Kubernetes cluster
- Cloudflare R2 storage
- Infrastructure as Code with Terraform

## Architecture

### Infrastructure Components

```
Hostinger VPC
├── VPS Instance 1 (K3S Server)
│   ├── K3S Control Plane
│   └── K3S API Server
├── VPS Instance 2 (K3S Agent)
│   └── K3S Worker Node
├── VPS Instance 3 (K3S Agent)
│   └── K3S Worker Node
└── Storage
    └── Cloudflare R2 (S3-compatible)
```

### Network Architecture

- **VPC**: Private network for all VPS instances
- **Internal Communication**: Nodes communicate via private IPs
- **External Access**: Through load balancer or ingress controller
- **Storage**: Cloudflare R2 via S3-compatible API

## Prerequisites

### Account Setup

1. **Hostinger Account**
   - Active Hostinger account
   - API access enabled
   - Sufficient quota for VPS instances

2. **Cloudflare Account**
   - Active Cloudflare account
   - R2 bucket created
   - API tokens generated

3. **Local Environment**
   - Terraform >= 1.0 installed
   - kubectl >= 1.28 installed
   - SSH client installed
   - Git for cloning repository

## Hostinger Account Configuration

### Step 1: Get API Token

1. Log in to [Hostinger](https://www.hostinger.com)
2. Navigate to **Account Settings** → **API**
3. Click **Generate New Token**
4. Copy and securely store the token

**Security Note**: Treat API tokens as passwords. Never commit them to version control.

### Step 2: Find Resource IDs

#### VPS Plan IDs

VPS plan IDs follow the pattern: `hostingercom-vps-kvm2-{currency}-{billing}`

Examples:
- `hostingercom-vps-kvm2-usd-1m` - US Dollar, monthly billing
- `hostingercom-vps-kvm2-eur-1m` - Euro, monthly billing

**To find available plans**:
1. Check Hostinger API documentation
2. Use Hostinger API to list plans
3. Check Hostinger control panel

#### Data Center IDs

Data center IDs are numeric identifiers for geographic locations.

Common values:
- `13` - United States
- `14` - United Kingdom
- `15` - Netherlands
- `16` - Singapore

**To find data center IDs**:
1. Check Hostinger API documentation
2. Use Hostinger API: `GET /vps/data-centers`
3. Contact Hostinger support

#### Template IDs

Template IDs identify OS images available for VPS instances.

Common templates:
- `1002` - Ubuntu 22.04 LTS
- `1001` - Ubuntu 20.04 LTS
- `1003` - Debian 11

**To find template IDs**:
1. Check Hostinger API documentation
2. Use Hostinger API: `GET /vps/templates`
3. Check Hostinger control panel when creating VPS

### Step 3: Configure SSH Keys (Recommended)

#### Generate SSH Key Pair

```bash
ssh-keygen -t ed25519 -f ~/.ssh/hostinger_key -C "hostinger-vpc-deployment"
```

#### Add SSH Key to Hostinger

**Option 1: Via API**

```bash
# Get your public key
PUBLIC_KEY=$(cat ~/.ssh/hostinger_key.pub)

# Add via Hostinger API (check API docs for exact endpoint)
curl -X POST https://api.hostinger.com/v1/vps/ssh-keys \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -d "{\"name\": \"hostinger-vpc\", \"public_key\": \"$PUBLIC_KEY\"}"
```

**Option 2: Via Control Panel**

1. Log in to Hostinger
2. Navigate to **VPS** → **SSH Keys**
3. Click **Add SSH Key**
4. Paste your public key
5. Note the SSH key ID

## Cloudflare R2 Configuration

### Step 1: Create R2 Bucket

1. Log in to [Cloudflare Dashboard](https://dash.cloudflare.com)
2. Navigate to **R2** → **Create Bucket**
3. Name: `neotool-prod` (or your preferred name)
4. Location: Choose closest region
5. Click **Create Bucket**

### Step 2: Generate R2 API Tokens

1. Navigate to **R2** → **Manage R2 API Tokens**
2. Click **Create API Token**
3. Permissions: **Object Read & Write**
4. Copy:
   - **Access Key ID**
   - **Secret Access Key**

**Security Note**: Store these securely. They provide full access to your R2 bucket.

### Step 3: Get Cloudflare Account ID

1. In Cloudflare Dashboard, select your account
2. Account ID is shown in the right sidebar
3. Copy the Account ID

## Terraform Configuration

### Directory Structure

```
infra/terraform/
├── modules/
│   ├── k3s/
│   │   ├── main.tf
│   │   ├── hostinger-instances.tf  # Hostinger-specific
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── storage/
├── environments/
│   └── hostinger/
│       ├── main.tf
│       ├── terraform.tfvars
│       └── terraform.tfvars.example
└── scripts/
    └── init-k3s-hostinger.sh
```

### Environment Configuration

Create `infra/terraform/environments/hostinger/main.tf`:

```hcl
terraform {
  required_version = ">= 1.0"
  required_providers {
    hostinger = {
      source  = "hostinger/hostinger"
      version = "~> 0.1"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "hostinger" {
  api_token = var.hostinger_api_token
}

# ... rest of configuration
```

### Variables Configuration

Create `infra/terraform/environments/hostinger/terraform.tfvars`:

```hcl
# Hostinger Configuration
hostinger_api_token       = "your-api-token-here"
hostinger_vps_plan       = "hostingercom-vps-kvm2-usd-1m"
hostinger_data_center_id = 13
hostinger_template_id    = 1002
hostinger_ssh_key_ids    = ["your-ssh-key-id"]

# Cluster Configuration
cluster_name = "neotool-hostinger"
node_count   = 3
k3s_version  = "v1.28.2+k3s1"

# Cloudflare R2
cloudflare_account_id        = "your-account-id"
cloudflare_r2_bucket        = "neotool-prod"
cloudflare_r2_access_key_id = "your-access-key-id"
cloudflare_r2_secret_access_key = "your-secret-access-key"
```

## K3S Module Configuration

### Hostinger-Specific Variables

The K3S module supports Hostinger through these variables:

```hcl
variable "hostinger_vps_plan" {
  description = "Hostinger VPS plan ID"
  type        = string
}

variable "hostinger_data_center_id" {
  description = "Hostinger data center ID"
  type        = number
}

variable "hostinger_template_id" {
  description = "Hostinger template ID (OS image)"
  type        = number
}

variable "hostinger_ssh_key_ids" {
  description = "List of Hostinger SSH key IDs"
  type        = list(string)
  default     = []
}
```

### K3S Installation Process

1. **VPS Provisioning**: Hostinger provider creates VPS instances
2. **Post-Install Scripts**: K3S installation scripts are uploaded
3. **Server Installation**: First node installs K3S server
4. **Agent Installation**: Remaining nodes join as agents
5. **Kubeconfig Retrieval**: Terraform downloads and configures kubeconfig

## Storage Configuration

### Cloudflare R2 Storage Class

The storage module creates:
- Kubernetes Secret with R2 credentials
- ConfigMap with R2 configuration
- StorageClass for R2-backed volumes

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: cloudflare-r2
provisioner: s3.csi.aws.com
parameters:
  bucket: neotool-prod
  endpoint: https://<account-id>.r2.cloudflarestorage.com
  region: auto
```

## Network Configuration

### VPC Networking

Hostinger VPC provides:
- Private network for VPS instances
- Internal communication between nodes
- Firewall rules for security

### Required Ports

Ensure these ports are open in Hostinger firewall:

- **6443**: K3S API server
- **10250**: Kubelet API
- **8472**: Flannel VXLAN (UDP)
- **22**: SSH (for management)

### Network Policies

Implement Kubernetes Network Policies for additional security:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
```

## Security Configuration

### SSH Key Management

**Best Practices**:
- Use SSH keys instead of passwords
- Rotate keys regularly
- Use different keys for different environments
- Store private keys securely

### API Token Security

**Best Practices**:
- Store tokens in environment variables or secret management
- Rotate tokens regularly
- Use least-privilege tokens
- Never commit tokens to version control

### Kubernetes Security

**Best Practices**:
- Enable RBAC
- Use network policies
- Regularly update K3S
- Monitor cluster access
- Encrypt secrets at rest

## Monitoring Setup

### Cluster Monitoring

Deploy monitoring stack:

```bash
kubectl apply -k infra/kubernetes/base/observability
```

Components:
- **Prometheus**: Metrics collection
- **Grafana**: Visualization
- **Loki**: Log aggregation
- **Promtail**: Log collection

### Access Monitoring

```bash
# Port forward to Grafana
kubectl port-forward -n neotool-observability svc/grafana 3000:80

# Access at http://localhost:3000
# Default credentials: admin/admin (change on first login)
```

## Backup Configuration

### Terraform State Backup

Configure remote state backend:

```hcl
terraform {
  backend "s3" {
    bucket = "neotool-terraform-state"
    key    = "hostinger/terraform.tfstate"
    region = "us-east-1"
  }
}
```

### Cluster Backup

**ETCD Backup** (for HA clusters):
```bash
kubectl exec -n kube-system etcd-<pod> -- etcdctl snapshot save /backup/snapshot.db
```

**Persistent Volume Backup**:
- Use Cloudflare R2 backup solution
- Implement automated backup jobs
- Test restore procedures

## Troubleshooting Setup Issues

### Common Issues

#### Invalid API Token

**Symptom**: `Error: authentication failed`

**Solution**:
1. Verify token is correct
2. Check token hasn't expired
3. Regenerate token if needed

#### Invalid Plan ID

**Symptom**: `Error: invalid VPS plan`

**Solution**:
1. Verify plan ID format
2. Check plan is available in your region
3. Use Hostinger API to list available plans

#### Template Not Found

**Symptom**: `Error: template not found`

**Solution**:
1. Verify template ID
2. Check template is available for your data center
3. Use Hostinger API to list templates

#### Quota Exceeded

**Symptom**: `Error: quota exceeded`

**Solution**:
1. Check Hostinger account quota
2. Delete unused VPS instances
3. Upgrade Hostinger plan if needed

## Cost Estimation

### VPS Costs

Example costs (varies by region and plan):
- **3x VPS instances**: ~$30-60/month
- **Storage**: Included in VPS
- **Bandwidth**: Included in VPS plan

### Cloudflare R2 Costs

- **Storage**: $0.015/GB/month
- **Class A Operations**: $4.50/million
- **Class B Operations**: $0.36/million

### Total Estimated Cost

For a 3-node cluster:
- **VPS**: $30-60/month
- **R2 Storage**: $5-20/month (depending on usage)
- **Total**: ~$35-80/month

## Next Steps

After completing setup:

1. **Deploy Applications**: Follow [Hostinger VPC Runbook](./hostinger-vpc-runbook.md)
2. **Configure Monitoring**: Set up alerts and dashboards
3. **Implement CI/CD**: Automate deployments
4. **Security Hardening**: Apply network policies and RBAC
5. **Documentation**: Document environment-specific procedures

## References

- [Hostinger Terraform Provider Documentation](https://www.hostinger.com/support/11080294-getting-started-with-the-hostinger-terraform-provider/)
- [Hostinger API Documentation](https://developers.hostinger.com/)
- [K3S Documentation](https://docs.k3s.io/)
- [Terraform Documentation](https://www.terraform.io/docs)
- [Cloudflare R2 Documentation](https://developers.cloudflare.com/r2/)

## Support Resources

- **Hostinger Support**: [support.hostinger.com](https://www.hostinger.com/contact)
- **K3S Issues**: [GitHub Issues](https://github.com/k3s-io/k3s/issues)
- **Terraform Community**: [Terraform Forums](https://discuss.hashicorp.com/c/terraform-core)

