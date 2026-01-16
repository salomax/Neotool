variable "provider_type" {
  description = "Storage provider: local, aws, gcp, azure, cloudflare-r2"
  type        = string
  default     = "local"
  validation {
    condition     = contains(["local", "aws", "gcp", "azure", "cloudflare-r2"], var.provider_type)
    error_message = "Provider type must be one of: local, aws, gcp, azure, cloudflare-r2"
  }
}

variable "storage_class_name" {
  description = "Name of the Kubernetes storage class"
  type        = string
  default     = ""
}

variable "region" {
  description = "Cloud provider region"
  type        = string
  default     = "us-east-1"
}

# Cloudflare R2 variables
variable "cloudflare_account_id" {
  description = "Cloudflare account ID for R2"
  type        = string
  default     = ""
}

variable "cloudflare_r2_bucket" {
  description = "Cloudflare R2 bucket name"
  type        = string
  default     = ""
}

variable "cloudflare_r2_access_key_id" {
  description = "Cloudflare R2 access key ID"
  type        = string
  default     = ""
  sensitive   = true
}

variable "cloudflare_r2_secret_access_key" {
  description = "Cloudflare R2 secret access key"
  type        = string
  default     = ""
  sensitive   = true
}

variable "cloudflare_r2_endpoint" {
  description = "Cloudflare R2 endpoint URL"
  type        = string
  default     = ""
}

# AWS variables
variable "aws_volume_type" {
  description = "AWS EBS volume type (gp3, gp2, io1, etc.)"
  type        = string
  default     = "gp3"
}

variable "aws_fsx_enabled" {
  description = "Enable AWS FSx for shared storage"
  type        = bool
  default     = false
}

# GCP variables
variable "gcp_project_id" {
  description = "GCP Project ID"
  type        = string
  default     = ""
}

variable "gcp_volume_type" {
  description = "GCP persistent disk type (pd-standard, pd-ssd, pd-balanced)"
  type        = string
  default     = "pd-ssd"
}

# Azure variables
variable "azure_storage_account_type" {
  description = "Azure storage account type (Premium_LRS, Standard_LRS, etc.)"
  type        = string
  default     = "Premium_LRS"
}

variable "namespace" {
  description = "Kubernetes namespace for storage class"
  type        = string
  default     = "kube-system"
}

