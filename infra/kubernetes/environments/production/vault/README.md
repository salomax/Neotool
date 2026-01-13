# Vault Production Setup

Production-ready HashiCorp Vault configuration for Neotool.

## Features

- ✅ **Persistent Storage** - Data survives pod restarts
- ✅ **Production Mode** - Not dev mode, proper initialization
- ✅ **Sealed by Default** - Security first
- ✅ **Kubernetes Auth** - Native K8s integration
- ✅ **KV Secrets v2** - Versioned secrets with history

## Quick Start

### 1. Deploy Vault

```bash
cd environments/production/vault
./01-deploy-vault.sh
```

**What it does:**
- Creates ServiceAccount with RBAC
- Deploys Vault StatefulSet with PVC
- Configures file backend storage

**Time:** ~2 minutes

### 2. Initialize Vault (FIRST TIME ONLY!)

```bash
./02-init-vault.sh
```

**CRITICAL:**
- Generates 5 unseal keys (need 3 to unseal)
- Generates root token
- Saves to file: `vault-credentials-TIMESTAMP.txt`
- **SAVE THESE SECURELY!** You can't recover them!

**Store in:**
- Password manager (1Password, LastPass, Bitwarden)
- Encrypted file in secure location
- Distribute unseal keys to different trusted people

### 3. Unseal Vault

```bash
./03-unseal-vault.sh
```

**Required after:**
- Initial setup
- Vault pod restart
- Node restart

**You need:**
- Any 3 of the 5 unseal keys

### 4. Configure Kubernetes Auth

```bash
./04-configure-k8s-auth.sh
```

**What it does:**
- Enables Kubernetes auth method
- Configures Vault to talk to K8s API
- Creates policies for applications
- Enables KV secrets engine v2

**You need:**
- Root token

### 5. Store PostgreSQL Credentials

```bash
./05-store-postgres-creds.sh
```

**What it does:**
- Prompts for PostgreSQL credentials
- Stores in Vault at path: `secret/postgres`
- Can generate secure random password

## Architecture

```
┌─────────────────────────────────────────┐
│  Vault (Production Mode)                │
│  ├─ File Backend (PVC)                  │
│  ├─ Sealed by Default                   │
│  └─ Kubernetes Auth Enabled             │
└─────────────────────────────────────────┘
         ↓ stores
┌─────────────────────────────────────────┐
│  Secrets (KV v2)                        │
│  ├─ secret/postgres                     │
│  ├─ secret/kafka                        │
│  └─ secret/api-keys                     │
└─────────────────────────────────────────┘
         ↓ synced by
┌─────────────────────────────────────────┐
│  External Secrets Operator              │
│  ├─ Watches Vault                       │
│  └─ Creates K8s Secrets                 │
└─────────────────────────────────────────┘
         ↓ consumed by
┌─────────────────────────────────────────┐
│  Applications                           │
│  ├─ PostgreSQL                          │
│  ├─ Kotlin Services                     │
│  └─ Other Apps                          │
└─────────────────────────────────────────┘
```

## Accessing Vault

### Via kubectl exec

```bash
# Get pod name
VAULT_POD=$(kubectl get pod -n production -l app=vault -o jsonpath='{.items[0].metadata.name}')

# Login
kubectl exec -n production -it $VAULT_POD -- vault login

# List secrets
kubectl exec -n production $VAULT_POD -- vault kv list secret/

# Read secret
kubectl exec -n production $VAULT_POD -- vault kv get secret/postgres
```

### Via Port Forward

```bash
# Forward port
kubectl port-forward -n production svc/vault 8200:8200

# In another terminal
export VAULT_ADDR='http://127.0.0.1:8200'
vault login
vault kv list secret/
```

### Via UI

```bash
kubectl port-forward -n production svc/vault 8200:8200
# Open: http://localhost:8200
# Login with root token
```

## Common Operations

### Check Vault Status

```bash
kubectl exec -n production vault-0 -- vault status
```

### Unseal After Restart

```bash
./03-unseal-vault.sh
```

### Store New Secret

```bash
kubectl exec -n production -it vault-0 -- vault login
kubectl exec -n production vault-0 -- vault kv put secret/myapp \
    api_key="secret123" \
    db_password="securepass"
```

### Read Secret

```bash
kubectl exec -n production vault-0 -- vault kv get secret/myapp
kubectl exec -n production vault-0 -- vault kv get -field=api_key secret/myapp
```

### List All Secrets

```bash
kubectl exec -n production vault-0 -- vault kv list secret/
```

## Security Best Practices

### ✅ DO:
- Store unseal keys separately (different people/locations)
- Rotate root token regularly
- Use policies with least privilege
- Enable audit logging
- Use TLS for external access
- Keep Vault sealed when not in use
- Back up Vault data regularly

### ❌ DON'T:
- Commit unseal keys or root token to Git
- Share unseal keys via email/slack
- Give root token to applications
- Use same token for multiple apps
- Leave Vault unsealed indefinitely

## Disaster Recovery

### Backup Vault Data

```bash
# Backup PVC data
kubectl exec -n production vault-0 -- tar czf /tmp/vault-backup.tar.gz /vault/data
kubectl cp production/vault-0:/tmp/vault-backup.tar.gz ./vault-backup-$(date +%Y%m%d).tar.gz
```

### Restore from Backup

```bash
# Copy backup to pod
kubectl cp ./vault-backup.tar.gz production/vault-0:/tmp/

# Extract
kubectl exec -n production vault-0 -- tar xzf /tmp/vault-backup.tar.gz -C /

# Restart pod
kubectl delete pod -n production vault-0
```

## Troubleshooting

### Vault pod not starting

```bash
kubectl describe pod -n production vault-0
kubectl logs -n production vault-0
```

### Can't unseal Vault

- Check you have correct unseal keys
- Need exactly 3 out of 5 keys
- Check Vault logs for errors

### "permission denied" errors

- Check policies are configured correctly
- Verify Kubernetes auth is enabled
- Check ServiceAccount has correct RBAC

## Monitoring

### Health Checks

```bash
# HTTP health endpoint
kubectl exec -n production vault-0 -- curl -s http://127.0.0.1:8200/v1/sys/health | jq

# Sealed status (should be false when unsealed)
kubectl exec -n production vault-0 -- vault status -format=json | jq -r '.sealed'
```

### Metrics

Vault exposes Prometheus metrics at: `/v1/sys/metrics`

## Next Steps

After Vault is set up:

1. **Install External Secrets Operator**
   ```bash
   helm repo add external-secrets https://charts.external-secrets.io
   helm install external-secrets external-secrets/external-secrets -n production
   ```

2. **Create SecretStore**
   ```yaml
   apiVersion: external-secrets.io/v1beta1
   kind: SecretStore
   metadata:
     name: vault-backend
     namespace: production
   spec:
     provider:
       vault:
         server: "http://vault.production.svc.cluster.local:8200"
         path: "secret"
         version: "v2"
         auth:
           kubernetes:
             mountPath: "kubernetes"
             role: "app"
   ```

3. **Create ExternalSecret for PostgreSQL**
   ```yaml
   apiVersion: external-secrets.io/v1beta1
   kind: ExternalSecret
   metadata:
     name: postgres-credentials
     namespace: production
   spec:
     refreshInterval: 1h
     secretStoreRef:
       name: vault-backend
       kind: SecretStore
     target:
       name: postgres-credentials
     data:
     - secretKey: POSTGRES_USER
       remoteRef:
         key: postgres
         property: username
     - secretKey: POSTGRES_PASSWORD
       remoteRef:
         key: postgres
         property: password
     - secretKey: POSTGRES_DB
       remoteRef:
         key: postgres
         property: database
   ```

## References

- [Vault Documentation](https://www.vaultproject.io/docs)
- [Vault on Kubernetes](https://www.vaultproject.io/docs/platform/k8s)
- [External Secrets Operator](https://external-secrets.io/)
