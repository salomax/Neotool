# Local Development Environment Configuration
# Single-node K3S cluster for local development

terraform {
  required_version = ">= 1.0"
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
  }
}

# Variables
variable "cluster_name" {
  description = "Name of the K3S cluster"
  type        = string
  default     = "neotool-local"
}

# K3S cluster module (single node)
module "k3s" {
  source = "../../modules/k3s"

  cluster_name = var.cluster_name
  node_count   = 1
  k3s_version  = "latest"

  # Keep Traefik for local development
  disable_components = []
}

# Storage module (local-path)
module "storage" {
  source = "../../modules/storage"

  provider_type = "local"
  storage_class_name = "local-path"
  namespace = "kube-system"

  depends_on = [module.k3s]
}

# Outputs
output "cluster_name" {
  description = "K3S cluster name"
  value       = module.k3s.cluster_name
}

output "kubeconfig_path" {
  description = "Path to kubeconfig file"
  value       = module.k3s.kubeconfig_path
}

output "storage_class_name" {
  description = "Storage class name for local development"
  value       = module.storage.storage_class_name
}

