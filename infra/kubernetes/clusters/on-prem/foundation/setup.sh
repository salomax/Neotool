#!/bin/bash
set -e

# On-Premises Kubernetes Foundation Setup
# For full Kubernetes installations (kubeadm, RKE, etc.)

KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
export KUBECONFIG

ENVIRONMENT="${1:-production}"

echo "================================================"
echo "On-Premises Kubernetes Foundation Setup"
echo "================================================"
echo ""
echo "Cluster Type: Full Kubernetes (on-premises)"
echo "Environment: $ENVIRONMENT"
echo ""

# Check prerequisites
echo "Prerequisites Check:"
echo ""

# Check kubectl
if ! command -v kubectl &>/dev/null; then
    echo "❌ kubectl not found - please install it first"
    echo "Visit: https://kubernetes.io/docs/tasks/tools/"
    exit 1
fi
echo "✓ kubectl found ($(kubectl version --client --short 2>/dev/null || kubectl version --client))"

# Check kubeconfig
if [ ! -f "$KUBECONFIG" ]; then
    echo "❌ Kubeconfig not found at: $KUBECONFIG"
    exit 1
fi
echo "✓ Kubeconfig found"
echo ""

# Check cluster connection
echo "Step 1: Checking cluster connection..."
if ! kubectl cluster-info &>/dev/null; then
    echo "❌ Error: Cannot connect to cluster"
    exit 1
fi
echo "✓ Connected to Kubernetes cluster"
kubectl get nodes
echo ""

# Create namespace
echo "Step 2: Creating $ENVIRONMENT namespace..."
kubectl create namespace $ENVIRONMENT --dry-run=client -o yaml | kubectl apply -f -
echo "✓ Namespace created"
echo ""

# Install MetalLB (LoadBalancer for bare metal)
echo "Step 3: Installing MetalLB (LoadBalancer)..."
if kubectl get namespace metallb-system &>/dev/null; then
    echo "✓ MetalLB already installed"
else
    kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.13.12/config/manifests/metallb-native.yaml
    echo "✓ MetalLB installed"
    echo "⚠️  You need to configure MetalLB IP pool manually!"
fi
echo ""

# Install Nginx Ingress Controller (instead of Traefik)
echo "Step 4: Installing Nginx Ingress Controller..."
if kubectl get namespace ingress-nginx &>/dev/null; then
    echo "✓ Nginx Ingress already installed"
else
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.9.5/deploy/static/provider/baremetal/deploy.yaml
    echo "✓ Nginx Ingress installed"
fi
echo ""

# Install Cert-Manager
echo "Step 5: Installing Cert-Manager..."
if kubectl get namespace cert-manager &>/dev/null; then
    echo "✓ Cert-Manager already installed"
else
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.0/cert-manager.yaml
    kubectl wait --for=condition=available --timeout=300s deployment/cert-manager -n cert-manager 2>/dev/null || true
    echo "✓ Cert-Manager installed"
fi
echo ""

# Install Linkerd
echo "Step 6: Installing Linkerd service mesh..."
if kubectl get namespace linkerd &>/dev/null; then
    echo "✓ Linkerd already installed"
else
    if command -v linkerd &>/dev/null; then
        linkerd install --crds | kubectl apply -f -
        linkerd install | kubectl apply -f -
        echo "✓ Linkerd installed"
    else
        echo "⚠️  Linkerd CLI not found. Install: curl -sfL https://run.linkerd.io/install | sh"
    fi
fi
echo ""

echo "================================================"
echo "On-Premises Kubernetes Foundation Complete!"
echo "================================================"
echo ""
echo "✓ Environment: $ENVIRONMENT"
echo "✓ LoadBalancer: MetalLB"
echo "✓ Ingress: Nginx Ingress Controller"
echo "✓ TLS: Cert-Manager"
echo "✓ Service Mesh: Linkerd"
echo ""
