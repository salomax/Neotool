#!/bin/bash
set -e

# Configure Vault for Kubernetes Auth and setup secrets engine

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

echo "================================================"
echo "  Configure Vault for Kubernetes"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found"
    exit 1
fi

# Load credentials
if [ ! -f "$VAULT_CREDS_FILE" ]; then
    echo "❌ Credentials file not found"
    exit 1
fi

source "$VAULT_CREDS_FILE"

echo "Configuring Vault..."
echo ""

kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'

echo '1. Enabling Kubernetes auth...'
vault auth enable kubernetes 2>/dev/null || echo '   Already enabled'

echo '2. Configuring Kubernetes auth...'
vault write auth/kubernetes/config \
    kubernetes_host=\"https://\$KUBERNETES_PORT_443_TCP_ADDR:443\" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

echo '3. Enabling KV secrets engine v2...'
vault secrets enable -path=secret kv-v2 2>/dev/null || echo '   Already enabled'

echo '4. Creating policy for apps...'
vault policy write app-policy - <<EOF
path \"secret/data/*\" {
  capabilities = [\"read\", \"list\"]
}
path \"secret/metadata/*\" {
  capabilities = [\"read\", \"list\"]
}
EOF

echo '5. Creating Kubernetes role...'
vault write auth/kubernetes/role/app \
    bound_service_account_names=external-secrets-vault-auth \
    bound_service_account_namespaces=$NAMESPACE \
    policies=app-policy \
    ttl=24h

echo ''
echo '✓ Vault configured successfully!'
"

echo ""
echo "================================================"
echo "  Configuration Complete!"
echo "================================================"
echo ""
echo "Vault is ready to store secrets"
echo ""
echo "Next: Store PostgreSQL credentials"
echo "  ./vault-store-postgres.sh"
echo ""
