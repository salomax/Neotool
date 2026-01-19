# Hostinger VPS Instance Provisioning for K3S

# Hostinger VPS Instances
resource "hostinger_vps" "k3s_nodes" {
  count = var.provider_type == "vps" ? var.node_count : 0

  plan_id        = var.hostinger_vps_plan
  data_center_id = var.hostinger_data_center_id
  template_id    = var.hostinger_template_id
  ssh_key_ids    = var.hostinger_ssh_key_ids

  # User data for K3S installation (no base64 encoding needed for Hostinger)
  user_data = templatefile("${path.module}/k3s-install.sh", {
    node_index         = count.index
    cluster_name       = var.cluster_name
    k3s_version        = var.k3s_version
    server_count       = var.server_count
    is_server          = count.index < var.server_count ? "true" : "false"
    server_ip          = count.index == 0 ? "" : hostinger_vps.k3s_nodes[0].private_ip
    k3s_token          = local.k3s_token
    disable_components = join(",", var.disable_components)
    server_flags       = join(" ", var.server_flags)
    agent_flags        = join(" ", var.agent_flags)
  })

  tags = merge(
    {
      Name    = "${var.cluster_name}-node-${count.index}"
      Cluster = var.cluster_name
      Role    = count.index < var.server_count ? "server" : "agent"
    },
    var.node_labels
  )
}

