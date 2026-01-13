#!/bin/bash
set -e

# K3S Foundation Setup Script
# This script sets up the foundational components for the Neotool K3S cluster

KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config-hostinger}"
export KUBECONFIG

echo "================================================"
echo "Neotool K3S Foundation Setup"
echo "================================================"
echo ""

# Check cluster connection
echo "1. Checking cluster connection..."
if ! kubectl cluster-info &>/dev/null; then
    echo "❌ Error: Cannot connect to cluster"
    echo "   Make sure KUBECONFIG is set: export KUBECONFIG=~/.kube/config-hostinger"
    exit 1
fi
echo "✓ Connected to cluster"
echo ""

# Display cluster info
echo "2. Cluster Information:"
kubectl get nodes
echo ""

# Apply namespaces
echo "3. Creating namespaces..."
kubectl apply -f ../base/namespaces/
echo "✓ Namespaces created"
echo ""

# Check storage class
echo "4. Checking storage class..."
kubectl get storageclass
echo ""

# Check if Traefik is running (default K3S ingress)
echo "5. Checking ingress controller..."
kubectl get pods -n kube-system | grep traefik || echo "Note: Traefik not found, you may want to install an ingress controller"
echo ""

echo "================================================"
echo "Foundation Setup Complete!"
echo "================================================"
echo ""
echo "Next steps:"
echo "  1. Install cert-manager (optional, for TLS):"
echo "     kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.0/cert-manager.yaml"
echo ""
echo "  2. Deploy Vault (secrets management):"
echo "     kubectl apply -k ../base/"
echo ""
echo "  3. Check the deployment guide:"
echo "     cat ../DEPLOYMENT.md"
echo ""
