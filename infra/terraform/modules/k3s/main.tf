# K3S Cluster Module
# Provider-agnostic K3S cluster provisioning
# Supports both local and cloud deployments

terraform {
  required_version = ">= 1.0"
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

# Local-exec provisioner for K3S installation (local only)
# For cloud deployments, use cloud-instances.tf

resource "null_resource" "k3s_install" {
  count = var.provider_type == "local" ? var.node_count : 0

  triggers = {
    cluster_name     = var.cluster_name
    node_count       = var.node_count
    k3s_version      = var.k3s_version
    server_flags     = join(" ", var.server_flags)
    agent_flags      = join(" ", var.agent_flags)
    disable_components = join(",", var.disable_components)
  }

  # Install K3S on the first node (server)
  provisioner "local-exec" {
    when    = count.index == 0 ? "create" : "destroy"
    command = <<-EOT
      if [ ! -f /usr/local/bin/k3s ]; then
        curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="${var.k3s_version}" sh -
      fi
      
      # Start K3S server
      if ! systemctl is-active --quiet k3s; then
        INSTALL_K3S_VERSION="${var.k3s_version}" \
        K3S_NODE_NAME="${var.cluster_name}-node-${count.index}" \
        ${length(var.disable_components) > 0 ? "INSTALL_K3S_EXEC=\"--disable ${join(",", var.disable_components)}\"" : ""} \
        ${join(" ", var.server_flags)} \
        sh -s - server --cluster-init
      fi
      
      # Wait for K3S to be ready
      timeout 120 bash -c 'until kubectl get nodes 2>/dev/null; do sleep 2; done'
      
      # Save kubeconfig
      mkdir -p $(dirname ${var.kubeconfig_path})
      sudo cp /etc/rancher/k3s/k3s.yaml ${var.kubeconfig_path}
      sudo chown $(id -u):$(id -g) ${var.kubeconfig_path}
      sed -i 's/127.0.0.1/localhost/g' ${var.kubeconfig_path}
    EOT
  }

  # Install K3S agents on additional nodes
  provisioner "local-exec" {
    when    = count.index > 0 ? "create" : "destroy"
    command = <<-EOT
      if [ ! -f /usr/local/bin/k3s ]; then
        curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="${var.k3s_version}" sh -
      fi
      
      # Get server token from first node
      SERVER_TOKEN=$(sudo cat /var/lib/rancher/k3s/server/node-token 2>/dev/null || echo "")
      
      if [ -n "$SERVER_TOKEN" ]; then
        INSTALL_K3S_VERSION="${var.k3s_version}" \
        K3S_NODE_NAME="${var.cluster_name}-node-${count.index}" \
        K3S_URL=https://${var.cluster_name}-node-0:6443 \
        K3S_TOKEN=$SERVER_TOKEN \
        ${join(" ", var.agent_flags)} \
        sh -s - agent
      fi
    EOT
  }

  # Cleanup on destroy
  provisioner "local-exec" {
    when    = destroy
    command = <<-EOT
      if systemctl is-active --quiet k3s; then
        if [ ${count.index} -eq 0 ]; then
          # Uninstall server
          /usr/local/bin/k3s-uninstall.sh || true
        else
          # Uninstall agent
          /usr/local/bin/k3s-agent-uninstall.sh || true
        fi
      fi
    EOT
  }
}

# Wait for cluster to be ready (local only)
resource "null_resource" "k3s_ready" {
  count = var.provider_type == "local" ? 1 : 0
  
  depends_on = [null_resource.k3s_install]

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG=${var.kubeconfig_path}
      timeout 300 bash -c 'until kubectl get nodes --no-headers 2>/dev/null | grep -q Ready; do sleep 5; done'
      echo "K3S cluster ${var.cluster_name} is ready"
    EOT
  }
}

