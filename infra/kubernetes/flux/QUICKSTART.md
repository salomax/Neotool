# Flux GitOps Quick Start

## 🚀 Setup (One Time)

### 1. Install Flux CLI

**macOS:**
```bash
brew install fluxcd/tap/flux
```

**Linux:**
```bash
curl -s https://fluxcd.io/install.sh | sudo bash
```

**Verify:**
```bash
flux --version
```

---

### 2. Bootstrap Flux

```bash
cd infra/kubernetes/flux
./bootstrap.sh
```

**Choose option 1 (GitHub)** and provide:
- GitHub username (e.g., `salomax`)
- Repository name (e.g., `Neotool`)
- Personal Access Token

**What it does:**
- ✅ Installs Flux in cluster
- ✅ Creates deploy key in GitHub
- ✅ Commits Flux manifests to your repo
- ✅ Sets up auto-sync (Git → Cluster)

---

### 3. Verify Flux

```bash
flux check
flux get all
```

You should see:
```
✔ all checks passed
```

---

### 4. Initialize Vault (One Time)

```bash
./vault-init.sh
```

**Saves credentials to:** `~/.neotool/vault-credentials.txt`

⚠️  **BACKUP THESE CREDENTIALS!**

---

### 5. Unseal Vault

```bash
./vault-unseal.sh
```

---

### 6. Configure Vault

```bash
./vault-configure.sh
```

Enables Kubernetes auth and creates policies.

---

### 7. Store PostgreSQL Credentials

```bash
./vault-store-postgres.sh
```

Stores credentials in Vault at path `secret/postgres`.

---

## ✅ Done! GitOps is Active

From now on, just use Git:

```bash
# 1. Make changes
vim flux/apps/myapp.yaml

# 2. Commit
git add .
git commit -m "Deploy myapp"

# 3. Push
git push

# 4. Flux auto-deploys! ✨
```

---

## 📊 Monitoring

### Watch Reconciliation

```bash
flux get kustomizations --watch
```

### View Logs

```bash
flux logs --all-namespaces --follow
```

### Check Status

```bash
# Infrastructure
flux get helmrelease -n flux-system

# Apps
kubectl get pods -n production
```

---

## 🔄 Common Workflows

### Deploy New App

```yaml
# flux/apps/myapp/deployment.yaml
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
        image: myapp:v1.0.0
```

```bash
git add flux/apps/myapp/
git commit -m "Add myapp"
git push

# Flux deploys automatically!
flux get kustomizations --watch
```

### Update App Version

```bash
# Edit image version in YAML
vim flux/apps/myapp/deployment.yaml

git commit -am "Update myapp to v1.1.0"
git push

# Flux updates!
```

### Rollback

```bash
# Git rollback = cluster rollback!
git revert HEAD
git push

# Flux reverts in cluster
```

### Force Sync

```bash
# Don't wait for interval, sync now
flux reconcile kustomization infrastructure --with-source
```

### Suspend/Resume

```bash
# Pause auto-sync
flux suspend kustomization apps

# Resume
flux resume kustomization apps
```

---

## 🔐 Vault Management

### Unseal After Pod Restart

```bash
./vault-unseal.sh
```

### Add New Secret

```bash
VAULT_POD=$(kubectl get pod -n production -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n production -it $VAULT_POD -- sh

# Inside pod:
export VAULT_TOKEN=<root-token>
vault kv put secret/myapp api_key="secret123"
```

### Create ExternalSecret for New App

```yaml
# flux/infrastructure/external-secrets/myapp-external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: myapp-credentials
  namespace: production
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: SecretStore
  target:
    name: myapp-credentials
  data:
    - secretKey: API_KEY
      remoteRef:
        key: myapp
        property: api_key
```

```bash
git add flux/infrastructure/external-secrets/myapp-external-secret.yaml
git commit -m "Add myapp external secret"
git push
```

---

## 🆘 Troubleshooting

### Flux not syncing

```bash
# Check source
flux get sources git

# Force reconcile
flux reconcile source git flux-system
```

### HelmRelease stuck

```bash
# Check status
flux get helmrelease vault

# View events
flux events --for HelmRelease/vault

# Check Helm
helm list -n production
```

### Vault sealed after restart

```bash
# Normal! Just unseal
./vault-unseal.sh
```

### External Secret not syncing

```bash
# Check ExternalSecret
kubectl get externalsecret -n production

# Check events
kubectl describe externalsecret postgres-credentials -n production

# Check SecretStore
kubectl get secretstore -n production
```

---

## 📚 Learn More

- [Flux Documentation](https://fluxcd.io/docs/)
- [External Secrets](https://external-secrets.io/)
- [Vault on Kubernetes](https://www.vaultproject.io/docs/platform/k8s)

---

## 🎯 Summary

### Before (Shell Scripts):
```bash
./01-script.sh
./02-script.sh
./03-script.sh
# ... manual, error-prone
```

### After (GitOps):
```bash
git push
# Flux handles everything! ✨
```

**Benefits:**
- ✅ Declarative (YAML > Bash)
- ✅ Git as source of truth
- ✅ Auto-sync
- ✅ Self-healing
- ✅ Audit trail
- ✅ Easy rollback
