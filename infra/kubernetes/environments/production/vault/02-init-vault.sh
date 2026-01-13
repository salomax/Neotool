#!/bin/bash
set -e

# Initialize Vault and Save Credentials

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app=vault -o jsonpath='{.items[0].metadata.name}')

echo "================================================"
echo "Initialize Vault"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found in namespace '$NAMESPACE'"
    echo "   Run: ./01-deploy-vault.sh first"
    exit 1
fi

echo "Vault pod: $VAULT_POD"
echo ""

# Check if already initialized
echo "Checking if Vault is already initialized..."
if kubectl exec -n $NAMESPACE $VAULT_POD -- vault status 2>&1 | grep -q "Initialized.*true"; then
    echo "⚠️  Vault is already initialized!"
    echo ""
    read -p "Do you want to see the status? (y/N): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kubectl exec -n $NAMESPACE $VAULT_POD -- vault status
    fi
    exit 0
fi

echo "Initializing Vault with 5 key shares (threshold: 3)..."
echo ""

# Initialize Vault
INIT_OUTPUT=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator init \
    -key-shares=5 \
    -key-threshold=3 \
    -format=json)

echo "================================================"
echo "🔐 VAULT INITIALIZATION COMPLETE"
echo "================================================"
echo ""
echo "⚠️  CRITICAL: Save these credentials in a SECURE location!"
echo "⚠️  You will need them to unseal Vault after restarts"
echo ""

# Extract keys and root token
UNSEAL_KEY_1=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[0]')
UNSEAL_KEY_2=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[1]')
UNSEAL_KEY_3=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[2]')
UNSEAL_KEY_4=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[3]')
UNSEAL_KEY_5=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[4]')
ROOT_TOKEN=$(echo "$INIT_OUTPUT" | jq -r '.root_token')

# Save to file (with warning)
CREDENTIALS_FILE="vault-credentials-$(date +%Y%m%d-%H%M%S).txt"
cat > "$CREDENTIALS_FILE" <<EOF
================================================
VAULT CREDENTIALS - KEEP SECURE!
================================================

Generated: $(date)
Namespace: $NAMESPACE

UNSEAL KEYS (need 3 of 5 to unseal):
-----------------------------------
Unseal Key 1: $UNSEAL_KEY_1
Unseal Key 2: $UNSEAL_KEY_2
Unseal Key 3: $UNSEAL_KEY_3
Unseal Key 4: $UNSEAL_KEY_4
Unseal Key 5: $UNSEAL_KEY_5

ROOT TOKEN:
-----------
Root Token: $ROOT_TOKEN

IMPORTANT:
----------
1. Store these credentials in a password manager (1Password, LastPass, etc.)
2. Distribute unseal keys to different trusted people
3. NEVER commit this file to Git
4. Delete this file after saving elsewhere

To unseal Vault:
  ./03-unseal-vault.sh

To login:
  kubectl exec -n production -it vault-0 -- vault login $ROOT_TOKEN
EOF

echo "Credentials saved to: $CREDENTIALS_FILE"
echo ""
echo "Root Token: $ROOT_TOKEN"
echo ""
echo "Unseal Keys:"
echo "  1: $UNSEAL_KEY_1"
echo "  2: $UNSEAL_KEY_2"
echo "  3: $UNSEAL_KEY_3"
echo "  4: $UNSEAL_KEY_4"
echo "  5: $UNSEAL_KEY_5"
echo ""
echo "================================================"
echo ""
echo "Next steps:"
echo "  1. Save these credentials securely (password manager)"
echo "  2. Run: ./03-unseal-vault.sh"
echo "  3. Delete the file: rm $CREDENTIALS_FILE"
echo ""
