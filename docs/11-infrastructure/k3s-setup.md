---
title: K3S Setup Guide
type: infrastructure
category: deployment
status: current
version: 1.0.0
tags: [k3s, kubernetes, infrastructure, deployment, terraform]
ai_optimized: true
search_keywords: [k3s, kubernetes, k8s, lightweight, cluster, setup, installation]
related:
  - 11-infrastructure/terraform-guide.md
  - 11-infrastructure/deployment-guide.md
  - 02-architecture/infrastructure-architecture.md
last_updated: 2026-01-02
---

# K3S Setup Guide

> **Purpose**: Guide for installing and configuring K3S (lightweight Kubernetes) for Neotool infrastructure.

## What is K3S?

K3S is a lightweight Kubernetes distribution designed for:
- Edge computing
- IoT devices
- Development environments
- Resource-constrained environments

## Installation Methods

### Using Terraform (Recommended)

The Terraform K3S module handles installation automatically:

```bash
cd infra/terraform/environments/local
terraform init
terraform apply
```

### Manual Installation

```bash
# Install K3S
curl -sfL https://get.k3s.io | sh -

# Check status
sudo systemctl status k3s

# Get kubeconfig
sudo cat /etc/rancher/k3s/k3s.yaml
```

## Local Development Setup

### Single-Node Cluster

For local development, a single-node cluster is sufficient:

```bash
# Initialize local cluster
./infra/terraform/scripts/init-k3s.sh local
```

### Verify Installation

```bash
# Set kubeconfig
export KUBECONFIG=~/.kube/config

# Check nodes
kubectl get nodes

# Check system pods
kubectl get pods -n kube-system
```

## Production VPC Setup

### Prerequisites

- Cloud provider account (AWS/GCP/Azure)
- Terraform >= 1.0
- Network access to VPC

### Configuration

1. Configure production environment:
   ```bash
   cd infra/terraform/environments/prod
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars
   ```

2. Initialize infrastructure:
   ```bash
   ./../../scripts/init-k3s.sh prod
   ```

### Node Specifications

- **CPU**: 8 cores per node
- **Memory**: 32GB RAM per node
- **Storage**: Cloudflare R2
- **Network**: Private subnets with NAT

## K3S Components

### Included Components

- **Traefik**: Ingress controller (can be disabled)
- **Local-path**: Storage provisioner
- **CoreDNS**: DNS service
- **Metrics Server**: Resource metrics

### Disabled Components

For Neotool, we disable Traefik (use custom ingress if needed):

```hcl
server_flags = ["--disable=traefik"]
```

## Storage Configuration

### Local-Path Storage

K3S includes a local-path provisioner by default:

```yaml
storageClassName: local-path
```

### Cloud Storage

For production, use Cloudflare R2:

```yaml
storageClassName: cloudflare-r2
```

## Networking

### Service Network

- Default: `10.43.0.0/16`
- Configurable via `--service-cidr`

### Pod Network

- Default: `10.42.0.0/16`
- Uses Flannel CNI by default

## Accessing the Cluster

### Kubeconfig

After installation, kubeconfig is saved to:
- Local: `~/.kube/config`
- Production: As specified in Terraform outputs

### Using kubectl

```bash
# Set kubeconfig
export KUBECONFIG=~/.kube/config

# Or use --kubeconfig flag
kubectl --kubeconfig=/path/to/kubeconfig get nodes
```

## Maintenance

### Upgrading K3S

```bash
# Stop K3S
sudo systemctl stop k3s

# Install new version
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=v1.28.0 sh -

# Start K3S
sudo systemctl start k3S
```

### Uninstalling K3S

```bash
# Uninstall script
/usr/local/bin/k3s-uninstall.sh
```

Or use Terraform:

```bash
./infra/terraform/scripts/destroy-k3s.sh local
```

## Troubleshooting

### K3S Not Starting

```bash
# Check logs
sudo journalctl -u k3s -f

# Check system resources
free -h
df -h
```

### Pods Not Scheduling

```bash
# Check node resources
kubectl describe node

# Check system pods
kubectl get pods -n kube-system
```

### Network Issues

```bash
# Check Flannel pods
kubectl get pods -n kube-flannel

# Check network policies
kubectl get networkpolicies -A
```

## Best Practices

### Resource Management

- Set appropriate resource limits
- Monitor node resource usage
- Use HPA for automatic scaling

### Security

- Use RBAC for access control
- Implement network policies
- Regular security updates

### Backup

- Backup etcd data (for HA clusters)
- Backup persistent volumes
- Document recovery procedures

## References

- [K3S Documentation](https://docs.k3s.io/)
- [K3S GitHub](https://github.com/k3s-io/k3s)
- [K3S Configuration Options](https://docs.k3s.io/reference/server-config)

