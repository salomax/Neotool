# Hostinger VPS K3S Cluster Setup

This Terraform configuration automatically installs and configures K3S (lightweight Kubernetes) on an existing Hostinger VPS using SSH provisioners.

## Features

- Automated K3S cluster installation and configuration
- Automatic kubeconfig download to your local machine
- Resource reservation for system stability
- Configurable K3S components and server flags
- Token-based cluster security (auto-generated or custom)

## Prerequisites

1. **SSH Access**: You must have SSH access to the VPS
2. **SSH Key**: Private SSH key file (default: `~/.ssh/hostinger-vps`)
3. **Terraform**: Version >= 1.5.0
4. **Local kubectl**: (Optional) For cluster access after setup

## Quick Start

1. **Edit `terraform.tfvars`** with your VPS details:
   ```hcl
   vps_ip           = "your.vps.ip.address"
   vps_user         = "root"
   private_key_path = "~/.ssh/hostinger-vps"
   ssh_port         = 22
   ```

2. **Initialize Terraform:**
   ```bash
   terraform init
   ```

3. **Review the plan:**
   ```bash
   terraform plan
   ```

4. **Apply the configuration:**
   ```bash
   terraform apply
   ```

5. **Access your cluster:**
   ```bash
   export KUBECONFIG=~/.kube/config-hostinger
   kubectl get nodes
   ```

## Configuration Options

### K3S Version

Leave empty for stable release, or specify a version:

```hcl
k3s_version = ""              # Stable release (recommended)
# k3s_version = "v1.28.5+k3s1"  # Specific version
```

**Note**: Do NOT use `"latest"` - it's not supported by the k3s installer.

### Resource Reservation

Configure resource limits to prevent pods from consuming all system resources:

```hcl
k3s_server_flags = [
  "--kubelet-arg=system-reserved=cpu=200m,memory=512Mi",
  "--kubelet-arg=kube-reserved=cpu=200m,memory=512Mi",
  "--kubelet-arg=eviction-hard=memory.available<100Mi,nodefs.available<10%"
]
```

**Adjust based on your VPS size:**

- **1GB RAM VPS**: `system-reserved=cpu=100m,memory=256Mi`, `kube-reserved=cpu=100m,memory=256Mi`
- **2GB RAM VPS**: `system-reserved=cpu=200m,memory=512Mi`, `kube-reserved=cpu=200m,memory=512Mi`
- **4GB+ RAM VPS**: `system-reserved=cpu=500m,memory=1Gi`, `kube-reserved=cpu=500m,memory=1Gi`

### Disable Components

Disable built-in components if you want to use alternatives:

```hcl
k3s_disable_components = ["traefik"]  # Disable Traefik ingress controller
# k3s_disable_components = ["traefik", "servicelb"]  # Disable multiple
```

### Additional Server Flags

Add custom k3s server flags:

```hcl
k3s_server_flags = [
  "--write-kubeconfig-mode=644",
  "--tls-san=your-domain.com"
]
```

### Custom Cluster Token

By default, a secure token is auto-generated. To use a custom token:

```hcl
k3s_token = "your-secure-token-here"
```

## Outputs

After successful deployment, you'll see:

- `vps_ip`: Your VPS IP address
- `k3s_api_endpoint`: Kubernetes API URL (https://your-ip:6443)
- `k3s_cluster_name`: Cluster name
- `k3s_version`: Installed K3S version
- `kubeconfig_path`: Local path to kubeconfig file

## Post-Installation

### Access Your Cluster

```bash
export KUBECONFIG=~/.kube/config-hostinger
kubectl get nodes
kubectl get pods -A
```

### Deploy Your First Application

```bash
kubectl create deployment nginx --image=nginx
kubectl expose deployment nginx --port=80 --type=NodePort
kubectl get services
```

## Architecture

The configuration creates these resources in sequence:

1. **setup_vps**: Runs initial setup commands on the VPS
2. **k3s_install**: Installs K3S with your configuration
3. **k3s_verify**: Verifies cluster is healthy and ready
4. **kubeconfig_retrieve**: Downloads kubeconfig to your local machine

## Re-running Setup

To force re-provisioning, change the trigger:

```hcl
trigger_on_change = "v2"  # Increment to trigger re-run
```

## Security Notes

- Never commit `terraform.tfvars` (it's in .gitignore)
- Keep your SSH private key secure (chmod 600)
- K3S tokens are marked as sensitive in Terraform
- Kubeconfig is saved with restricted permissions (600)
- Consider using a firewall to restrict port 6443 access

## Troubleshooting

### SSH Connection Issues

If you get SSH connection errors:
1. Verify the VPS IP is correct
2. Check SSH key permissions: `chmod 600 ~/.ssh/hostinger-vps`
3. Test SSH manually: `ssh -i ~/.ssh/hostinger-vps root@YOUR_VPS_IP`
4. Verify the SSH port (default is 22)

### K3S Installation Issues

**Output suppressed due to sensitive value**:
- Fixed in current version using `nonsensitive()` function
- If you still see this, ensure you're using the latest code

**Download failed errors**:
- Don't use `k3s_version = "latest"` - use `""` instead
- Specific versions must match exact release tags (e.g., "v1.28.5+k3s1")

**Kubectl not found**:
- K3S installation includes kubectl at `/usr/local/bin/kubectl`
- Check if k3s service is running: `ssh root@YOUR_VPS systemctl status k3s`

### Cluster Access Issues

**Connection refused on port 6443**:
1. Check if k3s is running: `systemctl status k3s`
2. Verify firewall allows port 6443
3. Ensure kubeconfig has the correct VPS IP

**kubectl commands fail**:
1. Verify KUBECONFIG is set: `echo $KUBECONFIG`
2. Check kubeconfig file exists: `cat ~/.kube/config-hostinger`
3. Test connection: `kubectl cluster-info`

## Resource Management

K3S itself is lightweight, but workloads can consume all resources if not limited. Always set resource limits on your deployments:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  template:
    spec:
      containers:
      - name: app
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "500m"
```

## Clean Up

To remove K3S and clean up:

```bash
terraform destroy
```

Or manually on the VPS:
```bash
/usr/local/bin/k3s-uninstall.sh
```
