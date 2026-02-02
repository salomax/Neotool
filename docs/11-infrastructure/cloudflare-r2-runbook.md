---
title: Cloudflare R2 Object Storage Runbook
id: cloudflare-r2-runbook
type: infrastructure
category: storage
status: current
version: 1.0.0
tags: [cloudflare, r2, s3, storage, assets, minio]
ai_optimized: true
search_keywords: [cloudflare, r2, s3, object storage, assets, presigned url, bucket]
related:
  - 11-infrastructure/cloudflare-runbook.md
  - 11-infrastructure/k8s-runbook.md
  - 05-backend/assets-service.md
last_updated: 2026-01-29
---

# Cloudflare R2 Object Storage Runbook

> **Goal**: Set up Cloudflare R2 as production object storage for the Assets service, replacing MinIO used in development.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Architecture](#architecture)
4. [Step 1: Create R2 Buckets](#step-1-create-r2-buckets)
5. [Step 2: Generate API Credentials](#step-2-generate-api-credentials)
6. [Step 3: Configure Public Access (Custom Domain)](#step-3-configure-public-access-custom-domain)
7. [Step 4: Add Secrets to Vault](#step-4-add-secrets-to-vault)
8. [Step 5: Deploy Assets Service](#step-5-deploy-assets-service)
9. [Validation Checklist](#validation-checklist)
10. [Configuration Reference](#configuration-reference)
11. [Troubleshooting](#troubleshooting)
12. [Data Migration (Optional)](#data-migration-optional)

---

## Overview

Cloudflare R2 is an S3-compatible object storage service. Our Assets service uses the AWS SDK v2 which works seamlessly with R2.

```
Development:  MinIO (Docker)     → localhost:9000
Production:   Cloudflare R2      → <account-id>.r2.cloudflarestorage.com
```

**Key differences from MinIO:**

| Setting | MinIO (Dev) | R2 (Prod) |
|---------|-------------|-----------|
| Endpoint | `localhost:9000` | `<account-id>.r2.cloudflarestorage.com` |
| Protocol | HTTP | HTTPS (required) |
| Region | `us-east-1` | `auto` |
| Path Style | `true` | `false` |
| Port | `9000` | `443` |

---

## Prerequisites

- Cloudflare account with R2 enabled
- Domain on Cloudflare (for custom domain public access)
- Vault access to store secrets
- kubectl access to production cluster

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ Assets Service (Kotlin/Micronaut)                               │
│ └─ S3StorageClient (AWS SDK v2)                                │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 │ HTTPS (S3 API)
                 ↓
┌─────────────────────────────────────────────────────────────────┐
│ Cloudflare R2                                                    │
│ ├─ neotool-assets-public   (Custom Domain: assets.domain.com)  │
│ └─ neotool-assets-private  (Presigned URLs only)               │
└─────────────────────────────────────────────────────────────────┘

Public Assets:   https://assets.yourdomain.com/<key>
Private Assets:  Presigned URL (temporary, expires in 1 hour)
```

---

## Step 1: Create R2 Buckets

### Understanding Public vs Private Buckets

| Bucket | Purpose | Public Access | How Files Are Accessed |
|--------|---------|---------------|------------------------|
| `neotool-assets-public` | Profile images, logos, shared assets | **Enabled** (custom domain) | Direct URL: `https://assets.domain.com/file.png` |
| `neotool-assets-private` | Attachments, sensitive documents | **Disabled** (default) | Presigned URLs only (temporary, authenticated) |

> **Important**: By default, R2 buckets are private. You only need to enable public access for the public bucket.

### 1.1 Access R2 Dashboard

1. Log in to [Cloudflare Dashboard](https://dash.cloudflare.com)
2. Navigate to **R2 Object Storage** in the left sidebar
3. If R2 is not enabled, click **Get Started** and follow the setup

### 1.2 Create Public Bucket

1. Click **Create bucket**
2. Configure:
   - **Name**: `neotool-assets-public`
   - **Location**: Auto (or choose a region close to your users)
3. Click **Create bucket**
4. **After creation**: Configure public access (see [Step 3](#step-3-configure-public-access-custom-domain))

### 1.3 Create Private Bucket

1. Click **Create bucket**
2. Configure:
   - **Name**: `neotool-assets-private`
   - **Location**: Same as public bucket
3. Click **Create bucket**
4. **No additional configuration needed** - private by default

> **Note**: Bucket names must be unique within your Cloudflare account.

---

## Step 2: Generate API Credentials

### 2.1 Create API Token

1. In R2 dashboard, click **Manage R2 API Tokens**
2. Click **Create API token**
3. Configure permissions:
   - **Token name**: `neotool-assets-service`
   - **Permissions**: **Object Read & Write**
   - **Specify bucket(s)**: Select both buckets
     - `neotool-assets-public`
     - `neotool-assets-private`
4. Click **Create API Token**

### 2.2 Save Credentials

You will see:
- **Access Key ID**: `xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
- **Secret Access Key**: `yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy`
- **Endpoint**: `https://<account-id>.r2.cloudflarestorage.com`

> **Important**: Save these immediately. The Secret Access Key is only shown once.

### 2.3 Note Your Account ID

The Account ID is in the endpoint URL. You can also find it:
1. Go to Cloudflare Dashboard
2. Click on your account name (top right)
3. Copy the **Account ID** from the URL or overview page

---

## Step 3: Configure Public Access (Custom Domain)

> **This step applies ONLY to `neotool-assets-public` bucket.**
>
> The private bucket (`neotool-assets-private`) should remain with default settings - no public access.

For public assets (like institution logos, profile images), we need public HTTP access.

### 3.1 Connect Custom Domain (Public Bucket Only)

1. In R2 dashboard, click on `neotool-assets-public` bucket
2. Go to **Settings** tab
3. Under **Public access**, click **Connect Domain**
4. Enter your subdomain: `assets.yourdomain.com`
5. Click **Continue**
6. Cloudflare will automatically create the DNS record
7. Click **Connect domain**

### 3.2 Enable Caching (Recommended)

1. Still in bucket settings, under **Public access**
2. Enable **Cache** for better performance
3. Configure cache TTL as needed (default is fine)

### 3.3 Verify Public Access

After a few minutes:

```bash
# Upload a test file via dashboard or API
# Then verify public access
curl -I https://assets.yourdomain.com/test.txt
```

Expected: HTTP 200 response

---

## Step 4: Add Secrets to Vault

### 4.1 Connect to Vault

```bash
# Port-forward to Vault if needed
kubectl port-forward -n production svc/vault 8200:8200

# Set Vault address
export VAULT_ADDR=http://localhost:8200

# Authenticate (use your auth method)
vault login
```

### 4.2 Store R2 Credentials

```bash
vault kv put secret/assets \
  storage-hostname="<account-id>.r2.cloudflarestorage.com" \
  storage-access-key="<your-r2-access-key-id>" \
  storage-secret="<your-r2-secret-access-key>" \
  storage-public-base-path="https://assets.yourdomain.com" \
  jwt-jwks-url="http://security-service.production.svc.cluster.local:8080/.well-known/jwks.json"
```

### 4.3 Verify Secret

```bash
vault kv get secret/assets
```

Expected output:
```
====== Data ======
Key                       Value
---                       -----
jwt-jwks-url              http://security-service...
storage-access-key        xxxxxx...
storage-hostname          xxxxxx.r2.cloudflarestorage.com
storage-public-base-path  https://assets.yourdomain.com
storage-secret            xxxxxx...
```

---

## Step 5: Deploy Assets Service

### 5.1 Build and Push Docker Image

```bash
cd invistus/service/kotlin

# Build the image
docker build -t ghcr.io/invistus/neotool-assets:v0.1.0 -f assets/Dockerfile .

# Push to registry
docker push ghcr.io/invistus/neotool-assets:v0.1.0
```

### 5.2 Verify Kubernetes Manifests

Ensure these files exist in `invistus-flux`:

```
invistus-flux/infra/kubernetes/flux/
├── apps/services/assets/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── kustomization.yaml
└── infrastructure/external-secrets-config/
    └── assets-external-secret.yaml
```

### 5.3 Commit and Push

```bash
cd invistus-flux
git add .
git commit -m "Add assets service deployment with R2 configuration"
git push
```

### 5.4 Verify Deployment

```bash
# Wait for Flux to reconcile (or force it)
flux reconcile kustomization apps --with-source

# Check ExternalSecret status
kubectl get externalsecret -n production assets-storage-config

# Check deployment
kubectl get deployment -n production neotool-assets
kubectl get pods -n production -l app=neotool-assets

# Check logs
kubectl logs -n production -l app=neotool-assets --tail=100
```

---

## Validation Checklist

### R2 Buckets

- [ ] `neotool-assets-public` bucket created
- [ ] `neotool-assets-private` bucket created
- [ ] API token has read/write access to both buckets
- [ ] Custom domain connected to **public bucket only**
- [ ] Private bucket has **no public access** (default)

### Vault Secrets

- [ ] `secret/assets` contains all required keys
- [ ] ExternalSecret synced successfully:
  ```bash
  kubectl get externalsecret -n production assets-storage-config
  # Status should be "SecretSynced"
  ```

### Assets Service

- [ ] Pod is running:
  ```bash
  kubectl get pods -n production -l app=neotool-assets
  ```
- [ ] Health check passes:
  ```bash
  kubectl exec -n production deploy/neotool-assets -- curl -s localhost:8083/health
  ```
- [ ] Buckets validated on startup (check logs for "Bucket validation successful")

### End-to-End Test

```bash
# Port-forward to assets service
kubectl port-forward -n production svc/neotool-assets 8083:8083

# Test GraphQL endpoint (requires auth token)
curl -X POST http://localhost:8083/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"query": "{ __schema { types { name } } }"}'
```

### Upload Flow Test

1. **Create upload** (via GraphQL mutation) → Get presigned URL
2. **Upload file** to presigned URL (HTTP PUT)
3. **Confirm upload** (via GraphQL mutation)
4. **Verify public URL** works (for public assets)

---

## Configuration Reference

### Environment Variables

| Variable | R2 Value | Description |
|----------|----------|-------------|
| `STORAGE_HOSTNAME` | `<account-id>.r2.cloudflarestorage.com` | R2 endpoint (without protocol) |
| `STORAGE_PORT` | `443` | HTTPS port |
| `STORAGE_USE_HTTPS` | `true` | Required for R2 |
| `STORAGE_REGION` | `auto` | R2 uses "auto" region |
| `STORAGE_ACCESS_KEY` | From Vault | R2 API Access Key ID |
| `STORAGE_SECRET` | From Vault | R2 API Secret Access Key |
| `STORAGE_PUBLIC_BUCKET` | `neotool-assets-public` | Public assets bucket |
| `STORAGE_PRIVATE_BUCKET` | `neotool-assets-private` | Private assets bucket |
| `STORAGE_FORCE_PATH_STYLE` | `false` | R2 uses virtual-hosted style |
| `STORAGE_PUBLIC_BASE_PATH` | `https://assets.yourdomain.com` | Public URL prefix |
| `STORAGE_UPLOAD_TTL_SECONDS` | `900` | Presigned upload URL TTL (15 min) |

### Kubernetes Files

| File | Purpose |
|------|---------|
| `apps/services/assets/deployment.yaml` | Assets service deployment |
| `apps/services/assets/service.yaml` | ClusterIP service (port 8083) |
| `infrastructure/external-secrets-config/assets-external-secret.yaml` | Pulls R2 creds from Vault |

---

## Troubleshooting

### "NoSuchBucket" Error

**Cause**: Bucket doesn't exist or name mismatch

**Fix**:
1. Verify bucket names in Cloudflare R2 dashboard
2. Check `STORAGE_PUBLIC_BUCKET` and `STORAGE_PRIVATE_BUCKET` env vars match exactly

### "AccessDenied" Error

**Cause**: Invalid credentials or insufficient permissions

**Fix**:
1. Verify API token is active in Cloudflare
2. Check token has read/write access to both buckets
3. Verify credentials in Vault are correct
4. Check ExternalSecret synced:
   ```bash
   kubectl describe externalsecret -n production assets-storage-config
   ```

### "SignatureDoesNotMatch" Error

**Cause**: Incorrect secret key or encoding issue

**Fix**:
1. Regenerate API token in Cloudflare
2. Update Vault secret with new credentials
3. Restart the assets pod to pick up new secret

### Connection Timeout

**Cause**: Network issue or incorrect endpoint

**Fix**:
1. Verify `STORAGE_HOSTNAME` is correct (no `https://` prefix)
2. Verify `STORAGE_PORT` is `443`
3. Test connectivity from the cluster:
   ```bash
   kubectl run -n production test-curl --rm -it --image=curlimages/curl -- \
     curl -v https://<account-id>.r2.cloudflarestorage.com
   ```

### Public URL Returns 404

**Cause**: Custom domain not configured or object doesn't exist

**Fix**:
1. Verify custom domain is connected in R2 bucket settings
2. Check DNS resolves correctly:
   ```bash
   dig +short assets.yourdomain.com
   ```
3. Verify object exists in the bucket via Cloudflare dashboard

### Pod CrashLoopBackOff

**Cause**: Usually missing secrets or database connection

**Fix**:
1. Check pod logs:
   ```bash
   kubectl logs -n production -l app=neotool-assets --previous
   ```
2. Verify all secrets exist:
   ```bash
   kubectl get secret -n production assets-storage-config -o yaml
   kubectl get secret -n production postgres-credentials -o yaml
   ```

---

## Data Migration (Optional)

If you have existing data in MinIO that needs to be migrated to R2.

### Using rclone

```bash
# Install rclone
brew install rclone  # macOS
# or
curl https://rclone.org/install.sh | sudo bash  # Linux

# Configure MinIO remote
rclone config
# Name: minio
# Type: s3
# Provider: Minio
# Access Key: minioadmin
# Secret Key: minioadmin
# Endpoint: http://localhost:9000

# Configure R2 remote
rclone config
# Name: r2
# Type: s3
# Provider: Cloudflare
# Access Key: <r2-access-key>
# Secret Key: <r2-secret-key>
# Endpoint: https://<account-id>.r2.cloudflarestorage.com

# Sync public bucket
rclone sync minio:neotool-assets-public r2:neotool-assets-public --progress

# Sync private bucket
rclone sync minio:neotool-assets-private r2:neotool-assets-private --progress
```

### Verify Migration

```bash
# List objects in R2
rclone ls r2:neotool-assets-public
rclone ls r2:neotool-assets-private
```

---

## Quick Reference

### Useful Commands

```bash
# Check R2 connectivity (using AWS CLI)
aws s3 ls \
  --endpoint-url https://<account-id>.r2.cloudflarestorage.com \
  --profile r2

# List bucket contents
aws s3 ls s3://neotool-assets-public \
  --endpoint-url https://<account-id>.r2.cloudflarestorage.com \
  --profile r2

# Check assets service logs
kubectl logs -n production -l app=neotool-assets -f

# Restart assets service (pick up new secrets)
kubectl rollout restart deployment/neotool-assets -n production

# Check ExternalSecret status
kubectl get externalsecret -n production
```

### Links

- [Cloudflare R2 Documentation](https://developers.cloudflare.com/r2/)
- [R2 S3 API Compatibility](https://developers.cloudflare.com/r2/api/s3/api/)
- [AWS SDK for Kotlin](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/home.html)
