# Complete Web Service Deployment Guide

Comprehensive tutorial for deploying Next.js web service to Kubernetes with Cloudflare CDN integration.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Phase 1: Kubernetes Deployment](#phase-1-kubernetes-deployment)
  - [Step 1: Configure Manifests](#step-1-configure-manifests)
  - [Step 2: Build Application](#step-2-build-application)
  - [Step 3: Build & Push Docker Image](#step-3-build--push-docker-image)
  - [Step 4: Deploy to Kubernetes](#step-4-deploy-to-kubernetes)
  - [Step 5: Verify Deployment](#step-5-verify-deployment)
  - [Step 6: Configure DNS](#step-6-configure-dns)
  - [Step 7: Test Deployment](#step-7-test-deployment)
- [Phase 2: Cloudflare Integration](#phase-2-cloudflare-integration)
  - [Step 8: Setup Cloudflare Account](#step-8-setup-cloudflare-account)
  - [Step 9: Configure SSL/TLS](#step-9-configure-ssltls)
  - [Step 10: Configure Firewall & Security](#step-10-configure-firewall--security)
  - [Step 11: Configure Caching](#step-11-configure-caching)
  - [Step 12: Enable Cloudflare Proxy](#step-12-enable-cloudflare-proxy)
  - [Step 13: Optimize Performance](#step-13-optimize-performance)
- [Operations & Maintenance](#operations--maintenance)
- [Troubleshooting](#troubleshooting)
- [Reference Commands](#reference-commands)

---

## Overview

This guide covers deploying a Next.js web application to your Kubernetes cluster in two phases:

**Phase 1**: Deploy to Kubernetes with direct HTTPS access
```
Internet → Your K8S Cluster → Next.js App
```

**Phase 2**: Add Cloudflare CDN/Proxy for performance and security
```
Internet → Cloudflare → Your K8S Cluster → Next.js App
```

**Time Required**:
- Phase 1: 30-60 minutes
- Phase 2: 30-45 minutes

---

## Prerequisites

Before starting, verify you have:

### Required
- [ ] Access to your Kubernetes cluster
- [ ] kubectl configured: `kubectl get nodes`
- [ ] Flux installed: `flux check`
- [ ] Docker installed: `docker --version`
- [ ] GitHub account and personal access token
- [ ] Domain name (or use placeholder temporarily)

### Optional
- [ ] Cloudflare account (for Phase 2)
- [ ] Experience with Kubernetes (helpful but not required)

---

# Phase 1: Kubernetes Deployment

## Step 1: Configure Manifests

### 1.1 Set Your Variables

```bash
cd /Users/salomax/src/Neotool

# Set your values
export GITHUB_USERNAME="your-github-username"
export DOMAIN="yourdomain.com"  # Or use placeholder
export IMAGE_TAG="v1.0.0"
```

### 1.2 Update Deployment Image Path

**Edit**: `infra/kubernetes/flux/apps/web/nextjs/deployment.yaml`

**Find line 13** and replace `USERNAME`:

```yaml
# Before:
image: ghcr.io/USERNAME/neotool-web:latest

# After:
image: ghcr.io/your-github-username/neotool-web:latest
```

**Quick command**:
```bash
sed -i "s/USERNAME/$GITHUB_USERNAME/g" \
  infra/kubernetes/flux/apps/web/nextjs/deployment.yaml
```

### 1.3 Update Ingress Domain

**Edit**: `infra/kubernetes/flux/apps/web/nextjs/ingress.yaml`

**Replace `yourdomain.com` in TWO places** (lines 24 and 34):

```yaml
# Line 24 - host:
- host: yourdomain.com  # ← Replace this

# Line 34 - tls.hosts:
  - hosts:
      - yourdomain.com  # ← Replace this
```

**Quick command**:
```bash
sed -i "s/yourdomain.com/$DOMAIN/g" \
  infra/kubernetes/flux/apps/web/nextjs/ingress.yaml
```

### 1.4 Update Backend API Endpoint (if needed)

**Edit**: `infra/kubernetes/flux/apps/web/nextjs/deployment.yaml`

**Line 33** - Update if your backend service has a different name:

```yaml
- name: NEXT_PUBLIC_GRAPHQL_ENDPOINT
  value: "http://neotool-backend.production.svc.cluster.local:8080/graphql"
  # ↑ Update 'neotool-backend' if needed
```

---

## Step 2: Build Application

### 2.1 Test Build Locally (Recommended)

```bash
cd web

# Install dependencies
pnpm install

# Run checks
pnpm typecheck
pnpm lint

# Build
pnpm build

# Test locally
pnpm start
# Visit http://localhost:3000
```

### 2.2 Test Health Endpoint

```bash
# While app is running locally
curl http://localhost:3000/api/health
```

**Expected response**:
```json
{
  "status": "ok",
  "timestamp": "2026-01-15T...",
  "uptime": 123.456
}
```

---

## Step 3: Build & Push Docker Image

### 3.1 Login to GitHub Container Registry

**Create GitHub token** (if you don't have one):
1. Go to: https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scopes: `write:packages`, `read:packages`
4. Copy the token

**Login**:
```bash
export GITHUB_TOKEN="ghp_your_token_here"
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin
```

Expected: `Login Succeeded`

### 3.2 Build Docker Image

```bash
cd /Users/salomax/src/Neotool/web

docker build -t ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG .
```

This takes 3-5 minutes. Expected output:
```
[+] Building 180.2s (18/18) FINISHED
...
=> => naming to ghcr.io/yourusername/neotool-web:v1.0.0
```

### 3.3 Test Docker Image

```bash
# Run container
docker run -p 3000:3000 ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG

# In another terminal
curl http://localhost:3000/api/health

# Stop container: Ctrl+C
```

### 3.4 Push to Registry

```bash
# Push versioned tag
docker push ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG

# Also push as latest
docker tag ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG \
           ghcr.io/$GITHUB_USERNAME/neotool-web:latest
docker push ghcr.io/$GITHUB_USERNAME/neotool-web:latest
```

### 3.5 Make Image Public (Optional)

For public repos:
1. Visit: https://github.com/users/$GITHUB_USERNAME/packages/container/neotool-web/settings
2. Change visibility to "Public"

### 3.6 Private Registry Setup (If Needed)

If image is private, create pull secret:

```bash
kubectl create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=$GITHUB_USERNAME \
  --docker-password=$GITHUB_TOKEN \
  --namespace=production
```

Then uncomment in `deployment.yaml` (lines 62-63):
```yaml
imagePullSecrets:
  - name: ghcr-credentials
```

---

## Step 4: Deploy to Kubernetes

### 4.1 Verify Cluster State

```bash
# Check cluster access
kubectl config current-context

# Check infrastructure is ready
kubectl get kustomization -n flux-system
# Should see 'infrastructure' with READY=True

# Check namespace exists
kubectl get namespace production
```

### 4.2 Commit Changes

```bash
cd /Users/salomax/src/Neotool

# Check what will be committed
git status

# Add files
git add infra/kubernetes/flux/apps/web/
git add infra/kubernetes/flux/clusters/production/web.yaml
git add web/src/app/api/health/

# Commit
git commit -m "feat: Deploy Next.js web service to Kubernetes

- Add deployment with health checks and resource limits
- Add service exposing port 3000
- Add ingress for $DOMAIN with TLS
- Add Traefik middleware for security headers
- Add health check API endpoint
- Add Flux Kustomization for GitOps deployment"

# Push
git push origin main
```

### 4.3 Trigger Flux Reconciliation

```bash
# Update Git source
flux reconcile source git flux-system

# Deploy web service
flux reconcile kustomization web -n flux-system
```

**Expected output**:
```
✔ applied revision main@sha1:xxxxx
```

---

## Step 5: Verify Deployment

### 5.1 Check Flux Status

```bash
kubectl get kustomization -n flux-system web
```

**Expected**:
```
NAME   READY   STATUS                       AGE
web    True    Applied revision: main/...   1m
```

**If READY is False**:
```bash
kubectl describe kustomization -n flux-system web
```

### 5.2 Watch Pod Starting

```bash
kubectl get pods -n production -l app=neotool-web -w
```

**Expected progression**:
```
NAME                           READY   STATUS              RESTARTS   AGE
neotool-web-xxx-yyy            0/1     ContainerCreating   0          5s
neotool-web-xxx-yyy            0/1     Running             0          15s
neotool-web-xxx-yyy            1/1     Running             0          30s
```

Press `Ctrl+C` to stop watching.

### 5.3 Check Pod Details (If Issues)

```bash
# Describe pod
kubectl describe pod -n production -l app=neotool-web

# View logs
kubectl logs -n production -l app=neotool-web

# Follow logs
kubectl logs -n production -l app=neotool-web -f
```

**Common pod issues**:
- **ImagePullBackOff**: Check image name/tag, registry credentials
- **CrashLoopBackOff**: Check logs for application errors
- **Pending**: Check resource quota

### 5.4 Check Service & Ingress

```bash
# Check service
kubectl get svc -n production neotool-web

# Check ingress
kubectl get ingress -n production neotool-web

# Check certificate
kubectl get certificate -n production neotool-web-tls
```

### 5.5 Check Certificate Status

```bash
# Wait for certificate (takes 1-2 minutes)
kubectl get certificate -n production neotool-web-tls -w

# If not ready after 5 minutes
kubectl describe certificate -n production neotool-web-tls
```

---

## Step 6: Configure DNS

### 6.1 Get Server IP

```bash
# Get your server's external IP
kubectl get nodes -o wide
# Look at EXTERNAL-IP or INTERNAL-IP
```

### 6.2 Add DNS Record

Add A record at your DNS provider:
```
Type: A
Name: @  (for yourdomain.com) or subdomain
Value: YOUR_SERVER_IP
TTL: 300 (5 minutes for testing)
```

### 6.3 Wait for DNS Propagation

```bash
# Check DNS resolution (wait 1-10 minutes)
dig $DOMAIN +short
# Should return your server IP
```

---

## Step 7: Test Deployment

### 7.1 Test from Inside Cluster

```bash
kubectl run -it --rm debug \
  --image=curlimages/curl \
  --restart=Never \
  -n production \
  -- curl http://neotool-web.production.svc.cluster.local:3000/api/health
```

**Expected**: `{"status":"ok",...}`

### 7.2 Test via Port Forward

```bash
# Forward port to localhost
kubectl port-forward -n production svc/neotool-web 3000:3000

# In browser or another terminal:
# http://localhost:3000
# http://localhost:3000/api/health

# Press Ctrl+C to stop
```

### 7.3 Test via Domain

```bash
# Test HTTP (redirects to HTTPS)
curl -I http://$DOMAIN

# Test HTTPS
curl https://$DOMAIN/api/health

# Test in browser
open https://$DOMAIN
```

**✅ Phase 1 Complete!** Your web service is now running on Kubernetes with HTTPS.

---

# Phase 2: Cloudflare Integration

## Architecture with Cloudflare

```
Internet
    ↓
Cloudflare (Proxy + CDN + Security)
    ↓
    ├─→ yourdomain.com (Web - Cached at Edge)
    │   ↓
    │   Your K8S Cluster → Next.js App
    │
    └─→ api.yourdomain.com (API - Proxied)
        ↓
        Your K8S Cluster → Backend Service
```

## Benefits

- **DDoS Protection**: Automatic attack mitigation
- **CDN Caching**: Static assets cached globally
- **WAF**: Web Application Firewall
- **Rate Limiting**: Protect against abuse
- **SSL/TLS**: Managed certificates
- **Analytics**: Traffic insights
- **Performance**: Global edge network

---

## Step 8: Setup Cloudflare Account

### 8.1 Create Account

1. Go to https://dash.cloudflare.com/sign-up
2. Create account and verify email

### 8.2 Add Domain

1. Click "Add site"
2. Enter your domain: `yourdomain.com`
3. Select plan (Free is sufficient)
4. Click "Continue"

### 8.3 Review DNS Records

Cloudflare scans existing DNS. Verify:

```
Type    Name    Content         Proxy Status
A       @       YOUR_SERVER_IP  DNS only (gray cloud)
```

**Important**: Keep proxy disabled (gray cloud) for now.

### 8.4 Change Nameservers

Cloudflare provides nameservers like:
```
dana.ns.cloudflare.com
fred.ns.cloudflare.com
```

**Update at your domain registrar**:
1. Log in to registrar (GoDaddy, Namecheap, etc.)
2. Find DNS/Nameserver settings
3. Replace with Cloudflare nameservers
4. Save

**Wait**: 2-24 hours (usually < 1 hour)

**Check**:
```bash
dig NS $DOMAIN
# Should show Cloudflare nameservers
```

---

## Step 9: Configure SSL/TLS

### 9.1 Set Encryption Mode

1. Go to **SSL/TLS → Overview**
2. Select: **Full (strict)**

**Why Full (strict)?**
- Encrypts visitor → Cloudflare → your server
- Validates your Let's Encrypt certificate
- End-to-end encryption

### 9.2 Enable Security Features

Go to **SSL/TLS → Edge Certificates**, enable:

- [x] Always Use HTTPS
- [x] HTTP Strict Transport Security (HSTS)
- [x] Minimum TLS Version: 1.2
- [x] TLS 1.3
- [x] Automatic HTTPS Rewrites

### 9.3 Configure HSTS

Click "Enable HSTS":
- Max Age: 6 months
- [x] Include subdomains
- [x] Preload

**⚠️ Warning**: HSTS preload is permanent. Only enable when HTTPS works perfectly.

---

## Step 10: Configure Firewall & Security

### 10.1 Enable WAF Managed Rules

1. Go to **Security → WAF**
2. Enable:
   - [x] Cloudflare Managed Ruleset
   - [x] Cloudflare OWASP Core Ruleset

### 10.2 Create Rate Limiting Rules

Go to **Security → WAF → Rate limiting rules**

**Rule 1: Rate Limit API**
```
Name: Rate limit API
If: Hostname equals api.yourdomain.com
Then: Block for 10 minutes
Requests: 100 per 10 minutes
```

**Rule 2: Block Bad Bots**
```
Name: Block bad bots
If: Hostname equals api.yourdomain.com
AND Known Bot equals true
Then: Block
```

### 10.3 Add Security Headers

Go to **Rules → Transform Rules → Modify Response Header**

Create rule:
```
Name: Security headers
If: All incoming requests
Then set headers:
  - X-Content-Type-Options: nosniff
  - X-Frame-Options: SAMEORIGIN
  - X-XSS-Protection: 1; mode=block
  - Referrer-Policy: strict-origin-when-cross-origin
```

---

## Step 11: Configure Caching

### 11.1 Basic Caching Settings

1. Go to **Caching → Configuration**
2. Settings:
   - Caching Level: Standard
   - Browser Cache TTL: 4 hours
   - [x] Always Online
   - [ ] Development Mode

### 11.2 Create Page Rules

Go to **Rules → Page Rules**

**Rule 1: No Cache for API Routes**
```
URL: *yourdomain.com/api/*
Settings:
  - Cache Level: Bypass
```

**Rule 2: Cache Static Assets**
```
URL: *yourdomain.com/_next/static/*
Settings:
  - Cache Level: Cache Everything
  - Edge Cache TTL: 1 year
  - Browser Cache TTL: 1 year
```

**Rule 3: Cache Images**
```
URL: *yourdomain.com/*.{jpg,jpeg,png,gif,webp,svg,ico}
Settings:
  - Cache Level: Cache Everything
  - Edge Cache TTL: 1 month
  - Browser Cache TTL: 1 month
```

### 11.3 API Caching (For Backend)

When you deploy backend:

```
URL: *api.yourdomain.com/*
Settings:
  - Cache Level: Cache Everything
  - Edge Cache TTL: 5 minutes
  - Browser Cache TTL: 1 minute
Note: Only caches GET requests
```

---

## Step 12: Enable Cloudflare Proxy

### 12.1 Test Direct Access First

```bash
# Test before enabling proxy
curl -H "Host: $DOMAIN" http://YOUR_SERVER_IP/api/health
# Should work
```

### 12.2 Enable Proxy (Orange Cloud)

1. Go to **DNS → Records**
2. For each record, click cloud icon:
   - `@` (yourdomain.com): Gray → **Orange**
   - `www`: Gray → **Orange**
   - `api`: Gray → **Orange** (when ready)

3. Save changes

### 12.3 Verify Proxy Active

```bash
# Check DNS returns Cloudflare IPs
dig $DOMAIN +short
# Should return 104.x.x.x or 172.x.x.x

# Check response headers
curl -I https://$DOMAIN
# Should include:
# server: cloudflare
# cf-ray: xxxxx
```

### 12.4 Test Caching

```bash
# First request (MISS)
curl -I https://$DOMAIN/_next/static/test.js

# Second request (HIT)
curl -I https://$DOMAIN/_next/static/test.js
# Should include: cf-cache-status: HIT
```

---

## Step 13: Optimize Performance

### 13.1 Enable Auto Minify

Go to **Speed → Optimization**, enable:
- [x] JavaScript
- [x] CSS
- [x] HTML

### 13.2 Verify Brotli

```bash
curl -H "Accept-Encoding: br" -I https://$DOMAIN
# Should include: content-encoding: br
```

### 13.3 Enable Polish (Paid Plans)

For image optimization:
1. Go to **Speed → Optimization → Polish**
2. Enable: Lossy or Lossless

### 13.4 Configure Analytics

1. Go to **Analytics → Web Analytics**
2. Automatic analytics are enabled by default
3. Monitor:
   - Cache hit ratio (goal: >80%)
   - Bandwidth savings
   - Security events
   - Response times

---

## Operations & Maintenance

### Update Application

```bash
# Build new image
cd web
docker build -t ghcr.io/$GITHUB_USERNAME/neotool-web:v1.1.0 .
docker push ghcr.io/$GITHUB_USERNAME/neotool-web:v1.1.0

# Update deployment.yaml with new tag
# Commit and push

# Or update directly
kubectl set image deployment/neotool-web -n production \
  nextjs=ghcr.io/$GITHUB_USERNAME/neotool-web:v1.1.0

# Watch rollout
kubectl rollout status deployment/neotool-web -n production

# Purge Cloudflare cache
# Go to Cloudflare Dashboard → Caching → Purge Everything
```

### Scale Replicas

```bash
# Manual scaling
kubectl scale deployment neotool-web -n production --replicas=3

# Or enable autoscaling
kubectl autoscale deployment neotool-web -n production \
  --cpu-percent=70 --min=1 --max=5
```

### Rollback

```bash
# View history
kubectl rollout history deployment/neotool-web -n production

# Rollback to previous
kubectl rollout undo deployment/neotool-web -n production

# Rollback to specific revision
kubectl rollout undo deployment/neotool-web -n production --to-revision=2
```

### Monitor Resources

```bash
# Resource usage
kubectl top pod -n production -l app=neotool-web

# View logs
kubectl logs -n production -l app=neotool-web -f --tail=100

# Get pod shell
kubectl exec -n production -it deployment/neotool-web -- sh
```

---

## Troubleshooting

### Pod Not Starting

**Check status**:
```bash
kubectl describe pod -n production -l app=neotool-web
kubectl logs -n production -l app=neotool-web
```

**Common issues**:
- **ImagePullBackOff**: Verify image exists and credentials are correct
- **CrashLoopBackOff**: Check logs for application errors
- **Pending**: Check resource quota: `kubectl describe resourcequota -n production`

### Certificate Not Ready

```bash
# Check certificate
kubectl describe certificate -n production neotool-web-tls

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager --tail=50

# Verify DNS points to server
dig $DOMAIN +short

# Verify port 80 accessible (for ACME challenge)
curl http://$DOMAIN/.well-known/acme-challenge/test
```

### 502 Bad Gateway

```bash
# Check pods are ready
kubectl get pods -n production -l app=neotool-web

# Check service endpoints
kubectl get endpoints -n production neotool-web

# Check Traefik logs
kubectl logs -n kube-system -l app.kubernetes.io/name=traefik --tail=50
```

### Cloudflare 521 Error (Web server down)

```bash
# Check pods running
kubectl get pods -n production

# Temporarily disable proxy (gray cloud) to test direct access
# Go to Cloudflare DNS, click orange cloud to gray

# Test direct access
curl https://$DOMAIN/api/health
```

### Cloudflare 525 Error (SSL handshake failed)

- Verify SSL/TLS mode is "Full (strict)"
- Check certificate valid: `kubectl get certificate -n production`
- Verify Traefik serving HTTPS on port 443

### Cache Not Working

```bash
# Check cache status
curl -I https://$DOMAIN/_next/static/test.js | grep cf-cache-status

# Enable Development Mode temporarily (bypasses cache for testing)
# Cloudflare Dashboard → Caching → Configuration → Development Mode

# Verify page rules are correct
# Cloudflare Dashboard → Rules → Page Rules
```

---

## Reference Commands

### Kubernetes

```bash
# View all resources
kubectl get all -n production -l app=neotool-web

# Logs
kubectl logs -n production -l app=neotool-web -f --tail=100

# Exec into pod
kubectl exec -n production -it deployment/neotool-web -- sh

# Port forward
kubectl port-forward -n production svc/neotool-web 3000:3000

# Restart deployment
kubectl rollout restart deployment/neotool-web -n production

# Scale
kubectl scale deployment neotool-web -n production --replicas=2

# Update image
kubectl set image deployment/neotool-web -n production \
  nextjs=ghcr.io/$GITHUB_USERNAME/neotool-web:v1.1.0

# Rollback
kubectl rollout undo deployment/neotool-web -n production

# Resource usage
kubectl top pod -n production -l app=neotool-web

# Events
kubectl get events -n production --sort-by='.lastTimestamp'
```

### Flux

```bash
# Check Flux status
flux check

# List kustomizations
flux get kustomizations -n flux-system

# Reconcile
flux reconcile kustomization web -n flux-system --with-source

# View logs
flux logs --level=error --follow
```

### Docker

```bash
# Build
docker build -t ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG .

# Test locally
docker run -p 3000:3000 ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG

# Push
docker push ghcr.io/$GITHUB_USERNAME/neotool-web:$IMAGE_TAG

# Login
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin
```

---

## Success Checklist

### Phase 1: Kubernetes Deployment

- [ ] Pod running: `kubectl get pods -n production -l app=neotool-web` → `1/1 Running`
- [ ] Service created: `kubectl get svc -n production neotool-web`
- [ ] Ingress configured: `kubectl get ingress -n production neotool-web`
- [ ] Certificate ready: `kubectl get certificate -n production neotool-web-tls` → `True`
- [ ] Health endpoint works: `curl https://$DOMAIN/api/health` → `{"status":"ok"}`
- [ ] Application loads: `curl https://$DOMAIN` → HTTP 200
- [ ] DNS configured: `dig $DOMAIN +short` → Your server IP
- [ ] HTTPS valid: No browser warnings

### Phase 2: Cloudflare Integration

- [ ] Nameservers updated: `dig NS $DOMAIN` → Cloudflare nameservers
- [ ] Proxy enabled: `dig $DOMAIN +short` → Cloudflare IPs (104.x or 172.x)
- [ ] SSL/TLS working: `curl -I https://$DOMAIN` → HTTP 200, server: cloudflare
- [ ] Cache working: `curl -I https://$DOMAIN/_next/static/test.js` → `cf-cache-status: HIT`
- [ ] Security headers: Visit https://securityheaders.com → Check score
- [ ] WAF active: Cloudflare Dashboard → Security → Events shows activity
- [ ] Analytics working: Cloudflare Dashboard → Analytics shows traffic

---

## Additional Resources

- **Kubernetes Docs**: https://kubernetes.io/docs/
- **Flux Docs**: https://fluxcd.io/docs/
- **Next.js Docs**: https://nextjs.org/docs
- **Cloudflare Docs**: https://developers.cloudflare.com/
- **Traefik Docs**: https://doc.traefik.io/traefik/

---

**🎉 Congratulations!** Your Next.js web service is now deployed with Kubernetes and protected by Cloudflare.

**Questions?** Check the troubleshooting section or review the specifications in [README.md](README.md).

---

**Last Updated**: 2026-01-15
**Version**: 1.0.0
