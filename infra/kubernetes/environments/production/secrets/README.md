# Secrets Management Strategy

## ⚠️ IMPORTANT: Secrets Are NOT Committed to Git

This directory contains instructions for managing secrets, but **actual secret values are never committed**.

## Architecture

```
Vault (HashiCorp Vault)
   ↓ stores
PostgreSQL Credentials
   ↓ synced by
External Secrets Operator
   ↓ creates
Kubernetes Secrets
   ↓ consumed by
Applications (PostgreSQL, Services, etc.)
```

## Initial Setup (First Time Only)

### 1. Create PostgreSQL Credentials Locally

```bash
# Generate secure credentials
PG_USER="neotool"
PG_PASSWORD=$(openssl rand -base64 32)
PG_DB="neotool_production"

echo "Save these credentials securely!"
echo "POSTGRES_USER: $PG_USER"
echo "POSTGRES_PASSWORD: $PG_PASSWORD"
echo "POSTGRES_DB: $PG_DB"
```

### 2. Create Kubernetes Secret Manually

```bash
# Create the secret in Kubernetes
kubectl create secret generic postgres-credentials \
  --from-literal=POSTGRES_USER="$PG_USER" \
  --from-literal=POSTGRES_PASSWORD="$PG_PASSWORD" \
  --from-literal=POSTGRES_DB="$PG_DB" \
  --namespace=production \
  --dry-run=client -o yaml > /tmp/postgres-secret.yaml

# Apply it
kubectl apply -f /tmp/postgres-secret.yaml

# Delete the temp file (don't commit!)
rm /tmp/postgres-secret.yaml
```

### 3. Verify Secret Was Created

```bash
kubectl get secret postgres-credentials -n production
kubectl describe secret postgres-credentials -n production
```

## Future: Vault Integration (Recommended)

Once Vault is properly set up in production mode:

### 1. Store credentials in Vault

```bash
# Port forward to Vault
kubectl port-forward -n neotool-security svc/vault 8200:8200

# Login to Vault
vault login <root-token>

# Store PostgreSQL credentials
vault kv put secret/postgres \
  username="neotool" \
  password="your-secure-password" \
  database="neotool_production"
```

### 2. Install External Secrets Operator

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets \
  --create-namespace
```

### 3. Create ExternalSecret Resource

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
      key: secret/postgres
      property: username
  - secretKey: POSTGRES_PASSWORD
    remoteRef:
      key: secret/postgres
      property: password
  - secretKey: POSTGRES_DB
    remoteRef:
      key: secret/postgres
      property: database
```

## Security Best Practices

### ✅ DO:
- Use strong, randomly generated passwords
- Rotate credentials regularly
- Use Vault or External Secrets Operator in production
- Keep secrets in secure password managers
- Use RBAC to restrict secret access

### ❌ DON'T:
- Commit secrets to Git
- Share secrets via Slack/email
- Use simple passwords like "password123"
- Use the same password across environments
- Store secrets in plain text files

## CI/CD Integration

For CI/CD pipelines, use:

1. **GitHub Actions**: Repository Secrets
2. **GitLab CI**: CI/CD Variables (masked)
3. **Vault**: AppRole authentication
4. **External Secrets Operator**: ServiceAccount tokens

## Troubleshooting

### Secret not found error

```bash
# Check if secret exists
kubectl get secret postgres-credentials -n production

# If missing, create it manually (see step 2 above)
```

### Can't read secret values

```bash
# Decode secret (for debugging only!)
kubectl get secret postgres-credentials -n production -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d
```

## References

- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [External Secrets Operator](https://external-secrets.io/)
- [HashiCorp Vault](https://www.vaultproject.io/)
