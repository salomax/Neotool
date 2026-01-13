# Production Setup - Simplified

## 🚀 Quick Start (Like Terraform!)

```bash
cd environments/production
./setup-production.sh
```

That's it! ✅

## What It Does (Idempotently)

The script intelligently checks and only does what's needed:

1. ✅ Creates `production` namespace (if not exists)
2. ✅ Enables Linkerd injection (if not enabled)
3. ✅ Deploys Vault (if not deployed)
4. ✅ Initializes Vault (if not initialized)
5. ✅ Unseals Vault (if sealed)
6. ✅ Configures Kubernetes auth (if not configured)
7. ✅ Generates & stores PostgreSQL credentials (if not exists)

## Idempotent = Safe to Run Multiple Times

```bash
# First run
./setup-production.sh
# Creates everything

# Second run (after pod restart)
./setup-production.sh
# Only unseals Vault, skips rest

# Third run (everything ready)
./setup-production.sh
# Validates everything is ready ✓
```

## Credentials Management

All credentials are saved to:
```
~/.neotool/vault-credentials.txt
```

**IMPORTANT:**
- ⚠️  Backup this file securely!
- ⚠️  Don't commit to Git
- ⚠️  Store in password manager (1Password, LastPass, etc.)

## Manual Steps (Optional)

If you prefer step-by-step control:

```bash
# 1. Deploy Vault
./vault/01-deploy-vault.sh

# 2. Initialize (first time only)
./vault/02-init-vault.sh

# 3. Unseal
./vault/03-unseal-vault.sh

# 4. Configure auth
./vault/04-configure-k8s-auth.sh

# 5. Store PostgreSQL creds
./vault/05-store-postgres-creds.sh
```

## Architecture

```
setup-production.sh
  ├─ Namespace ✓
  ├─ Linkerd ✓
  ├─ Vault
  │   ├─ Deploy
  │   ├─ Initialize (once)
  │   ├─ Unseal (auto with saved keys)
  │   ├─ Kubernetes auth
  │   └─ PostgreSQL credentials
  └─ Ready for apps!
```

## Comparison: Terraform vs K8s Setup

### Before (Complex):
```bash
# Terraform
cd terraform
terraform apply  # ✅ Simple

# K8s
cd kubernetes
./01-script.sh
./02-script.sh   # ❌ Complex
./03-script.sh
./04-script.sh
./05-script.sh
```

### After (Simple):
```bash
# Terraform
cd terraform
terraform apply  # ✅ Simple

# K8s
cd kubernetes
./setup-production.sh  # ✅ Simple!
```

## Troubleshooting

### Script fails with "Vault pod not found"

```bash
# Check if Vault is running
kubectl get pods -n production -l app=vault

# Check logs
kubectl logs -n production -l app=vault
```

### Credentials file not found

The script creates it at `~/.neotool/vault-credentials.txt` on first initialization.

If lost, you'll need to:
1. Delete Vault StatefulSet and PVC
2. Run setup script again (will reinitialize)

### Vault stays sealed

Check if credentials file has correct unseal keys:
```bash
cat ~/.neotool/vault-credentials.txt
```

## Next Steps

After running `setup-production.sh`:

1. **Install External Secrets Operator:**
   ```bash
   helm repo add external-secrets https://charts.external-secrets.io
   helm install external-secrets external-secrets/external-secrets -n production
   ```

2. **Deploy PostgreSQL:**
   ```bash
   kubectl apply -f postgres/
   ```

3. **Deploy applications:**
   ```bash
   kubectl apply -k .
   ```

## Philosophy: Infrastructure as Code

```
Terraform → VPS + K3S (done ✅)
setup-production.sh → Vault + Secrets (done ✅)
kubectl apply → Applications (next)
```

All **idempotent**, all **repeatable**, all **simple**!
