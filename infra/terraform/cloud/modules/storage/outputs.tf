output "storage_class_name" {
  description = "Name of the created storage class"
  value = var.storage_class_name != "" ? var.storage_class_name : (
    var.provider_type == "local" ? "local-path" : (
      var.provider_type == "aws" ? "ebs-gp3" : (
        var.provider_type == "gcp" ? "pd-ssd" : (
          var.provider_type == "azure" ? "managed-premium" : (
            var.provider_type == "cloudflare-r2" ? "cloudflare-r2" : "unknown"
          )
        )
      )
    )
  )
}

output "secret_name" {
  description = "Name of the secret for Cloudflare R2 credentials (if applicable)"
  value       = var.provider_type == "cloudflare-r2" ? (length(kubernetes_secret.cloudflare_r2) > 0 ? kubernetes_secret.cloudflare_r2[0].metadata[0].name : null) : null
}

output "config_map_name" {
  description = "Name of the config map for Cloudflare R2 configuration (if applicable)"
  value       = var.provider_type == "cloudflare-r2" ? (length(kubernetes_config_map.cloudflare_r2) > 0 ? kubernetes_config_map.cloudflare_r2[0].metadata[0].name : null) : null
}

