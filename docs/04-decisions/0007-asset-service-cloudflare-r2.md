---
title: ADR-0007 Asset Service with Cloudflare R2
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/assets/src/main/kotlin/io/github/salomax/neotool/assets/storage/S3StorageClient.kt
  - service/kotlin/assets/src/main/kotlin/io/github/salomax/neotool/assets/storage/StorageConfig.kt
  - contracts/graphql/supergraph/supergraph.graphql
load_when:
  - storing user/binary assets
  - changing object-storage provider
supersedes:
schema: docs-metadata-v1
---

# ADR-0007: Asset Service with Cloudflare R2

Purpose: record the durable decision and consequences for asset service with cloudflare r2.

## Status

accepted

## Context

Binary assets need durable object storage with pre-signed upload flows and provider flexibility.

## Decision

Own asset metadata in the `assets` Micronaut service. Store bytes in S3-compatible object storage, with Cloudflare R2 as the production target and MinIO-compatible config for local/dev. Expose create/confirm upload GraphQL mutations with pre-signed URLs.

## Consequences

- Provider abstraction via `StorageClient` / `S3StorageClient`.
- Clients upload directly to object storage; service confirms completion.

## Evidence

- `S3StorageClient` documents AWS S3, Cloudflare R2, and MinIO support.
- `StorageConfig` configures S3-compatible clients for MinIO/R2.
- Supergraph documents `createAssetUpload` pre-signed URL flow.

## Related

- [README](README.md)
- [Cloudflare R2 runbook](../05-runbooks/README.md)
