# Self-Hosted GitHub Actions Runner Setup Guide

This guide covers setting up GitHub Actions runners in two phases:
1. **Phase 1**: Run locally on your machine (quick start)
2. **Phase 2**: Migrate to Oracle Cloud Always Free tier (production-ready)

---

## Table of Contents
- [Prerequisites](#prerequisites)
- [Phase 1: Local Self-Hosted Runner](#phase-1-local-self-hosted-runner)
- [Running Multiple Runners in Parallel](#running-multiple-runners-in-parallel)
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

## Running Multiple Runners in Parallel

By default, each runner handles **one job at a time**. To run multiple jobs concurrently, you need to set up **multiple runner instances**.

### Understanding Runner Concurrency

- **1 runner = 1 concurrent job**
- **Multiple runners = parallel job execution**
- **Recommendation**: 2-4 runners on a local Mac (depending on CPU cores and RAM)

### Check Your System Resources

```bash
# Check CPU cores
sysctl -n hw.ncpu

# Check available RAM (in GB)
echo "$(( $(sysctl -n hw.memsize) / 1024 / 1024 / 1024 )) GB"
```

**Recommended runner count based on resources:**
- **8 cores, 16GB RAM**: 2-3 runners
- **10+ cores, 32GB+ RAM**: 3-4 runners
- **M1/M2/M3 Mac**: Can handle 2-4 runners easily

### Setup Multiple Runners

#### Step 1: Create Multiple Runner Directories

```bash
# Create directories for each runner (example: 3 runners)
for i in {1..3}; do
  mkdir -p ~/actions-runner-$i
done
```

#### Step 2: Download and Extract Runner for Each Instance

```bash
# Download once to runner-1
cd ~/actions-runner-1
curl -o actions-runner-osx-arm64-2.314.1.tar.gz -L \
  https://github.com/actions/runner/releases/download/v2.314.1/actions-runner-osx-arm64-2.314.1.tar.gz
tar xzf ./actions-runner-osx-arm64-2.314.1.tar.gz

# Copy to other runners
for i in {2..3}; do
  cp -r ~/actions-runner-1/* ~/actions-runner-$i/
done
```

#### Step 3: Get Runner Tokens from GitHub

For **each runner**, you need a separate token:

1. Go to: **Settings** → **Actions** → **Runners**
2. Click **"New self-hosted runner"**
3. Copy the token
4. Repeat for each runner (you'll need 3 tokens for 3 runners)

> **Important**: Each token can only be used once.

#### Step 4: Configure Each Runner

```bash
# Runner 1
cd ~/actions-runner-1
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token TOKEN_1 \
  --name "local-runner-1" \
  --labels "self-hosted,macOS,ARM64,worker-1"

# Runner 2
cd ~/actions-runner-2
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token TOKEN_2 \
  --name "local-runner-2" \
  --labels "self-hosted,macOS,ARM64,worker-2"

# Runner 3
cd ~/actions-runner-3
./config.sh --url https://github.com/YOUR_ORG/YOUR_REPO --token TOKEN_3 \
  --name "local-runner-3" \
  --labels "self-hosted,macOS,ARM64,worker-3"
```

#### Step 5: Install and Start All Runners

```bash
# Install all as services
for i in {1..3}; do
  cd ~/actions-runner-$i && sudo ./svc.sh install
done

# Start all runners
for i in {1..3}; do
  cd ~/actions-runner-$i && sudo ./svc.sh start
done

# Check status
for i in {1..3}; do
  echo "=== Runner $i ==="
  cd ~/actions-runner-$i && sudo ./svc.sh status
done
```

#### Step 6: Verify All Runners Are Connected

Go to GitHub: **Settings** → **Actions** → **Runners**

You should see:
- ✅ `local-runner-1` - Idle
- ✅ `local-runner-2` - Idle
- ✅ `local-runner-3` - Idle

### Managing Multiple Runners

```bash
# Start all runners
for i in {1..3}; do cd ~/actions-runner-$i && sudo ./svc.sh start; done

# Stop all runners
for i in {1..3}; do cd ~/actions-runner-$i && sudo ./svc.sh stop; done

# Check status of all runners
for i in {1..3}; do
  echo "=== Runner $i Status ==="
  cd ~/actions-runner-$i && sudo ./svc.sh status
  echo ""
done

# View logs for a specific runner
tail -f ~/actions-runner-1/_diag/Runner_*.log
```

### How Parallel Execution Works

Your workflows **don't need any changes**! Multiple runners automatically pick up jobs in parallel.

**Example 1: Matrix Jobs Run in Parallel**

```yaml
jobs:
  test-kotlin:
    runs-on: self-hosted
    strategy:
      fail-fast: false
      matrix:
        include:
          - service: security
          - service: assets
          - service: financialdata
    steps:
      - name: Test ${{ matrix.service }}
        run: echo "Testing ${{ matrix.service }}"
```

With 3 runners, all 3 matrix jobs run **simultaneously**!

**Example 2: Independent Jobs Run in Parallel**

```yaml
jobs:
  test-kotlin:
    runs-on: self-hosted
  test-web:
    runs-on: self-hosted
  test-golang:
    runs-on: self-hosted
```

With 3 runners, these 3 jobs run **simultaneously**!

### Performance Benefits

**Before (1 runner):**
- Security tests: 5 minutes
- Assets tests: 5 minutes
- FinancialData tests: 5 minutes
- **Total: 15 minutes** (sequential)

**After (3 runners):**
- All tests run in parallel
- **Total: ~5 minutes** (parallel)

### Monitoring Script

Create a script to monitor all runners:

```bash
cat > ~/monitor-runners.sh << 'EOF'
#!/bin/bash
echo "GitHub Actions Runners Status"
echo "=============================="
for i in {1..3}; do
  if [ -d ~/actions-runner-$i ]; then
    echo "Runner $i:"
    if pgrep -f "actions-runner-$i" > /dev/null; then
      echo "  Status: ✅ Running"
    else
      echo "  Status: ❌ Stopped"
    fi
  fi
done
echo ""
echo "System: $(ps aux | awk '{sum+=$3} END {print sum}')% CPU"
docker ps --format "{{.Names}}" | wc -l | xargs echo "Docker containers:"
EOF

chmod +x ~/monitor-runners.sh
```

Run it with:
```bash
~/monitor-runners.sh

# Or watch continuously
watch -n 10 ~/monitor-runners.sh
```

### Maintenance for Multiple Runners

#### Docker Cleanup

With multiple runners, Docker usage increases. Set up automatic cleanup:

```bash
# Create cleanup script
cat > ~/cleanup-docker-runners.sh << 'EOF'
#!/bin/bash
echo "Cleaning Docker for all runners..."
docker system prune -a -f
docker volume prune -f
echo "Cleanup complete!"
df -h
EOF

chmod +x ~/cleanup-docker-runners.sh

# Schedule with cron (every Sunday at 2 AM)
crontab -e
# Add this line:
0 2 * * 0 /Users/YOUR_USERNAME/cleanup-docker-runners.sh >> /Users/YOUR_USERNAME/cleanup.log 2>&1
```

#### Gradle Daemon Sharing

If running multiple Kotlin/Gradle jobs, configure shared Gradle cache:

```bash
# Add to ~/.gradle/gradle.properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon.idletimeout=3600000
org.gradle.workers.max=2
```

### Troubleshooting Parallel Runners

**Issue**: Only one runner works, others stay idle

**Solution**: Check that all runners show "Idle" status in GitHub. If any show "Offline":
```bash
cd ~/actions-runner-X && sudo ./svc.sh restart
```

**Issue**: High CPU/Memory usage

**Solution**: Reduce number of runners or limit Gradle workers:
```bash
# In ~/.gradle/gradle.properties
org.gradle.workers.max=2
```

**Issue**: Runners not picking up jobs evenly

**Solution**: This is normal. GitHub distributes jobs based on availability. All runners will be utilized over time.

### Removing a Runner

```bash
# Stop the service
cd ~/actions-runner-2
sudo ./svc.sh stop
sudo ./svc.sh uninstall

# Remove from GitHub
./config.sh remove --token REMOVAL_TOKEN

# Delete directory
rm -rf ~/actions-runner-2
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

**Option C**: Run multiple runners on Oracle Cloud

You can set up 2-4 runner instances on the same Oracle Cloud instance for parallel execution:

```bash
# On Oracle instance, follow the same steps as "Running Multiple Runners in Parallel"
# Create multiple runner directories
for i in {1..3}; do
  mkdir -p ~/actions-runner-$i
done

# Download and configure each runner (using separate tokens)
# The 4 OCPUs and 24GB RAM can easily handle 2-3 runners
```

This gives you **unlimited parallel CI/CD** for **$0 cost**!

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

### Phase 1: Local Runner (Single)
- **Cost**: $0 (uses your computer)
- **Electricity**: ~$5-10/month (if running 24/7)
- **Concurrency**: 1 job at a time

### Phase 1: Local Multiple Runners (2-4 runners)
- **Cost**: $0 (uses your computer)
- **Electricity**: ~$10-20/month (if running 24/7)
- **Concurrency**: 2-4 jobs in parallel
- **Time savings**: 50-75% reduction in total CI time

### Phase 2: Oracle Cloud (Single Runner)
- **Cost**: $0 (Always Free tier)
- **No credit card charges**
- **No time limit** (truly free forever)
- **Concurrency**: 1 job at a time

### Phase 2: Oracle Cloud (Multiple Runners)
- **Cost**: $0 (Always Free tier - 4 OCPUs can handle 2-3 runners)
- **Concurrency**: 2-3 jobs in parallel
- **Best value**: Unlimited parallel CI/CD for $0

### Comparison with GitHub Actions
- **GitHub Free**: 2,000 minutes/month (~33 hours)
- **GitHub Team**: $4/user/month + $0.008/minute
- **Self-hosted (1 runner)**: Unlimited minutes, $0, 1 job at a time
- **Self-hosted (3 runners)**: Unlimited minutes, $0, 3 jobs in parallel

**Example cost savings with 3 parallel runners:**
- If you run 100 hours of CI/month with 3x parallelization
- GitHub equivalent: 300 minutes of parallel time
- GitHub Team cost: ~$2.40/month per concurrent job
- Self-hosted cost: **$0**

---

## Next Steps

### Quick Start Path
1. ✅ Complete Phase 1 (local runner - single runner)
2. ✅ Test with a simple workflow
3. ✅ Verify everything works

### Performance Path (Recommended)
1. ✅ Complete Phase 1 (local runner - single runner)
2. ✅ Set up multiple runners (2-3) for parallel execution
3. ✅ Test parallel job execution
4. ✅ Monitor resource usage and adjust runner count

### Production Path
1. ✅ Complete Phase 1 locally (with multiple runners)
2. ✅ Create Oracle Cloud account
3. ✅ Setup Phase 2 (Oracle runner with 2-3 runner instances)
4. ✅ Migrate workflows to Oracle runners
5. ✅ Remove local runners or keep as backup
6. ✅ Setup monitoring and automatic cleanup
7. ✅ Configure alerts for failed jobs

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
**Updated**: 2026-02-12
**Version**: 2.0
**Status**: Production ready with parallel runner support
