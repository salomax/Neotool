#!/bin/bash
set -e

# Flux GitOps Bootstrap Script

echo "================================================"
echo "  Flux GitOps Bootstrap"
echo "================================================"
echo ""

# Check prerequisites
if ! command -v flux &> /dev/null; then
    echo "❌ Flux CLI not installed"
    echo ""
    echo "Install it first:"
    echo "  macOS:  brew install fluxcd/tap/flux"
    echo "  Linux:  curl -s https://fluxcd.io/install.sh | sudo bash"
    echo ""
    exit 1
fi

if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl not installed"
    exit 1
fi

echo "✓ Flux CLI installed: $(flux version --client)"
echo "✓ kubectl installed"
echo ""

# Check cluster connection
if ! kubectl cluster-info &>/dev/null; then
    echo "❌ Cannot connect to cluster"
    echo "   Set KUBECONFIG: export KUBECONFIG=~/.kube/config-hostinger"
    exit 1
fi

echo "✓ Connected to cluster"
kubectl get nodes
echo ""

# Pre-flight check
echo "Running Flux pre-flight check..."
flux check --pre

echo ""
echo "================================================"
echo "  Bootstrap Options"
echo "================================================"
echo ""
echo "Choose bootstrap method:"
echo "  1. GitHub (Recommended - auto-sync from Git)"
echo "  2. GitLab"
echo "  3. Generic Git"
echo "  4. Local (no Git sync)"
echo ""
read -p "Enter choice [1-4]: " choice

case $choice in
  1)
    echo ""
    echo "GitHub Bootstrap"
    echo "================"
    echo ""
    echo "You need a GitHub Personal Access Token with 'repo' scope."
    echo "Create one at: https://github.com/settings/tokens"
    echo ""
    read -p "GitHub username: " GITHUB_USER
    read -p "Repository name (e.g., Neotool): " GITHUB_REPO
    read -sp "GitHub token (hidden): " GITHUB_TOKEN
    echo ""
    echo ""

    export GITHUB_TOKEN

    flux bootstrap github \
      --owner="$GITHUB_USER" \
      --repository="$GITHUB_REPO" \
      --branch=main \
      --path=infra/kubernetes/flux/clusters/production \
      --personal \
      --components-extra=image-reflector-controller,image-automation-controller

    ;;

  2)
    echo ""
    echo "GitLab bootstrap - implement if needed"
    exit 1
    ;;

  3)
    echo ""
    echo "Generic Git bootstrap - implement if needed"
    exit 1
    ;;

  4)
    echo ""
    echo "Local Bootstrap (No Git)"
    echo "========================"
    echo ""
    echo "This installs Flux without Git sync."
    echo "⚠️  Not recommended for production!"
    echo ""

    flux install \
      --components-extra=image-reflector-controller,image-automation-controller

    echo ""
    echo "Flux installed locally."
    echo "You'll need to apply resources manually:"
    echo "  kubectl apply -k flux/infrastructure"
    ;;

  *)
    echo "Invalid choice"
    exit 1
    ;;
esac

echo ""
echo "================================================"
echo "  Bootstrap Complete!"
echo "================================================"
echo ""
echo "Verify installation:"
echo "  flux check"
echo "  flux get all"
echo ""
echo "Watch reconciliation:"
echo "  flux get kustomizations --watch"
echo ""
echo "Next steps:"
echo "  1. Vault needs initialization (one-time)"
echo "  2. Store PostgreSQL credentials in Vault"
echo "  3. Push app manifests to Git"
echo ""
