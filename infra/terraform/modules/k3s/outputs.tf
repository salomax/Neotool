output "cluster_name" {
  description = "Name of the K3S cluster"
  value       = var.cluster_name
}

output "kubeconfig_path" {
  description = "Path to kubeconfig file"
  value       = var.kubeconfig_path
}

output "cluster_endpoint" {
  description = "K3S cluster API endpoint"
  value = var.provider_type == "local" ? "https://localhost:6443" : (
    var.provider_type == "aws" ? format("https://%s:6443", coalesce(aws_instance.k3s_nodes[0].public_ip, aws_instance.k3s_nodes[0].private_ip)) : (
    var.provider_type == "gcp" ? format("https://%s:6443", google_compute_instance.k3s_nodes[0].network_interface[0].access_config[0].nat_ip != "" ? google_compute_instance.k3s_nodes[0].network_interface[0].access_config[0].nat_ip : google_compute_instance.k3s_nodes[0].network_interface[0].network_ip) : (
    var.provider_type == "azure" ? format("https://%s:6443", azurerm_network_interface.k3s_nodes[0].private_ip_address) : ""
  )))
}

output "node_count" {
  description = "Number of nodes in the cluster"
  value       = var.node_count
}
