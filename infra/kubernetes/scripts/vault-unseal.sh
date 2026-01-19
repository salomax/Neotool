#!/bin/bash
set -e

# Vault Unseal Helper

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

echo "================================================"
echo "  Vault Unseal"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found"
    exit 1
fi

# Check seal status
VAULT_SEALED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.sealed')

if [ "$VAULT_SEALED" = "false" ]; then
    echo "✅ Vault is already unsealed"
    kubectl exec -n $NAMESPACE $VAULT_POD -- vault status
    exit 0
fi

echo "🔒 Vault is sealed"
echo ""

# Load credentials
if [ ! -f "$VAULT_CREDS_FILE" ]; then
    echo "❌ Credentials file not found: $VAULT_CREDS_FILE"
    echo ""
    echo "Initialize Vault first: ./vault-init.sh"
    exit 1
fi

source "$VAULT_CREDS_FILE"

echo "Unsealing with saved keys..."

# Unseal with 3 keys
kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$UNSEAL_KEY_1" > /dev/null 2>&1
echo "  Key 1 ✓"

kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$UNSEAL_KEY_2" > /dev/null 2>&1
echo "  Key 2 ✓"

kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$UNSEAL_KEY_3" > /dev/null 2>&1
echo "  Key 3 ✓"

echo ""
echo "================================================"
echo "  Vault Unsealed!"
echo "================================================"
echo ""

kubectl exec -n $NAMESPACE $VAULT_POD -- vault status

echo ""
echo "Next: Configure Kubernetes auth"
echo "  ./vault-configure.sh"
echo ""
