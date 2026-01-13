provider "null" {}

# Generate K3S token if not provided
resource "random_password" "k3s_token" {
  count   = var.k3s_token == "" ? 1 : 0
  length  = 64
  special = false
}

locals {
  k3s_token_final = var.k3s_token != "" ? var.k3s_token : random_password.k3s_token[0].result
  k3s_disable_flag = length(var.k3s_disable_components) > 0 ? "--disable ${join(",", var.k3s_disable_components)}" : ""
  k3s_server_flags_joined = join(" ", var.k3s_server_flags)
  # Expand ~ to home directory for kubeconfig path
  kubeconfig_path_expanded = startswith(var.kubeconfig_path, "~") ? replace(var.kubeconfig_path, "~", "$HOME") : var.kubeconfig_path
}

resource "null_resource" "setup_vps" {
  triggers = {
    # Re-run when trigger value changes
    trigger = var.trigger_on_change
    
    # Re-run when commands change
    commands = join("\n", var.setup_commands)
    
    # Re-run when connection details change
    vps_ip = var.vps_ip
  }

  connection {
    type        = "ssh"
    host        = var.vps_ip
    user        = var.vps_user
    private_key = file(var.private_key_path)
    port        = var.ssh_port
    timeout     = "5m"
  }

  # Run setup commands
  provisioner "remote-exec" {
    inline = var.setup_commands
  }
}

# K3S Installation Resource
resource "null_resource" "k3s_install" {
  depends_on = [null_resource.setup_vps]

  triggers = {
    k3s_version         = var.k3s_version
    k3s_cluster_name    = var.k3s_cluster_name
    k3s_token           = local.k3s_token_final
    disable_components  = join(",", var.k3s_disable_components)
    server_flags        = local.k3s_server_flags_joined
    vps_ip              = var.vps_ip
  }

  connection {
    type        = "ssh"
    host        = var.vps_ip
    user        = var.vps_user
    private_key = file(var.private_key_path)
    port        = var.ssh_port
    timeout     = "10m"
  }

  provisioner "remote-exec" {
    inline = concat(
      [
        "set -e",
        "# Check if K3S is already installed",
        "if systemctl is-active --quiet k3s 2>/dev/null || [ -f /usr/local/bin/k3s ]; then",
        "  echo 'K3S is already installed, skipping installation'",
        "  exit 0",
        "fi",
        "# Update system and install prerequisites",
        "export DEBIAN_FRONTEND=noninteractive",
        "apt-get update",
        "apt-get install -y curl",
        "# Prepare K3S installation command",
      ],
      var.k3s_version != "" ? ["export INSTALL_K3S_VERSION='${var.k3s_version}'"] : [],
      [
        "export K3S_NODE_NAME='${var.k3s_cluster_name}-node-0'",
        "export K3S_TOKEN='${nonsensitive(local.k3s_token_final)}'",
        "# Build INSTALL_K3S_EXEC with all flags",
        "INSTALL_K3S_EXEC='server --cluster-init --token '\"$K3S_TOKEN\"",
      ],
      local.k3s_disable_flag != "" ? ["INSTALL_K3S_EXEC=\"$INSTALL_K3S_EXEC ${local.k3s_disable_flag}\""] : [],
      local.k3s_server_flags_joined != "" ? ["INSTALL_K3S_EXEC=\"$INSTALL_K3S_EXEC ${local.k3s_server_flags_joined}\""] : [],
      [
        "export INSTALL_K3S_EXEC",
        "# Install K3S",
        "echo 'Installing K3S...'",
        "echo \"Install command: $INSTALL_K3S_EXEC\"",
        "curl -sfL https://get.k3s.io | sh -",
        "# Wait for K3S service to be active",
        "echo 'Waiting for K3S service to start...'",
        "timeout 300 bash -c 'until systemctl is-active --quiet k3s; do sleep 2; done'",
        "# Wait for kubectl to be available",
        "echo 'Waiting for kubectl to be ready...'",
        "timeout 300 bash -c 'until kubectl get nodes 2>/dev/null; do sleep 2; done'",
        "echo 'K3S installation completed successfully'"
      ]
    )
  }
}

# K3S Verification Resource
resource "null_resource" "k3s_verify" {
  depends_on = [null_resource.k3s_install]

  triggers = {
    k3s_install_id = null_resource.k3s_install.id
  }

  connection {
    type        = "ssh"
    host        = var.vps_ip
    user        = var.vps_user
    private_key = file(var.private_key_path)
    port        = var.ssh_port
    timeout     = "5m"
  }

  provisioner "remote-exec" {
    inline = [
      <<-EOT
        set -e
        
        echo "Verifying K3S cluster is ready..."
        
        # Wait for cluster to be fully ready
        timeout 300 bash -c 'until kubectl get nodes --no-headers 2>/dev/null | grep -q Ready; do sleep 5; done'
        
        # Display cluster info
        echo "K3S cluster status:"
        kubectl get nodes
        
        echo "K3S cluster is ready!"
      EOT
    ]
  }
}

# Kubeconfig Retrieval Resource
resource "null_resource" "kubeconfig_retrieve" {
  depends_on = [null_resource.k3s_verify]

  triggers = {
    k3s_verify_id = null_resource.k3s_verify.id
    vps_ip        = var.vps_ip
  }

  connection {
    type        = "ssh"
    host        = var.vps_ip
    user        = var.vps_user
    private_key = file(var.private_key_path)
    port        = var.ssh_port
    timeout     = "5m"
  }

  # Retrieve kubeconfig from VPS
  provisioner "remote-exec" {
    inline = [
      <<-EOT
        # Ensure kubeconfig exists
        if [ ! -f /etc/rancher/k3s/k3s.yaml ]; then
          echo "Error: kubeconfig not found at /etc/rancher/k3s/k3s.yaml"
          exit 1
        fi
        
        # Copy kubeconfig to a temporary location with proper permissions
        cp /etc/rancher/k3s/k3s.yaml /tmp/k3s.yaml
        chmod 644 /tmp/k3s.yaml
        
        echo "Kubeconfig ready at /tmp/k3s.yaml"
      EOT
    ]
  }

  # Copy kubeconfig to local machine
  provisioner "local-exec" {
    command = <<-EOT
      # Expand ~ to home directory
      KUBECONFIG_PATH="${local.kubeconfig_path_expanded}"
      KUBECONFIG_PATH=$(eval echo "$KUBECONFIG_PATH")
      
      # Create kubeconfig directory if it doesn't exist
      mkdir -p "$(dirname "$KUBECONFIG_PATH")"
      
      # Copy kubeconfig from VPS
      scp -i ${var.private_key_path} \
          -o StrictHostKeyChecking=no \
          -o UserKnownHostsFile=/dev/null \
          -P ${var.ssh_port} \
          ${var.vps_user}@${var.vps_ip}:/tmp/k3s.yaml \
          "$KUBECONFIG_PATH.tmp"
      
      # Replace 127.0.0.1 with VPS IP address
      sed 's/127.0.0.1/${var.vps_ip}/g' "$KUBECONFIG_PATH.tmp" > "$KUBECONFIG_PATH"
      
      # Set proper permissions
      chmod 600 "$KUBECONFIG_PATH"
      
      # Remove temporary file
      rm -f "$KUBECONFIG_PATH.tmp"
      
      echo "Kubeconfig saved to $KUBECONFIG_PATH"
    EOT
  }
}
