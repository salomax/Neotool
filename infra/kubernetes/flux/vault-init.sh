#!/bin/bash
set -e

# Vault Initialization Helper for Flux-deployed Vault

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

echo "================================================"
echo "  Vault Initialization (Flux-deployed)"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found"
    echo ""
    echo "Wait for Flux to deploy Vault:"
    echo "  flux get helmrelease vault"
    echo "  kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=vault"
    exit 1
fi

echo "Vault pod: $VAULT_POD"
echo ""

# Check if already initialized
VAULT_INITIALIZED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.initialized' || echo "false")

if [ "$VAULT_INITIALIZED" = "true" ]; then
    echo "✓ Vault already initialized"
    echo ""

    # Check seal status
    VAULT_SEALED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.sealed')

    if [ "$VAULT_SEALED" = "true" ]; then
        echo "🔒 Vault is SEALED"
        echo ""
        echo "Unseal it with:"
        echo "  ./vault-unseal.sh"
    else
        echo "🔓 Vault is UNSEALED and ready"
        kubectl exec -n $NAMESPACE $VAULT_POD -- vault status
    fi
    exit 0
fi

echo "Initializing Vault..."
echo ""

# Create credentials directory
mkdir -p "$(dirname "$VAULT_CREDS_FILE")"

# Initialize
INIT_OUTPUT=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator init \
    -key-shares=5 \
    -key-threshold=3 \
    -format=json)

# Extract keys
UNSEAL_KEY_1=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[0]')
UNSEAL_KEY_2=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[1]')
UNSEAL_KEY_3=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[2]')
UNSEAL_KEY_4=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[3]')
UNSEAL_KEY_5=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[4]')
ROOT_TOKEN=$(echo "$INIT_OUTPUT" | jq -r '.root_token')

# Save credentials
cat > "$VAULT_CREDS_FILE" <<EOF
# Vault Credentials - KEEP SECURE!
# Generated: $(date)

ROOT_TOKEN=$ROOT_TOKEN

UNSEAL_KEY_1=$UNSEAL_KEY_1
UNSEAL_KEY_2=$UNSEAL_KEY_2
UNSEAL_KEY_3=$UNSEAL_KEY_3
UNSEAL_KEY_4=$UNSEAL_KEY_4
UNSEAL_KEY_5=$UNSEAL_KEY_5
EOF

chmod 600 "$VAULT_CREDS_FILE"

echo "================================================"
echo "  Vault Initialized!"
echo "================================================"
echo ""
echo "✓ Credentials saved to: $VAULT_CREDS_FILE"
echo ""
echo "⚠️  BACKUP THESE CREDENTIALS SECURELY!"
echo ""
echo "Next steps:"
echo "  1. Unseal Vault: ./vault-unseal.sh"
echo "  2. Configure Kubernetes auth: ./vault-configure.sh"
echo "  3. Store PostgreSQL credentials: ./vault-store-postgres.sh"
echo ""
