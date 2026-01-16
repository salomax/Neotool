# Cloudflare R2 Integration
# This file provides additional resources for Cloudflare R2 integration
# Used by Loki and other services requiring S3-compatible storage

# Additional ConfigMap for Loki S3 configuration
resource "kubernetes_config_map" "loki_s3_config" {
  count = var.provider_type == "cloudflare-r2" ? 1 : 0

  metadata {
    name      = "loki-s3-config"
    namespace = "neotool-observability"
  }

  data = {
    endpoint        = var.cloudflare_r2_endpoint != "" ? var.cloudflare_r2_endpoint : "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com"
    bucket          = "${var.cloudflare_r2_bucket}-loki"
    region          = "auto"
    access_key_id   = var.cloudflare_r2_access_key_id
    secret_access_key = var.cloudflare_r2_secret_access_key
    s3_force_path_style = "true"
  }
}

# Secret for Loki S3 credentials
resource "kubernetes_secret" "loki_s3_credentials" {
  count = var.provider_type == "cloudflare-r2" ? 1 : 0

  metadata {
    name      = "loki-s3-credentials"
    namespace = "neotool-observability"
  }

  data = {
    access-key-id     = base64encode(var.cloudflare_r2_access_key_id)
    secret-access-key = base64encode(var.cloudflare_r2_secret_access_key)
  }

  type = "Opaque"
}

