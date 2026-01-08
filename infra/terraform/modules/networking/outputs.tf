output "vpc_id" {
  description = "VPC ID (AWS) or VPC name (GCP/Azure). For VPS, returns null as Hostinger manages networking."
  value = var.provider_type == "aws" ? (length(aws_vpc.main) > 0 ? aws_vpc.main[0].id : null) : (
    var.provider_type == "gcp" ? (length(google_compute_network.main) > 0 ? google_compute_network.main[0].id : null) : (
      var.provider_type == "azure" ? (length(azurerm_virtual_network.main) > 0 ? azurerm_virtual_network.main[0].id : null) : (
        var.provider_type == "vps" ? null : null
      )
    )
  )
}

output "public_subnet_ids" {
  description = "List of public subnet IDs. For VPS, returns empty list as Hostinger manages networking."
  value = var.provider_type == "aws" ? aws_subnet.public[*].id : (
    var.provider_type == "gcp" ? google_compute_subnetwork.public[*].id : (
      var.provider_type == "azure" ? azurerm_subnet.public[*].id : (
        var.provider_type == "vps" ? [] : []
      )
    )
  )
}

output "private_subnet_ids" {
  description = "List of private subnet IDs. For VPS, returns empty list as Hostinger manages networking."
  value = var.provider_type == "aws" ? aws_subnet.private[*].id : (
    var.provider_type == "gcp" ? google_compute_subnetwork.private[*].id : (
      var.provider_type == "azure" ? azurerm_subnet.private[*].id : (
        var.provider_type == "vps" ? [] : []
      )
    )
  )
}

output "security_group_id" {
  description = "Security group ID (AWS) or name (GCP/Azure). For VPS, returns null as Hostinger manages firewall."
  value = var.provider_type == "aws" ? (length(aws_security_group.k3s) > 0 ? aws_security_group.k3s[0].id : null) : (
    var.provider_type == "gcp" ? (length(google_compute_firewall.k3s) > 0 ? google_compute_firewall.k3s[0].name : null) : (
      var.provider_type == "azure" ? (length(azurerm_network_security_group.k3s) > 0 ? azurerm_network_security_group.k3s[0].id : null) : (
        var.provider_type == "vps" ? null : null
      )
    )
  )
}

output "vpc_cidr" {
  description = "VPC CIDR block"
  value       = var.vpc_cidr
}

