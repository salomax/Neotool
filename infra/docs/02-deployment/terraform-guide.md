# Terraform Guide

This guide covers Terraform usage for Neotool infrastructure.

## Overview

Terraform manages:
- K3S cluster provisioning
- VPC and networking resources
- Storage classes and Cloudflare R2
- Environment-specific configurations

## Directory Structure

```
infra/terraform/
├── modules/
│   ├── k3s/          # K3S cluster module
│   ├── networking/   # VPC and networking
│   └── storage/      # Storage classes
├── environments/
│   ├── local/        # Local development
│   └── prod/         # Production VPC
└── scripts/          # Helper scripts
```

## Modules

### K3S Module

Provider-agnostic K3S cluster provisioning:

```hcl
module "k3s" {
  source = "../../modules/k3s"
  
  cluster_name = "neotool-local"
  node_count   = 1
  k3s_version  = "latest"
}
```

### Networking Module

Multi-cloud VPC abstraction:

```hcl
module "networking" {
  source = "../../modules/networking"
  
  provider_type = "aws"  # or gcp, azure, local
  vpc_name      = "neotool-vpc"
  vpc_cidr      = "10.0.0.0/16"
  region        = "us-east-1"
}
```

### Storage Module

Storage class management:

```hcl
module "storage" {
  source = "../../modules/storage"
  
  provider_type = "cloudflare-r2"
  storage_class_name = "cloudflare-r2"
  
  cloudflare_account_id = "your-account-id"
  cloudflare_r2_bucket  = "neotool-prod"
}
```

## Environments

### Local Environment

Single-node K3S for development:

```bash
cd infra/terraform/environments/local
terraform init
terraform plan
terraform apply
```

### Production Environment

Multi-node K3S in VPC:

```bash
cd infra/terraform/environments/prod
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars
terraform init
terraform plan
terraform apply
```

## Variables

### Required Variables

- `cluster_name`: Name of the K3S cluster
- `provider_type`: Cloud provider (aws, gcp, azure, local)

### Production Variables

- `node_count`: Number of nodes (default: 3)
- `node_cpu`: CPU per node (default: 8)
- `node_memory_gb`: Memory per node (default: 32)
- `cloudflare_account_id`: Cloudflare account ID
- `cloudflare_r2_bucket`: R2 bucket name

## Outputs

### K3S Module Outputs

- `cluster_name`: Cluster name
- `kubeconfig_path`: Path to kubeconfig
- `cluster_endpoint`: API endpoint

### Networking Module Outputs

- `vpc_id`: VPC identifier
- `public_subnet_ids`: Public subnet IDs
- `private_subnet_ids`: Private subnet IDs

### Storage Module Outputs

- `storage_class_name`: Storage class name
- `secret_name`: Secret name (for R2)

## Usage

### Initialize Terraform

```bash
terraform init
```

### Plan Changes

```bash
terraform plan
```

### Apply Changes

```bash
terraform apply
```

### Destroy Infrastructure

```bash
terraform destroy
```

## Helper Scripts

### Initialize K3S

```bash
./infra/terraform/scripts/init-k3s.sh <environment>
```

### Destroy K3S

```bash
./infra/terraform/scripts/destroy-k3s.sh <environment>
```

## State Management

### Backend Configuration

For production, configure remote state:

```hcl
terraform {
  backend "s3" {
    bucket = "neotool-terraform-state"
    key    = "prod/terraform.tfstate"
    region = "us-east-1"
  }
}
```

### State Locking

Use DynamoDB for state locking (AWS):

```hcl
terraform {
  backend "s3" {
    dynamodb_table = "terraform-state-lock"
    encrypt        = true
  }
}
```

## Best Practices

### Version Pinning

Pin provider versions:

```hcl
terraform {
  required_version = ">= 1.0"
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
  }
}
```

### Variable Validation

Validate input variables:

```hcl
variable "provider_type" {
  validation {
    condition     = contains(["aws", "gcp", "azure"], var.provider_type)
    error_message = "Provider must be aws, gcp, or azure"
  }
}
```

### Output Values

Use outputs for integration:

```hcl
output "kubeconfig_path" {
  description = "Path to kubeconfig"
  value       = module.k3s.kubeconfig_path
  sensitive   = false
}
```

## Troubleshooting

### State Lock Issues

```bash
# Force unlock (use with caution)
terraform force-unlock <lock-id>
```

### Provider Errors

```bash
# Reinitialize providers
terraform init -upgrade
```

### Module Errors

```bash
# Update module sources
terraform get -update
```

## References

- [Terraform Documentation](https://www.terraform.io/docs)
- [Terraform Best Practices](https://www.terraform.io/docs/cloud/guides/recommended-practices/index.html)
- [K3S Terraform Provider](https://registry.terraform.io/providers/rancher/k3s)

