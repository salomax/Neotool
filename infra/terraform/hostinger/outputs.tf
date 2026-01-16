output "vps_ip" {
  description = "IP address of the configured VPS"
  value       = var.vps_ip
}

output "setup_completed" {
  description = "Indicates that the VPS setup has been completed"
  value       = null_resource.setup_vps.id != null ? "completed" : "pending"
}

# K3S Outputs
output "k3s_installed" {
  description = "Indicates that K3S has been installed"
  value       = null_resource.k3s_install.id != null ? "installed" : "pending"
}

output "kubeconfig_path" {
  description = "Local path where kubeconfig is saved"
  value       = var.kubeconfig_path
}

output "k3s_cluster_name" {
  description = "Name of the K3S cluster"
  value       = var.k3s_cluster_name
}

output "k3s_version" {
  description = "K3S version installed"
  value       = var.k3s_version
}

output "k3s_api_endpoint" {
  description = "K3S API endpoint URL"
  value       = "https://${var.vps_ip}:6443"
}
