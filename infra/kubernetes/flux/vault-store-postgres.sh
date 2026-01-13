#!/bin/bash
set -e

# Store PostgreSQL credentials in Vault

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

echo "================================================"
echo "  Store PostgreSQL Credentials in Vault"
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

# Check if credentials already exist
CREDS_EXIST=$(kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'
vault kv get secret/postgres > /dev/null 2>&1 && echo true || echo false
")

if [ "$CREDS_EXIST" = "true" ]; then
    echo "✓ PostgreSQL credentials already exist in Vault"
    echo ""
    kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'
vault kv get secret/postgres
"
    exit 0
fi

echo "PostgreSQL credentials not found. Creating..."
echo ""

# Prompt for credentials
read -p "PostgreSQL username (default: neotool): " PG_USER
PG_USER=${PG_USER:-neotool}

read -p "PostgreSQL database (default: neotool_production): " PG_DB
PG_DB=${PG_DB:-neotool_production}

echo ""
read -p "Generate random password? (Y/n): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Nn]$ ]]; then
    read -sp "Enter PostgreSQL password: " PG_PASSWORD
    echo ""
else
    PG_PASSWORD=$(openssl rand -base64 32)
    echo "Generated password: $PG_PASSWORD"
    echo ""
    echo "⚠️  SAVE THIS PASSWORD!"
    read -p "Press Enter to continue..."
fi

echo ""
echo "Storing credentials in Vault..."

kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'

vault kv put secret/postgres \
    username='$PG_USER' \
    password='$PG_PASSWORD' \
    database='$PG_DB'

echo ''
echo 'Verifying...'
vault kv get secret/postgres
"

echo ""
echo "================================================"
echo "  Credentials Stored!"
echo "================================================"
echo ""
echo "External Secrets Operator will now sync these to Kubernetes"
echo ""
echo "Verify sync:"
echo "  kubectl get externalsecret postgres-credentials -n production"
echo "  kubectl get secret postgres-credentials -n production"
echo ""
