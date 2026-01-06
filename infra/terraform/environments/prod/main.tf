# Production Environment Configuration
# K3S cluster in VPC with 8 CPU / 32GB RAM nodes
# Cloudflare R2 for production storage

terraform {
  required_version = ">= 1.0"
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}

# Variables
variable "cluster_name" {
  description = "Name of the K3S cluster"
  type        = string
  default     = "neotool-prod"
}

variable "node_count" {
  description = "Number of nodes in the cluster"
  type        = number
  default     = 3
}

variable "node_cpu" {
  description = "CPU count per node"
  type        = number
  default     = 8
}

variable "node_memory_gb" {
  description = "Memory in GB per node"
  type        = number
  default     = 32
}

variable "provider_type" {
  description = "Cloud provider: aws, gcp, azure"
  type        = string
  default     = "aws"
}

variable "region" {
  description = "Cloud provider region"
  type        = string
  default     = "us-east-1"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

# Cloudflare R2 configuration
variable "cloudflare_account_id" {
  description = "Cloudflare account ID for R2"
  type        = string
  default     = ""
}

variable "cloudflare_r2_bucket" {
  description = "Cloudflare R2 bucket name"
  type        = string
  default     = "neotool-prod"
}

variable "cloudflare_r2_access_key_id" {
  description = "Cloudflare R2 access key ID"
  type        = string
  sensitive   = true
  default     = ""
}

variable "cloudflare_r2_secret_access_key" {
  description = "Cloudflare R2 secret access key"
  type        = string
  sensitive   = true
  default     = ""
}

# Networking module
module "networking" {
  source = "../../modules/networking"

  provider_type = var.provider_type
  vpc_name      = "${var.cluster_name}-vpc"
  vpc_cidr      = var.vpc_cidr
  region        = var.region

  public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  private_subnet_cidrs = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]

  tags = {
    Environment = "production"
    ManagedBy   = "terraform"
    Cluster     = var.cluster_name
  }
}

# K3S cluster module
module "k3s" {
  source = "../../modules/k3s"

  cluster_name = var.cluster_name
  node_count   = var.node_count
  k3s_version  = "latest"

  server_flags = [
    "--disable=traefik", # Use custom ingress if needed
  ]

  depends_on = [module.networking]
}

# Storage module with Cloudflare R2
module "storage" {
  source = "../../modules/storage"

  provider_type = "cloudflare-r2"

  storage_class_name = "cloudflare-r2"

  cloudflare_account_id        = var.cloudflare_account_id
  cloudflare_r2_bucket         = var.cloudflare_r2_bucket
  cloudflare_r2_access_key_id  = var.cloudflare_r2_access_key_id
  cloudflare_r2_secret_access_key = var.cloudflare_r2_secret_access_key
  cloudflare_r2_endpoint        = var.cloudflare_account_id != "" ? "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com" : ""

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

output "vpc_id" {
  description = "VPC ID"
  value       = module.networking.vpc_id
}

output "storage_class_name" {
  description = "Storage class name for production"
  value       = module.storage.storage_class_name
}

