# Cloud Instance Provisioning for K3S
# Supports AWS EC2, GCP Compute Engine, Azure VMs

# AWS EC2 Instances
resource "aws_instance" "k3s_nodes" {
  count = var.provider_type == "aws" ? var.node_count : 0

  ami                    = var.aws_ami_id != "" ? var.aws_ami_id : data.aws_ami.ubuntu[0].id
  instance_type          = var.instance_type != "" ? var.instance_type : "t3.large"
  subnet_id              = length(var.subnet_ids) > count.index ? var.subnet_ids[count.index] : var.subnet_ids[0]
  vpc_security_group_ids = length(var.security_group_ids) > 0 ? var.security_group_ids : []
  key_name               = var.ssh_key_name != "" ? var.ssh_key_name : null

  # AWS EC2 user_data accepts plain text and auto-encodes it
  user_data = templatefile("${path.module}/k3s-install.sh", {
    node_index         = count.index
    cluster_name      = var.cluster_name
    k3s_version       = var.k3s_version
    server_count      = var.server_count
    is_server         = count.index < var.server_count ? "true" : "false"
    server_ip         = count.index == 0 ? "" : aws_instance.k3s_nodes[0].private_ip
    k3s_token         = local.k3s_token
    disable_components = join(",", var.disable_components)
    server_flags      = join(" ", var.server_flags)
    agent_flags       = join(" ", var.agent_flags)
  })

  tags = merge(
    {
      Name    = "${var.cluster_name}-node-${count.index}"
      Cluster = var.cluster_name
      Role    = count.index < var.server_count ? "server" : "agent"
    },
    var.node_labels
  )

  root_block_device {
    volume_size = 50
    volume_type = "gp3"
    encrypted   = true
  }
}

data "aws_ami" "ubuntu" {
  count = var.provider_type == "aws" && var.aws_ami_id == "" ? 1 : 0

  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# GCP Compute Engine Instances
resource "google_compute_instance" "k3s_nodes" {
  count = var.provider_type == "gcp" ? var.node_count : 0

  name         = "${var.cluster_name}-node-${count.index}"
  machine_type = var.instance_type != "" ? var.instance_type : "n1-standard-2"
  zone         = length(var.gcp_zones) > count.index ? var.gcp_zones[count.index] : var.gcp_zones[0]

  boot_disk {
    initialize_params {
      image = var.gcp_image != "" ? var.gcp_image : "ubuntu-os-cloud/ubuntu-2204-jammy-v20240111"
      size  = 50
      type  = "pd-ssd"
    }
  }

  network_interface {
    network    = var.vpc_id
    subnetwork = length(var.subnet_ids) > count.index ? var.subnet_ids[count.index] : var.subnet_ids[0]

    access_config {
      // Ephemeral public IP
    }
  }

  metadata_startup_script = templatefile("${path.module}/k3s-install.sh", {
    node_index         = count.index
    cluster_name       = var.cluster_name
    k3s_version        = var.k3s_version
    server_count       = var.server_count
    is_server          = count.index < var.server_count ? "true" : "false"
    server_ip          = count.index == 0 ? "" : google_compute_instance.k3s_nodes[0].network_interface[0].network_ip
    k3s_token          = local.k3s_token
    disable_components = join(",", var.disable_components)
    server_flags       = join(" ", var.server_flags)
    agent_flags        = join(" ", var.agent_flags)
  })

  tags = ["k3s", "${var.cluster_name}"]

  labels = merge(
    {
      cluster = var.cluster_name
      role    = count.index < var.server_count ? "server" : "agent"
    },
    var.node_labels
  )
}

# Azure Virtual Machines
resource "azurerm_linux_virtual_machine" "k3s_nodes" {
  count = var.provider_type == "azure" ? var.node_count : 0

  name                = "${var.cluster_name}-node-${count.index}"
  resource_group_name = var.azure_resource_group_name
  location            = var.region
  size                = var.instance_type != "" ? var.instance_type : "Standard_B2s"
  admin_username      = "ubuntu"

  network_interface_ids = [azurerm_network_interface.k3s_nodes[count.index].id]

  admin_ssh_key {
    username   = "ubuntu"
    public_key = var.ssh_public_key != "" ? var.ssh_public_key : tls_private_key.k3s_ssh[0].public_key_openssh
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
    disk_size_gb         = 50
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts-gen2"
    version   = "latest"
  }

  # Azure requires base64-encoded custom_data
  custom_data = base64encode(templatefile("${path.module}/k3s-install.sh", {
    node_index         = count.index
    cluster_name       = var.cluster_name
    k3s_version        = var.k3s_version
    server_count       = var.server_count
    is_server          = count.index < var.server_count ? "true" : "false"
    server_ip          = count.index == 0 ? "" : azurerm_network_interface.k3s_nodes[0].private_ip_address
    k3s_token          = local.k3s_token
    disable_components = join(",", var.disable_components)
    server_flags       = join(" ", var.server_flags)
    agent_flags        = join(" ", var.agent_flags)
  }))

  tags = merge(
    {
      Cluster = var.cluster_name
      Role    = count.index < var.server_count ? "server" : "agent"
    },
    var.node_labels
  )
}

resource "azurerm_network_interface" "k3s_nodes" {
  count = var.provider_type == "azure" ? var.node_count : 0

  name                = "${var.cluster_name}-nic-${count.index}"
  location            = var.region
  resource_group_name = var.azure_resource_group_name

  ip_configuration {
    name                          = "internal"
    subnet_id                     = length(var.subnet_ids) > count.index ? var.subnet_ids[count.index] : var.subnet_ids[0]
    private_ip_address_allocation = "Dynamic"
  }
}

# Generate SSH key for Azure if not provided
resource "tls_private_key" "k3s_ssh" {
  count = var.provider_type == "azure" && var.ssh_public_key == "" ? 1 : 0

  algorithm = "RSA"
  rsa_bits  = 4096
}

