#!/bin/bash
set -e

# K3S-Specific Foundation Setup Script
# This script is tailored for K3S clusters with their specific features

KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config-hostinger}"
export KUBECONFIG

ENVIRONMENT="${1:-production}"

echo "================================================"
echo "K3S Foundation Setup"
echo "================================================"
echo ""
echo "Cluster Type: K3S"
echo "Environment: $ENVIRONMENT"
echo ""

# Check prerequisites
echo "Prerequisites Check:"
echo ""

# Check kubectl
if ! command -v kubectl &>/dev/null; then
    echo "❌ kubectl not found"
    echo ""
    echo "Please install kubectl first:"
    echo ""
    echo "macOS:"
    echo "  brew install kubectl"
    echo "  # or"
    echo "  curl -LO \"https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/darwin/amd64/kubectl\""
    echo "  chmod +x kubectl && sudo mv kubectl /usr/local/bin/"
    echo ""
    echo "Linux:"
    echo "  curl -LO \"https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl\""
    echo "  chmod +x kubectl && sudo mv kubectl /usr/local/bin/"
    echo ""
    exit 1
fi
echo "✓ kubectl found ($(kubectl version --client --short 2>/dev/null || kubectl version --client))"

# Check kubeconfig file exists
if [ ! -f "$KUBECONFIG" ]; then
    echo "❌ Kubeconfig not found at: $KUBECONFIG"
    echo ""
    echo "Expected kubeconfig file doesn't exist. Did you run Terraform?"
    echo "The kubeconfig should have been created by: terraform apply"
    echo ""
    echo "Check if it exists at a different location:"
    echo "  ls -la ~/.kube/"
    echo ""
    exit 1
fi
echo "✓ Kubeconfig found at: $KUBECONFIG"
echo ""

# Check cluster connection
echo "Step 1: Checking cluster connection..."
if ! kubectl cluster-info &>/dev/null; then
    echo "❌ Error: Cannot connect to cluster"
    echo ""
    echo "Possible issues:"
    echo "  1. VPS might be down or unreachable"
    echo "  2. K3S service might not be running on VPS"
    echo "  3. Firewall blocking port 6443"
    echo "  4. Wrong IP in kubeconfig"
    echo ""
    echo "Debug steps:"
    echo "  # Check if VPS is reachable"
    echo "  ping \$(grep server $KUBECONFIG | awk -F'[/:]' '{print \$4}')"
    echo ""
    echo "  # Check kubeconfig contents"
    echo "  cat $KUBECONFIG"
    echo ""
    echo "  # SSH to VPS and check K3S"
    echo "  ssh root@\$(grep server $KUBECONFIG | awk -F'[/:]' '{print \$4}') 'systemctl status k3s'"
    echo ""
    exit 1
fi
echo "✓ Connected to K3S cluster"
kubectl get nodes
echo ""

# Create environment namespace
echo "Step 2: Creating $ENVIRONMENT namespace..."
kubectl apply -f ../../environments/$ENVIRONMENT/namespace.yaml 2>/dev/null || \
kubectl create namespace $ENVIRONMENT
echo "✓ Namespace created"
echo ""

# Check storage class (K3S uses local-path by default)
echo "Step 3: Verifying K3S storage class..."
kubectl get storageclass
if ! kubectl get storageclass local-path &>/dev/null; then
    echo "⚠️  Warning: local-path storage class not found"
    echo "   This is the default K3S storage class. Something may be wrong."
else
    echo "✓ K3S local-path storage class available"
fi
echo ""

# Check Traefik ingress (K3S default)
echo "Step 4: Verifying Traefik ingress controller (K3S default)..."
if kubectl get pods -n kube-system -l app.kubernetes.io/name=traefik &>/dev/null; then
    kubectl get pods -n kube-system -l app.kubernetes.io/name=traefik
    echo "✓ Traefik is running (K3S default ingress)"
else
    echo "⚠️  Warning: Traefik not found"
    echo "   K3S should have Traefik by default. Check if it was disabled."
fi
echo ""

# Install Cert-Manager
echo "Step 5: Installing Cert-Manager for TLS..."
if kubectl get namespace cert-manager &>/dev/null; then
    echo "✓ Cert-Manager namespace already exists"
else
    echo "   Downloading cert-manager v1.14.0..."
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.0/cert-manager.yaml

    echo "   Waiting for cert-manager to be ready..."
    kubectl wait --for=condition=available --timeout=300s deployment/cert-manager -n cert-manager 2>/dev/null || true
    kubectl wait --for=condition=available --timeout=300s deployment/cert-manager-webhook -n cert-manager 2>/dev/null || true
    kubectl wait --for=condition=available --timeout=300s deployment/cert-manager-cainjector -n cert-manager 2>/dev/null || true

    echo "✓ Cert-Manager installed"
fi
echo ""

# Install Linkerd CLI (if not already installed)
echo "Step 6: Checking Linkerd CLI..."
if ! command -v linkerd &>/dev/null; then
    echo "   Installing Linkerd CLI..."
    curl -sfL https://run.linkerd.io/install | sh
    export PATH=$PATH:$HOME/.linkerd2/bin
    echo "✓ Linkerd CLI installed"
    echo "   Add to your PATH: export PATH=\$PATH:\$HOME/.linkerd2/bin"
else
    echo "✓ Linkerd CLI already installed ($(linkerd version --client --short))"
fi
echo ""

# Pre-check for Linkerd
echo "Step 7: Running Linkerd pre-installation check..."
if command -v linkerd &>/dev/null; then
    linkerd check --pre || echo "⚠️  Some pre-checks failed, but continuing..."
else
    echo "⚠️  Linkerd CLI not found. Install it manually:"
    echo "   curl -sfL https://run.linkerd.io/install | sh"
fi
echo ""

# Install Linkerd
echo "Step 8: Installing Linkerd service mesh..."
if kubectl get namespace linkerd &>/dev/null; then
    echo "✓ Linkerd already installed"
else
    if command -v linkerd &>/dev/null; then
        echo "   Installing Linkerd CRDs..."
        linkerd install --crds | kubectl apply -f -

        echo "   Installing Linkerd control plane..."
        linkerd install | kubectl apply -f -

        echo "   Waiting for Linkerd to be ready..."
        linkerd check || echo "⚠️  Linkerd check had warnings"

        echo "✓ Linkerd service mesh installed"
    else
        echo "⚠️  Skipping Linkerd installation (CLI not available)"
    fi
fi
echo ""

echo "================================================"
echo "K3S Foundation Setup Complete!"
echo "================================================"
echo ""
echo "✓ Environment namespace: $ENVIRONMENT"
echo "✓ Storage: local-path (K3S default)"
echo "✓ Ingress: Traefik (K3S default)"
echo "✓ TLS: Cert-Manager"
echo "✓ Service Mesh: Linkerd"
echo ""
echo "Next Steps:"
echo ""
echo "1. Enable Linkerd on $ENVIRONMENT namespace:"
echo "   kubectl annotate namespace $ENVIRONMENT linkerd.io/inject=enabled"
echo ""
echo "2. Deploy your applications:"
echo "   kubectl apply -k ../../environments/$ENVIRONMENT/"
echo ""
echo "3. Install Linkerd Viz (optional, for dashboard):"
echo "   linkerd viz install | kubectl apply -f -"
echo "   linkerd viz dashboard"
echo ""
