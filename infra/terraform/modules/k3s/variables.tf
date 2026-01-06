variable "cluster_name" {
  description = "Name of the K3S cluster"
  type        = string
}

variable "node_count" {
  description = "Number of nodes in the cluster (1 for single-node, 3+ for HA)"
  type        = number
  default     = 1
}

variable "disable_components" {
  description = "List of K3S components to disable"
  type        = list(string)
  default     = []
}

variable "k3s_version" {
  description = "K3S version to install"
  type        = string
  default     = "latest"
}

variable "node_labels" {
  description = "Labels to apply to nodes"
  type        = map(string)
  default     = {}
}

variable "node_taints" {
  description = "Taints to apply to nodes"
  type        = list(string)
  default     = []
}

variable "kubeconfig_path" {
  description = "Path to save kubeconfig file"
  type        = string
  default     = "~/.kube/config"
}

variable "server_flags" {
  description = "Additional K3S server flags"
  type        = list(string)
  default     = []
}

variable "agent_flags" {
  description = "Additional K3S agent flags"
  type        = list(string)
  default     = []
}

# Cloud provisioning variables
variable "provider_type" {
  description = "Cloud provider type: local, aws, gcp, azure"
  type        = string
  default     = "local"
}

variable "vpc_id" {
  description = "VPC ID for cloud deployments"
  type        = string
  default     = ""
}

variable "subnet_ids" {
  description = "List of subnet IDs for cloud deployments"
  type        = list(string)
  default     = []
}

variable "security_group_ids" {
  description = "List of security group IDs for cloud deployments (AWS)"
  type        = list(string)
  default     = []
}

variable "instance_type" {
  description = "Cloud instance type (e.g., t3.large for AWS, n1-standard-2 for GCP)"
  type        = string
  default     = ""
}

variable "ssh_key_name" {
  description = "SSH key name for cloud instances"
  type        = string
  default     = ""
}

variable "ssh_private_key_path" {
  description = "Path to SSH private key for remote-exec"
  type        = string
  default     = ""
  sensitive   = true
}

variable "region" {
  description = "Cloud provider region"
  type        = string
  default     = ""
}

variable "k3s_token" {
  description = "K3S token for joining agents (auto-generated if not provided)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "aws_ami_id" {
  description = "AWS AMI ID (auto-detected if not provided)"
  type        = string
  default     = ""
}

variable "gcp_image" {
  description = "GCP image (auto-detected if not provided)"
  type        = string
  default     = ""
}

variable "gcp_zones" {
  description = "GCP zones for instances"
  type        = list(string)
  default     = []
}

variable "gcp_project_id" {
  description = "GCP Project ID"
  type        = string
  default     = ""
}

variable "azure_resource_group_name" {
  description = "Azure Resource Group name"
  type        = string
  default     = ""
}

variable "ssh_public_key" {
  description = "SSH public key for cloud instances"
  type        = string
  default     = ""
}

