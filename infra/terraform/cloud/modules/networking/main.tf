# Multi-Cloud Networking Module
# Supports AWS, GCP, Azure, and local (bridge networking)

terraform {
  required_version = ">= 1.0"
  required_providers {
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
  }
}

# Local provider (bridge networking for K3S)
# For local development, we don't need to create actual network resources
# K3S uses bridge networking by default

# AWS VPC Configuration
resource "aws_vpc" "main" {
  count            = var.provider_type == "aws" ? 1 : 0
  cidr_block       = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(
    var.tags,
    {
      Name = var.vpc_name
    }
  )
}

resource "aws_internet_gateway" "main" {
  count  = var.provider_type == "aws" ? 1 : 0
  vpc_id = aws_vpc.main[0].id

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-igw"
    }
  )
}

resource "aws_subnet" "public" {
  count             = var.provider_type == "aws" ? length(var.public_subnet_cidrs) : 0
  vpc_id            = aws_vpc.main[0].id
  cidr_block        = var.public_subnet_cidrs[count.index]
  availability_zone = length(var.availability_zones) > 0 && length(var.availability_zones) > count.index ? var.availability_zones[count.index] : (length(data.aws_availability_zones.available) > 0 ? data.aws_availability_zones.available[0].names[count.index] : "")
  map_public_ip_on_launch = true

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-public-${count.index + 1}"
    }
  )
}

resource "aws_subnet" "private" {
  count             = var.provider_type == "aws" ? length(var.private_subnet_cidrs) : 0
  vpc_id            = aws_vpc.main[0].id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = length(var.availability_zones) > 0 && length(var.availability_zones) > count.index ? var.availability_zones[count.index] : (length(data.aws_availability_zones.available) > 0 ? data.aws_availability_zones.available[0].names[count.index] : "")

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-private-${count.index + 1}"
    }
  )
}

resource "aws_eip" "nat" {
  count  = var.provider_type == "aws" && var.aws_enable_nat_gateway ? length(var.public_subnet_cidrs) : 0
  domain = "vpc"

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-nat-eip-${count.index + 1}"
    }
  )

  depends_on = [aws_internet_gateway.main]
}

resource "aws_nat_gateway" "main" {
  count         = var.provider_type == "aws" && var.aws_enable_nat_gateway ? length(var.public_subnet_cidrs) : 0
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-nat-${count.index + 1}"
    }
  )

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "public" {
  count  = var.provider_type == "aws" ? 1 : 0
  vpc_id = aws_vpc.main[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main[0].id
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-public-rt"
    }
  )
}

resource "aws_route_table_association" "public" {
  count          = var.provider_type == "aws" ? length(var.public_subnet_cidrs) : 0
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public[0].id
}

resource "aws_route_table" "private" {
  count  = var.provider_type == "aws" ? length(var.private_subnet_cidrs) : 0
  vpc_id = aws_vpc.main[0].id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = var.aws_enable_nat_gateway ? aws_nat_gateway.main[count.index].id : null
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-private-rt-${count.index + 1}"
    }
  )
}

resource "aws_route_table_association" "private" {
  count          = var.provider_type == "aws" ? length(var.private_subnet_cidrs) : 0
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_security_group" "k3s" {
  count  = var.provider_type == "aws" ? 1 : 0
  name   = "${var.vpc_name}-k3s-sg"
  vpc_id = aws_vpc.main[0].id

  ingress {
    from_port   = 6443
    to_port     = 6443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "K3S API server"
  }

  ingress {
    from_port   = 10250
    to_port     = 10250
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "Kubelet API"
  }

  ingress {
    from_port   = 8472
    to_port     = 8472
    protocol    = "udp"
    cidr_blocks = [var.vpc_cidr]
    description = "Flannel VXLAN"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound"
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.vpc_name}-k3s-sg"
    }
  )
}

# Data sources
data "aws_availability_zones" "available" {
  count = var.provider_type == "aws" && length(var.availability_zones) == 0 ? 1 : 0
  state = "available"
}

# GCP VPC Configuration
resource "google_compute_network" "main" {
  count                   = var.provider_type == "gcp" ? 1 : 0
  name                    = var.vpc_name
  auto_create_subnetworks = false
  project                 = var.gcp_project_id
}

resource "google_compute_subnetwork" "public" {
  count         = var.provider_type == "gcp" ? length(var.public_subnet_cidrs) : 0
  name          = "${var.vpc_name}-public-${count.index + 1}"
  ip_cidr_range = var.public_subnet_cidrs[count.index]
  region        = var.region
  network       = google_compute_network.main[0].id
  project       = var.gcp_project_id
}

resource "google_compute_subnetwork" "private" {
  count         = var.provider_type == "gcp" ? length(var.private_subnet_cidrs) : 0
  name          = "${var.vpc_name}-private-${count.index + 1}"
  ip_cidr_range = var.private_subnet_cidrs[count.index]
  region        = var.region
  network       = google_compute_network.main[0].id
  private_ip_google_access = true
  project       = var.gcp_project_id
}

resource "google_compute_router" "main" {
  count   = var.provider_type == "gcp" ? 1 : 0
  name    = "${var.vpc_name}-router"
  region  = var.region
  network = google_compute_network.main[0].id
  project = var.gcp_project_id
}

resource "google_compute_router_nat" "main" {
  count                              = var.provider_type == "gcp" ? 1 : 0
  name                               = "${var.vpc_name}-nat"
  router                             = google_compute_router.main[0].name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
  project                            = var.gcp_project_id
}

resource "google_compute_firewall" "k3s" {
  count   = var.provider_type == "gcp" ? 1 : 0
  name    = "${var.vpc_name}-k3s-fw"
  network = google_compute_network.main[0].id
  project = var.gcp_project_id

  allow {
    protocol = "tcp"
    ports    = ["6443", "10250"]
  }

  allow {
    protocol = "udp"
    ports    = ["8472"]
  }

  source_ranges = [var.vpc_cidr]
  target_tags   = ["k3s"]
}

# Azure VNet Configuration
resource "azurerm_virtual_network" "main" {
  count               = var.provider_type == "azure" ? 1 : 0
  name                = var.vpc_name
  address_space       = [var.vpc_cidr]
  location            = var.region
  resource_group_name = var.azure_resource_group_name
}

resource "azurerm_subnet" "public" {
  count                = var.provider_type == "azure" ? length(var.public_subnet_cidrs) : 0
  name                 = "${var.vpc_name}-public-${count.index + 1}"
  resource_group_name  = var.azure_resource_group_name
  virtual_network_name = azurerm_virtual_network.main[0].name
  address_prefixes     = [var.public_subnet_cidrs[count.index]]
}

resource "azurerm_subnet" "private" {
  count                = var.provider_type == "azure" ? length(var.private_subnet_cidrs) : 0
  name                 = "${var.vpc_name}-private-${count.index + 1}"
  resource_group_name  = var.azure_resource_group_name
  virtual_network_name = azurerm_virtual_network.main[0].name
  address_prefixes     = [var.private_subnet_cidrs[count.index]]
}

resource "azurerm_public_ip" "nat" {
  count               = var.provider_type == "azure" ? 1 : 0
  name                = "${var.vpc_name}-nat-pip"
  location            = var.region
  resource_group_name = var.azure_resource_group_name
  allocation_method    = "Static"
  sku                 = "Standard"
}

resource "azurerm_nat_gateway" "main" {
  count               = var.provider_type == "azure" ? 1 : 0
  name                = "${var.vpc_name}-nat"
  location            = var.region
  resource_group_name = var.azure_resource_group_name
}

resource "azurerm_nat_gateway_public_ip_association" "main" {
  count               = var.provider_type == "azure" ? 1 : 0
  nat_gateway_id      = azurerm_nat_gateway.main[0].id
  public_ip_address_id = azurerm_public_ip.nat[0].id
}

resource "azurerm_subnet_nat_gateway_association" "private" {
  count          = var.provider_type == "azure" ? length(var.private_subnet_cidrs) : 0
  subnet_id      = azurerm_subnet.private[count.index].id
  nat_gateway_id = azurerm_nat_gateway.main[0].id
}

resource "azurerm_network_security_group" "k3s" {
  count               = var.provider_type == "azure" ? 1 : 0
  name                = "${var.vpc_name}-k3s-nsg"
  location            = var.region
  resource_group_name = var.azure_resource_group_name

  security_rule {
    name                       = "k3s-api"
    priority                   = 1000
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "6443"
    source_address_prefix      = var.vpc_cidr
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "kubelet"
    priority                   = 1001
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "10250"
    source_address_prefix      = var.vpc_cidr
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "flannel"
    priority                   = 1002
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Udp"
    source_port_range          = "*"
    destination_port_range     = "8472"
    source_address_prefix      = var.vpc_cidr
    destination_address_prefix = "*"
  }
}

