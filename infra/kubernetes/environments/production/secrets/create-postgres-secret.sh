#!/bin/bash
set -e

# Script to create PostgreSQL credentials secret
# Run this ONCE before deploying PostgreSQL

NAMESPACE="production"
SECRET_NAME="postgres-credentials"

echo "================================================"
echo "Create PostgreSQL Credentials Secret"
echo "================================================"
echo ""

# Check if secret already exists
if kubectl get secret $SECRET_NAME -n $NAMESPACE &>/dev/null; then
    echo "⚠️  Secret '$SECRET_NAME' already exists in namespace '$NAMESPACE'"
    echo ""
    read -p "Do you want to recreate it? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted. Using existing secret."
        exit 0
    fi
    kubectl delete secret $SECRET_NAME -n $NAMESPACE
fi

echo "Creating new PostgreSQL credentials..."
echo ""

# Prompt for values or generate
read -p "PostgreSQL username (default: neotool): " PG_USER
PG_USER=${PG_USER:-neotool}

read -p "PostgreSQL database name (default: neotool_production): " PG_DB
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
    echo "⚠️  SAVE THIS PASSWORD! It won't be shown again."
    echo ""
    read -p "Press Enter to continue..."
fi

# Create the secret
echo ""
echo "Creating Kubernetes secret..."

kubectl create secret generic $SECRET_NAME \
  --from-literal=POSTGRES_USER="$PG_USER" \
  --from-literal=POSTGRES_PASSWORD="$PG_PASSWORD" \
  --from-literal=POSTGRES_DB="$PG_DB" \
  --namespace=$NAMESPACE

echo ""
echo "✅ Secret '$SECRET_NAME' created successfully in namespace '$NAMESPACE'"
echo ""
echo "Credentials summary:"
echo "  Username: $PG_USER"
echo "  Database: $PG_DB"
echo "  Password: [hidden]"
echo ""
echo "To verify:"
echo "  kubectl get secret $SECRET_NAME -n $NAMESPACE"
echo "  kubectl describe secret $SECRET_NAME -n $NAMESPACE"
echo ""
echo "To decode password (for debugging):"
echo "  kubectl get secret $SECRET_NAME -n $NAMESPACE -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d"
echo ""
