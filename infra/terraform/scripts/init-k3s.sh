#!/bin/bash
# K3S Cluster Initialization Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$(cd "$SCRIPT_DIR/../environments" && pwd)"
ENVIRONMENT="${1:-local}"

echo "Initializing K3S cluster for environment: $ENVIRONMENT"

cd "$TERRAFORM_DIR/$ENVIRONMENT"

# Check if Terraform is installed
if ! command -v terraform &> /dev/null; then
    echo "Error: Terraform is not installed"
    exit 1
fi

# Initialize Terraform
echo "Initializing Terraform..."
terraform init

# Plan the deployment
echo "Planning Terraform deployment..."
terraform plan -out=tfplan

# Apply the deployment
echo "Applying Terraform deployment..."
terraform apply tfplan

# Get kubeconfig path
KUBECONFIG_PATH=$(terraform output -raw kubeconfig_path 2>/dev/null || echo "$HOME/.kube/config")

echo "K3S cluster initialized successfully!"
echo "Kubeconfig saved to: $KUBECONFIG_PATH"
echo ""
echo "To use the cluster, run:"
echo "  export KUBECONFIG=$KUBECONFIG_PATH"
echo "  kubectl get nodes"

