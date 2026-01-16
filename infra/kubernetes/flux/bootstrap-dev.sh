#!/bin/bash
set -e

# Flux GitOps Bootstrap for Development Branch

CURRENT_BRANCH=$(git branch --show-current)

echo "================================================"
echo "  Flux GitOps Bootstrap (Development)"
echo "================================================"
echo ""
echo "Current Git branch: $CURRENT_BRANCH"
echo ""
echo "⚠️  IMPORTANT: This will bootstrap Flux to watch your"
echo "   current development branch instead of 'main'."
echo ""
echo "This is useful for testing, but you'll need to"
echo "re-bootstrap to 'main' when ready for production."
echo ""
read -p "Continue with branch '$CURRENT_BRANCH'? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

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

echo "Bootstrapping Flux to watch branch: $CURRENT_BRANCH"
echo ""

~/.local/bin/flux bootstrap github \
  --owner="$GITHUB_USER" \
  --repository="$GITHUB_REPO" \
  --branch="$CURRENT_BRANCH" \
  --path=infra/kubernetes/flux/clusters/production \
  --personal

echo ""
echo "================================================"
echo "  Bootstrap Complete!"
echo "================================================"
echo ""
echo "Flux is now watching branch: $CURRENT_BRANCH"
echo ""
echo "Workflow:"
echo "  1. Edit files locally"
echo "  2. git add . && git commit -m 'changes'"
echo "  3. git push origin $CURRENT_BRANCH"
echo "  4. Flux auto-deploys! ✨"
echo ""
echo "Verify:"
echo "  flux get sources git"
echo "  flux get kustomizations --watch"
echo ""
echo "When ready for production:"
echo "  1. Merge to main"
echo "  2. Re-bootstrap to main branch"
echo ""
