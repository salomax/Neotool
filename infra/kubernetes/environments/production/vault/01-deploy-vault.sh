#!/bin/bash
set -e

# Deploy Vault to Production

NAMESPACE="production"

echo "================================================"
echo "Deploying Vault to Production"
echo "================================================"
echo ""

# Check namespace exists
if ! kubectl get namespace $NAMESPACE &>/dev/null; then
    echo "❌ Namespace '$NAMESPACE' doesn't exist"
    echo "   Run: kubectl create namespace $NAMESPACE"
    exit 1
fi

echo "Step 1: Creating ServiceAccount and RBAC..."
kubectl apply -f vault-serviceaccount.yaml
echo "✓ ServiceAccount created"
echo ""

echo "Step 2: Creating Vault configuration..."
kubectl apply -f vault-config.yaml
echo "✓ ConfigMap created"
echo ""

echo "Step 3: Deploying Vault StatefulSet..."
kubectl apply -f vault-deployment.yaml
echo "✓ Vault deployed"
echo ""

echo "Waiting for Vault pod to be ready (this may take 1-2 minutes)..."
kubectl wait --for=condition=ready pod -l app=vault -n $NAMESPACE --timeout=300s || true

echo ""
echo "================================================"
echo "Vault Deployed Successfully!"
echo "================================================"
echo ""
echo "⚠️  IMPORTANT: Vault is SEALED and needs initialization"
echo ""
echo "Next steps:"
echo "  1. Run: ./02-init-vault.sh"
echo "  2. Save the unseal keys and root token securely!"
echo ""
echo "Check Vault status:"
echo "  kubectl get pods -n $NAMESPACE -l app=vault"
echo "  kubectl logs -n $NAMESPACE -l app=vault"
echo ""
