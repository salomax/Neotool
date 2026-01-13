#!/bin/bash
set -e

# Apply Flux manifests locally (without GitOps)

echo "================================================"
echo "  Apply Flux Manifests Locally"
echo "================================================"
echo ""
echo "This applies your Flux manifests directly to the cluster"
echo "without GitOps (no Git sync)."
echo ""
echo "Good for: Development and testing"
echo "Not for: Production (use bootstrap for production)"
echo ""

FLUX_DIR="/Users/salomax/src/Neotool/infra/kubernetes/flux"

echo "Step 1: Apply namespace"
echo "========================"
kubectl apply -f "$FLUX_DIR/infrastructure/namespace.yaml"
echo ""

echo "Step 2: Apply Helm sources"
echo "=========================="
kubectl apply -k "$FLUX_DIR/infrastructure/sources"
echo ""

echo "Waiting for sources to be ready..."
sleep 5

echo ""
echo "Step 3: Apply Vault"
echo "==================="
kubectl apply -k "$FLUX_DIR/infrastructure/vault"
echo ""

echo "Waiting for Vault to deploy..."
echo "(This may take 1-2 minutes)"
echo ""

# Wait for Vault pod
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=vault \
  -n production \
  --timeout=120s 2>/dev/null || echo "⚠️  Vault pod not ready yet (check manually)"

echo ""
echo "Step 4: Apply External Secrets Operator"
echo "========================================"
kubectl apply -k "$FLUX_DIR/infrastructure/external-secrets"
echo ""

echo "Waiting for External Secrets Operator..."
sleep 10

echo ""
echo "================================================"
echo "  Infrastructure Deployed!"
echo "================================================"
echo ""

echo "Check status:"
echo "  kubectl get pods -n production"
echo "  kubectl get helmrelease -A"
echo ""

echo "Next steps:"
echo "  1. Initialize Vault: ./vault-init.sh"
echo "  2. Unseal Vault: ./vault-unseal.sh"
echo "  3. Configure Vault: ./vault-configure.sh"
echo "  4. Store credentials: ./vault-store-postgres.sh"
echo ""

echo "Current status:"
kubectl get pods -n production 2>/dev/null || echo "No pods yet in production namespace"
