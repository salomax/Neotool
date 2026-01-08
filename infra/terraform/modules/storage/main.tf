# Multi-Cloud Storage Module
# Supports local-path, AWS EBS, GCP Persistent Disk, Azure Managed Disk, and Cloudflare R2

terraform {
  required_version = ">= 1.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}

# Local-path storage class (K3S default)
resource "kubernetes_storage_class" "local_path" {
  count = var.provider_type == "local" ? 1 : 0

  metadata {
    name = var.storage_class_name != "" ? var.storage_class_name : "local-path"
  }

  storage_provisioner    = "rancher.io/local-path"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true
}

# AWS EBS storage class
resource "kubernetes_storage_class" "aws_ebs" {
  count = var.provider_type == "aws" ? 1 : 0

  metadata {
    name = var.storage_class_name != "" ? var.storage_class_name : "ebs-gp3"
  }

  storage_provisioner    = "ebs.csi.aws.com"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    type      = var.aws_volume_type
    encrypted = "true"
  }
}

# GCP Persistent Disk storage class
resource "kubernetes_storage_class" "gcp_pd" {
  count = var.provider_type == "gcp" ? 1 : 0

  metadata {
    name = var.storage_class_name != "" ? var.storage_class_name : "pd-ssd"
  }

  storage_provisioner    = "pd.csi.storage.gke.io"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    type = var.gcp_volume_type
  }
}

# Azure Managed Disk storage class
resource "kubernetes_storage_class" "azure_managed" {
  count = var.provider_type == "azure" ? 1 : 0

  metadata {
    name = var.storage_class_name != "" ? var.storage_class_name : "managed-premium"
  }

  storage_provisioner    = "disk.csi.azure.com"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    skuname = var.azure_storage_account_type
  }
}

# Cloudflare R2 S3-compatible storage
# This creates a Kubernetes Secret for R2 credentials and a ConfigMap for S3 configuration
resource "kubernetes_secret" "cloudflare_r2" {
  count = var.provider_type == "cloudflare-r2" ? 1 : 0

  metadata {
    name      = "cloudflare-r2-credentials"
    namespace = var.namespace
  }

  data = {
    access-key-id     = var.cloudflare_r2_access_key_id
    secret-access-key = var.cloudflare_r2_secret_access_key
  }

  type = "Opaque"
}

resource "kubernetes_config_map" "cloudflare_r2" {
  count = var.provider_type == "cloudflare-r2" ? 1 : 0

  metadata {
    name      = "cloudflare-r2-config"
    namespace = var.namespace
  }

  data = {
    endpoint = var.cloudflare_r2_endpoint != "" ? var.cloudflare_r2_endpoint : "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com"
    bucket   = var.cloudflare_r2_bucket
    region   = "auto"
  }
}

# Storage class for Cloudflare R2 (using S3 CSI driver)
# Note: This requires the S3 CSI driver to be installed in the cluster
resource "kubernetes_storage_class" "cloudflare_r2" {
  count = var.provider_type == "cloudflare-r2" ? 1 : 0

  metadata {
    name = var.storage_class_name != "" ? var.storage_class_name : "cloudflare-r2"
  }

  storage_provisioner    = "s3.csi.aws.com"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = false

  parameters = {
    bucket    = var.cloudflare_r2_bucket
    endpoint  = var.cloudflare_r2_endpoint != "" ? var.cloudflare_r2_endpoint : "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com"
    region    = "auto"
    mounter   = "s3fs"
    usePathStyle = "true"
  }

  secret_ref = {
    name      = kubernetes_secret.cloudflare_r2[0].metadata[0].name
    namespace = var.namespace
  }
}

