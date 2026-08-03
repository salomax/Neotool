---
title: Cloudflare Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/terraform/cloud/modules/networking
load_when:
  - changing DNS/CDN/TLS fronting public hosts
  - rolling back a Cloudflare DNS cutover
supersedes:
  - historical Cloudflare runbook
schema: docs-metadata-v1
---

# Cloudflare Runbook

Purpose: operate Cloudflare DNS/CDN/TLS in front of cluster ingress.

## Symptoms

- Public hostname resolves incorrectly or SSL mode mismatches origin.
- CDN caching serves stale HTML/assets after a web release.

## Authority

Networking Terraform modules are the in-repo evidence. Exact dashboard clicks are operator UI; keep durable rules here and verify against live Cloudflare + ingress config before production changes.

## Commands

1. Confirm authoritative DNS for the hostname points at the intended Cloudflare zone.
2. Confirm SSL/TLS mode matches origin (typically Full/Strict when cert-manager terminates on cluster).
3. Purge cache for the hostname after web releases that must be immediate.
4. Keep Traefik/ingress and cert-manager changes coordinated with DNS cutovers.

Use Cloudflare dashboard or API tokens stored in the environment secret store — never commit tokens.

## Rollback

- Revert DNS records to the previous target.
- Disable proxy (grey-cloud) only as a temporary diagnosis step, then restore intended proxy state.
- Restore previous SSL/TLS encryption mode if changed.

## Verification

- `dig`/`nslookup` shows expected records.
- HTTPS certificate validates for the hostname.
- Origin health checks succeed through Cloudflare.

## Related

- [Cloudflare R2](cloudflare-r2.md)
- [Infrastructure](../01-architecture/infrastructure.md)
- [Web deployment](deployment-web.md)
