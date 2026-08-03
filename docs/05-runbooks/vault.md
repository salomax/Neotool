---
title: Vault Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/vault/init-vault.sh
  - infra/kubernetes/scripts/vault-setup.sh
load_when:
  - initializing JWT keys in Vault
  - unsealing or bootstrapping Vault via in-repo scripts
supersedes:
  - docs/09-security/vault-setup.md
  - docs/93-reference/vault-setup.md
schema: docs-metadata-v1
---

# Vault Runbook

Purpose: bootstrap and verify Vault JWT key material using scripts that exist in this repo.

## Symptoms

- Security service cannot load JWT signing keys.
- Fresh environment needs KV secrets seeded.
- Current docs avoid missing `infra/kubernetes/base/vault/` paths.

## Authority

Canonical scripts: `infra/vault/init-vault.sh` (KV seed/generate keys) and `infra/kubernetes/scripts/vault-setup.sh` (cluster init/unseal helper). Do **not** `kubectl apply -f infra/kubernetes/base/vault/` — that path is absent.

## Commands

```bash
# Local/container seed (requires VAULT_ADDR + VAULT_TOKEN)
export VAULT_ADDR=...
export VAULT_TOKEN=...
sh infra/vault/init-vault.sh

# Cluster helper (requires kubectl context)
infra/kubernetes/scripts/vault-setup.sh --namespace production
```

## Rollback

- Keep a backup of unseal/root materials outside the repo before re-init.
- Re-seed JWT keys only when intentionally rotating; coordinate security service restart.

## Verification

- `vault status` reachable.
- `vault kv get` succeeds for the configured JWT key path (`secret/jwt/keys/...` defaults in `init-vault.sh`).
- Security service authenticates/signs after reload.

## Related

- [Security architecture](../01-architecture/security.md)
- [Kubernetes operations](kubernetes-operations.md)
