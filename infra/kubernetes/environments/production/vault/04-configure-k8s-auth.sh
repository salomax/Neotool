#!/bin/bash
set -e

# Configure Vault for Kubernetes authentication

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app=vault -o jsonpath='{.items[0].metadata.name}')

echo "================================================"
echo "Configure Vault Kubernetes Authentication"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found"
    exit 1
fi

# Check if Vault is unsealed
SEALED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.sealed')
if [ "$SEALED" = "true" ]; then
    echo "❌ Vault is sealed. Run: ./03-unseal-vault.sh"
    exit 1
fi

echo "Vault is unsealed ✓"
echo ""

# Prompt for root token
read -sp "Enter Vault Root Token: " ROOT_TOKEN
echo ""
echo ""

echo "Configuring Kubernetes authentication..."

# Enable Kubernetes auth
kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'

echo 'Enabling Kubernetes auth method...'
vault auth enable kubernetes 2>/dev/null || echo 'Kubernetes auth already enabled'

echo 'Configuring Kubernetes auth...'
vault write auth/kubernetes/config \
    kubernetes_host=\"https://\$KUBERNETES_PORT_443_TCP_ADDR:443\" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

echo 'Enabling KV secrets engine...'
vault secrets enable -path=secret kv-v2 2>/dev/null || echo 'KV secrets already enabled'

echo 'Creating policy for applications...'
vault policy write app-policy - <<EOF
path \"secret/data/*\" {
  capabilities = [\"read\", \"list\"]
}
EOF

echo 'Creating Kubernetes role...'
vault write auth/kubernetes/role/app \
    bound_service_account_names=default \
    bound_service_account_namespaces=$NAMESPACE \
    policies=app-policy \
    ttl=24h

echo 'Configuration complete!'
"

echo ""
echo "================================================"
echo "✅ Vault Kubernetes Auth Configured!"
echo "================================================"
echo ""
echo "Vault is now ready to store secrets"
echo ""
echo "Next steps:"
echo "  1. Store PostgreSQL credentials: ./05-store-postgres-creds.sh"
echo "  2. Install External Secrets Operator"
echo ""
