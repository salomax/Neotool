---
title: Hostinger Infrastructure Runbook
type: infrastructure
category: provisioning
status: current
version: 1.0.0
tags: [infrastructure, terraform, hostinger, vps, k3s, provisioning]
ai_optimized: true
search_keywords: [infrastructure, terraform, hostinger, vps, k3s, provisioning, ssh, setup]
related:
  - 11-infrastructure/architecture.md
  - 11-infrastructure/k8s-runbook.md
last_updated: 2026-01-15
---

# Hostinger Infrastructure Runbook

> **Complete guide for provisioning and managing Neotool infrastructure on Hostinger VPS**: Terraform automation, K3S cluster setup, and VPS operations.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Initial Setup](#initial-setup)
4. [Terraform Provisioning](#terraform-provisioning)
5. [K3S Cluster Management](#k3s-cluster-management)
6. [VPS Operations](#vps-operations)
7. [Troubleshooting](#troubleshooting)
8. [Teardown & Cleanup](#teardown--cleanup)
9. [Reference](#reference)

---

## Overview

### What This Runbook Covers

This runbook covers **infrastructure provisioning** on Hostinger VPS:

- Creating and configuring VPS instances
- Terraform automation for K3S installation
- K3S cluster lifecycle management
- VPS-level operations and maintenance
- Infrastructure teardown

**For Kubernetes operations** (pods, deployments, databases, monitoring), see [Kubernetes Operations Runbook](./k8s-runbook.md).

### Infrastructure Approach

The Neotool infrastructure uses a **simple, practical approach**:

1. **Manual VPS Creation**: Create VPS via Hostinger control panel
2. **Automated K3S Installation**: Terraform connects via SSH and installs K3S
3. **GitOps Deployment**: Flux CD automatically deploys applications from Git

**Benefits**:
- Simple and straightforward
- Infrastructure managed by code
- Container orchestration with K3S
- Works with any VPS provider (just change the IP)
- Cost-effective: ~$10-30/month for single VPS
- Easy to understand and troubleshoot

---

## Prerequisites

### Required Tools

```bash
# Check tool versions
terraform --version  # >= 1.0
kubectl version --client  # >= 1.28
flux version  # >= 2.0
ssh --version
```

**Install tools**:

```bash
# macOS
brew install terraform kubectl fluxcd/tap/flux

# Linux (Ubuntu/Debian)
# Terraform
curl -fsSL https://apt.releases.hashicorp.com/gpg | sudo apt-key add -
sudo apt-add-repository "deb [arch=amd64] https://apt.releases.hashicorp.com $(lsb_release -cs) main"
sudo apt-get update && sudo apt-get install terraform

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Flux
curl -s https://fluxcd.io/install.sh | sudo bash
```

### Required Access

- **Hostinger Account**: Access to control panel
- **SSH Key**: For VPS access
- **GitHub Account**: For Flux CD GitOps

---

## Initial Setup

### Step 1: Create VPS on Hostinger

#### Via Hostinger Control Panel

1. Login to [Hostinger](https://www.hostinger.com)
2. Navigate to **VPS** section
3. Click **Create VPS**
4. Select configuration:
   - **Operating System**: Ubuntu 24.04 LTS (or latest)
   - **Location**: Choose closest to your users
   - **Plan**: Based on your needs (minimum 2GB RAM recommended)
5. Click **Create**
6. **Note the IP address** once provisioned

**Why Ubuntu?**
- Excellent K3S compatibility
- LTS support (5 years of security updates)
- Wide community support
- Mature package management with apt
- Proven in production Kubernetes deployments

### Step 2: Set Up SSH Access

#### Generate SSH Key

```bash
# Generate SSH key pair
ssh-keygen -t ed25519 -f ~/.ssh/hostinger-vps -C "hostinger-k3s"

# Display public key (for copying)
cat ~/.ssh/hostinger-vps.pub
```

#### Add Public Key to VPS

**Option 1: via Hostinger Control Panel**
1. Go to VPS → Settings → SSH Keys
2. Paste your public key
3. Save

**Option 2: via ssh-copy-id**
```bash
# Copy public key to VPS (one-time)
ssh-copy-id -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Enter root password when prompted
```

#### Test SSH Connection

```bash
# Test connection
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Should connect without password
```

### Step 3: Configure Terraform

#### Navigate to Terraform Directory

```bash
cd infra/terraform/hostinger
```

#### Edit terraform.tfvars

```bash
vim terraform.tfvars
```

**Update with your VPS details**:

```hcl
# VPS Connection Details
vps_ip          = "YOUR_VPS_IP"  # ← Change to your VPS IP!
vps_user        = "root"
private_key_path = "~/.ssh/hostinger-vps"
ssh_port        = 22

# Setup Commands (run before K3S installation)
setup_commands = [
  "apt-get update",
  "apt-get upgrade -y",
  "echo 'Basic setup completed'"
]

# Trigger re-provisioning by changing this value
trigger_on_change = "initial"

# K3S Configuration
k3s_version = ""  # Empty = stable release (recommended)
# k3s_version = "v1.28.5+k3s1"  # Or specify exact version

k3s_cluster_name = "neotool-hostinger"

# K3S token (auto-generated if empty)
# k3s_token = ""

# Components to disable (optional)
k3s_disable_components = []  # e.g., ["traefik"] to disable Traefik

# K3S server flags - adjust based on your VPS specs
# These values work for VPS with 2-4GB RAM
k3s_server_flags = [
  "--kubelet-arg=system-reserved=cpu=200m,memory=512Mi",
  "--kubelet-arg=kube-reserved=cpu=200m,memory=512Mi",
  "--kubelet-arg=eviction-hard=memory.available<100Mi,nodefs.available<10%"
]

# Local path where kubeconfig will be saved
kubeconfig_path = "~/.kube/config-hostinger"
```

**Resource Reservation Guide**:

| VPS RAM | system-reserved | kube-reserved | Notes |
|---------|-----------------|---------------|-------|
| 2GB | cpu=200m,memory=512Mi | cpu=200m,memory=512Mi | Minimum recommended |
| 4GB | cpu=200m,memory=512Mi | cpu=200m,memory=512Mi | Good for small apps |
| 8GB | cpu=300m,memory=1Gi | cpu=300m,memory=1Gi | Production-ready |
| 16GB+ | cpu=500m,memory=2Gi | cpu=500m,memory=2Gi | Large deployments |

---

## Terraform Provisioning

### How Terraform Works

Terraform automates K3S installation via SSH provisioners:

1. Connects to VPS via SSH
2. Runs setup commands (apt-get update, etc.)
3. Installs K3S using official installer
4. Configures cluster settings
5. Downloads kubeconfig to local machine

**Note**: VPS is manually created (not managed by Terraform). Terraform only automates K3S installation.

### Step 1: Initialize Terraform

```bash
cd infra/terraform/hostinger

# Initialize Terraform (downloads providers)
terraform init
```

**Expected output**:
```
Initializing provider plugins...
- Finding latest version of hashicorp/null...
- Finding latest version of hashicorp/random...
Terraform has been successfully initialized!
```

### Step 2: Review Plan

```bash
# Preview what will be created
terraform plan
```

**Review the planned actions**:
- `null_resource.setup_vps`: Runs setup commands
- `null_resource.k3s_install`: Installs K3S
- `null_resource.k3s_verify`: Verifies cluster health
- `null_resource.kubeconfig_retrieve`: Downloads kubeconfig

### Step 3: Apply Configuration

```bash
# Apply Terraform configuration
terraform apply

# Type 'yes' when prompted
```

**Duration**: 5-10 minutes

**What happens**:
1. ✅ Connects to VPS via SSH
2. ✅ Runs setup commands (apt-get update, upgrade)
3. ✅ Installs K3S with your configuration
4. ✅ Waits for K3S to be ready (checks service status)
5. ✅ Verifies cluster health (nodes are Ready)
6. ✅ Downloads kubeconfig to `~/.kube/config-hostinger`

### Step 4: Verify Installation

```bash
# Set kubeconfig environment variable
export KUBECONFIG=~/.kube/config-hostinger

# Verify cluster connection
kubectl cluster-info

# Check nodes
kubectl get nodes

# Expected output:
# NAME                       STATUS   ROLES                  AGE   VERSION
# neotool-hostinger-node-0   Ready    control-plane,master   2m    v1.28.2+k3s1

# Check system pods
kubectl get pods -n kube-system
```

### Terraform Outputs

View Terraform outputs:

```bash
terraform output
```

**Outputs**:
- `vps_ip`: Your VPS IP address
- `k3s_api_endpoint`: Kubernetes API URL (https://your-ip:6443)
- `k3s_cluster_name`: Cluster name
- `k3s_version`: Installed K3S version
- `kubeconfig_path`: Local path to kubeconfig file

### Re-running Terraform

To re-run provisioners (after config changes):

```bash
# Method 1: Change trigger value
vim terraform.tfvars
# Update: trigger_on_change = "v2"

terraform apply

# Method 2: Target specific resources
terraform apply -target=null_resource.k3s_install
terraform apply -target=null_resource.kubeconfig_retrieve
```

---

## K3S Cluster Management

### What is K3S?

K3S is a **lightweight, certified Kubernetes distribution**:
- Single binary (~70MB)
- Production-ready
- Full Kubernetes API compatibility
- Perfect for VPS environments

**Built-in components**:
- Traefik (ingress controller, can be disabled)
- Local-path storage provisioner
- CoreDNS (DNS service)
- Metrics Server (resource metrics)
- Flannel (CNI for pod networking)

### Check K3S Status

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check K3S service status
systemctl status k3s

# View K3S logs
journalctl -u k3s -f

# Check K3S version
k3s --version

# Exit SSH session
exit
```

### Upgrade K3S Version

#### Via Terraform

```bash
# Edit terraform.tfvars
vim infra/terraform/hostinger/terraform.tfvars

# Update version
k3s_version = "v1.29.0+k3s1"

# Update trigger
trigger_on_change = "upgrade-v1.29"

# Apply changes
terraform apply
```

**Note**: Upgrading may cause brief downtime. Plan accordingly.

#### Manual Upgrade

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Download and run K3S install script with new version
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="v1.29.0+k3s1" sh -

# Verify upgrade
k3s --version

# Exit SSH session
exit
```

### Restart K3S Service

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Restart K3S
systemctl restart k3s

# Verify service is running
systemctl status k3s

# Exit SSH session
exit
```

### View K3S Configuration

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# View K3S config
cat /etc/systemd/system/k3s.service

# View K3S data directory
ls -la /var/lib/rancher/k3s/

# View kubeconfig on VPS
cat /etc/rancher/k3s/k3s.yaml

# Exit SSH session
exit
```

---

## VPS Operations

### Access VPS

```bash
# SSH with key
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Or if key is added to ssh-agent
ssh root@YOUR_VPS_IP
```

### System Information

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# System info
uname -a
lsb_release -a

# Resource usage
htop  # or top
df -h
free -h

# Network configuration
ip addr show
ip route show

# Exit SSH session
exit
```

### Update Operating System

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Update package lists
apt-get update

# Upgrade packages
apt-get upgrade -y

# Check if reboot is required
ls /var/run/reboot-required

# Reboot if needed
reboot

# Exit SSH session (will disconnect)
exit
```

**Note**: After reboot, Vault will need to be unsealed. See [Kubernetes Operations Runbook](./k8s-runbook.md#unseal-vault).

### Network Configuration

#### Required Ports

Ensure these ports are open in firewall:

| Port | Protocol | Purpose |
|------|----------|---------|
| 6443 | TCP | Kubernetes API Server (kubectl, Flux) |
| 10250 | TCP | Kubelet API (node agent) |
| 8472 | UDP | Flannel VXLAN (pod networking) |
| 22 | TCP | SSH (server management) |
| 80/443 | TCP | HTTP/HTTPS (ingress traffic, optional) |

#### Check Firewall Rules

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check if ufw is active
ufw status

# If ufw is active, ensure ports are allowed
ufw allow 6443/tcp
ufw allow 10250/tcp
ufw allow 8472/udp
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp

# Or check iptables
iptables -L -n

# Exit SSH session
exit
```

#### Network Ranges

K3S uses these network ranges:

- **Service Network**: `10.43.0.0/16` (Kubernetes services)
- **Pod Network**: `10.42.0.0/16` (Flannel CNI)
- **Cluster DNS**: `10.43.0.10` (CoreDNS)

### Storage Management

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check disk usage
df -h

# Check K3S storage usage
du -sh /var/lib/rancher/k3s/

# Check container images
crictl images

# Clean up unused images
crictl rmi --prune

# Exit SSH session
exit
```

### Backup VPS

#### Manual Snapshot (via Hostinger)

1. Login to Hostinger control panel
2. Navigate to VPS → Snapshots
3. Click **Create Snapshot**
4. Wait for snapshot to complete

#### Backup Important Files

```bash
# From local machine

# Backup kubeconfig
cp ~/.kube/config-hostinger ~/.kube/config-hostinger.backup-$(date +%Y%m%d)

# Backup Vault credentials
cp ~/.neotool/vault-credentials.txt ~/.neotool/vault-credentials.backup-$(date +%Y%m%d)

# Backup from VPS
scp -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP:/etc/rancher/k3s/k3s.yaml ./k3s-backup-$(date +%Y%m%d).yaml
```

---

## Troubleshooting

### SSH Connection Issues

#### Cannot Connect via SSH

```bash
# Test SSH connection
ssh -v -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check SSH key permissions
ls -la ~/.ssh/hostinger-vps
chmod 600 ~/.ssh/hostinger-vps  # Fix if needed

# Test from different location
# (firewall may be blocking your IP)
```

#### SSH Key Rejected

```bash
# Verify public key is on VPS
ssh root@YOUR_VPS_IP -i ~/.ssh/hostinger-vps 'cat ~/.ssh/authorized_keys'

# Re-copy public key
ssh-copy-id -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP
```

### Terraform Issues

#### Terraform Cannot Connect to VPS

**Symptoms**: `Error connecting to SSH agent` or `connection refused`

**Solutions**:

```bash
# Verify SSH connection manually first
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check terraform.tfvars
cat terraform.tfvars | grep vps_ip
cat terraform.tfvars | grep private_key_path

# Ensure IP and key path are correct
```

#### Terraform State Issues

```bash
# View Terraform state
terraform show

# List resources
terraform state list

# Refresh state
terraform refresh

# If state is corrupted, remove and reapply
terraform state rm null_resource.k3s_install
terraform apply -target=null_resource.k3s_install
```

### K3S Issues

#### K3S Not Starting

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check K3S service status
systemctl status k3s

# View logs
journalctl -u k3s -n 100 --no-pager

# Common issues:
# 1. Port 6443 already in use
lsof -i :6443

# 2. Insufficient resources
free -h
df -h

# 3. Previous installation not cleaned up
/usr/local/bin/k3s-killall.sh
rm -rf /var/lib/rancher/k3s
```

#### kubectl Not Working

```bash
# Check kubeconfig environment variable
echo $KUBECONFIG

# Set it if not set
export KUBECONFIG=~/.kube/config-hostinger

# Verify kubeconfig file exists
ls -la ~/.kube/config-hostinger

# Test connection
kubectl cluster-info

# If still not working, re-download kubeconfig
cd infra/terraform/hostinger
terraform apply -target=null_resource.kubeconfig_retrieve
```

#### Nodes Not Ready

```bash
# Check node status
kubectl get nodes

# Describe node for details
kubectl describe node <node-name>

# Common causes:
# 1. K3S service not running (SSH and check)
# 2. Network issues (check Flannel)
# 3. Resource exhaustion (check memory/cpu)

# View K3S logs
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP journalctl -u k3s -f
```

### VPS Issues

#### High CPU/Memory Usage

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check resource usage
htop  # or top

# Check processes
ps aux --sort=-%mem | head
ps aux --sort=-%cpu | head

# Check K3S pod resource usage (from local)
kubectl top nodes
kubectl top pods -A --sort-by=memory
```

#### Disk Full

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check disk usage
df -h

# Find large directories
du -sh /*

# Clean up logs
journalctl --vacuum-time=7d

# Clean up container images
crictl rmi --prune

# Clean up old pod logs
rm -rf /var/log/pods/*

# Exit and check from Kubernetes
exit

# Clean up completed pods
kubectl delete pods --field-selector status.phase=Succeeded -A
kubectl delete pods --field-selector status.phase=Failed -A
```

---

## Teardown & Cleanup

### Complete VPS Cleanup

**Warning**: This will destroy K3S and all data. Backup first!

#### Step 1: Backup Important Data

```bash
# Backup kubeconfig
cp ~/.kube/config-hostinger ~/backup/

# Backup Vault credentials
cp ~/.neotool/vault-credentials.txt ~/backup/

# Backup database (see K8S runbook)
# See docs/11-infrastructure/k8s-runbook.md#database-backups
```

#### Step 2: Remove K3S from VPS

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Run K3S uninstall script
/usr/local/bin/k3s-uninstall.sh

# Verify K3S is removed
systemctl status k3s  # Should show "not found"
ls /usr/local/bin/k3s*  # Should show "No such file"

# Exit SSH session
exit
```

**Verify complete removal**:

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Check processes
ps aux | grep k3s | grep -v grep  # Should be empty

# Check binaries
ls -la /usr/local/bin/k3s* 2>/dev/null  # Should not exist

# Check data directories
ls -la /var/lib/rancher/k3s 2>/dev/null  # Should not exist
ls -la /etc/rancher/k3s 2>/dev/null  # Should not exist

# Check systemd services
systemctl list-unit-files | grep k3s  # Should be empty

# Exit SSH session
exit
```

#### Step 3: Clean Terraform State

```bash
cd infra/terraform/hostinger

# Remove Terraform state for K3S resources
terraform state rm null_resource.setup_vps
terraform state rm null_resource.k3s_install
terraform state rm null_resource.k3s_verify
terraform state rm null_resource.kubeconfig_retrieve

# Optional: Remove all Terraform files
rm -rf .terraform
rm terraform.tfstate*
```

#### Step 4: Clean Local Files

```bash
# Remove kubeconfig
rm -f ~/.kube/config-hostinger

# Keep backups!
# DO NOT delete:
# - ~/backup/config-hostinger
# - ~/backup/vault-credentials.txt
# - Database backups
```

### Partial Cleanup

#### Remove Only K3S (Keep VPS)

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Uninstall K3S
/usr/local/bin/k3s-uninstall.sh

# VPS remains running
# Exit SSH session
exit
```

#### Manual K3S Cleanup (if uninstall script fails)

```bash
# SSH to VPS
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Stop and disable K3S
systemctl stop k3s
systemctl disable k3s

# Remove binaries
rm -f /usr/local/bin/k3s
rm -f /usr/local/bin/k3s-killall.sh
rm -f /usr/local/bin/k3s-uninstall.sh
rm -f /usr/local/bin/kubectl
rm -f /usr/local/bin/crictl
rm -f /usr/local/bin/ctr

# Remove data directories
rm -rf /var/lib/rancher/k3s
rm -rf /etc/rancher/k3s

# Remove systemd service
rm -f /etc/systemd/system/k3s.service
systemctl daemon-reload

# Remove network interfaces
ip link delete cni0 2>/dev/null || true
ip link delete flannel.1 2>/dev/null || true

# Exit SSH session
exit
```

### Re-deployment After Cleanup

To redeploy infrastructure after cleanup:

1. **VPS is still running** (no need to recreate)
2. **Re-run Terraform**: `terraform apply`
3. **Re-bootstrap Flux**: See [K8S Runbook](./k8s-runbook.md#gitops-with-flux-cd)
4. **Re-initialize Vault**: See [K8S Runbook](./k8s-runbook.md#secrets-management)

---

## Reference

### Terraform Commands

```bash
# Initialize
terraform init

# Preview changes
terraform plan

# Apply changes
terraform apply

# Show current state
terraform show

# List resources
terraform state list

# View outputs
terraform output

# Remove specific resource from state
terraform state rm <resource>

# Refresh state
terraform refresh
```

### K3S Commands (on VPS)

```bash
# SSH to VPS first
ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP

# Service management
systemctl status k3s
systemctl start k3s
systemctl stop k3s
systemctl restart k3s

# View logs
journalctl -u k3s -f
journalctl -u k3s -n 100 --no-pager

# Version info
k3s --version

# Kubeconfig
cat /etc/rancher/k3s/k3s.yaml

# Data directory
ls -la /var/lib/rancher/k3s/

# Container runtime (containerd via crictl)
crictl ps
crictl images
crictl rmi --prune
```

### VPS Management Commands

```bash
# System info
uname -a
lsb_release -a
hostnamectl

# Resource monitoring
htop  # or top
df -h
free -h
iostat

# Network
ip addr show
ip route show
ss -tulpn

# Firewall
ufw status
iptables -L -n

# Package management
apt-get update
apt-get upgrade -y
apt-get autoremove

# Logs
journalctl -xe
journalctl --vacuum-time=7d
```

### Important File Locations

| File/Directory | Purpose |
|----------------|---------|
| `~/.ssh/hostinger-vps` | SSH private key (local) |
| `~/.kube/config-hostinger` | Kubeconfig (local) |
| `~/.neotool/vault-credentials.txt` | Vault credentials (local) |
| `infra/terraform/hostinger/` | Terraform configuration (local) |
| `/etc/rancher/k3s/k3s.yaml` | Kubeconfig (on VPS) |
| `/var/lib/rancher/k3s/` | K3S data directory (on VPS) |
| `/etc/systemd/system/k3s.service` | K3S systemd service (on VPS) |

### Configuration Files

**terraform.tfvars**:
```hcl
vps_ip          = "YOUR_VPS_IP"
vps_user        = "root"
private_key_path = "~/.ssh/hostinger-vps"
ssh_port        = 22
k3s_version     = ""  # Empty = stable
k3s_cluster_name = "neotool-hostinger"
kubeconfig_path = "~/.kube/config-hostinger"
```

### Network Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 6443 | TCP | Kubernetes API Server |
| 10250 | TCP | Kubelet API |
| 8472 | UDP | Flannel VXLAN (pod networking) |
| 22 | TCP | SSH (management) |

### Best Practices

#### Security
- ✅ Use SSH keys (never passwords)
- ✅ Keep VPS OS updated
- ✅ Backup Vault credentials securely
- ✅ Configure firewall properly
- ✅ Rotate SSH keys periodically

#### Operations
- ✅ Backup before major changes
- ✅ Test in development first
- ✅ Monitor VPS resource usage
- ✅ Keep Terraform state backed up
- ✅ Document custom configurations

#### Maintenance
- ✅ Update VPS OS monthly
- ✅ Update K3S when stable versions released
- ✅ Monitor disk space
- ✅ Clean up old logs and images
- ✅ Review firewall rules periodically

---

## Related Documentation

- [Infrastructure Architecture](./architecture.md) - High-level architecture overview
- [Kubernetes Operations Runbook](./k8s-runbook.md) - Kubernetes operations, Flux, database, monitoring

---

**Version**: 1.0.0
**Last Updated**: 2026-01-15
**Review Frequency**: Quarterly

*Infrastructure as Code. Simple. Reliable. Production-ready.* 🚀
