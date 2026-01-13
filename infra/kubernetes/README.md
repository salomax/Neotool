# Neotool Kubernetes Infrastructure

Multi-cluster Kubernetes configuration supporting K3S, EKS, GKE, AKS, and on-premises deployments.

## 📁 Directory Structure

```
infra/kubernetes/
├── clusters/              # Cluster-type-specific configurations
│   ├── k3s/              # K3S (VPS, edge) - CURRENT
│   ├── eks/              # AWS EKS
│   ├── gke/              # Google GKE
│   ├── aks/              # Azure AKS
│   └── on-prem/          # Full K8s on bare metal
│
├── environments/          # Environment-specific configurations
│   ├── production/       # Production environment
│   ├── staging/          # Staging environment
│   └── development/      # Development environment
│
├── base/                 # Cluster-agnostic application manifests
│   ├── databases/        # PostgreSQL, etc.
│   ├── services/         # Backend services
│   ├── web/              # Frontend services
│   ├── storage/          # MinIO, etc.
│   └── observability/    # Prometheus, Grafana, Loki
│
└── scripts/              # Helper scripts
```

## 🚀 Quick Start (K3S Production)

### 1. Set kubeconfig

```bash
export KUBECONFIG=~/.kube/config-hostinger
```

### 2. Run K3S foundation setup

```bash
cd clusters/k3s/foundation
./setup.sh production
```

### 3. Enable service mesh

```bash
kubectl annotate namespace production linkerd.io/inject=enabled
```

### 4. Deploy applications

```bash
kubectl apply -k environments/production/
```

## 📚 Documentation

- **[Cluster Types](clusters/README.md)** - K3S vs EKS vs GKE vs On-Prem
- **[Production Deployment](PRODUCTION-DEPLOYMENT.md)** - Full production guide
- **[Quick Start](QUICKSTART.md)** - Get started in 5 minutes
- **[CI/CD Guide](environments/production/cicd/README.md)** - Automated deployments

## 🎯 Current Setup

**Cluster**: K3S on Hostinger VPS
**Resources**: 8 CPU, 32GB RAM
**Environment**: Production
**Service Mesh**: Linkerd
**Ingress**: Traefik
**TLS**: Cert-Manager

## 🔄 Switching Cluster Types

To migrate from K3S to another cluster type:

```bash
# Deploy to EKS
cd clusters/eks/foundation
./setup.sh production

# Deploy to GKE
cd clusters/gke/foundation
./setup.sh production

# Deploy to on-premises K8s
cd clusters/on-prem/foundation
./setup.sh production
```

Applications in `base/` and `environments/` remain cluster-agnostic!

## 📦 What Gets Deployed

### Foundation (All Clusters)
- Namespaces
- Cert-Manager (TLS)
- Linkerd (Service Mesh)
- Ingress Controller (cluster-specific)
- Storage Class (cluster-specific)

### Applications (Environment-Specific)
- PostgreSQL + PgBouncer
- Kotlin services
- Apollo Router (GraphQL)
- REST API Gateway
- Next.js web app
- MinIO (object storage)
- Prometheus + Grafana (monitoring)

## 🏗️ Architecture Principles

1. **Cluster-agnostic applications** - Can run on any cluster type
2. **Environment-specific configs** - Production, staging, development
3. **Infrastructure as Code** - Everything versioned in Git
4. **Declarative deployment** - Using Kustomize and kubectl
5. **Service mesh by default** - Linkerd for mTLS and observability

## 🛠️ Common Commands

```bash
# Check cluster connection
kubectl cluster-info

# List all pods
kubectl get pods -n production

# View logs
kubectl logs -f <pod-name> -n production

# Port forward
kubectl port-forward -n production svc/postgres 5432:5432

# Check resource usage
kubectl top nodes
kubectl top pods -n production

# View Linkerd dashboard
linkerd viz dashboard

# Apply changes
kubectl apply -k environments/production/
```

## 🆘 Troubleshooting

### Can't connect to cluster

```bash
# K3S
export KUBECONFIG=~/.kube/config-hostinger

# EKS
aws eks update-kubeconfig --name <cluster> --region <region>

# GKE
gcloud container clusters get-credentials <cluster> --region <region>
```

### Pod won't start

```bash
kubectl describe pod <pod-name> -n production
kubectl logs <pod-name> -n production
```

### Service not accessible

```bash
kubectl get svc -n production
kubectl get endpoints <service> -n production
```

## 📖 Learn More

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [K3S Documentation](https://docs.k3s.io/)
- [Linkerd Documentation](https://linkerd.io/docs/)
- [Cert-Manager Documentation](https://cert-manager.io/docs/)

## 🔐 Security

- All secrets stored in Kubernetes secrets
- Service mesh provides mTLS by default
- Network policies restrict pod-to-pod traffic
- RBAC for service accounts (see `environments/production/cicd/`)
- Regular security updates via renovate/dependabot

## 🎯 Roadmap

- [ ] Argo CD for GitOps
- [ ] Sealed Secrets for encrypted secrets in Git
- [ ] Policy enforcement with OPA/Kyverno
- [ ] Multi-cluster federation
- [ ] Disaster recovery automation
