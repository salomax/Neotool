variable "provider_type" {
  description = "Cloud provider type: local, aws, gcp, azure, vps"
  type        = string
  default     = "local"
  validation {
    condition     = contains(["local", "aws", "gcp", "azure", "vps"], var.provider_type)
    error_message = "Provider type must be one of: local, aws, gcp, azure, vps"
  }
}

variable "vpc_name" {
  description = "Name of the VPC/VNet"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for VPC (e.g., 10.0.0.0/16)"
  type        = string
  default     = "10.0.0.0/16"
}

variable "region" {
  description = "Cloud provider region"
  type        = string
  default     = "us-east-1"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = []
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = []
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

# AWS-specific variables
variable "aws_enable_nat_gateway" {
  description = "Enable NAT Gateway for AWS"
  type        = bool
  default     = true
}

# GCP-specific variables
variable "gcp_project_id" {
  description = "GCP Project ID"
  type        = string
  default     = ""
}

# Azure-specific variables
variable "azure_resource_group_name" {
  description = "Azure Resource Group name"
  type        = string
  default     = ""
}

