variable "vps_ip" {
  type        = string
  description = "IP address of the existing VPS"
}

variable "vps_user" {
  type        = string
  description = "SSH user for the VPS (usually 'root')"
}

variable "private_key_path" {
  type        = string
  description = "Path to the private SSH key file"
}

variable "ssh_port" {
  type        = number
  description = "SSH port (usually 22)"
}

variable "setup_commands" {
  type        = list(string)
  description = "List of shell commands to run on the VPS for initial setup"
}

variable "trigger_on_change" {
  type        = string
  description = "Value to trigger re-provisioning when changed"
}

# K3S Configuration Variables
variable "k3s_version" {
  type        = string
  description = "K3S version to install (leave empty for stable release, or specify like 'v1.28.5+k3s1')"
  default     = ""
}

variable "k3s_cluster_name" {
  type        = string
  description = "Name of the K3S cluster"
  default     = "neotool-hostinger"
}

variable "k3s_token" {
  type        = string
  description = "K3S cluster token (auto-generated if not provided)"
  default     = ""
  sensitive   = true
}

variable "k3s_disable_components" {
  type        = list(string)
  description = "List of K3S components to disable (e.g., ['traefik'])"
  default     = []
}

variable "k3s_server_flags" {
  type        = list(string)
  description = "Additional K3S server flags"
  default     = []
}

variable "kubeconfig_path" {
  type        = string
  description = "Local path where kubeconfig will be saved"
  default     = "~/.kube/config-hostinger"
}
