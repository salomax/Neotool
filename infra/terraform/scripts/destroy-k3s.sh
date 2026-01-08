#!/bin/bash
# K3S Cluster Destruction Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$(cd "$SCRIPT_DIR/../environments" && pwd)"
ENVIRONMENT="${1:-local}"

echo "Destroying K3S cluster for environment: $ENVIRONMENT"

cd "$TERRAFORM_DIR/$ENVIRONMENT"

# Check if Terraform is installed
if ! command -v terraform &> /dev/null; then
    echo "Error: Terraform is not installed"
    exit 1
fi

# Confirm destruction
read -p "Are you sure you want to destroy the K3S cluster? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Destruction cancelled"
    exit 0
fi

# Destroy the infrastructure
echo "Destroying infrastructure..."
terraform destroy

echo "K3S cluster destroyed successfully!"

