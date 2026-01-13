#!/bin/bash
set -e

# Store PostgreSQL credentials in Vault

NAMESPACE="production"
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app=vault -o jsonpath='{.items[0].metadata.name}')

echo "================================================"
echo "Store PostgreSQL Credentials in Vault"
echo "================================================"
echo ""

if [ -z "$VAULT_POD" ]; then
    echo "❌ Vault pod not found"
    exit 1
fi

# Prompt for root token
read -sp "Enter Vault Root Token: " ROOT_TOKEN
echo ""
echo ""

# Prompt for PostgreSQL credentials
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

echo 'Credentials stored successfully!'
echo ''
echo 'Verifying...'
vault kv get secret/postgres
"

echo ""
echo "================================================"
echo "✅ PostgreSQL Credentials Stored in Vault!"
echo "================================================"
echo ""
echo "Credentials:"
echo "  Username: $PG_USER"
echo "  Database: $PG_DB"
echo "  Password: [stored in Vault]"
echo ""
echo "Path: secret/postgres"
echo ""
echo "Next step:"
echo "  Install External Secrets Operator to sync to Kubernetes"
echo ""
