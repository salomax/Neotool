# Kubernetes Cluster Configurations

This directory contains cluster-type-specific configurations and setup scripts.

## Directory Structure

```
clusters/
├── k3s/           # Lightweight Kubernetes (VPS, edge, IoT)
├── eks/           # AWS Elastic Kubernetes Service
├── gke/           # Google Kubernetes Engine
├── aks/           # Azure Kubernetes Service
└── on-prem/       # Full Kubernetes on bare metal/VMs
```

## When to Use Each

### K3S (`k3s/`)
**Use when:**
- Running on VPS (Hostinger, DigitalOcean, Linode)
- Edge computing or IoT devices
- Resource-constrained environments
- Single-server or small clusters

**Features:**
- Lightweight (< 100MB binary)
- Built-in Traefik ingress
- Built-in local-path storage
- SQLite or etcd backend
- Easy upgrades

**Current Setup:**
- Hostinger VPS (8 CPU, 32GB RAM)
- Production environment

### EKS (`eks/`)
**Use when:**
- Running on AWS
- Need tight AWS integration (IAM, EBS, ALB, etc.)
- Enterprise requirements
- Multi-region deployments

**Features:**
- Managed control plane
- Auto-scaling with CA
- IAM for Service Accounts
- Native AWS integrations

### GKE (`gke/`)
**Use when:**
- Running on Google Cloud
- Need GCP integrations
- Want the most "vanilla" Kubernetes experience

**Features:**
- Managed control plane
- Autopilot mode available
- Best-in-class networking
- Integrated logging/monitoring

### AKS (`aks/`)
**Use when:**
- Running on Azure
- Need Azure integrations
- Enterprise Windows workloads

**Features:**
- Managed control plane
- Azure AD integration
- Windows node pools
- Azure CNI networking

### On-Premises (`on-prem/`)
**Use when:**
- Self-hosted infrastructure
- Data sovereignty requirements
- No cloud vendor lock-in
- Full control needed

**Features:**
- Full Kubernetes (via kubeadm, RKE, etc.)
- Your own hardware/VMs
- MetalLB for LoadBalancer
- Nginx Ingress Controller

## Common Foundation Components

All clusters get:
- ✅ Cert-Manager (TLS certificates)
- ✅ Service Mesh (Linkerd recommended)
- ✅ Ingress Controller (varies by cluster)
- ✅ Storage Class (varies by cluster)

## Cluster-Specific Differences

| Component | K3S | EKS | GKE | AKS | On-Prem |
|-----------|-----|-----|-----|-----|---------|
| Ingress | Traefik | ALB Controller | GCE Ingress | App Gateway | Nginx |
| Storage | local-path | EBS CSI | GCE PD | Azure Disk | Local/NFS/Ceph |
| LoadBalancer | ServiceLB | AWS LB | GCP LB | Azure LB | MetalLB |
| DNS | CoreDNS | CoreDNS | kube-dns | CoreDNS | CoreDNS |

## Quick Start

### For K3S (Current Setup)

**Step 1: Foundation Setup (Prerequisites)**
```bash
cd clusters/k3s/foundation
./setup.sh production
```

This installs:
- Cert-Manager (for TLS certificates)
- Linkerd (service mesh)

**Step 2: Bootstrap Flux GitOps**
```bash
cd ../../flux
./bootstrap.sh  # For production branch
# or
./bootstrap-dev.sh  # For development branch
```

After Flux is bootstrapped, all deployments are managed via GitOps automatically.

### For EKS

```bash
# First, update kubeconfig
aws eks update-kubeconfig --name your-cluster --region us-east-1

cd clusters/eks/foundation
./setup.sh production
```

### For On-Premises

```bash
cd clusters/on-prem/foundation
./setup.sh production
```

## Migration Between Clusters

To migrate from one cluster type to another:

1. **Export your application configs** (environment-agnostic)
2. **Run new cluster foundation setup**
3. **Adjust cluster-specific configs** (storage classes, ingress annotations)
4. **Deploy applications** to new cluster
5. **Migrate data** (databases, volumes)
6. **Update DNS** to point to new cluster

## Adding a New Cluster Type

1. Create directory: `clusters/new-cluster-type/`
2. Add `foundation/setup.sh` script
3. Document cluster-specific features in `README.md`
4. Test with a sample deployment
5. Update this README

## Best Practices

1. **Keep applications cluster-agnostic** - store in `flux/apps/` (GitOps)
2. **Use Flux GitOps** for all deployments (not Kustomize overlays)
3. **Document cluster-specific quirks** in cluster READMEs
4. **Version your cluster configs** alongside application code
5. **Test foundation scripts** on clean clusters

## Need Help?

- K3S: [../PRODUCTION-DEPLOYMENT.md](../PRODUCTION-DEPLOYMENT.md)
- General: [../README.md](../README.md)
