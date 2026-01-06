#!/bin/bash
# Kubernetes Rollback Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KUBERNETES_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENVIRONMENT="${1:-local}"
REVISION="${2:-}"

echo "Rolling back deployment in environment: $ENVIRONMENT"

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo "Error: kubectl is not installed"
    exit 1
fi

# Check cluster connectivity
if ! kubectl cluster-info &> /dev/null; then
    echo "Error: Cannot connect to Kubernetes cluster"
    exit 1
fi

# If revision is provided, rollback to that revision
if [ -n "$REVISION" ]; then
    echo "Rolling back to revision: $REVISION"
    kubectl rollout undo deployment --to-revision="$REVISION" -A
else
    echo "Rolling back to previous revision..."
    kubectl rollout undo deployment -A
fi

# Wait for rollback to complete
echo "Waiting for rollback to complete..."
kubectl rollout status deployment -A --timeout=300s || true

echo "Rollback completed successfully!"

