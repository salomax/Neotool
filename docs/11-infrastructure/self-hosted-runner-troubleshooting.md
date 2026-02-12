# Self-Hosted Runner Troubleshooting Guide

Common issues and solutions for self-hosted GitHub Actions runners on macOS.

---

## Docker Login Issues

### Error: "User interaction is not allowed. (-25308)"

**Full error:**
```
Error: error saving credentials: error storing credentials - err: exit status 1, out: `User interaction is not allowed. (-25308)`
```

**Cause:**
This is a macOS Keychain error. Docker Desktop/Colima tries to store credentials in the macOS Keychain, but the GitHub Actions runner service doesn't have permission to access it (runs without a logged-in user session).

**Solution 1: Disable Keychain Credential Store** (Recommended)

Update your Docker config to not use macOS Keychain:

```bash
# Edit ~/.docker/config.json
cat > ~/.docker/config.json << 'EOF'
{
  "auths": {},
  "currentContext": "colima",
  "credsStore": ""
}
EOF
```

**What this does:**
- Removes `osxkeychain` as the credential helper
- Stores credentials in plain text in `~/.docker/config.json`
- Safe for CI/CD since tokens are ephemeral (expire after job completes)

**Solution 2: Use Direct Docker Login** (Also Recommended)

Instead of using `docker/login-action@v3`, use direct `docker login`:

```yaml
# ❌ Old way (causes keychain issues)
- name: Login to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

# ✅ New way (works on self-hosted)
- name: Login to GHCR
  run: |
    echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
```

**Why this works:**
- Bypasses the GitHub Action's credential helper detection
- Uses stdin for password (secure, not visible in logs)
- No keychain interaction needed

**Solution 3: Grant Keychain Access** (Not Recommended)

You can grant the runner service keychain access, but this is complex and less secure:

```bash
# Create a new keychain for the runner
security create-keychain -p "" runner.keychain
security unlock-keychain -p "" runner.keychain
security set-keychain-settings -t 3600 -l runner.keychain

# Add to Docker config
{
  "auths": {},
  "currentContext": "colima",
  "credsStore": "osxkeychain"
}
```

Not recommended because:
- More complex setup
- Requires keychain management
- Can still have permission issues
- Solutions 1 & 2 are simpler and work better

---

## Path Changes Not Detected

### Issue: `test-*` jobs always skipped

**Symptoms:**
- All test jobs show "This job was skipped"
- `changes` job completes successfully
- No errors in logs

**Cause:**
The `dorny/paths-filter` action needs full git history to detect changes.

**Solution:**

Update the checkout in the `changes` job:

```yaml
jobs:
  changes:
    runs-on: self-hosted
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # ← Add this to fetch full history

      - uses: dorny/paths-filter@v3
        # ...
```

**Verify it works:**

```bash
# In your repository
git fetch origin main
git diff --name-only origin/main...HEAD
```

This should show changed files. If it does, the filter will work.

---

## Runner Not Appearing in GitHub

### Symptom: Runner shows "Offline" or doesn't appear

**Check 1: Service Status**

```bash
cd ~/actions-runner-1
sudo ./svc.sh status
```

**If stopped:**
```bash
sudo ./svc.sh start
```

**Check 2: Runner Logs**

```bash
tail -f ~/actions-runner-1/_diag/Runner_*.log
```

Look for errors like:
- Network connectivity issues
- Token expiration
- Configuration problems

**Common fixes:**

```bash
# Restart runner
cd ~/actions-runner-1
sudo ./svc.sh stop
sudo ./svc.sh start

# If that doesn't work, reconfigure
./config.sh remove --token OLD_TOKEN
./config.sh --url https://github.com/ORG/REPO --token NEW_TOKEN
sudo ./svc.sh install
sudo ./svc.sh start
```

---

## Docker Permission Denied

### Error: "permission denied while trying to connect to the Docker daemon"

**Cause:**
The runner user doesn't have permission to access Docker.

**Solution:**

```bash
# Add current user to docker group
sudo usermod -aG docker $USER

# For Colima
sudo usermod -aG docker $(whoami)

# Logout and login again (or restart runner)
exit
# SSH back in
```

**Verify:**
```bash
docker ps
# Should work without sudo
```

**For runner service:**
```bash
# Check which user runs the runner
ps aux | grep Runner.Listener

# Add that user to docker group
sudo usermod -aG docker <username>

# Restart runner
cd ~/actions-runner-1
sudo ./svc.sh restart
```

---

## High Memory/CPU Usage

### Symptom: System slows down during builds

**Check resource usage:**

```bash
# Overall system
htop

# Docker containers
docker stats

# Runner processes
ps aux | grep Runner
```

**Solution 1: Limit Parallel Runners**

If you have 3 runners but only 8GB RAM:

```bash
# Stop extra runners
cd ~/actions-runner-3
sudo ./svc.sh stop
sudo ./svc.sh uninstall
```

**Solution 2: Limit Gradle Workers**

Edit `~/.gradle/gradle.properties`:

```properties
org.gradle.workers.max=2
org.gradle.daemon.idletimeout=600000
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
```

**Solution 3: Limit Go Build Parallelism**

```bash
# Set in workflow or globally
export GOMAXPROCS=2
```

**Solution 4: Limit Docker Build Parallelism**

In Dockerfile:

```dockerfile
# Limit parallel builds
RUN gradle build --max-workers=2 --no-daemon
```

---

## Cache Not Working

### Symptom: Builds always take full time, cache never hits

**Check cache directory:**

```bash
# Docker buildx cache
ls -lh /tmp/.buildx-cache/security
du -sh /tmp/.buildx-cache/*

# Gradle cache
du -sh ~/.gradle/caches

# Go cache
du -sh ~/go/pkg/mod

# pnpm cache
du -sh ~/.pnpm-store
```

**If cache directory is empty or missing:**

```bash
# Recreate with proper permissions
sudo rm -rf /tmp/.buildx-cache
mkdir -p /tmp/.buildx-cache
chmod -R 755 /tmp/.buildx-cache
```

**Check Docker buildx:**

```bash
docker buildx ls
docker buildx du --verbose
```

**Force cache rebuild:**

```bash
# Clean everything
docker buildx prune -a -f

# Run build again to populate cache
```

---

## Disk Space Full

### Error: "no space left on device"

**Check disk usage:**

```bash
df -h /
df -h /tmp
```

**Find large directories:**

```bash
du -sh /tmp/.buildx-cache/* | sort -h
du -sh ~/.gradle/caches/* | sort -h
du -sh ~/actions-runner-*/_work/* | sort -h
```

**Clean up:**

```bash
# Docker (most common culprit)
docker system prune -a -f
docker volume prune -f

# Buildx cache
docker buildx prune -a -f

# Gradle
rm -rf ~/.gradle/caches/modules-2/files-2.1/*

# Go
go clean -cache -modcache

# pnpm
pnpm store prune

# Old runner work directories
for i in {1..3}; do
  rm -rf ~/actions-runner-$i/_work/*
done
```

**Set up automatic cleanup:**

```bash
cat > ~/cleanup-all.sh << 'EOF'
#!/bin/bash
echo "Starting cleanup..."

# Docker
docker system prune -a -f --filter "until=72h"
docker volume prune -f

# Build cache older than 30 days
find /tmp/.buildx-cache -type f -mtime +30 -delete

# Gradle
~/.gradle/wrapper/dists/*/*/gradle-*/bin/gradle clean 2>/dev/null || true

echo "Cleanup complete!"
df -h /
EOF

chmod +x ~/cleanup-all.sh

# Run weekly
crontab -e
# Add: 0 3 * * 0 ~/cleanup-all.sh >> ~/cleanup.log 2>&1
```

---

## Network Issues

### Error: "Could not resolve host" or "Connection timeout"

**Check connectivity:**

```bash
# Test GitHub
curl -I https://github.com

# Test GHCR
curl -I https://ghcr.io

# Test DNS
nslookup github.com
```

**Common fixes:**

```bash
# Flush DNS (macOS)
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder

# Restart runner
cd ~/actions-runner-1
sudo ./svc.sh restart
```

**For Colima:**

```bash
# Restart Colima
colima stop
colima start

# Check Colima status
colima status
```

---

## Runner Can't Pull Private Images

### Error: "pull access denied"

**Cause:**
Runner doesn't have credentials for private registries.

**Solution:**

```bash
# Login manually once (credentials persist)
echo "$GITHUB_TOKEN" | docker login ghcr.io -u USERNAME --password-stdin

# Or add to workflow
- name: Login to GHCR
  run: |
    echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
```

---

## Gradle Daemon Issues

### Error: "Gradle daemon disappeared unexpectedly"

**Solution:**

```bash
# Kill all Gradle daemons
pkill -f GradleDaemon

# Clean Gradle cache
rm -rf ~/.gradle/caches
rm -rf ~/.gradle/daemon

# Add to ~/.gradle/gradle.properties
org.gradle.daemon=true
org.gradle.daemon.idletimeout=600000
org.gradle.jvmargs=-Xmx2g
```

---

## Multiple Runners Conflicting

### Issue: Runners interfere with each other

**Symptoms:**
- Port conflicts
- Cache corruption
- Build failures

**Solution: Isolate work directories**

Make sure each runner has its own directories:

```bash
# Check runner configs
cd ~/actions-runner-1 && grep workFolder .runner
cd ~/actions-runner-2 && grep workFolder .runner
cd ~/actions-runner-3 && grep workFolder .runner

# Should all be different, like:
# ~/actions-runner-1/_work
# ~/actions-runner-2/_work
# ~/actions-runner-3/_work
```

**Shared cache is OK:**
- Gradle cache: `~/.gradle/` (shared)
- Go cache: `~/go/pkg/mod/` (shared)
- pnpm cache: `~/.pnpm-store/` (shared)
- Docker buildx: `/tmp/.buildx-cache/` (shared)

These are safe to share across runners.

---

## Summary of Quick Fixes

```bash
# Docker login issues
cat > ~/.docker/config.json << 'EOF'
{"auths":{},"currentContext":"colima","credsStore":""}
EOF

# Runner not appearing
cd ~/actions-runner-1 && sudo ./svc.sh restart

# Permission denied
sudo usermod -aG docker $USER && exit

# High memory
echo "org.gradle.workers.max=2" >> ~/.gradle/gradle.properties

# Cache not working
sudo rm -rf /tmp/.buildx-cache && mkdir -p /tmp/.buildx-cache

# Disk full
docker system prune -a -f && docker buildx prune -a -f

# Network issues (Colima)
colima restart

# Gradle issues
pkill -f GradleDaemon && rm -rf ~/.gradle/daemon
```

---

## Getting Help

**Check logs:**
```bash
# Runner logs
tail -f ~/actions-runner-1/_diag/Runner_*.log

# Workflow logs
# Go to GitHub Actions UI

# System logs
tail -f /var/log/system.log
```

**Debug mode:**

In your workflow, add:

```yaml
env:
  ACTIONS_RUNNER_DEBUG: true
  ACTIONS_STEP_DEBUG: true
```

This provides verbose logging.

---

**Created**: 2026-02-12
**Version**: 1.0
**Status**: Comprehensive troubleshooting guide for macOS self-hosted runners
