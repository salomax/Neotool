# Production Deployment - Quick Reference

## 🚀 Foundation Setup (Do This First)

### 1. Set your kubeconfig

```bash
export KUBECONFIG=~/.kube/config-hostinger
```

Add this to your `~/.bashrc` or `~/.zshrc` to make it permanent:
```bash
echo 'export KUBECONFIG=~/.kube/config-hostinger' >> ~/.zshrc
source ~/.zshrc
```

### 2. Run the foundation setup script

```bash
cd infra/kubernetes/scripts
./setup-production-foundation.sh
```

**What it does:**
- Creates `production` namespace
- Verifies local-path storage
- Verifies Traefik ingress
- Installs Cert-Manager for TLS
- Installs Linkerd service mesh

**Expected time:** 5 minutes

### 3. Enable Linkerd on production namespace

```bash
kubectl annotate namespace production linkerd.io/inject=enabled
```

This makes all pods automatically get Linkerd sidecar proxies for service mesh features.

### 4. Verify everything is working

```bash
# Check namespace
kubectl get namespace production

# Check cert-manager (should see 3 pods running)
kubectl get pods -n cert-manager

# Check Linkerd (should see multiple pods running)
kubectl get pods -n linkerd

# Check Traefik
kubectl get pods -n kube-system | grep traefik

# Run Linkerd check
linkerd check
```

## 📋 What's Next?

After foundation setup is complete:

1. **Configure TLS Issuer** (Choose one):
   - Production: Edit `tls/letsencrypt-issuer.yaml` with your email, then apply
   - Testing: Use `tls/letsencrypt-staging-issuer.yaml`
   - No domain: Use `tls/selfsigned-issuer.yaml`

2. **Deploy Services** (In order):
   - PostgreSQL first
   - Then Kotlin backend services
   - Finally web-facing services (NextJS, Apollo Router, API Gateway)

3. **Configure Ingress**:
   - Set up DNS records pointing to your VPS IP
   - Apply ingress manifests for external services

## 📚 Full Guides

- **Detailed deployment**: [../PRODUCTION-DEPLOYMENT.md](../PRODUCTION-DEPLOYMENT.md)
- **K3S setup**: [../../terraform/hostinger/README.md](../../terraform/hostinger/README.md)

## 🆘 Troubleshooting

### Can't connect to cluster

```bash
# Check if kubeconfig exists
ls -la ~/.kube/config-hostinger

# Test connection
kubectl cluster-info

# Check nodes
kubectl get nodes
```

### Linkerd CLI not found

```bash
# Install Linkerd CLI
curl -sfL https://run.linkerd.io/install | sh

# Add to PATH
export PATH=$PATH:$HOME/.linkerd2/bin

# Make permanent
echo 'export PATH=$PATH:$HOME/.linkerd2/bin' >> ~/.zshrc
```

### Cert-Manager not working

```bash
# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager

# Check webhook
kubectl logs -n cert-manager -l app=webhook
```

## ✅ Success Checklist

Before deploying services, verify:
- [ ] `kubectl get nodes` shows Ready
- [ ] `production` namespace exists
- [ ] Cert-manager pods are running
- [ ] Linkerd pods are running
- [ ] Traefik pods are running
- [ ] `linkerd check` passes all checks
- [ ] Production namespace is annotated for Linkerd injection

## 🎯 Your VPS Specs

- **CPU**: 8 cores
- **RAM**: 32 GB
- **Available for workloads**: ~30 GB (after system/k3s overhead)

You have plenty of resources for:
- Multiple replicas of services
- Service mesh overhead
- Observability stack
- Future growth
