# Self-Hosted GitHub Actions Runner Setup Guide

This guide covers setting up GitHub Actions runners in two phases:
1. **Phase 1**: Run locally on your machine (quick start)
2. **Phase 2**: Migrate to Oracle Cloud Always Free tier (production-ready)

---

## Table of Contents
- [Prerequisites](#prerequisites)
- [Phase 1: Local Self-Hosted Runner](#phase-1-local-self-hosted-runner)
- [Phase 2: Oracle Cloud Always Free](#phase-2-oracle-cloud-always-free)
- [Security Considerations](#security-considerations)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required
- GitHub repository admin access
- Docker installed locally (for Phase 1)
- Oracle Cloud account (for Phase 2 - free to create)

### Recommended
- Dedicated directory for runner: `~/actions-runner`
- At least 20GB free disk space (for Docker images)
- Stable internet connection

---

## Phase 1: Local Self-Hosted Runner

### Step 1: Create a Dedicated Runner Directory

```bash
mkdir -p ~/actions-runner
cd ~/actions-runner
```

### Step 2: Get Runner Token from GitHub

1. Go to your GitHub repository
2. Navigate to: **Settings** → **Actions** → **Runners**
3. Click **"New self-hosted runner"**
4. Select your OS (macOS in your case)
5. **Copy the download and configuration commands** (don't run them yet)

The URL will look like:
```
https://github.com/YOUR_ORG/YOUR_REPO/settings/actions/runners/new
```

### Step 3: Download and Extract the Runner

```bash
# For macOS (arm64 - M1/M2/M3)
curl -o actions-runner-osx-arm64-2.314.1.tar.gz -L \
  https://github.com/actions/runner/releases/download/v2.314.1/actions-runner-osx-arm64-2.314.1.tar.gz

# Validate the hash (optional but recommended)
echo "3d27b1cea99c2c35d2a15e1b3fa37deb1a73eb0cbdabb03e9f3c4e8e9c8c8b8c  actions-runner-osx-arm64-2.314.1.tar.gz" | shasum -a 256 -c

# Extract
tar xzf ./actions-runner-osx-arm64-2.314.1.tar.gz
```

> **Note**: Check [GitHub's releases page](https://github.com/actions/runner/releases) for the latest version.

### Step 4: Configure the Runner

```bash
# Run the configuration script with the token from GitHub
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token YOUR_TOKEN_HERE

# You'll be prompted for:
# - Runner name (e.g., "local-mac-runner")
# - Runner group (press Enter for default)
# - Labels (press Enter for default, or add custom like "local,mac")
# - Work folder (press Enter for default: _work)
```

### Step 5: Install Runner as a Service (Optional but Recommended)

This ensures the runner starts automatically:

```bash
# Install the service
./svc.sh install

# Start the service
./svc.sh start

# Check status
./svc.sh status
```

**Alternative**: Run in foreground (useful for testing):
```bash
./run.sh
```

### Step 6: Verify Runner is Connected

1. Go back to GitHub: **Settings** → **Actions** → **Runners**
2. You should see your runner with a green "Idle" status

### Step 7: Update Your Workflows (if needed)

If your workflows don't already specify `runs-on`, update them:

```yaml
# .github/workflows/build.yml
name: Build and Push

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: self-hosted  # ← Use your self-hosted runner

    steps:
      - uses: actions/checkout@v4

      - name: Build Docker image
        run: |
          docker build -t ghcr.io/invistus/neotool-web:${{ github.sha }} ./web

      - name: Login to GitHub Container Registry
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin

      - name: Push image
        run: docker push ghcr.io/invistus/neotool-web:${{ github.sha }}
```

### Step 8: Test the Setup

```bash
# Push a commit or manually trigger a workflow
# Watch the runner logs:
tail -f ~/actions-runner/_diag/Runner_*.log
```

---

## Phase 2: Oracle Cloud Always Free

Oracle Cloud offers **Always Free** resources:
- **4 OCPU** ARM-based Ampere A1 cores
- **24 GB RAM**
- **200 GB block storage**
- **10 TB outbound data transfer/month**

Perfect for running CI/CD workloads!

### Step 1: Create Oracle Cloud Account

1. Go to [oracle.com/cloud/free](https://www.oracle.com/cloud/free/)
2. Sign up (requires credit card for verification, but won't be charged)
3. Complete identity verification

### Step 2: Create an Always Free ARM Instance

1. **Login** to Oracle Cloud Console
2. Go to: **Compute** → **Instances** → **Create Instance**

3. **Configure the instance**:
   - **Name**: `github-actions-runner`
   - **Image**: Ubuntu 22.04 (minimal)
   - **Shape**: Ampere A1 (VM.Standard.A1.Flex)
     - **OCPUs**: 4 (max for free tier)
     - **Memory**: 24 GB (max for free tier)
   - **Boot Volume**: 100-200 GB
   - **Network**:
     - Select default VCN (or create one)
     - Assign public IPv4 address: ✓ Yes

4. **SSH Keys**:
   - Generate a new key pair or upload your existing public key
   - **Download the private key** (you'll need this!)

5. Click **Create**

6. Wait for the instance to provision (~2-3 minutes)

### Step 3: Configure Security List (Firewall)

1. Go to: **Networking** → **Virtual Cloud Networks**
2. Click your VCN → **Security Lists** → **Default Security List**
3. Click **Add Ingress Rules**:
   - **Source CIDR**: `0.0.0.0/0`
   - **Destination Port Range**: `22`
   - **Description**: SSH access

> **Security Note**: Later, restrict SSH to your IP only.

### Step 4: Connect to Your Instance

```bash
# Get the public IP from the Oracle Console
ssh -i ~/.ssh/oracle_key ubuntu@YOUR_INSTANCE_PUBLIC_IP

# First time connection - accept the fingerprint
```

### Step 5: Install Dependencies on Oracle Instance

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add ubuntu user to docker group
sudo usermod -aG docker ubuntu

# Logout and login again for group changes
exit
# SSH back in
ssh -i ~/.ssh/oracle_key ubuntu@YOUR_INSTANCE_PUBLIC_IP

# Verify Docker works
docker run hello-world

# Install additional tools (optional but useful)
sudo apt install -y git curl wget htop
```

### Step 6: Setup GitHub Actions Runner on Oracle Instance

```bash
# Create runner directory
mkdir -p ~/actions-runner && cd ~/actions-runner

# Download runner (ARM64 Linux version)
curl -o actions-runner-linux-arm64-2.314.1.tar.gz -L \
  https://github.com/actions/runner/releases/download/v2.314.1/actions-runner-linux-arm64-2.314.1.tar.gz

# Extract
tar xzf ./actions-runner-linux-arm64-2.314.1.tar.gz
```

### Step 7: Get a New Runner Token from GitHub

1. Go to: **Settings** → **Actions** → **Runners**
2. Click **"New self-hosted runner"** again
3. Select **Linux** and **ARM64**
4. Copy the token

### Step 8: Configure the Oracle Runner

```bash
# Configure with a descriptive name
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token YOUR_NEW_TOKEN

# When prompted:
# - Name: "oracle-cloud-runner" (or similar)
# - Labels: "oracle,arm64,production" (custom labels)
# - Work folder: press Enter (default)
```

### Step 9: Install as a System Service

```bash
# Install as systemd service
sudo ./svc.sh install

# Start the service
sudo ./svc.sh start

# Enable auto-start on boot
sudo systemctl enable actions.runner.*

# Check status
sudo ./svc.sh status
```

### Step 10: Remove Your Local Runner (or Keep Both)

**Option A**: Remove local runner
```bash
# On your local machine
cd ~/actions-runner
./config.sh remove --token YOUR_REMOVAL_TOKEN
```

**Option B**: Keep both runners (use labels to route jobs)
```yaml
# In your workflow, specify which runner to use:
jobs:
  build-heavy:
    runs-on: [self-hosted, oracle]  # Use Oracle runner

  build-quick:
    runs-on: [self-hosted, local]  # Use local runner
```

### Step 11: Configure Runner to Use Labels

Update your workflows to use specific runners:

```yaml
jobs:
  build:
    runs-on: [self-hosted, linux, ARM64, oracle]
    # This ensures it runs on your Oracle instance
```

### Step 12: Monitor Resource Usage

```bash
# SSH into Oracle instance
ssh -i ~/.ssh/oracle_key ubuntu@YOUR_INSTANCE_PUBLIC_IP

# Check resource usage
htop

# Check disk space
df -h

# Check Docker images
docker images

# Clean up old Docker images periodically
docker system prune -a -f
```

---

## Security Considerations

### 1. **Runner Isolation**
- ⚠️ **Never use self-hosted runners on public repositories** (security risk!)
- Only use for private repos with trusted contributors
- Runners have access to repository secrets

### 2. **Secure Your Oracle Instance**

```bash
# On Oracle instance, configure firewall
sudo ufw enable
sudo ufw allow 22/tcp
sudo ufw status

# Change SSH port (optional but recommended)
sudo nano /etc/ssh/sshd_config
# Change: Port 22 → Port 2222
sudo systemctl restart sshd

# Disable password authentication (key-only)
sudo nano /etc/ssh/sshd_config
# Set: PasswordAuthentication no
sudo systemctl restart sshd
```

### 3. **Restrict GitHub Runner Permissions**

In GitHub:
1. **Settings** → **Actions** → **General**
2. Set **"Workflow permissions"** to:
   - ✓ Read repository contents
   - ✓ Write packages (for pushing to ghcr.io)

### 4. **Use Separate Runner Groups** (GitHub Enterprise only)

For organizations with GitHub Enterprise, create runner groups:
- Production runners
- Development runners
- Restrict which repos can use which runners

### 5. **Rotate Tokens Regularly**

```bash
# Every 6-12 months, regenerate the runner token
./config.sh remove --token OLD_TOKEN
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token NEW_TOKEN
```

### 6. **Monitor Runner Activity**

- Check GitHub Actions logs regularly
- Set up alerts for failed jobs
- Monitor Oracle instance metrics

---

## Troubleshooting

### Runner Not Appearing in GitHub

```bash
# Check runner status
./svc.sh status

# Check logs
tail -f ~/actions-runner/_diag/Runner_*.log

# Restart service
sudo ./svc.sh stop
sudo ./svc.sh start
```

### Docker Permission Denied

```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Logout and login again
exit
# SSH back in
```

### Oracle Instance Out of Memory

```bash
# Check memory usage
free -h

# Check Docker usage
docker stats

# Clean up Docker
docker system prune -a -f
docker volume prune -f
```

### Runner Stuck in "Offline" State

```bash
# Reconfigure the runner
./config.sh remove --token YOUR_TOKEN
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token NEW_TOKEN

# Restart service
sudo ./svc.sh start
```

### Jobs Queuing but Not Running

Check workflow configuration:
```yaml
# Make sure labels match your runner
runs-on: [self-hosted, linux, ARM64]
# Or just:
runs-on: self-hosted
```

### Oracle Instance SSH Connection Refused

1. Check Security List allows port 22
2. Check instance is running (Oracle Console)
3. Verify you're using correct private key
4. Check instance public IP hasn't changed

---

## Maintenance

### Weekly Tasks
```bash
# SSH to Oracle instance
ssh -i ~/.ssh/oracle_key ubuntu@YOUR_INSTANCE_PUBLIC_IP

# Update system
sudo apt update && sudo apt upgrade -y

# Clean Docker
docker system prune -f

# Check disk space
df -h
```

### Monthly Tasks
```bash
# Check runner version
cd ~/actions-runner
./run.sh --version

# Update runner if needed (check GitHub releases)
# Stop service, extract new version, restart
```

### Setup Automatic Cleanup Script

```bash
# Create cleanup script
cat > ~/cleanup-docker.sh << 'EOF'
#!/bin/bash
echo "Starting Docker cleanup..."
docker system prune -a -f
docker volume prune -f
echo "Cleanup complete!"
df -h
EOF

chmod +x ~/cleanup-docker.sh

# Add to crontab (runs every Sunday at 2 AM)
crontab -e
# Add this line:
0 2 * * 0 /home/ubuntu/cleanup-docker.sh >> /home/ubuntu/cleanup.log 2>&1
```

---

## Cost Breakdown

### Phase 1: Local Runner
- **Cost**: $0 (uses your computer)
- **Electricity**: ~$5-10/month (if running 24/7)

### Phase 2: Oracle Cloud
- **Cost**: $0 (Always Free tier)
- **No credit card charges**
- **No time limit** (truly free forever)

### Comparison with GitHub Actions
- **GitHub Free**: 2,000 minutes/month (~33 hours)
- **GitHub Team**: $4/user/month + $0.008/minute
- **Self-hosted**: Unlimited minutes, $0

---

## Next Steps

1. ✅ Complete Phase 1 (local runner)
2. ✅ Test with a simple workflow
3. ✅ Create Oracle Cloud account
4. ✅ Setup Phase 2 (Oracle runner)
5. ✅ Migrate workflows to use new runner
6. ✅ Remove local runner or keep as backup
7. ✅ Setup monitoring and alerts

---

## Additional Resources

- [GitHub Actions Self-Hosted Runner Docs](https://docs.github.com/en/actions/hosting-your-own-runners)
- [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/)
- [Docker Documentation](https://docs.docker.com/)
- [GitHub Actions Security Hardening](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)

---

## Questions or Issues?

If you encounter problems:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review GitHub Actions runner logs: `~/actions-runner/_diag/Runner_*.log`
3. Check GitHub's [self-hosted runner issues](https://github.com/actions/runner/issues)

---

**Created**: 2026-02-12
**Version**: 1.0
**Status**: Ready for Phase 1 implementation
