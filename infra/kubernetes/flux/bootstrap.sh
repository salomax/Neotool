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
echo "  1. GitHub (PAT over HTTPS)"
echo "  5. GitHub App (Recommended for org policy / no deploy keys)"
echo "  2. GitLab"
echo "  3. Generic Git"
echo "  4. Local (no Git sync)"
echo ""
read -p "Enter choice [1-5]: " choice

case $choice in
  1)
    echo ""
    echo "GitHub Bootstrap"
    echo "================"
    echo ""
    echo "You need a GitHub Personal Access Token (PAT) with repo access."
    echo "Note: This uses HTTPS auth (no deploy keys)."
    echo "Create one at: https://github.com/settings/tokens"
    echo ""
    read -p "GitHub owner/org: " GITHUB_OWNER
    read -p "Repository name (e.g., Neotool): " GITHUB_REPO
    read -p "Is this a personal repo? [y/N]: " GITHUB_PERSONAL
    read -sp "GitHub token (hidden): " GITHUB_TOKEN
    echo ""
    echo ""

    export GITHUB_TOKEN
    PERSONAL_FLAG=""
    if [[ "$GITHUB_PERSONAL" =~ ^[Yy]$ ]]; then
      PERSONAL_FLAG="--personal"
    fi

    flux bootstrap github \
      --token-auth \
      --owner="$GITHUB_OWNER" \
      --repository="$GITHUB_REPO" \
      --branch=main \
      --path=infra/kubernetes/flux/clusters/production \
      $PERSONAL_FLAG \
      --components-extra=image-reflector-controller,image-automation-controller

    ;;

  5)
    echo ""
    echo "GitHub App Bootstrap"
    echo "===================="
    echo ""
    echo "This bootstraps with a PAT, then replaces the Flux secret with GitHub App auth."
    echo "You need:"
    echo "  - GitHub App ID"
    echo "  - GitHub App Installation ID"
    echo "  - GitHub App private key file (.pem)"
    echo ""
    read -p "GitHub owner/org: " GITHUB_OWNER
    read -p "Repository name (e.g., Neotool): " GITHUB_REPO
    read -p "Is this a personal repo? [y/N]: " GITHUB_PERSONAL
    read -p "GitHub App ID: " GITHUB_APP_ID
    read -p "GitHub App Installation ID: " GITHUB_APP_INSTALLATION_ID
    read -p "GitHub App private key file path: " GITHUB_APP_PRIVATE_KEY
    read -sp "GitHub PAT (hidden, used only for bootstrap): " GITHUB_TOKEN
    echo ""
    echo ""

    export GITHUB_TOKEN
    PERSONAL_FLAG=""
    if [[ "$GITHUB_PERSONAL" =~ ^[Yy]$ ]]; then
      PERSONAL_FLAG="--personal"
    fi

    flux bootstrap github \
      --token-auth \
      --owner="$GITHUB_OWNER" \
      --repository="$GITHUB_REPO" \
      --branch=main \
      --path=infra/kubernetes/flux/clusters/production \
      $PERSONAL_FLAG \
      --components-extra=image-reflector-controller,image-automation-controller

    echo ""
    echo "Switching GitRepository auth to GitHub App..."
    flux create secret githubapp flux-system \
      --app-id="$GITHUB_APP_ID" \
      --app-installation-id="$GITHUB_APP_INSTALLATION_ID" \
      --app-private-key="$GITHUB_APP_PRIVATE_KEY" \
      -n flux-system

    echo "✓ GitHub App secret applied (flux-system)"
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
