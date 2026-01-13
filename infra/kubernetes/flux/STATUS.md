# Flux GitOps Setup Status

**Last Updated**: 2026-01-10
**Cluster**: K3S on Hostinger VPS (72.60.13.72)

## ✅ Completed Steps

### 1. Infrastructure
- K3S cluster deployed via Terraform
- Node: `neotool-hostinger-node-0` (8 CPU, 32GB RAM)
- Kubernetes version: v1.34.3+k3s1
- Linkerd service mesh installed
- Production namespace created

### 2. Local Environment
- Flux CLI installed: v2.7.5 (at `~/.local/bin/flux`)
- Kubeconfig configured: `~/.kube/config` (merged from `config-hostinger`)
- Current context: `default` (K3S cluster)
- Cluster connectivity: ✅ Working

### 3. Prerequisites Check
```
✔ Kubernetes 1.34.3+k3s1 >=1.32.0-0
✔ prerequisites checks passed
```

## 📋 Next Steps

### Step 1: Create GitHub Personal Access Token

Visit: https://github.com/settings/tokens

**Settings**:
- Note: `Flux GitOps - Neotool`
- Expiration: 90 days (recommended) or No expiration
- Scopes: ✅ `repo` (full control)

**Save the token** - you'll need it for bootstrap!

### Step 2: Bootstrap Flux

```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./bootstrap.sh
```

**Provide when prompted**:
- GitHub username: `salomax`
- Repository name: `Neotool`
- Personal Access Token: `ghp_xxx...` (from Step 1)

**What happens**:
- Flux installs to `flux-system` namespace
- Creates deploy key in GitHub
- Commits Flux manifests to your repo
- Sets up auto-sync (Git → Cluster)

### Step 3: Verify Flux Installation

```bash
flux check
flux get all
```

Expected output:
```
✔ all checks passed
```

### Step 4: Initialize Vault

```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./vault-init.sh
```

**IMPORTANT**: Saves credentials to `~/.neotool/vault-credentials.txt` - BACKUP THIS FILE!

### Step 5: Unseal Vault

```bash
./vault-unseal.sh
```

Uses the saved credentials to unseal Vault.

### Step 6: Configure Vault

```bash
./vault-configure.sh
```

Enables Kubernetes authentication and creates policies.

### Step 7: Store PostgreSQL Credentials

```bash
./vault-store-postgres.sh
```

Prompts for PostgreSQL credentials and stores them in Vault.

## 📊 Current Cluster State

```bash
# Nodes
kubectl get nodes
# NAME                       STATUS   ROLES                AGE   VERSION
# neotool-hostinger-node-0   Ready    control-plane,etcd   22h   v1.34.3+k3s1

# Namespaces
kubectl get namespaces
# cert-manager      Active   20h
# linkerd           Active   20h
# production        Active   20h
```

## 🔧 Useful Commands

### Flux
```bash
# Check Flux status
flux check

# Watch reconciliation
flux get kustomizations --watch

# View logs
flux logs --all-namespaces --follow

# Force sync
flux reconcile kustomization infrastructure --with-source
```

### Cluster
```bash
# List all resources in production
kubectl get all -n production

# Check Vault status
kubectl get pods -n production -l app.kubernetes.io/name=vault

# Check External Secrets Operator
flux get helmrelease -n flux-system
```

## 📝 Important Files

### Flux Configuration
- [bootstrap.sh](./bootstrap.sh) - Flux bootstrap script
- [QUICKSTART.md](./QUICKSTART.md) - Quick reference guide
- [GITHUB-TOKEN-SETUP.md](./GITHUB-TOKEN-SETUP.md) - Token creation guide

### Vault Scripts
- [vault-init.sh](./vault-init.sh) - Initialize Vault (one time)
- [vault-unseal.sh](./vault-unseal.sh) - Unseal Vault after restarts
- [vault-configure.sh](./vault-configure.sh) - Configure Kubernetes auth
- [vault-store-postgres.sh](./vault-store-postgres.sh) - Store DB credentials

### Flux Manifests
- `clusters/production/` - Cluster entry point
- `infrastructure/` - Vault, External Secrets Operator
- `apps/` - Your applications (to be added)

## 🎯 After Bootstrap: GitOps Workflow

Once Flux is bootstrapped, all changes go through Git:

```bash
# 1. Make changes
vim flux/apps/myapp.yaml

# 2. Commit
git add .
git commit -m "Deploy myapp"

# 3. Push
git push

# 4. Flux auto-deploys! ✨
flux get kustomizations --watch
```

## 🔐 Security Notes

- GitHub token: Only needed once for bootstrap
- After bootstrap: Flux uses deploy key (more secure)
- Vault credentials: Stored in `~/.neotool/vault-credentials.txt` (local only)
- Never commit secrets to Git
- Vault sealed by default (must unseal after pod restarts)

## 📚 Documentation

- [Flux Docs](https://fluxcd.io/docs/)
- [External Secrets](https://external-secrets.io/)
- [Vault on K8s](https://www.vaultproject.io/docs/platform/k8s)

## 🆘 Troubleshooting

### Flux CLI not found
```bash
export PATH=$HOME/.local/bin:$PATH
echo 'export PATH=$HOME/.local/bin:$PATH' >> ~/.zshrc
```

### Cluster not accessible
```bash
kubectl config use-context default
kubectl get nodes
```

### Bootstrap fails
Check [GITHUB-TOKEN-SETUP.md](./GITHUB-TOKEN-SETUP.md) for token requirements.

## 🚀 Ready to Bootstrap!

Everything is prepared. You can now run:

```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./bootstrap.sh
```

Make sure you have your GitHub Personal Access Token ready!
