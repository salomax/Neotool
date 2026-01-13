#!/bin/bash
set -e

# AWS EKS Foundation Setup
# For Amazon Elastic Kubernetes Service

KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
export KUBECONFIG

ENVIRONMENT="${1:-production}"
AWS_REGION="${AWS_REGION:-us-east-1}"

echo "================================================"
echo "AWS EKS Foundation Setup"
echo "================================================"
echo ""
echo "Cluster Type: AWS EKS"
echo "Environment: $ENVIRONMENT"
echo "AWS Region: $AWS_REGION"
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
echo "✓ kubectl found"

# Check AWS CLI
if ! command -v aws &>/dev/null; then
    echo "❌ AWS CLI not found - please install it first"
    echo "Visit: https://aws.amazon.com/cli/"
    exit 1
fi
echo "✓ AWS CLI found"

# Check AWS credentials
if ! aws sts get-caller-identity &>/dev/null; then
    echo "❌ AWS credentials not configured"
    echo "Run: aws configure"
    exit 1
fi
echo "✓ AWS credentials configured"
echo ""

# Check cluster connection
echo "Step 1: Checking cluster connection..."
if ! kubectl cluster-info &>/dev/null; then
    echo "❌ Error: Cannot connect to cluster"
    echo "   Run: aws eks update-kubeconfig --name <cluster-name> --region $AWS_REGION"
    exit 1
fi
echo "✓ Connected to EKS cluster"
kubectl get nodes
echo ""

# Create namespace
echo "Step 2: Creating $ENVIRONMENT namespace..."
kubectl create namespace $ENVIRONMENT --dry-run=client -o yaml | kubectl apply -f -
echo "✓ Namespace created"
echo ""

# Install AWS Load Balancer Controller (replaces classic ELB)
echo "Step 3: Installing AWS Load Balancer Controller..."
if kubectl get deployment -n kube-system aws-load-balancer-controller &>/dev/null; then
    echo "✓ AWS Load Balancer Controller already installed"
else
    echo "⚠️  AWS Load Balancer Controller not installed"
    echo "   Install via: https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html"
    echo "   Or use Helm: helm install aws-load-balancer-controller eks/aws-load-balancer-controller"
fi
echo ""

# Install Cert-Manager
echo "Step 4: Installing Cert-Manager..."
if kubectl get namespace cert-manager &>/dev/null; then
    echo "✓ Cert-Manager already installed"
else
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.0/cert-manager.yaml
    kubectl wait --for=condition=available --timeout=300s deployment/cert-manager -n cert-manager 2>/dev/null || true
    echo "✓ Cert-Manager installed"
fi
echo ""

# Install Linkerd (optional - EKS has its own service mesh option with App Mesh)
echo "Step 5: Installing Linkerd service mesh..."
echo "   Note: EKS also supports AWS App Mesh as an alternative"
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
echo "AWS EKS Foundation Complete!"
echo "================================================"
echo ""
echo "✓ Environment: $ENVIRONMENT"
echo "✓ LoadBalancer: AWS Load Balancer Controller (ALB/NLB)"
echo "✓ Ingress: AWS Load Balancer Controller"
echo "✓ TLS: Cert-Manager (or ACM via ALB)"
echo "✓ Service Mesh: Linkerd (or AWS App Mesh)"
echo ""
echo "EKS-specific features:"
echo "- IAM Roles for Service Accounts (IRSA)"
echo "- EBS CSI Driver for persistent volumes"
echo "- CloudWatch Container Insights for monitoring"
echo ""
