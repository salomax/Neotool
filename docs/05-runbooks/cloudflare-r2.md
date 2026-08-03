---
title: Cloudflare R2 Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/assets/src/main/kotlin/io/github/salomax/neotool/assets/storage/S3StorageClient.kt
  - service/kotlin/assets/src/main/kotlin/io/github/salomax/neotool/assets/storage/StorageConfig.kt
  - infra/terraform/cloud/modules/storage/cloudflare-r2.tf
load_when:
  - configuring assets object storage for R2
  - debugging pre-signed upload failures against R2
supersedes:
schema: docs-metadata-v1
---

# Cloudflare R2 Runbook

Purpose: operate S3-compatible Cloudflare R2 storage used by the assets service.

## Symptoms

- Asset uploads fail with signature/endpoint errors.
- Public asset URLs 404 after bucket or token rotation.

## Authority

Assets service `S3StorageClient` / `StorageConfig` (MinIO locally, R2 in prod). Terraform helper `infra/terraform/cloud/modules/storage/cloudflare-r2.tf` for related R2 usage.

## Commands

1. Create/verify R2 buckets and API tokens in Cloudflare.
2. Store endpoint, access key, secret, and bucket names in the environment secret store (Vault/K8s secrets) consumed by `assets`.
3. Confirm `StorageConfig` points at the R2 endpoint (`*.r2.cloudflarestorage.com` or account-specific endpoint).
4. Exercise `createAssetUpload` → client PUT → confirm mutation against a non-prod bucket first.

## Rollback

- Restore previous R2 credentials/buckets in secrets.
- Point assets config back to the last known-good endpoint/buckets.
- For local recovery, use MinIO-compatible settings from compose/env.

## Verification

- Pre-signed upload succeeds end-to-end.
- Object exists in the expected bucket/key.
- Public/private visibility matches namespace configuration.

## Related

- [ADR-0007](../04-decisions/0007-asset-service-cloudflare-r2.md)
- [Cloudflare](cloudflare.md)
- [Vault](vault.md)
