#!/bin/bash
set -e

# Unseal Vault using unseal keys

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app=vault -o jsonpath='{.items[0].metadata.name}')

echo "================================================"
echo "Unseal Vault"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found in namespace '$NAMESPACE'"
    exit 1
fi

echo "Vault pod: $VAULT_POD"
echo ""

# Check current status
echo "Checking Vault status..."
SEALED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.sealed')

if [ "$SEALED" = "false" ]; then
    echo "✅ Vault is already unsealed!"
    kubectl exec -n $NAMESPACE $VAULT_POD -- vault status
    exit 0
fi

echo "🔒 Vault is currently SEALED"
echo ""
echo "You need to provide 3 unseal keys (out of 5)"
echo ""

# Prompt for unseal keys
read -sp "Enter Unseal Key 1: " KEY1
echo ""
read -sp "Enter Unseal Key 2: " KEY2
echo ""
read -sp "Enter Unseal Key 3: " KEY3
echo ""

echo ""
echo "Unsealing Vault..."

# Unseal with key 1
echo -n "  Key 1... "
kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$KEY1" > /dev/null 2>&1
echo "✓"

# Unseal with key 2
echo -n "  Key 2... "
kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$KEY2" > /dev/null 2>&1
echo "✓"

# Unseal with key 3
echo -n "  Key 3... "
kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$KEY3" > /dev/null 2>&1
echo "✓"

echo ""
echo "================================================"
echo "✅ Vault Unsealed Successfully!"
echo "================================================"
echo ""

kubectl exec -n $NAMESPACE $VAULT_POD -- vault status

echo ""
echo "Next steps:"
echo "  1. Login: kubectl exec -n $NAMESPACE -it $VAULT_POD -- vault login"
echo "  2. Configure Kubernetes auth: ./04-configure-k8s-auth.sh"
echo ""
